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

package com.google.android.apps.exposurenotification.nearby;

import com.google.android.apps.exposurenotification.common.Qualifiers.BackgroundExecutor;
import com.google.android.apps.exposurenotification.common.Qualifiers.ScheduledExecutor;
import com.google.android.apps.exposurenotification.common.TaskToFutureAdapter;
import com.google.android.apps.exposurenotification.common.logging.Logger;
import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.android.apps.exposurenotification.keydownload.KeyFile;
import com.google.android.apps.exposurenotification.keydownload.KeyFileConstants;
import com.google.android.apps.exposurenotification.proto.TEKSignatureList;
import com.google.android.apps.exposurenotification.proto.TemporaryExposureKey;
import com.google.android.apps.exposurenotification.proto.TemporaryExposureKeyExport;
import com.google.android.apps.exposurenotification.storage.DownloadServerEntity;
import com.google.android.apps.exposurenotification.storage.DownloadServerRepository;
import com.google.android.apps.exposurenotification.storage.ExposureCheckEntity;
import com.google.android.apps.exposurenotification.storage.ExposureCheckRepository;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.common.collect.ImmutableList;
import com.google.common.io.BaseEncoding;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.inject.Inject;
import org.apache.commons.io.IOUtils;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;
import org.threeten.bp.Duration;

/**
 * A thin class to take responsibility for submitting downloaded Diagnosis Key files to the Google
 * Play Services Exposure Notifications API.
 */
public class DiagnosisKeyFileSubmitter {

  private static final Logger logger = Logger.getLogger("KeyFileSubmitter");
  // Use a very very long timeout, in case of a stress-test that supplies a very large number of
  // diagnosis key files.
  private static final Duration PROVIDE_KEYS_TIMEOUT = Duration.ofMinutes(60);
  private static final BaseEncoding BASE16 = BaseEncoding.base16().lowerCase();
  private static final BaseEncoding BASE64 = BaseEncoding.base64();

  private final ExposureNotificationClientWrapper exposureNotificationClientWrapper;
  private final DownloadServerRepository downloadServerRepo;
  private final ExposureCheckRepository exposureCheckRepo;
  private final ExposureNotificationSharedPreferences preferences;
  private final ExecutorService backgroundExecutor;
  private final ScheduledExecutorService scheduledExecutor;
  private final Clock clock;

  @Inject
  DiagnosisKeyFileSubmitter(
      ExposureNotificationClientWrapper exposureNotificationClientWrapper,
      DownloadServerRepository downloadServerRepo,
      ExposureCheckRepository exposureCheckRepo,
      ExposureNotificationSharedPreferences preferences,
      Clock clock,
      @BackgroundExecutor ExecutorService backgroundExecutor,
      @ScheduledExecutor ScheduledExecutorService scheduledExecutor) {
    this.exposureNotificationClientWrapper = exposureNotificationClientWrapper;
    this.downloadServerRepo = downloadServerRepo;
    this.exposureCheckRepo = exposureCheckRepo;
    this.preferences = preferences;
    this.clock = clock;
    this.backgroundExecutor = backgroundExecutor;
    this.scheduledExecutor = scheduledExecutor;
  }

