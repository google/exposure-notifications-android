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
import com.google.android.apps.exposurenotification.network.DiagnosisKey;
import com.google.android.gms.nearby.exposurenotification.TemporaryExposureKey;
import com.google.android.gms.nearby.exposurenotification.TemporaryExposureKey.TemporaryExposureKeyBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.File;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.threeten.bp.Duration;

/**
 * A class to take responsibility for parsing downloaded Diagnosis Keys and submitting them to
 * Google Play Services.
 *
 * <p>This implementation is a fake that generates random keys instead of actually parsing files.
 * The implementor would flesh out the file parsing logic and likely enhance robustness in the face
 * of large data volumes, partial failures, etc.
 */
public class DiagnosisKeyFileSubmitter {
  private static final String TAG = "KeyFileSubmitter";
  private static final SecureRandom RAND = new SecureRandom();
  private static final Duration API_TIMEOUT = Duration.ofSeconds(10);

  private ExposureNotificationClientWrapper client;

  public DiagnosisKeyFileSubmitter(Context context) {
    client = new ExposureNotificationClientWrapper(context);
  }

  /**
   * Parses the given files, and submits their keys to provideDiagnosisKeys(), and returns a future
   * representing the completion of that task.
   *
   * <p>This naive implementation is not robust to individual failures. In fact, a single failure
   * will fail the entire operation. A more robust implementation would support retries, partial
   * completion, and other robustness measures.
   */
  public ListenableFuture<?> parseFiles(List<File> files) {
    Log.d(TAG, "Parsing " + files.size() + " diagnosis key files for submission.");
    List<ListenableFuture<?>> completedFiles = new ArrayList<>();
    for (File f : files) {
      ListenableFuture<?> completedFile =
          FluentFuture.from(parseFile(f))
              .transform(
                  keys -> {
                    List<TemporaryExposureKey> forSubmission = new ArrayList<>();
                    for (DiagnosisKey k : keys) {
                      forSubmission.add(
                          new TemporaryExposureKeyBuilder()
                              .setKeyData(k.getKeyBytes())
                              .setRollingStartIntervalNumber(k.getIntervalNumber())
                              .build());
                    }
                    return submitKeys(forSubmission);
                  },
                  AppExecutors.getBackgroundExecutor());
      completedFiles.add(completedFile);
    }
    return Futures.allAsList(completedFiles);
  }

  private ListenableFuture<?> submitKeys(List<TemporaryExposureKey> keys) {
    Log.d(TAG, "Submitting " + keys.size() + " keys.");
    return FluentFuture.from(getChunkSize())
        .transformAsync(
            chunkSize -> {
              List<ListenableFuture<?>> submissions = new ArrayList<>();
              for (List<TemporaryExposureKey> chunk : Iterables.partition(keys, chunkSize)) {
                ListenableFuture<?> submission =
                    TaskToFutureAdapter.getFutureWithTimeout(
                        client.provideDiagnosisKeys(chunk),
                        API_TIMEOUT.toMillis(),
                        TimeUnit.MILLISECONDS,
                        AppExecutors.getScheduledExecutor());
                submissions.add(submission);
              }
              Log.d(TAG, "Submitted " + submissions.size() + " chunks of " + chunkSize + " keys.");
              return Futures.allAsList(submissions);
            },
            AppExecutors.getBackgroundExecutor());
  }

  private ListenableFuture<Integer> getChunkSize() {
    return TaskToFutureAdapter.getFutureWithTimeout(
        client.getMaxDiagnosisKeysCount(),
        API_TIMEOUT.toMillis(),
        TimeUnit.MILLISECONDS,
        AppExecutors.getScheduledExecutor());
  }

  private static ListenableFuture<List<DiagnosisKey>> parseFile(File file) {
    // This is a fake implementation, so we actually ignore the file.
    // Were it not fake, it's likely that the parsing of the file is heavyweight enough to post
    // to an Executor with a fixed size threadpool and return a Future representing the parsing of
    // the file.
    return Futures.immediateFuture(fakeKeys());
  }

  private static ImmutableList<DiagnosisKey> fakeKeys() {
    int intervalNumber = 2647121; // A recent, realistic interval number.
    byte[] bytes = new byte[16];
    ImmutableList.Builder builder = new ImmutableList.Builder();
    for (int i = 0; i < 100; i++) {
      RAND.nextBytes(bytes);
      builder.add(
          DiagnosisKey.newBuilder().setKeyBytes(bytes).setIntervalNumber(intervalNumber++).build());
    }
    return builder.build();
  }
}
