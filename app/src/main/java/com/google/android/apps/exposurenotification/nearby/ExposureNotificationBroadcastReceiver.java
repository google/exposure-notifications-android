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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;
import androidx.work.WorkManager;
import com.google.android.apps.exposurenotification.BuildConfig;
import com.google.android.apps.exposurenotification.common.NotificationHelper;
import com.google.android.apps.exposurenotification.common.logging.Logger;
import com.google.android.apps.exposurenotification.restore.RestoreNotificationUtil;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.gms.nearby.exposurenotification.ExposureNotificationClient;
import dagger.hilt.android.AndroidEntryPoint;
import javax.inject.Inject;

/**
 * Broadcast receiver for callbacks from exposure notification API.
 */
@AndroidEntryPoint(BroadcastReceiver.class)
public class ExposureNotificationBroadcastReceiver extends
    Hilt_ExposureNotificationBroadcastReceiver {

  private static final Logger logger = Logger.getLogger("ENBroadcastReceiver");

  @Inject
  WorkManager workManager;

  @Inject
  NotificationHelper notificationHelper;

  @Inject
  ExposureNotificationSharedPreferences exposureNotificationSharedPreferences;

  @Override
  public void onReceive(Context context, Intent intent) {
    super.onReceive(context, intent);
    String action = intent.getAction();
    if (action == null) {
      return;
    }

    switch (action) {
      case ExposureNotificationClient.ACTION_EXPOSURE_NOT_FOUND:
        // For debug/testing purposes show a toast when no exposures were found
        if (BuildConfig.DEBUG) {
          Toast.makeText(context, "No exposures found", Toast.LENGTH_SHORT).show();
        }
        StateUpdatedWorker.runOnce(workManager);
        break;
      case ExposureNotificationClient.ACTION_EXPOSURE_STATE_UPDATED:
        StateUpdatedWorker.runOnce(workManager);
        break;
      case ExposureNotificationClientWrapper.ACTION_WAKE_UP:
        RestoreNotificationUtil.onENApiWakeupEvent(context,
            exposureNotificationSharedPreferences, workManager, notificationHelper);
        break;
    }
  }

}
