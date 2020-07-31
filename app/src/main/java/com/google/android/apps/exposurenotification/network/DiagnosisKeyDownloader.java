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

package com.google.android.apps.exposurenotification.network;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.concurrent.futures.CallbackToFutureAdapter;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.Response.ErrorListener;
import com.android.volley.Response.Listener;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.HttpHeaderParser;
import com.google.android.apps.exposurenotification.common.AppExecutors;
import com.google.common.collect.ImmutableList;
import com.google.common.io.BaseEncoding;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.commons.io.FileUtils;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;
import org.threeten.bp.Duration;

/**
 * A class to download all the files in a given batch of Diagnosis Key files.
 *
 * <p>This sample implementation focuses on demonstrating the server API and how to retrieve files
 * from it. It simply downloads all files available for the user's applicable regions every time it
 * is called. This does not address the different batching or interval strategies that a production
 * app might implement.
 *
 * <p>A production implementation should remember past keyfiles it has successfully downloaded and
 * provided to the EN API for matching, then skip those files in future. In production. each keyfile
 * need only be provided to the EN API once.
 */
class DiagnosisKeyDownloader {

  private static final String TAG = "KeyDownloader";
  private static final SecureRandom RAND = new SecureRandom();
  private static final BaseEncoding BASE32 = BaseEncoding.base32().lowerCase().omitPadding();

  private static final String FILE_PATTERN = "/diag_keys/%s/keys_%s.zip";
  private static final Duration DOWNLOAD_ALL_FILES_TIMEOUT = Duration.ofMinutes(30);

  private static final Duration SINGLE_FILE_TIMEOUT = Duration.ofSeconds(30);
  private static final int MAX_RETRIES = 3;
  private static final float RETRY_BACKOFF = 1.0f;

  private final Context context;
  private final CountryCodes countries;
  private final Uris uris;
  private final RequestQueueWrapper queue;

  DiagnosisKeyDownloader(Context context) {
    this.context = context;
    countries = new CountryCodes(context);
    uris = new Uris(context);
    queue = RequestQueueWrapper.wrapping(RequestQueueSingleton.get(context));
  }

  DiagnosisKeyDownloader(
      Context context,
      CountryCodes countries,
      Uris uris,
      RequestQueueWrapper queue) {
    this.context = context;
    this.countries = countries;
    this.uris = uris;
    this.queue = queue;
  }

  /**
   * Downloads all available files of Diagnosis Keys for the currently applicable regions and
   * returns a future with a list of all the batches of files.
   */
  ListenableFuture<ImmutableList<KeyFileBatch>> download() {
    String dir = randDirname();

    ListenableFuture<ImmutableList<KeyFileBatch>> batchesDownloaded =
        // Start with the relevant country codes for the user.
        FluentFuture.from(uris.getDownloadFileUris(countries.getExposureRelevantCountryCodes()))
            // Now initiate file downloads for each URI in each of those batches.
            .transformAsync(
                uriBatches -> initiateDownloads(uriBatches, dir),
                AppExecutors.getBackgroundExecutor())
            // Having completed all those downloads, group them back into batches for submission
            // batch-by-batch to the Exposure Notifications API.
            .transform(this::groupAsBatches, AppExecutors.getBackgroundExecutor())
            // It's important to have a timeout since we're waiting for network operations that may
            // or may not complete.
            .withTimeout(
                DOWNLOAD_ALL_FILES_TIMEOUT.toMillis(),
                TimeUnit.MILLISECONDS,
                AppExecutors.getScheduledExecutor());

    // Add a callback just to log success/failure.
    Futures.addCallback(batchesDownloaded, LOG_OUTCOME, AppExecutors.getLightweightExecutor());

    return batchesDownloaded;
  }

  private ListenableFuture<List<BatchFile>> initiateDownloads(
      List<KeyFileBatch> batches, String dir) {
    List<ListenableFuture<BatchFile>> batchFiles = new ArrayList<>();
    int fileCounter = 1;
    for (KeyFileBatch b : batches) {
      for (Uri uri : b.uris()) {
        batchFiles.add(downloadAndSave(b, uri, dir, fileCounter++));
      }
    }
    return Futures.allAsList(batchFiles);
  }

