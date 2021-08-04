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

package com.google.android.apps.exposurenotification.roaming;

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
import com.google.android.apps.exposurenotification.common.logging.Logger;
import com.google.android.apps.exposurenotification.logging.AnalyticsLogger;
import com.google.android.apps.exposurenotification.proto.WorkManagerTask.WorkerTask;
import com.google.android.apps.exposurenotification.work.WorkerStartupManager;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public class CountryCheckingWorker extends ListenableWorker {

  private static final Logger logcat = Logger.getLogger("CountryCheckingWorker");
  private static final String WORKER_NAME = "CountryCheckingWorker";

  private final ExecutorService backgroundExecutor;
  private final CountryCodes countryCodes;
  private final WorkerStartupManager workerStartupManager;
  private final AnalyticsLogger logger;

  @WorkerInject
  public CountryCheckingWorker(
      @Assisted @NonNull Context context,
      @Assisted @NonNull WorkerParameters workerParams,
      @BackgroundExecutor ExecutorService backgroundExecutor,
      CountryCodes countryCodes,
      WorkerStartupManager workerStartupManager,
      AnalyticsLogger logger) {
    super(context, workerParams);
    this.backgroundExecutor = backgroundExecutor;
    this.countryCodes = countryCodes;
    this.workerStartupManager = workerStartupManager;
    this.logger = logger;
  }

  @NonNull
  @Override
  public ListenableFuture<Result> startWork() {
    return FluentFuture.from(
        workerStartupManager.getIsEnabledWithStartupTasks())
        .transformAsync(
            (isEnabled) -> {
              logger.logWorkManagerTaskStarted(WorkerTask.TASK_COUNTRY_CHECKING);
              // Only continue if it is enabled.
              if (isEnabled) {
                countryCodes.updateDatabaseWithCurrentCountryCode();
              }
              countryCodes.deleteObsoleteCountryCodes();
              logger.logWorkManagerTaskSuccess(WorkerTask.TASK_COUNTRY_CHECKING);
              return Futures.immediateFuture(Result.success());
            },
            backgroundExecutor)
        .catching(
            Exception.class,
            x -> {
              logcat.e("Failure to check country code", x);
              logger.logWorkManagerTaskFailure(WorkerTask.TASK_COUNTRY_CHECKING, x);
              return Result.failure();
            },
            backgroundExecutor);
  }

  public static Operation schedule(WorkManager workManager) {
    logcat.d("Scheduling country code checker");
    // WARNING: You must set ExistingPeriodicWorkPolicy.REPLACE if you want to change the params for
    //          previous app version users.
    return workManager.enqueueUniquePeriodicWork(WORKER_NAME, ExistingPeriodicWorkPolicy.KEEP,
        new PeriodicWorkRequest.Builder(CountryCheckingWorker.class, 6, TimeUnit.HOURS).build());
  }

  public static void cancel(WorkManager workManager) {
    logcat.d("Cancelling country code checker");
    workManager.cancelUniqueWork(WORKER_NAME);
  }

}
