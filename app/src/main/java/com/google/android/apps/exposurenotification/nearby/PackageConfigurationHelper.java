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
import com.google.common.base.Optional;
import javax.inject.Inject;

/**
 * Helper methods for {@link PackageConfiguration}.
 */
public class PackageConfigurationHelper {

  public static final String APP_ANALYTICS_OPT_IN = "METRICS_OPT_IN";
  public static final String PRIVATE_ANALYTICS_OPT_IN = "APPA_OPT_IN";
  public static final String CHECK_BOX_API_KEY = "check_box_api";

  private final ExposureNotificationSharedPreferences exposureNotificationSharedPreferences;

  @Inject
  public PackageConfigurationHelper(
      ExposureNotificationSharedPreferences exposureNotificationSharedPreferences) {
    this.exposureNotificationSharedPreferences = exposureNotificationSharedPreferences;
  }

  /**
   * Updates the various analytics states with a given {@link PackageConfiguration}
   *
   * <p>If app analytics is not set and app analytics state from {@link PackageConfiguration} is
   * true,
   * update the app analytics state in {@link ExposureNotificationSharedPreferences}.
   *
   * <p>If private analytics is not set, and private analytics state exists in the {@link
   * PackageConfiguration}, set the private analytics state in {@link
   * ExposureNotificationSharedPreferences}.
   */
  public void maybeUpdateAnalyticsState(@Nullable PackageConfiguration packageConfiguration) {
    if (packageConfiguration == null) {
      return;
    }
    if (getAppAnalyticsFromPackageConfiguration(packageConfiguration)) {
      if (!exposureNotificationSharedPreferences.isAppAnalyticsSet()) {
        exposureNotificationSharedPreferences.setAppAnalyticsState(true);
      }
    }

    Optional<Boolean> privateAnalyticsFromConfiguration =
        maybeGetPrivateAnalyticsFromPackageConfiguration(packageConfiguration);
    if (privateAnalyticsFromConfiguration.isPresent()) {
      if (!exposureNotificationSharedPreferences.isPrivateAnalyticsStateSet()) {
        exposureNotificationSharedPreferences
            .setPrivateAnalyticsState(privateAnalyticsFromConfiguration.get());
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
      return values.getBoolean(APP_ANALYTICS_OPT_IN, false);
    }
    return false;
  }

  /**
   * Fetches the private analytics state from a {@link PackageConfiguration} if it exists. Otherwise
   * absent.
   */
  private static Optional<Boolean> maybeGetPrivateAnalyticsFromPackageConfiguration(
      PackageConfiguration packageConfiguration) {
    Bundle values = packageConfiguration.getValues();
    if (values == null) {
      return Optional.absent();
    }
    if (!values.containsKey(PRIVATE_ANALYTICS_OPT_IN)) {
      return Optional.absent();
    }
    return Optional.of(values.getBoolean(PRIVATE_ANALYTICS_OPT_IN));
  }

  /**
   * Fetches the checkbox consent from a {@link PackageConfiguration} if it exists. Otherwise
   * returns false.
   */
  public static boolean getCheckboxConsentFromPackageConfiguration(
      @Nullable PackageConfiguration packageConfiguration) {
    if (packageConfiguration == null) {
      return false;
    }

    Bundle values = packageConfiguration.getValues();
    if (values != null) {
      return values.getBoolean(CHECK_BOX_API_KEY, false);
    }
    return false;
  }


}
