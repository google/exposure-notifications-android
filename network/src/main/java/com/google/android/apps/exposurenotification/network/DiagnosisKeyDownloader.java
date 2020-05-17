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

import android.app.DownloadManager;
import android.app.DownloadManager.Query;
import android.app.DownloadManager.Request;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;
import com.google.android.apps.exposurenotification.common.AppExecutors;
import com.google.common.io.BaseEncoding;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import org.apache.commons.io.FileUtils;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;
import org.threeten.bp.Duration;

/** A class to download all the files in a given batch of Diagnosis Key files. */
class DiagnosisKeyDownloader {
  private static final String TAG = "KeyDownloader";
  private static final SecureRandom RAND = new SecureRandom();
  private static final BaseEncoding BASE32 = BaseEncoding.base32().lowerCase().omitPadding();

  private static final String FILE_PATTERN = "/diag_keys/%s/keys_%s.pb";
  // TODO: Set a reasonable timeout and make it adjustable.
  private static final Duration TIMEOUT = Duration.ofSeconds(600);

  private final Context context;
  private final CountryCodes countries;
  private final DownloadManager downloadManager;
  private final Uris uris;

  private final ConcurrentMap<Long, Download> diagnosisKeysDownloadIds = new ConcurrentHashMap<>();

  DiagnosisKeyDownloader(Context context) {
    this.context = context;
    countries = new CountryCodes(context);
    downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
    uris = new Uris(context);
  }

  /**
   * Downloads all the bundles of Diagnosis Keys for the current batch and returns a future with a
   * list of all the files.
   *
   * <p>Uses DownloadManager but there may be other solutions. DownloadManager initially downloads
   * the files in its default location then we copy them to app-specific storage. Times out the
   * whole operation after TIMEOUT duration.
   *
   * <p>TODO: Apply the timeout individually to each file instead.
   *
   * <p>Currently all files in a given batch fail or succeed as a group. This is also not ideal; it
   * would be better to support retrying only the failed downloads.
   */
  ListenableFuture<List<File>> download() {
    String dir = randDirname();

    context.registerReceiver(
        downloadStatusReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));

    ListenableFuture<List<File>> filesDownloaded =
        FluentFuture.from(countries.getExposureRelevantCountryCodes())
            .transformAsync(uris::getDownloadFileUris, AppExecutors.getLightweightExecutor())
            .transform(
                fileUris -> {
                  // Start a separate DownloadManager operation for each file.
                  Log.i(TAG, "Start " + fileUris.size() + " key file downloads.");
                  for (Uri uri : fileUris) {
                    DownloadManager.Request req =
                        new Request(uri)
                            // TODO: Consider applying some policies such as:
                            // .setAllowedNetworkTypes(Request.NETWORK_WIFI)
                            // .setAllowedOverMetered(false)
                            // .setRequiresCharging(true)
                            // .setRequiresDeviceIdle(true)
                            .setNotificationVisibility(Request.VISIBILITY_VISIBLE)
                            .setMimeType("application/octet-stream")
                            .setTitle("Exposure Notifications Check")
                            .setDescription("Exposure Notifications Check");

                    long downloadId = downloadManager.enqueue(req);
                    diagnosisKeysDownloadIds.put(downloadId, new Download(downloadId, dir));
                  }

                  // Pull out just the File instances for return to the caller.
                  List<ListenableFuture<File>> files = new ArrayList<>();
                  for (Download d : diagnosisKeysDownloadIds.values()) {
                    files.add(d.fileFuture);
                  }
                  return files;
                },
                AppExecutors.getBackgroundExecutor())
            .transformAsync(
                files ->
                    Futures.withTimeout(
                        Futures.allAsList(files),
                        TIMEOUT.toMillis(),
                        TimeUnit.MILLISECONDS,
                        AppExecutors.getScheduledExecutor()),
                AppExecutors.getBackgroundExecutor());

    // Add one callback just to log success/failure.
    Futures.addCallback(
        filesDownloaded,
        new FutureCallback<List<File>>() {
          @Override
          public void onSuccess(@NullableDecl List<File> result) {
            Log.i(TAG, "Key file download succeeded.");
          }

          @Override
          public void onFailure(Throwable t) {
            Log.e(TAG, "Key file download failed.");
          }
        },
        AppExecutors.getLightweightExecutor());

    // Add a listener to clean up the receiver.
    filesDownloaded.addListener(
        () -> context.unregisterReceiver(downloadStatusReceiver),
        AppExecutors.getLightweightExecutor());

    return filesDownloaded;
  }

  private static String randDirname() {
    byte[] bytes = new byte[8];
    RAND.nextBytes(bytes);
    return BASE32.encode(bytes);
  }

  /** */
  private final BroadcastReceiver downloadStatusReceiver =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          String action = intent.getAction();
          if (!DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(action)) {
            // This really shouldn't happen.
            return;
          }
          long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
          if (downloadId == -1) {
            // This is also unexpected.
            return;
          }

          // Grab the details of this download "chunk".
          Download download;
          if (!diagnosisKeysDownloadIds.containsKey(downloadId)) {
            return;
          }
          download = diagnosisKeysDownloadIds.get(downloadId);

          Query q = new Query();
          q.setFilterById(downloadId);
          Cursor c = downloadManager.query(q);
          if (c.moveToFirst()) {
            int columnIndex = c.getColumnIndex(DownloadManager.COLUMN_STATUS);
            if (DownloadManager.STATUS_SUCCESSFUL == c.getInt(columnIndex)) {
              // We have a file.
              Uri uri = downloadManager.getUriForDownloadedFile(download.id);
              try (InputStream from = context.getContentResolver().openInputStream(uri)) {
                // Great. Now copy the file to app-specific storage.
                String filename = String.format(FILE_PATTERN, download.dir, downloadId);
                File to = new File(context.getFilesDir(), filename);
                FileUtils.copyInputStreamToFile(from, to);
                // And complete the Download's SettableFuture with the file's ultimate destination.
                download.fileFuture.set(to);
                // Then remove the original from DownloadManager.
                downloadManager.remove(downloadId);
              } catch (IOException | NullPointerException e) {
                // Failed to get the downloaded file, or to copy it. Fail the future.
                download.fileFuture.setException(e);
              }
            } else {
              // This download failed. We fail the future.
              // TODO: Use some other exception.
              download.fileFuture.setException(new FileNotFoundException());
            }
          }

          diagnosisKeysDownloadIds.remove(downloadId);
        }
      };

  /** A simple value class for holding all the details of a single downloaded file. */
  private static class Download {
    private final long id;
    // The dir is really the same for all downloads in a single "run", but this is a convenient
    // place to retain it for the benefit of the BroadcastReceiver that does the post-download copy.
    private final String dir;
    // SettableFutures can be error prone. CallbackToFutureAdapter would be better.
    // TODO(someone): Figure a way to use CallbackToFutureAdapter or similar.
    private final SettableFuture<File> fileFuture = SettableFuture.create();

    private Download(long downloadId, String dir) {
      this.id = downloadId;
      this.dir = dir;
    }
  }
}