  /**
   * Accepts key files, and submits them to provideDiagnosisKeys(), and returns a future
   * representing the completion of that task.
   *
   * <p>This naive implementation is not robust to individual failures. In fact, a single failure
   * will fail the entire operation. A more robust implementation would support retries, partial
   * completion, and other robustness measures.
   *
   * <p>Returns early if given an empty list of batches.
   */
  public ListenableFuture<?> submitFiles(ImmutableList<KeyFile> keyFiles) {
    if (keyFiles.isEmpty()) {
      logger.d("No files to provide to google play services.");
      return Futures.immediateFuture(null);
    }
    logger.d("Providing  " + keyFiles.size() + " diagnosis key files to google play services.");

    // Log submitted keys only in debug and when debug settings are looking for a certain key.
    if (!preferences.getProvidedDiagnosisKeyHexToLog().isEmpty()) {
      logger.d("Logging keyfiles; keys limited to those containing ["
          + preferences.getProvidedDiagnosisKeyHexToLog() + "] (hex).");
      logKeys(keyFiles, preferences.getProvidedDiagnosisKeyHexToLog());
    }

    ListenableFuture<Void> allDone =
        TaskToFutureAdapter.getFutureWithTimeout(
            exposureNotificationClientWrapper.provideDiagnosisKeys(filesFrom(keyFiles)),
            PROVIDE_KEYS_TIMEOUT,
            scheduledExecutor);

    Futures.addCallback(allDone, new FutureCallback<Void>() {
      @Override
      public void onSuccess(@NullableDecl Void result) {
        for (KeyFile f : keyFiles) {
          if (f.isMostRecent()) {
            // On success, remember the last successful file for each server.
            logger.d(String.format(
                "Mark last successful download [%s] for server [%s]", f.uri(), f.index()));
            downloadServerRepo.upsert(DownloadServerEntity.create(f.index(), f.uri()));
          }
          // and delete all files locally...
          f.file().delete();
        }
        // and, finally, capture time of the exposure check.
        exposureCheckRepo.insertExposureCheck(ExposureCheckEntity.create(clock.now()));
      }

      @Override
      public void onFailure(Throwable t) {
        for (KeyFile f : keyFiles) {
          // After failures, only delete the local files.
          f.file().delete();
        }
      }
    }, backgroundExecutor);

    return allDone;
  }

  private static List<File> filesFrom(List<KeyFile> keyFiles) {
    List<File> files = new ArrayList<>();
    for (KeyFile f : keyFiles) {
      files.add(f.file());
    }
    return files;
  }

  private void logKeys(ImmutableList<KeyFile> files, String keyHexToLog) {
    int filenum = 1;
    for (KeyFile f : files) {
      try {
        FileContent fc = readFile(f.file());
        logger.d("File " + filenum + " has signature:\n" + fc.signature);
        logger.d("File " + filenum + " has [" + fc.export.getKeysCount() + "] keys.");
        for (TemporaryExposureKey k : fc.export.getKeysList()) {
          // We don't log all keys. Sometimes that's too much to log. Log only keys matching a hex
          // substring we're interested in for debug purposes.
          String keyHex = BASE16.encode(k.getKeyData().toByteArray());
          if (!keyHex.contains(keyHexToLog.toLowerCase())) {
            continue;
          }
          logger.d(
              "TEK hex:["
                  + keyHex
                  + "] base64:["
                  + BASE64.encode(k.getKeyData().toByteArray())
                  + "] interval_num:["
                  + k.getRollingStartIntervalNumber()
                  + "] rolling_period:["
                  + k.getRollingPeriod()
                  + "] risk:["
                  + k.getTransmissionRiskLevel()
                  + "]");
        }
        filenum++;
      } catch (IOException e) {
        logger.d("Failed to read or parse file " + f, e);
      }
    }
  }

  private FileContent readFile(File file) throws IOException {
    try (ZipFile zip = new ZipFile(file)) {
      ZipEntry signatureEntry = zip.getEntry(KeyFileConstants.SIG_FILENAME);
      ZipEntry exportEntry = zip.getEntry(KeyFileConstants.EXPORT_FILENAME);

      byte[] sigData = IOUtils.toByteArray(zip.getInputStream(signatureEntry));
      byte[] bodyData = IOUtils.toByteArray(zip.getInputStream(exportEntry));

      byte[] header = Arrays.copyOf(bodyData, 16);
      byte[] exportData = Arrays.copyOfRange(bodyData, 16, bodyData.length);

      String headerString = new String(header);
      TEKSignatureList signature = TEKSignatureList.parseFrom(sigData);
      TemporaryExposureKeyExport export = TemporaryExposureKeyExport.parseFrom(exportData);

      return new FileContent(headerString, export, signature);
    }
  }

  private static class FileContent {

    private final String header;
    private final TemporaryExposureKeyExport export;
    private final TEKSignatureList signature;

    FileContent(String header, TemporaryExposureKeyExport export, TEKSignatureList signature) {
      this.export = export;
      this.header = header;
      this.signature = signature;
    }
  }
}
