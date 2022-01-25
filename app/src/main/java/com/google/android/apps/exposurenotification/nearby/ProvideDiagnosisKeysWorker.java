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
import androidx.annotation.NonNull;
import androidx.hilt.work.HiltWorker;
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
import com.google.android.apps.exposurenotification.common.Qualifiers.ScheduledExecutor;
import com.google.android.apps.exposurenotification.common.TaskToFutureAdapter;
import com.google.android.apps.exposurenotification.common.logging.Logger;
import com.google.android.apps.exposurenotification.keydownload.DiagnosisKeyDownloader;
import com.google.android.apps.exposurenotification.logging.AnalyticsLogger;
import com.google.android.apps.exposurenotification.proto.WorkManagerTask.WorkerTask;
import com.google.android.apps.exposurenotification.work.WorkerStartupManager;
import com.google.android.apps.exposurenotification.work.WorkerStartupManager.IsEnabledWithStartupTasksException;
import com.google.android.gms.nearby.exposurenotification.DiagnosisKeysDataMapping;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.threeten.bp.Duration;

/**
 * Performs work to provide diagnosis keys to the exposure notifications API.
 */
@HiltWorker
public class ProvideDiagnosisKeysWorker extends ListenableWorker {

  /*
   * If we schedule the provide job more frequent than every 4 hours, nearby_en returns call-quota
   * exceeded errors. This variable represents the lower bound to the configuration value set by
   * the HA.
   */
  private static final Duration MINIMAL_TEK_PUBLISH_INTERVAL = Duration.ofHours(4);

  private static final Logger logcat = Logger.getLogger("ProvideDiagnosisKeysWkr");

  private static final Duration GET_DIAGNOSIS_KEY_DATA_MAPPING_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration SET_DIAGNOSIS_KEY_DATA_MAPPING_TIMEOUT = Duration.ofSeconds(10);
  public static final String WORKER_NAME = "ProvideDiagnosisKeysWorker";

  private final DiagnosisKeyDownloader downloader;
  private final DiagnosisKeyFileSubmitter diagnosisKeyFileSubmitter;
  private final ExposureNotificationClientWrapper exposureNotificationClientWrapper;
  private final DiagnosisKeysDataMapping diagnosisKeysDataMapping;
  private final ExecutorService backgroundExecutor;
  private final ScheduledExecutorService scheduledExecutor;
  private final WorkerStartupManager workerStartupManager;
  private final AnalyticsLogger logger;

  @AssistedInject
  public ProvideDiagnosisKeysWorker(
      @Assisted @NonNull Context context,
      @Assisted @NonNull WorkerParameters workerParams,
      DiagnosisKeyDownloader downloadController,
      ExposureNotificationClientWrapper exposureNotificationClientWrapper,
      DiagnosisKeyFileSubmitter diagnosisKeyFileSubmitter,
      DiagnosisKeysDataMapping diagnosisKeysDataMapping,
      @BackgroundExecutor ExecutorService backgroundExecutor,
      @ScheduledExecutor ScheduledExecutorService scheduledExecutor,
      WorkerStartupManager workerStartupManager,
      AnalyticsLogger logger) {
    super(context, workerParams);
    this.downloader = downloadController;
    this.exposureNotificationClientWrapper = exposureNotificationClientWrapper;
    this.diagnosisKeyFileSubmitter = diagnosisKeyFileSubmitter;
    this.diagnosisKeysDataMapping = diagnosisKeysDataMapping;
    this.backgroundExecutor = backgroundExecutor;
    this.scheduledExecutor = scheduledExecutor;
    this.workerStartupManager = workerStartupManager;
    this.logger = logger;
  }

