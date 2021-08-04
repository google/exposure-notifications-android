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

import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE;

import android.app.ActivityManager;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Build;
import androidx.work.WorkManager;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.NotificationHelper;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences.OnboardingStatus;

/**
 * Helper class for managing Restore Notification work in the app.
 */
public class RestoreNotificationUtil {

  // UsageStatsManager.STANDBY_BUCKET_NEVER is annotated @SystemApi,
  // hence it is not available to use.
  private static final int STANDBY_BUCKET_NEVER = 50;
  private static final int STANDBY_BUCKET_ACTIVE = 10;

  private RestoreNotificationUtil() {
    // Prevent instantiation.
  }

  /**
   * Schedules restore notification work or shows restore notification if work cannot be scheduled.
   * Called when EN Api Wakeup Service Broadcast is received.
   */
  public static void onENApiWakeupEvent(Context context,
      ExposureNotificationSharedPreferences sharedPreferences,
      WorkManager workManager,
      NotificationHelper notificationHelper) {
    if (sharedPreferences.hasPendingRestoreNotification()) {
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
        RestoreNotificationWorker.scheduleWork(workManager);
      } else {
        int standByBucket = getAppStandbyBucket(context);
        switch (standByBucket) {
          case UsageStatsManager.STANDBY_BUCKET_ACTIVE:
          case UsageStatsManager.STANDBY_BUCKET_WORKING_SET:
          case UsageStatsManager.STANDBY_BUCKET_FREQUENT:
            RestoreNotificationWorker.scheduleWork(workManager);
            break;

          default:

            notificationHelper.showReActivateENAppNotification(context,
                R.string.reactivate_exposure_notification_app_subject,
                R.string.reactivate_exposure_notification_app_body);
            break;
        }
      }
      sharedPreferences.removeHasPendingRestoreNotificationState();
    }
  }

  /**
   * Show restore notification if user has not onboarded and app is not active.
   * Called when WorkManager starts RestoreNotificationWorker work.
   */
  public static void doRestoreNotificationWork(Context context,
      ExposureNotificationSharedPreferences sharedPreferences,
      NotificationHelper notificationHelper) {
    boolean isAppActivityInForeground = isAppActivityInForeground();
    if (sharedPreferences.getOnboardedState() == OnboardingStatus.UNKNOWN &&
        !isAppActivityInForeground) {

      notificationHelper.showReActivateENAppNotification(context,
          R.string.reactivate_exposure_notification_app_subject,
          R.string.reactivate_exposure_notification_app_body);
    }
  }

  private static int getAppStandbyBucket(Context context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      UsageStatsManager usageStatsManager =
          (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
      if (usageStatsManager != null) {
        return usageStatsManager.getAppStandbyBucket();
      }
      return STANDBY_BUCKET_NEVER;
    }
    return STANDBY_BUCKET_ACTIVE;
  }

  private static boolean isAppActivityInForeground() {
    ActivityManager.RunningAppProcessInfo appProcessInfo =
        new ActivityManager.RunningAppProcessInfo();
    ActivityManager.getMyMemoryState(appProcessInfo);
    return (appProcessInfo.importance == IMPORTANCE_FOREGROUND
        || appProcessInfo.importance == IMPORTANCE_VISIBLE);
  }
}
