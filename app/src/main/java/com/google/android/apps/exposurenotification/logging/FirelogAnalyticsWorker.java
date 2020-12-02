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

package com.google.android.apps.exposurenotification.logging;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.hilt.Assisted;
import androidx.hilt.work.WorkerInject;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ListenableWorker;
import androidx.work.Operation;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkerParameters;
import com.google.android.apps.exposurenotification.common.Qualifiers.BackgroundExecutor;
import com.google.android.apps.exposurenotification.proto.WorkManagerTask.WorkerTask;
import com.google.android.apps.exposurenotification.work.WorkerStartupManager;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.threeten.bp.Duration;

/**
 * A worker that uploads analytical logs through firelog.
 */
public class FirelogAnalyticsWorker extends ListenableWorker {

  public static final String WORKER_NAME = "FirelogAnalyticsUploadWorker";
  public static final Duration JOB_INTERVAL = Duration.ofHours(4).plusMinutes(30);
  private final AnalyticsLogger logger;
  private final WorkerStartupManager workerStartupManager;
  private final ExecutorService backgroundExecutor;

  /**
   * @param appContext   The application {@link Context}
   * @param workerParams Parameters to setup the internal state of this worker
   */
  @WorkerInject
  public FirelogAnalyticsWorker(
      @Assisted @NonNull Context appContext,
      @Assisted @NonNull WorkerParameters workerParams,
      AnalyticsLogger logger,
      WorkerStartupManager workerStartupManager,
      @BackgroundExecutor ExecutorService backgroundExecutor) {
    super(appContext, workerParams);
    this.logger = logger;
    this.backgroundExecutor = backgroundExecutor;
    this.workerStartupManager = workerStartupManager;
  }

  @NonNull
  @Override
  public ListenableFuture<Result> startWork() {
    return FluentFuture.from(
        workerStartupManager.getIsEnabledWithStartupTasks())
        .transformAsync(
            isEnabled -> {
              logger.logWorkManagerTaskStarted(WorkerTask.TASK_FIRELOG_ANALYTICS);
              if (!isEnabled) {
                // If the API is not enabled, do not attempt sending logs.
                return Futures.immediateFailedFuture(new NotEnabledException());
              }
              return logger.sendLoggingBatchIfEnabled();
            },
            backgroundExecutor)
        // Report success or failure.
        .transform(unused -> {
          logger.logWorkManagerTaskSuccess(WorkerTask.TASK_FIRELOG_ANALYTICS);
          return Result.success();
        }, backgroundExecutor)
        .catching(
            NotEnabledException.class,
            x -> {
              // Not enabled. Return as success.
              logger.logWorkManagerTaskAbandoned(WorkerTask.TASK_FIRELOG_ANALYTICS);
              return Result.success();
            },
            backgroundExecutor)
        .catching(
            Exception.class, x -> {
              logger.logWorkManagerTaskFailure(WorkerTask.TASK_FIRELOG_ANALYTICS, x);
              return Result.failure();
            },
            backgroundExecutor);
  }

  /**
   * Schedules a job that runs once every four hours to upload Firelog logs to the server.
   */
  public static Operation schedule(WorkManager workManager) {
    PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(FirelogAnalyticsWorker.class,
        JOB_INTERVAL.toMinutes(), TimeUnit.MINUTES).build();
    return workManager
        .enqueueUniquePeriodicWork(WORKER_NAME, ExistingPeriodicWorkPolicy.KEEP, workRequest);
  }

  private static class NotEnabledException extends Exception {

  }
}
