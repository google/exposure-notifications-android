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

package com.google.android.apps.exposurenotification.common;

import static com.google.android.apps.exposurenotification.common.IntentUtil.NOTIFICATION_DISMISSED_ACTION_ID;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.android.apps.exposurenotification.riskcalculation.ExposureClassification;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences.NotificationInteraction;
import dagger.hilt.android.AndroidEntryPoint;
import javax.inject.Inject;

/**
 * Broadcast receiver for callbacks from dismissed exposure notifications
 */
@AndroidEntryPoint(BroadcastReceiver.class)
public class ExposureNotificationDismissedReceiver extends
    Hilt_ExposureNotificationDismissedReceiver {

  @Inject
  ExposureNotificationSharedPreferences exposureNotificationSharedPreferences;

  @Inject
  Clock clock;

  @Override
  public void onReceive(Context context, Intent intent) {
    super.onReceive(context, intent);

    boolean isPossibleExposurePresent =
        exposureNotificationSharedPreferences.getExposureClassification().getClassificationIndex()
            != ExposureClassification.NO_EXPOSURE_CLASSIFICATION_INDEX
            || exposureNotificationSharedPreferences.getIsExposureClassificationRevoked();

    if (NOTIFICATION_DISMISSED_ACTION_ID.equals(intent.getAction())
        && isPossibleExposurePresent) {
      // We assume it dismissed the last notification received.
      int classificationIndex = exposureNotificationSharedPreferences
          .getExposureNotificationLastShownClassification();
      exposureNotificationSharedPreferences.setExposureNotificationLastInteraction(
          clock.now(), NotificationInteraction.DISMISSED, classificationIndex);
    }
  }
}