  @NonNull
  @Override
  public ListenableFuture<Result> startWork() {
    logcat.d(
        "Starting worker providing the DiagnosisKeysDataMapping to the API, "
            + "downloading diagnosis key files and submitting "
            + "them to the API for exposure detection, then storing the token used.");
    return FluentFuture.from(
        workerStartupManager.getIsEnabledWithStartupTasks())
        .transformAsync(
            (isEnabled) -> {
              logger.logWorkManagerTaskStarted(WorkerTask.TASK_PROVIDE_DIAGNOSIS_KEYS);
              // Only continue if it is enabled.
              if (isEnabled) {
                return FluentFuture.from(TaskToFutureAdapter.getFutureWithTimeout(
                    exposureNotificationClientWrapper.getDiagnosisKeysDataMapping(),
                    GET_DIAGNOSIS_KEY_DATA_MAPPING_TIMEOUT,
                    scheduledExecutor))
                    .catchingAsync(
                        Exception.class,
                        e -> Futures.immediateFailedFuture(new DiagnosisKeyDataMappingException()),
                        backgroundExecutor);
              } else {
                // Stop here because things aren't enabled. Will still return successful though.
                return Futures.immediateFailedFuture(new NotEnabledException());
              }
            },
            backgroundExecutor)
        .transformAsync(
            diagnosisKeysDataMapping ->
                FluentFuture.from(checkDiagnosisKeyDataMappingForUpdate(diagnosisKeysDataMapping))
                    .catchingAsync(
                        Exception.class,
                        e -> Futures.immediateFailedFuture(new DiagnosisKeyDataMappingException()),
                        backgroundExecutor),
            backgroundExecutor)
        .catchingAsync(
            DiagnosisKeyDataMappingException.class,
            e -> {
              // Ignore exceptions thrown during DiagnosisKeyDataMapping calls
              logcat.e("Exception while updating the DiagnosisKeyDataMapping", e);
              return Futures.immediateVoidFuture();
            },
            backgroundExecutor
        )
        .transformAsync(
            (unused) -> downloader.download(), backgroundExecutor)
        .transformAsync(
            diagnosisKeyFileSubmitter::submitFiles,
            backgroundExecutor)
        .transform(done -> {
          logger.logWorkManagerTaskSuccess(WorkerTask.TASK_PROVIDE_DIAGNOSIS_KEYS);
          return Result.success();
        }, backgroundExecutor)
        .catching(
            NotEnabledException.class,
            x -> {
              // Not enabled. Return as success.
              logger.logWorkManagerTaskAbandoned(WorkerTask.TASK_PROVIDE_DIAGNOSIS_KEYS);
              return Result.success();
            },
            backgroundExecutor)
        .catching(
            IsEnabledWithStartupTasksException.class,
            x -> {
              logger.logWorkManagerTaskFailure(WorkerTask.TASK_PROVIDE_DIAGNOSIS_KEYS, x);
              return Result.failure();
            },
            backgroundExecutor)
        .catching(
            Exception.class,
            x -> {
              logcat.e("Failure to provide diagnosis keys", x);
              logger.logWorkManagerTaskFailure(WorkerTask.TASK_PROVIDE_DIAGNOSIS_KEYS, x);
              return Result.failure();
            },
            backgroundExecutor);
  }

  /**
   * Check if we need to update EN Api's version of DiagnosisKeysDataMapping. Only on update call
   * Nearby.setDiagnosisKeysDataMapping().
   * <p>
   * Please note that if two or more updates / calls to setDiagnosisKeysDataMapping() happen within
   * the same week, the method throws an ApiException with StatusCode
   * ExposureNotificationStatusCodes.FAILED_RATE_LIMITED. As it is only called on
   * DiagnosisKeysDataMapping updates this should happen rarely.
   */
  private ListenableFuture<Void> checkDiagnosisKeyDataMappingForUpdate(
      DiagnosisKeysDataMapping previousDkdm) {

    DiagnosisKeysDataMapping newDkdm = diagnosisKeysDataMapping;

    if (previousDkdm.equals(newDkdm)) {
      logcat.d("DiagnosisKeysDataMapping unchanged, not requesting update");
      return Futures.immediateVoidFuture();
    } else {
      logcat.d("Updated DiagnosisKeysDataMapping detected, "
          + "calling Nearby.setDiagnosisKeysDataMapping");

      return TaskToFutureAdapter.getFutureWithTimeout(
          exposureNotificationClientWrapper.setDiagnosisKeysDataMapping(newDkdm),
          SET_DIAGNOSIS_KEY_DATA_MAPPING_TIMEOUT,
          scheduledExecutor);
    }
  }

  /**
   * Schedules a job that runs once a day to fetch diagnosis keys from a server and to provide them
   * to the exposure notifications API with flex period.
   *
   * <p>This job will only be run when not low battery and with network connection.
   */
  public static Operation schedule(WorkManager workManager, Duration tekPublishInterval) {
    // Lower-bound tekPublishInterval and convert to primitive as required by PeriodicWorkRequest
    long tekPublishIntervalHours =
        Math.max(MINIMAL_TEK_PUBLISH_INTERVAL.toHours(), tekPublishInterval.toHours());

    // WARNING: You must set ExistingPeriodicWorkPolicy.REPLACE if you want to change the params for
    //          previous app version users.
    PeriodicWorkRequest workRequest =
        new PeriodicWorkRequest.Builder(
            ProvideDiagnosisKeysWorker.class,
            tekPublishIntervalHours,
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

  /**
   * An {@link Exception} thrown if EN is off.
   */
  private static class NotEnabledException extends Exception {

  }

  /**
   * An {@link Exception} thrown if either of the
   * {@link ExposureNotificationClientWrapper#getDiagnosisKeysDataMapping()} or
   * {@link ExposureNotificationClientWrapper#setDiagnosisKeysDataMapping(DiagnosisKeysDataMapping)}
   * calls fail.
   */
  private static class DiagnosisKeyDataMappingException extends Exception {

  }
}
