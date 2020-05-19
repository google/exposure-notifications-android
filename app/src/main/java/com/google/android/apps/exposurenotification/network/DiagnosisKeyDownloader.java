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
import com.google.common.collect.ImmutableList;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
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
 */
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

  // A map of Downloads, keyed by download IDs (from DownloadManager).
  private final ConcurrentMap<Long, Download> downloadMap = new ConcurrentHashMap<>();

  DiagnosisKeyDownloader(Context context) {
    this.context = context;
    countries = new CountryCodes(context);
    downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
    uris = new Uris(context);
  }

  /**
   * Downloads all available files of Diagnosis Keys for the currently applicable regions and
   * returns a future with a list of all the files.
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
  ListenableFuture<ImmutableList<KeyFileBatch>> download() {
    String dir = randDirname();

    context.registerReceiver(
        downloadStatusReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));

    ListenableFuture<ImmutableList<KeyFileBatch>> batchesDownloaded =
        // Start with the relevant country codes for the user.
        FluentFuture.from(countries.getExposureRelevantCountryCodes())
            // Get the network locations of all the files we need to download for those
            // countries/regions, as batches of URIs.
            .transformAsync(uris::getDownloadFileUris, AppExecutors.getLightweightExecutor())
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
                TIMEOUT.toMillis(), TimeUnit.MILLISECONDS, AppExecutors.getScheduledExecutor());

    // Add a callback just to log success/failure.
    Futures.addCallback(batchesDownloaded, logOutcome, AppExecutors.getLightweightExecutor());

    // Add a listener to clean up the receiver.
    batchesDownloaded.addListener(
        () -> context.unregisterReceiver(downloadStatusReceiver),
        AppExecutors.getLightweightExecutor());

    return batchesDownloaded;
  }

  private ListenableFuture<List<BatchFile>> initiateDownloads(
      List<KeyFileBatch> batches, String dir) {
    List<ListenableFuture<BatchFile>> batchFiles = new ArrayList<>();
    for (KeyFileBatch b : batches) {
      batchFiles.addAll(handleBatch(b, dir));
    }
    return Futures.allAsList(batchFiles);
  }

  /**
   * Initiates download of all the files in the given batch and returns a list of futures, one for
   * each file.
   *
   * <p>Each returned future will complete when the file download is complete (or fails). The value
   * of each returned future is a {@link BatchFile}, which just pairs a {@link File} with the {@link
   * KeyFileBatch} it belongs to so that we can more easily group them by batch later in this
   * download process.
   */
  private List<ListenableFuture<BatchFile>> handleBatch(KeyFileBatch batch, String dir) {
    // Start a separate DownloadManager operation for each file.
    Log.i(TAG, "Start " + batch.uris().size() + " key file downloads.");
    List<ListenableFuture<BatchFile>> files = new ArrayList<>();
    for (Uri uri : batch.uris()) {
      DownloadManager.Request req =
          new DownloadManager.Request(uri)
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
      Download d = new Download(batch, downloadId, dir);
      files.add(d.fileFuture);
      downloadMap.put(downloadId, d);
    }
    return files;
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

  /** A {@link BroadcastReceiver} to receive the results of each file download. */
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
          if (!downloadMap.containsKey(downloadId)) {
            return;
          }
          download = downloadMap.get(downloadId);

          Query q = new Query();
          q.setFilterById(downloadId);
          Cursor c = downloadManager.query(q);
          if (c.moveToFirst()) {
            int columnIndex = c.getColumnIndex(DownloadManager.COLUMN_STATUS);
            if (DownloadManager.STATUS_SUCCESSFUL == c.getInt(columnIndex)) {
              // We have a file.
              Uri uri = downloadManager.getUriForDownloadedFile(download.downloadId);
              try (InputStream stream = context.getContentResolver().openInputStream(uri)) {
                // Great. Now copy the file to app-specific storage.
                String filename = String.format(FILE_PATTERN, download.dir, downloadId);
                File toFile = new File(context.getFilesDir(), filename);
                FileUtils.copyInputStreamToFile(stream, toFile);
                // And complete the Download's SettableFuture with the file's ultimate destination.
                download.succeed(toFile);
                // Then remove the original from DownloadManager.
                downloadManager.remove(downloadId);
              } catch (IOException | NullPointerException e) {
                // Failed to get the downloaded file, or to copy it. Fail the future.
                download.fail(e);
              }
            } else {
              // This download failed. We fail the future.
              // TODO: Use some other exception.
              download.fileFuture.setException(new FileNotFoundException());
            }
          }

          downloadMap.remove(downloadId);
        }
      };

  /** A {@link File} that knows which {@link KeyFileBatch} it belongs to. */
  private static class BatchFile {
    private final KeyFileBatch batch;
    private final File file;

    private BatchFile(KeyFileBatch batch, File file) {
      this.batch = batch;
      this.file = file;
    }
  }

  /** A simple value class for holding all the details of a single downloaded file. */
  private static class Download {
    private final KeyFileBatch batch;
    private final long downloadId;
    // The dir is really the same for all downloads in a single "run", but this is a convenient
    // place to retain it for the benefit of the BroadcastReceiver that does the post-download copy.
    private final String dir;
    // SettableFutures can be error prone. CallbackToFutureAdapter would be better.
    // TODO: Figure a way to use CallbackToFutureAdapter or similar.
    private final SettableFuture<BatchFile> fileFuture = SettableFuture.create();

    private Download(KeyFileBatch batch, long downloadId, String dir) {
      this.batch = batch;
      this.downloadId = downloadId;
      this.dir = dir;
    }

    private void succeed(File f) {
      fileFuture.set(new BatchFile(batch, f));
    }

    private void fail(Throwable t) {
      fileFuture.setException(t);
    }
  }

  private FutureCallback<ImmutableList<KeyFileBatch>> logOutcome =
      new FutureCallback<ImmutableList<KeyFileBatch>>() {
        @Override
        public void onSuccess(@NullableDecl ImmutableList<KeyFileBatch> result) {
          Log.i(TAG, "Key file download succeeded.");
        }

        @Override
        public void onFailure(Throwable t) {
          Log.e(TAG, "Key file download failed.");
        }
      };
}
