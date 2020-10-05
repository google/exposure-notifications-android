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

import android.os.Bundle;
import androidx.annotation.Nullable;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.gms.nearby.exposurenotification.PackageConfiguration;
import javax.inject.Inject;

/**
 * Helper methods for {@link PackageConfiguration}.
 */
public class PackageConfigurationHelper {

  public static final String METRICS_OPT_IN = "METRICS_OPT_IN";

  private final ExposureNotificationSharedPreferences exposureNotificationSharedPreferences;

  @Inject
  public PackageConfigurationHelper(
      ExposureNotificationSharedPreferences exposureNotificationSharedPreferences) {
    this.exposureNotificationSharedPreferences = exposureNotificationSharedPreferences;
  }

  /**
   * If app analytics is not set and app analytics state from {@link PackageConfiguration} is true,
   * update the app analytics state in {@link ExposureNotificationSharedPreferences}.
   */
  public void maybeUpdateAppAnalyticsState(@Nullable PackageConfiguration packageConfiguration) {
    if (packageConfiguration == null) {
      return;
    }
    if (getAppAnalyticsFromPackageConfiguration(packageConfiguration)) {
      if (!exposureNotificationSharedPreferences.isAppAnalyticsSet()) {
        exposureNotificationSharedPreferences.setAppAnalyticsState(true);
      }
    }
  }

  /**
   * Fetches the app analytics state from a {@link PackageConfiguration} if it exists. Otherwise
   * false.
   */
  private static boolean getAppAnalyticsFromPackageConfiguration(
      PackageConfiguration packageConfiguration) {
    Bundle values = packageConfiguration.getValues();
    if (values != null) {
      return values.getBoolean(METRICS_OPT_IN, false);
    }
    return false;
  }

}
