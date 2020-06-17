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

import static com.google.android.gms.nearby.exposurenotification.ExposureNotificationClient.EXTRA_TOKEN;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import com.google.android.gms.nearby.exposurenotification.ExposureNotificationClient;

/** Broadcast receiver for callbacks from exposure notification API. */
public class ExposureNotificationBroadcastReceiver extends BroadcastReceiver {

  private static final String TAG = "ENBroadcastReceiver";

  @Override
  public void onReceive(Context context, Intent intent) {
    String action = intent.getAction();
    WorkManager workManager = WorkManager.getInstance(context);
    if (ExposureNotificationClient.ACTION_EXPOSURE_STATE_UPDATED.equals(action)) {
      String token = intent.getStringExtra(EXTRA_TOKEN);
      workManager.enqueue(
          new OneTimeWorkRequest.Builder(StateUpdatedWorker.class)
              .setInputData(new Data.Builder().putString(EXTRA_TOKEN, token).build())
              .build());
    }
  }
}
