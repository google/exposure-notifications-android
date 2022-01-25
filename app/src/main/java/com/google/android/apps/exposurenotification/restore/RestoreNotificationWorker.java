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

package com.google.android.apps.exposurenotification.restore;

import static com.google.android.apps.exposurenotification.nearby.ExposureNotificationClientWrapper.GET_STATUS_TIMEOUT;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.hilt.work.HiltWorker;
import androidx.work.ExistingWorkPolicy;
import androidx.work.ListenableWorker;
import androidx.work.OneTimeWorkRequest;
import androidx.work.Operation;
import androidx.work.WorkManager;
import androidx.work.WorkerParameters;
import com.google.android.apps.exposurenotification.BuildConfig;
import com.google.android.apps.exposurenotification.common.NotificationHelper;
import com.google.android.apps.exposurenotification.common.Qualifiers.BackgroundExecutor;
import com.google.android.apps.exposurenotification.common.Qualifiers.ScheduledExecutor;
import com.google.android.apps.exposurenotification.common.TaskToFutureAdapter;
import com.google.android.apps.exposurenotification.nearby.ExposureNotificationClientWrapper;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.gms.nearby.exposurenotification.ExposureNotificationStatus;
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
 * Performs work to notify user to reactivate exposure notification application after device
 * restore.
 */
@HiltWorker
public class RestoreNotificationWorker extends ListenableWorker {

  public static final String RESTORE_NOTIFICATION_WORKER_TAG = "RESTORE_NOTIFICATION_WORKER_TAG";

  private static final Duration RESTORE_NOTIFICATION_DELAY = Duration.ofHours(24);
  private static final Duration QUICK_RESTORE_NOTIFICATION_DELAY = Duration.ofMinutes(30);
  private static final String RESTORE_NOTIFICATION_WORK_TAG = "RestoreNotificationWork";

  private final Context context;
  private final NotificationHelper notificationHelper;
  private final ExposureNotificationSharedPreferences exposureNotificationSharedPreferences;
  private final ExposureNotificationClientWrapper exposureNotificationClientWrapper;
  private final ExecutorService backgroundExecutor;
  private final ScheduledExecutorService scheduledExecutor;

  @AssistedInject
  public RestoreNotificationWorker(
      @Assisted @NonNull Context context,
      @Assisted @NonNull WorkerParameters params,
      NotificationHelper notificationHelper,
      ExposureNotificationSharedPreferences sharedPreferences,
      ExposureNotificationClientWrapper exposureNotificationClientWrapper,
      @BackgroundExecutor ExecutorService backgroundExecutor,
      @ScheduledExecutor ScheduledExecutorService scheduledExecutor) {
    super(context, params);
    this.context = context;
    this.notificationHelper = notificationHelper;
    this.exposureNotificationSharedPreferences = sharedPreferences;
    this.exposureNotificationClientWrapper = exposureNotificationClientWrapper;
    this.backgroundExecutor = backgroundExecutor;
    this.scheduledExecutor = scheduledExecutor;
  }

  @NonNull
  @Override
  public ListenableFuture<Result> startWork() {
    return FluentFuture.from(TaskToFutureAdapter.getFutureWithTimeout(
        exposureNotificationClientWrapper.getStatus(),
        GET_STATUS_TIMEOUT,
        scheduledExecutor))
        .transformAsync(statuses -> {
          if (statuses.contains(ExposureNotificationStatus.EN_NOT_SUPPORT)
              || statuses.contains(ExposureNotificationStatus.NOT_IN_ALLOWLIST)) {
            // EN is turned down, don't show restore notification.
            return Futures.immediateVoidFuture();
          }
          RestoreNotificationUtil.doRestoreNotificationWork(
              context, exposureNotificationSharedPreferences, notificationHelper);
          return Futures.immediateVoidFuture();
        }, backgroundExecutor)
        .transform(unused -> Result.success(), backgroundExecutor)
        .catching(Exception.class, t -> Result.failure(), backgroundExecutor);
  }

  public static void scheduleWork(WorkManager workManager) {
    Duration restoreNotificationDelay = BuildConfig.QUICK_RESTORE_NOTIFICATION_SUPPORTED
        ? QUICK_RESTORE_NOTIFICATION_DELAY : RESTORE_NOTIFICATION_DELAY;

    OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(RestoreNotificationWorker.class)
        .addTag(RESTORE_NOTIFICATION_WORKER_TAG)
        .setInitialDelay(restoreNotificationDelay.toMillis(), TimeUnit.MILLISECONDS)
        .build();

    workManager.enqueueUniqueWork(RESTORE_NOTIFICATION_WORK_TAG,
        ExistingWorkPolicy.KEEP, workRequest);
  }

  public static Operation cancelRestoreNotificationWorkIfExisting(WorkManager workManager) {
    return workManager.cancelUniqueWork(RESTORE_NOTIFICATION_WORK_TAG);
  }

}
