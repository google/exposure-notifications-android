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

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.hilt.work.HiltWorker;
import androidx.work.ExistingWorkPolicy;
import androidx.work.ListenableWorker;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkerParameters;
import com.google.android.apps.exposurenotification.BuildConfig;
import com.google.android.apps.exposurenotification.common.NotificationHelper;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;
import java.util.concurrent.TimeUnit;
import org.threeten.bp.Duration;

/**
 * Performs work to notify user to reactivate exposure notification application after
 * device restore.
 */
@HiltWorker
public class RestoreNotificationWorker extends ListenableWorker {

  private static final Duration RESTORE_NOTIFICATION_DELAY = Duration.ofHours(24);
  private static final Duration QUICK_RESTORE_NOTIFICATION_DELAY = Duration.ofMinutes(30);
  private static final String RESTORE_NOTIFICATION_WORK_TAG = "RestoreNotificationWork";

  private final Context context;
  private final NotificationHelper notificationHelper;
  private final ExposureNotificationSharedPreferences exposureNotificationSharedPreferences;

  @AssistedInject
  public RestoreNotificationWorker(
      @Assisted @NonNull Context context,
      @Assisted @NonNull WorkerParameters params,
      NotificationHelper notificationHelper,
      ExposureNotificationSharedPreferences sharedPreferences) {
    super(context, params);
    this.context = context;
    this.notificationHelper = notificationHelper;
    this.exposureNotificationSharedPreferences = sharedPreferences;
  }

  @NonNull
  @Override
  public ListenableFuture<Result> startWork() {

    RestoreNotificationUtil
        .doRestoreNotificationWork(context, exposureNotificationSharedPreferences,
        notificationHelper);

    return Futures.immediateFuture(Result.success());
  }

  public static void scheduleWork(WorkManager workManager) {
    Duration restoreNotificationDelay = BuildConfig.QUICK_RESTORE_NOTIFICATION_SUPPORTED
        ? QUICK_RESTORE_NOTIFICATION_DELAY : RESTORE_NOTIFICATION_DELAY;

    OneTimeWorkRequest workRequest = new OneTimeWorkRequest
        .Builder(RestoreNotificationWorker.class)
        .setInitialDelay(restoreNotificationDelay.toMillis(), TimeUnit.MILLISECONDS)
        .build();

    workManager.enqueueUniqueWork(RESTORE_NOTIFICATION_WORK_TAG,
        ExistingWorkPolicy.KEEP, workRequest);
  }

  public static void cancelRestoreNotificationWorkIfExisting(WorkManager workManager) {
    workManager.cancelUniqueWork(RESTORE_NOTIFICATION_WORK_TAG);
  }

}
