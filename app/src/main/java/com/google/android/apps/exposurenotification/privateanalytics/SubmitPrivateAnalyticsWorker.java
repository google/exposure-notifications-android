/*
 * Copyright 2021 Google LLC
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

package com.google.android.apps.exposurenotification.privateanalytics;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.hilt.Assisted;
import androidx.hilt.work.WorkerInject;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ListenableWorker;
import androidx.work.NetworkType;
import androidx.work.Operation;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;
import androidx.work.WorkerParameters;
import com.google.android.apps.exposurenotification.common.Qualifiers.BackgroundExecutor;
import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.apps.exposurenotification.work.WorkerStartupManager;
import com.google.android.libraries.privateanalytics.DefaultPrivateAnalyticsDeviceAttestation;
import com.google.android.libraries.privateanalytics.PrivateAnalyticsEventListener;
import com.google.android.libraries.privateanalytics.PrivateAnalyticsSubmitter;
import com.google.common.base.Optional;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.threeten.bp.Duration;
import org.threeten.bp.Instant;

/**
 * Performs work to submit private analytics to the configured ingestion server.
 */
public class SubmitPrivateAnalyticsWorker extends ListenableWorker {

  // Logging TAG
  private static final String TAG = "PrioSubmitWorker";

  public static final String WORKER_NAME = "SubmitPrivateAnalyticsWorker";

  private static final Duration MINIMAL_ENPA_TASK_INTERVAL = Duration.ofDays(1);
  private final PrivateAnalyticsSubmitter privateAnalyticsSubmitter;
  private final ExecutorService backgroundExecutor;
  private final ExposureNotificationSharedPreferences exposureNotificationSharedPreferences;
  private final Clock clock;
  private final WorkerStartupManager workerStartupManager;
  private final Optional<PrivateAnalyticsEventListener> analyticsListener;

  @WorkerInject
  public SubmitPrivateAnalyticsWorker(
      @Assisted @NonNull Context context,
      @Assisted @NonNull WorkerParameters workerParams,
      PrivateAnalyticsSubmitter privateAnalyticsSubmitter,
      @BackgroundExecutor ExecutorService backgroundExecutor,
      WorkerStartupManager workerStartupManager,
      Clock clock,
      ExposureNotificationSharedPreferences exposureNotificationSharedPreferences,
      Optional<PrivateAnalyticsEventListener> analyticsListener) {
    super(context, workerParams);
    this.privateAnalyticsSubmitter = privateAnalyticsSubmitter;
    this.backgroundExecutor = backgroundExecutor;
    this.clock = clock;
    this.exposureNotificationSharedPreferences = exposureNotificationSharedPreferences;
    this.workerStartupManager = workerStartupManager;
    this.analyticsListener = analyticsListener;
  }

  @NonNull
  @Override
  public ListenableFuture<Result> startWork() {
    Log.d(TAG, "Starting worker for submitting private analytics to ingestion server.");
    return FluentFuture.from(workerStartupManager.getIsEnabledWithStartupTasks())
        .transformAsync(
            (isEnabled) -> {
              if (analyticsListener.isPresent()) {
                analyticsListener.get().onPrivateAnalyticsWorkerTaskStarted();
              }

              Instant lastWorkerTime = exposureNotificationSharedPreferences
                  .getPrivateAnalyticsWorkerLastTime();
              Instant fourteenDaysAgo = clock.now().minus(Duration.ofDays(14));
              Instant latestTimeToClear =
                  lastWorkerTime.isAfter(fourteenDaysAgo) ? lastWorkerTime : fourteenDaysAgo;

              // Clear data older than two weeks + since last run (whichever comes first).
              exposureNotificationSharedPreferences
                  .clearPrivateAnalyticsFieldsBefore(latestTimeToClear);

              if (isEnabled && DefaultPrivateAnalyticsDeviceAttestation
                  .isDeviceAttestationAvailable()) {
                Log.d(TAG, "Private analytics enabled and device attestation available.");
                // Attempt to submit packets. Note this this will return early is private analytics
                // are remotely disabled or toggled off.
                return privateAnalyticsSubmitter.submitPackets();
              } else {
                Log.d(TAG, "API not enabled or device attestation unavailable.");
                // Stop here because things are not enabled. Will still return successful though.
                return Futures.immediateFailedFuture(new NotEnabledException());
              }
            },
            backgroundExecutor)
        .transform(done -> {
          exposureNotificationSharedPreferences.setPrivateAnalyticsWorkerLastTime(clock.now());
          return Result.success();
        }, backgroundExecutor)
        .catching(
            NotEnabledException.class,
            x -> {
              exposureNotificationSharedPreferences.setPrivateAnalyticsWorkerLastTime(clock.now());
              return Result.success();
            },
            backgroundExecutor)
        .catching(
            Exception.class,
            x -> {
              Log.e(TAG, "Failure to submit private analytics", x);
              // Even if we observe an Exception, we store the last time the worker run.
              // Note that this will prevent older data to be uploaded.
              exposureNotificationSharedPreferences.setPrivateAnalyticsWorkerLastTime(clock.now());
              return Result.failure();
            },
            backgroundExecutor);
  }

  /**
   * Schedules a job that runs once a day to submit private analytics.
   *
   * <p>This job will only be run when not low battery and with network connection.
   */
  public static Operation schedule(WorkManager workManager) {
    Log.d(TAG,
        "Scheduling periodic work request. repeatInterval=" + MINIMAL_ENPA_TASK_INTERVAL.toHours());
    PeriodicWorkRequest workRequest =
        new PeriodicWorkRequest.Builder(
            SubmitPrivateAnalyticsWorker.class,
            MINIMAL_ENPA_TASK_INTERVAL.toHours(),
            TimeUnit.HOURS)
            .setConstraints(
                new Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build())
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.DEFAULT_BACKOFF_DELAY_MILLIS,
                TimeUnit.MILLISECONDS)
            .build();
    return workManager.enqueueUniquePeriodicWork(
        WORKER_NAME, ExistingPeriodicWorkPolicy.KEEP, workRequest);
  }

  private static class NotEnabledException extends Exception {

  }
}
