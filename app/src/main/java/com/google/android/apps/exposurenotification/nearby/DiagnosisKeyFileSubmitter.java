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

import android.content.Context;
import android.util.Log;
import com.google.android.apps.exposurenotification.common.AppExecutors;
import com.google.android.apps.exposurenotification.common.TaskToFutureAdapter;
import com.google.android.apps.exposurenotification.network.KeyFileBatch;
import com.google.android.apps.exposurenotification.network.KeyFileConstants;
import com.google.android.apps.exposurenotification.proto.TEKSignatureList;
import com.google.android.apps.exposurenotification.proto.TemporaryExposureKey;
import com.google.android.apps.exposurenotification.proto.TemporaryExposureKeyExport;
import com.google.common.io.BaseEncoding;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.apache.commons.io.IOUtils;
import org.threeten.bp.Duration;

/**
 * A thin class to take responsibility for submitting downloaded Diagnosis Key files to the Google
 * Play Services Exposure Notifications API.
 */
public class DiagnosisKeyFileSubmitter {
  private static final String TAG = "KeyFileSubmitter";
  private static final Duration PROVIDE_KEYS_TIMEOUT = Duration.ofMinutes(10);
  private static final BaseEncoding BASE16 = BaseEncoding.base16().lowerCase();
  private static final BaseEncoding BASE64 = BaseEncoding.base64();

  private final ExposureNotificationClientWrapper client;

  public DiagnosisKeyFileSubmitter(Context context) {
    client = ExposureNotificationClientWrapper.get(context);
  }

  /**
   * Accepts batches of key files, and submits them to provideDiagnosisKeys(), and returns a future
   * representing the completion of that task.
   *
   * <p>This naive implementation is not robust to individual failures. In fact, a single failure
   * will fail the entire operation. A more robust implementation would support retries, partial
   * completion, and other robustness measures.
   *
   * <p>Returns early if given an empty list of batches.
   */
  public ListenableFuture<?> submitFiles(List<KeyFileBatch> batches, String token) {
    if (batches.isEmpty()) {
      Log.d(TAG, "No files to provide to google play services.");
      return Futures.immediateFuture(null);
    }
    Log.d(TAG, "Providing  " + batches.size() + " diagnosis key batches to google play services.");
    List<ListenableFuture<?>> batchCompletions = new ArrayList<>();
    for (KeyFileBatch b : batches) {
      batchCompletions.add(submitBatch(b, token));
    }

    ListenableFuture<?> allDone = Futures.allAsList(batchCompletions);
    allDone.addListener(
        () -> {
          for (KeyFileBatch b : batches) {
            for (File f : b.files()) {
              f.delete();
            }
          }
        },
        AppExecutors.getBackgroundExecutor());

    return allDone;
  }

  private ListenableFuture<?> submitBatch(KeyFileBatch batch, String token) {
    logBatch(batch);
    return TaskToFutureAdapter.getFutureWithTimeout(
        client.provideDiagnosisKeys(batch.files(), token),
        PROVIDE_KEYS_TIMEOUT.toMillis(),
        TimeUnit.MILLISECONDS,
        AppExecutors.getScheduledExecutor());
  }

  private void logBatch(KeyFileBatch batch) {
    Log.d(
        TAG,
        "Submitting batch [" + batch.batchNum() + "] having [" + batch.files().size() + "] files.");
    int filenum = 1;
    for (File f : batch.files()) {
      try {
        FileContent fc = readFile(f);
        Log.d(TAG, "File " + filenum + " has signature:\n" + fc.signature);
        Log.d(TAG, "File " + filenum + " has [" + fc.export.getKeysCount() + "] keys.");
        for (TemporaryExposureKey k : fc.export.getKeysList()) {
          Log.d(
              TAG,
              "TEK hex:["
                  + BASE16.encode(k.getKeyData().toByteArray())
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
        Log.d(TAG, "Failed to read or parse file " + f, e);
      }
    }
  }

  private FileContent readFile(File file) throws IOException {
    ZipFile zip = new ZipFile(file);

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
