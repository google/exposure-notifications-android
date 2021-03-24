/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.google.android.apps.exposurenotification.keydownload;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.concurrent.futures.CallbackToFutureAdapter;
import com.android.volley.Response.ErrorListener;
import com.android.volley.Response.Listener;
import com.google.android.apps.exposurenotification.common.Qualifiers.BackgroundExecutor;
import com.google.android.apps.exposurenotification.common.Qualifiers.LightweightExecutor;
import com.google.android.apps.exposurenotification.common.Qualifiers.ScheduledExecutor;
import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.android.apps.exposurenotification.keydownload.Qualifiers.HomeDownloadUriPair;
import com.google.android.apps.exposurenotification.keydownload.Qualifiers.TravellerDownloadUriPairs;
import com.google.android.apps.exposurenotification.logging.AnalyticsLogger;
import com.google.android.apps.exposurenotification.network.RequestQueueWrapper;
import com.google.android.apps.exposurenotification.network.RespondableByteArrayRequest;
import com.google.android.apps.exposurenotification.network.VolleyUtils;
import com.google.android.apps.exposurenotification.proto.RpcCall.RpcCallType;
import com.google.android.apps.exposurenotification.roaming.CountryCodes;
import com.google.common.collect.ImmutableList;
import com.google.common.io.BaseEncoding;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.hilt.android.qualifiers.ApplicationContext;
import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import org.apache.commons.io.FileUtils;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;
import org.threeten.bp.Duration;

/**
 * A class to download Diagnosis Key files from one or more servers.
 *
 * <p>Consults one service for users who not travelled outside their home region in the past 14 days
 * (as far as we can tell very roughly from cell network country codes). Consults roaming servers
 * for travellers, if the app is configured to do so by the user's home health authority.
 */
public class DiagnosisKeyDownloader {

  private static final String TAG = "KeyDownloader";
  private static final SecureRandom RAND = new SecureRandom();
  private static final BaseEncoding BASE32 = BaseEncoding.base32().lowerCase().omitPadding();

  private static final String FILE_PATTERN = "/diag_keys/%s/keys_%s.zip";
  private static final Duration DOWNLOAD_ALL_FILES_TIMEOUT = Duration.ofMinutes(30);

  private final Context context;
  private final CountryCodes countryCodes;
  private final KeyFileUriResolver keyFileUriResolver;
  private final DownloadUriPair homeDownloadUris;
  private final Map<String, List<DownloadUriPair>> travellerDownloadUriPairs;
  private final RequestQueueWrapper requestQueueWrapper;
  private final ExecutorService backgroundExecutor;
  private final ExecutorService lightweightExecutor;
  private final ScheduledExecutorService scheduledExecutor;
  private final AnalyticsLogger logger;
  private final Clock clock;

  @Inject
  DiagnosisKeyDownloader(
      @ApplicationContext Context context,
      RequestQueueWrapper requestQueueWrapper,
      CountryCodes countryCodes,
      KeyFileUriResolver keyFileUriResolver,
      @HomeDownloadUriPair DownloadUriPair homeDownloadUris,
      @TravellerDownloadUriPairs Map<String, List<DownloadUriPair>> travellerDownloadUriPairs,
      @BackgroundExecutor ExecutorService backgroundExecutor,
      @LightweightExecutor ExecutorService lightweightExecutor,
      @ScheduledExecutor ScheduledExecutorService scheduledExecutor,
      AnalyticsLogger logger,
      Clock clock) {
    this.context = context;
    this.requestQueueWrapper = requestQueueWrapper;
    this.countryCodes = countryCodes;
    this.keyFileUriResolver = keyFileUriResolver;
    this.homeDownloadUris = homeDownloadUris;
    this.travellerDownloadUriPairs = travellerDownloadUriPairs;
    this.backgroundExecutor = backgroundExecutor;
    this.lightweightExecutor = lightweightExecutor;
    this.scheduledExecutor = scheduledExecutor;
    this.logger = logger;
    this.clock = clock;
  }

