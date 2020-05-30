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
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ListenableWorker;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkerParameters;
import com.google.android.apps.exposurenotification.common.AppExecutors;
import com.google.android.apps.exposurenotification.common.TaskToFutureAdapter;
import com.google.android.apps.exposurenotification.nearby.ExposureNotificationClientWrapper;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;
import org.threeten.bp.Duration;

/**
 * A worker that uploads a random number of fake Diagnosis Key uploads each day.
 *
 * <p>Somewhat random operation is achieved two ways: using WorkManager's flex interval to operate
 * within a loose time period, and by skipping some executions at random.
 */
public final class UploadCoverTrafficWorker extends ListenableWorker {
  private static final String TAG = "UploadCoverTrafficWrk";
  private static final String WORKER_NAME = "UploadCoverTrafficWorker";
  private static final int REPEAT_INTERVAL = 6;
  private static final TimeUnit REPEAT_INTERVAL_UNITS = TimeUnit.HOURS;
  private static final int FLEX_INTERVAL = 1;
  private static final TimeUnit FLEX_INTERVAL_UNITS = TimeUnit.HOURS;
  private static final SecureRandom RAND = new SecureRandom();
  private static final double EXECUTION_PROBABILITY = 0.5d;
  public static final Duration API_TIMEOUT = Duration.ofSeconds(5);

  private final DiagnosisKeyUploader uploader;
  private final ExposureNotificationClientWrapper enClient;

  /**
   * @param appContext The application {@link Context}
   * @param workerParams Parameters to setup the internal state of this worker
   */
  public UploadCoverTrafficWorker(
      @NonNull Context appContext, @NonNull WorkerParameters workerParams) {
    super(appContext, workerParams);
    uploader = new DiagnosisKeyUploader(appContext);
    enClient = ExposureNotificationClientWrapper.get(appContext);
  }

  @NonNull
  @Override
  public ListenableFuture<Result> startWork() {
    if (!shouldExecute()) {
      // We skip execution with random probability.
      return Futures.immediateFuture(Result.success());
    }

    // First see if the API is enabled in the first place.
    return FluentFuture.from(apiIsEnabled())
        .transformAsync(
            // If the API is not enabled, skip the upload.
            isEnabled -> isEnabled ? uploader.fakeUpload() : Futures.immediateFuture(null),
            AppExecutors.getLightweightExecutor())
        // Report success or failure.
        .transform(unused -> Result.success(), AppExecutors.getLightweightExecutor())
        .catching(Throwable.class, t -> Result.failure(), AppExecutors.getLightweightExecutor());
  }

  private boolean shouldExecute() {
    return RAND.nextDouble() < EXECUTION_PROBABILITY;
  }

  private ListenableFuture<Boolean> apiIsEnabled() {
    return TaskToFutureAdapter.getFutureWithTimeout(
        enClient.isEnabled(),
        API_TIMEOUT.toMillis(),
        TimeUnit.MILLISECONDS,
        AppExecutors.getScheduledExecutor());
  }

  public static void schedule(Context context) {
    Log.i(TAG, "Scheduling periodic WorkManager job...");
    WorkManager workManager = WorkManager.getInstance(context);
    PeriodicWorkRequest workRequest =
        new PeriodicWorkRequest.Builder(
                UploadCoverTrafficWorker.class,
                REPEAT_INTERVAL,
                REPEAT_INTERVAL_UNITS,
                FLEX_INTERVAL,
                FLEX_INTERVAL_UNITS)
            .setConstraints(
                new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build();
    workManager.enqueueUniquePeriodicWork(
        WORKER_NAME, ExistingPeriodicWorkPolicy.REPLACE, workRequest);
  }
}
