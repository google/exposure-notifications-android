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

package com.google.android.apps.exposurenotification.nearby;

import android.content.Context;
import android.text.TextUtils;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.home.ExposureNotificationViewModel.ExposureNotificationState;

/**
 * Helper class for obtaining information on the {@link ExposureNotificationState}.
 */
public class EnStateUtil {

  /**
   * Checks if the current {@link ExposureNotificationState} means that Exposure Notifications
   * has been turned down (either by the local health authority or globally).
   *
   * @param state current Exposure Notifications state
   */
  public static boolean isEnTurndown(ExposureNotificationState state) {
    return state == ExposureNotificationState.PAUSED_EN_NOT_SUPPORT
        || state == ExposureNotificationState.PAUSED_NOT_IN_ALLOWLIST;
  }

  /**
   * Checks if the current {@link ExposureNotificationState} means that Exposure Notifications
   * has been turned down for the current region.
   *
   * @param state current Exposure Notifications state
   */
  public static boolean isEnTurndownForRegion(ExposureNotificationState state) {
    return state == ExposureNotificationState.PAUSED_NOT_IN_ALLOWLIST;
  }

  /**
   * Checks if a health authority has provided a message to be displayed in case of a turndown.
   *
   * @param context current application context
   */
  public static boolean isAgencyTurndownMessagePresent(Context context) {
    return !TextUtils.isEmpty(context.getString(R.string.turndown_agency_message));
  }

  private EnStateUtil() {}

}