  /**
   * Downloads all available files of Diagnosis Keys for the currently applicable regions and
   * returns a future with a list of all the batches of files.
   */
  public ListenableFuture<ImmutableList<KeyFile>> download() {
    ImmutableList.Builder<DownloadUriPair> keyserversToCall =
        ImmutableList.<DownloadUriPair>builder().add(homeDownloadUris);
    // Did the user travel outside their home region in the last 14 days? If so, include their HA's
    // traveller URLs too.
    for (String countryCode : countryCodes.getExposureRelevantCountryCodes()) {
      if (travellerDownloadUriPairs.containsKey(countryCode)) {
        keyserversToCall.addAll(travellerDownloadUriPairs.get(countryCode));
      }
    }

    ListenableFuture<ImmutableList<KeyFile>> downloadedFiles =
        // Start with the user's home region download URIs.
        FluentFuture.from(keyFileUriResolver.resolve(keyserversToCall.build()))
            // Now initiate file downloads for each URI
            .transformAsync(
                this::initiateDownloads,
                backgroundExecutor)
            // It's important to have a timeout since we're waiting for network operations that may
            // or may not complete.
            .withTimeout(
                DOWNLOAD_ALL_FILES_TIMEOUT.toMillis(),
                TimeUnit.MILLISECONDS,
                scheduledExecutor);

    // Add a callback just to log success/failure.
    Futures.addCallback(downloadedFiles, LOG_OUTCOME, backgroundExecutor);

    return downloadedFiles;
  }

  private ListenableFuture<ImmutableList<KeyFile>> initiateDownloads(List<KeyFile> keyFiles) {
    String dir = randDirname();
    List<ListenableFuture<KeyFile>> downloadedFiles = new ArrayList<>();
    int fileCounter = 1;
    for (KeyFile file : keyFiles) {
      String path = String.format(FILE_PATTERN, dir, fileCounter++);
      downloadedFiles.add(downloadAndSave(file, path));
    }
    return FluentFuture.from(Futures.allAsList(downloadedFiles))
        .transform(ImmutableList::copyOf, lightweightExecutor);
  }

  private ListenableFuture<KeyFile> downloadAndSave(KeyFile keyFile, String path) {
    return FluentFuture.from(downloadFile(keyFile.uri()))
        .transformAsync(
            bytes -> {
              try {
                return Futures.immediateFuture(keyFile.with(saveKeyFile(bytes, path)));
              } catch (IOException e) {
                return Futures.immediateFailedFuture(e);
              }
            },
            backgroundExecutor);
  }

  private ListenableFuture<byte[]> downloadFile(Uri uri) {
    return CallbackToFutureAdapter.getFuture(
        completer -> {
          Listener<byte[]> responseListener =
              response -> {
                Log.d(
                    TAG,
                    "Keyfile " + uri + " successfully downloaded " + response.length + " bytes.");
                completer.set(response);
              };

          ErrorListener errorListener =
              err -> {
                Log.e(TAG, "Error getting keyfile " + uri);
                completer.setException(err);
              };

          Log.d(TAG, "Downloading keyfile file from " + uri);
          RespondableByteArrayRequest request =
              new RespondableByteArrayRequest(uri, responseListener, errorListener, clock);
          requestQueueWrapper.add(request);
          return request;
        });
  }

  private File saveKeyFile(byte[] content, String path) throws IOException {
    File toFile = new File(context.getFilesDir(), path);
    FileUtils.writeByteArrayToFile(toFile, content);
    return toFile;
  }

  private static String randDirname() {
    byte[] bytes = new byte[8];
    RAND.nextBytes(bytes);
    return BASE32.encode(bytes);
  }

  private final FutureCallback<ImmutableList<KeyFile>> LOG_OUTCOME =
      new FutureCallback<ImmutableList<KeyFile>>() {
        @Override
        public void onSuccess(@NullableDecl ImmutableList<KeyFile> files) {
          int totalBytesDownloaded = 0;
          for (KeyFile file : files) {
            totalBytesDownloaded += file.file().length();
          }
          logger.logRpcCallSuccess(RpcCallType.RPC_TYPE_KEYS_DOWNLOAD, totalBytesDownloaded);
        }

        @Override
        public void onFailure(@NonNull Throwable t) {
          logger.logRpcCallFailure(RpcCallType.RPC_TYPE_KEYS_DOWNLOAD, t);
          Log.d(TAG, VolleyUtils.getErrorBodyWithoutPadding(t).toString());
        }
      };
}