  private ListenableFuture<BatchFile> downloadAndSave(
      KeyFileBatch batch, Uri uri, String dir, int fileCounter) {
    return FluentFuture.from(downloadFile(uri))
        .transformAsync(
            bytes -> saveKeyFile(batch, bytes, dir, fileCounter),
            AppExecutors.getBackgroundExecutor());
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
                completer.setCancelled();
              };

          Log.d(TAG, "Downloading keyfile file from " + uri);
          ByteArrayRequest request = new ByteArrayRequest(uri, responseListener, errorListener);
          queue.add(request);
          return request;
        });
  }

  private ListenableFuture<BatchFile> saveKeyFile(
      KeyFileBatch batch, byte[] content, String dir, int fileCounter) {
    String filename = String.format(FILE_PATTERN, dir, fileCounter);
    File toFile = new File(context.getFilesDir(), filename);
    try {
      FileUtils.writeByteArrayToFile(toFile, content);
      return Futures.immediateFuture(new BatchFile(batch, toFile));
    } catch (IOException e) {
      return Futures.immediateFailedFuture(e);
    }
  }

  /**
   * Here's where, after downloading each file, we group them back into {@link KeyFileBatch}es.
   */
  private ImmutableList<KeyFileBatch> groupAsBatches(List<BatchFile> batchFiles) {
    // Collect the downloaded files per KeyFileBatch
    Map<KeyFileBatch, List<File>> collector = new HashMap<>();
    for (BatchFile bf : batchFiles) {
      if (!collector.containsKey(bf.batch)) {
        collector.put(bf.batch, new ArrayList<>());
      }
      collector.get(bf.batch).add(bf.file);
    }
    // And build them back into batches, this time with their files.
    ImmutableList.Builder<KeyFileBatch> builder = ImmutableList.builder();
    for (Map.Entry<KeyFileBatch, List<File>> e : collector.entrySet()) {
      builder.add(e.getKey().copyWith(e.getValue()));
    }
    return builder.build();
  }

  private static String randDirname() {
    byte[] bytes = new byte[8];
    RAND.nextBytes(bytes);
    return BASE32.encode(bytes);
  }

  /**
   * A request for the raw bytes of a keyfile.
   */
  private static class ByteArrayRequest extends Request<byte[]> {

    private final Response.Listener<byte[]> listener;

    public ByteArrayRequest(
        Uri uri, Response.Listener<byte[]> listener, ErrorListener errorListener) {
      super(Method.GET, uri.toString(), errorListener);
      this.listener = listener;
      setRetryPolicy(
          new DefaultRetryPolicy((int) SINGLE_FILE_TIMEOUT.toMillis(), MAX_RETRIES, RETRY_BACKOFF));
    }

    @Override
    protected Response<byte[]> parseNetworkResponse(NetworkResponse response) {
      return response.statusCode < 400
          ? Response.success(response.data, HttpHeaderParser.parseCacheHeaders(response))
          : Response.error(new VolleyError(response));
    }

    @Override
    protected void deliverResponse(byte[] response) {
      listener.onResponse(response);
    }
  }

  /**
   * A {@link File} that knows which {@link KeyFileBatch} it belongs to.
   */
  private static class BatchFile {

    private final KeyFileBatch batch;
    private final File file;

    private BatchFile(KeyFileBatch batch, File file) {
      this.batch = batch;
      this.file = file;
    }
  }

  private static FutureCallback<ImmutableList<KeyFileBatch>> LOG_OUTCOME =
      new FutureCallback<ImmutableList<KeyFileBatch>>() {
        @Override
        public void onSuccess(@NullableDecl ImmutableList<KeyFileBatch> result) {
          Log.i(TAG, "Key file download succeeded.");
        }

        @Override
        public void onFailure(@NonNull Throwable t) {
          Log.e(TAG, "Key file download failed.");
        }
      };
}
