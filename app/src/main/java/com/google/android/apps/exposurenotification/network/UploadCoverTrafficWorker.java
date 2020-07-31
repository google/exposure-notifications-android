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
import com.google.common.collect.ImmutableList;
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
  private static final int KEY_SIZE_BYTES = 16;
  private static final int FAKE_INTERVAL_NUM = 2650847; // Only size matters here, not the value.
  private static final int FAKE_SAFETYNET_ATTESTATION_LENGTH = 5394; // Measured from a real payload

  private final UploadController controller;
  private final ExposureNotificationClientWrapper enClient;

  /**
   * @param appContext   The application {@link Context}
   * @param workerParams Parameters to setup the internal state of this worker
   */
  public UploadCoverTrafficWorker(
      @NonNull Context appContext, @NonNull WorkerParameters workerParams) {
    super(appContext, workerParams);
    controller = UploadControllerFactory.create(appContext);
    enClient = ExposureNotificationClientWrapper.get(appContext);
  }

  @NonNull
  @Override
  public ListenableFuture<Result> startWork() {
    if (!shouldExecute()) {
      // We skip execution with random probability.
      return Futures.immediateFuture(Result.success());
    }

    ImmutableList.Builder<DiagnosisKey> builder = ImmutableList.builder();
    // Build up 14 random diagnosis keys.
    for (int i = 0; i < 14; i++) {
      byte[] bytes = new byte[KEY_SIZE_BYTES];
      RAND.nextBytes(bytes);
      builder.add(
          DiagnosisKey.newBuilder()
              .setKeyBytes(bytes)
              // Accepting the default rolling period that the DiagnosisKey.Builder comes with.
              .setTransmissionRisk(i % 7)
              .setIntervalNumber(FAKE_INTERVAL_NUM)
              .build());
    }

    Upload fakeUpload = Upload.newBuilder(builder.build(), "FAKE-VALIDATION-CODE")
        .setIsCoverTraffic(true)
        .build();

    // First see if the API is enabled in the first place.
    return FluentFuture.from(apiIsEnabled())
        .transformAsync(
            isEnabled -> {
              if (!isEnabled) {
                // If the API is not enabled, skip the upload.
                return Futures.immediateFuture(null);
              }
              return FluentFuture.from(controller.verify(fakeUpload))
                  .transformAsync(
                      verified -> controller.upload(verified),
                      AppExecutors.getBackgroundExecutor());
            },
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
