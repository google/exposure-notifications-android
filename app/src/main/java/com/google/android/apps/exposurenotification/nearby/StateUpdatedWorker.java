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
import androidx.annotation.NonNull;
import androidx.work.ListenableWorker;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkerParameters;
import com.google.android.apps.exposurenotification.common.AppExecutors;
import com.google.android.apps.exposurenotification.common.NotificationHelper;
import com.google.android.apps.exposurenotification.common.TaskToFutureAdapter;
import com.google.android.apps.exposurenotification.storage.ExposureRepository;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.TimeUnit;
import org.threeten.bp.Duration;

/**
 * Performs work for {@value com.google.android.gms.nearby.exposurenotification.ExposureNotificationClient#ACTION_EXPOSURE_STATE_UPDATED}
 * broadcast from exposure notification API.
 */
public class StateUpdatedWorker extends ListenableWorker {
  private static final String TAG = "StateUpdatedWorker";

  private static final Duration GET_WINDOWS_TIMEOUT = Duration.ofSeconds(120);

  private final Context context;
  private final ExposureRepository exposureRepository;

  public StateUpdatedWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
    super(context, workerParams);
    this.context = context;
    this.exposureRepository = new ExposureRepository(context);
  }

  @NonNull
  @Override
  public ListenableFuture<Result> startWork() {
    return FluentFuture.from(
        TaskToFutureAdapter.getFutureWithTimeout(
            ExposureNotificationClientWrapper.get(context).getExposureWindows(),
            GET_WINDOWS_TIMEOUT.toMillis(),
            TimeUnit.MILLISECONDS,
            AppExecutors.getScheduledExecutor()))
        .transform(
            exposureRepository::refreshWithExposureWindows,
            AppExecutors.getBackgroundExecutor())
        .transform((exposuresAdded) -> {
          if (exposuresAdded) {
            NotificationHelper.showPossibleExposureNotification(context);
          }
          return Result.success();
        }, AppExecutors.getLightweightExecutor())
        .catching(
            Exception.class,
            x -> {
              Log.e(TAG, "Failure to update app state (tokens, etc) from exposure summary.", x);
              return Result.failure();
            },
            AppExecutors.getLightweightExecutor());
  }

  static void runOnce(Context context) {
    WorkManager.getInstance(context).enqueue(
        new OneTimeWorkRequest.Builder(StateUpdatedWorker.class).build());
  }
}
