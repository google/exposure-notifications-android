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
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.File;
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
  private static final Duration API_TIMEOUT = Duration.ofSeconds(10);

  private final ExposureNotificationClientWrapper client;

  public DiagnosisKeyFileSubmitter(Context context) {
    client = ExposureNotificationClientWrapper.get(context);
  }

  /**
   * Parses the given files, and submits their keys to provideDiagnosisKeys(), and returns a future
   * representing the completion of that task.
   *
   * <p>This naive implementation is not robust to individual failures. In fact, a single failure
   * will fail the entire operation. A more robust implementation would support retries, partial
   * completion, and other robustness measures.
   */
  public ListenableFuture<?> parseFiles(List<File> files, String token) {
    Log.d(TAG, "Parsing " + files.size() + " diagnosis key files for submission.");

    return TaskToFutureAdapter.getFutureWithTimeout(
        client.provideDiagnosisKeys(files, token),
        API_TIMEOUT.toMillis(),
        TimeUnit.MILLISECONDS,
        AppExecutors.getScheduledExecutor());
  }

}
