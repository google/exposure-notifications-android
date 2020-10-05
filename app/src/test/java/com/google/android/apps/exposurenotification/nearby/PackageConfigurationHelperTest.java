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

import static com.google.common.truth.Truth.assertThat;

import android.os.Bundle;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.apps.exposurenotification.testsupport.ExposureNotificationRules;
import com.google.android.gms.nearby.exposurenotification.PackageConfiguration;
import com.google.android.gms.nearby.exposurenotification.PackageConfiguration.PackageConfigurationBuilder;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@HiltAndroidTest
@RunWith(RobolectricTestRunner.class)
@Config(application = HiltTestApplication.class)
public class PackageConfigurationHelperTest {

  @Rule
  public ExposureNotificationRules rules = ExposureNotificationRules.forTest(this).build();

  @Inject
  ExposureNotificationSharedPreferences exposureNotificationSharedPreferences;

  private PackageConfigurationHelper packageConfigurationHelper;

  @Before
  public void setup() {
    rules.hilt().inject();
    packageConfigurationHelper = new PackageConfigurationHelper(
        exposureNotificationSharedPreferences);
  }

  @Test
  public void maybeUpdateAppAnalyticsState_nullConfiguration_notUpdated() {
    packageConfigurationHelper.maybeUpdateAppAnalyticsState(null);

    assertThat(exposureNotificationSharedPreferences.isAppAnalyticsSet()).isEqualTo(false);
    assertThat(exposureNotificationSharedPreferences.getAppAnalyticsState()).isEqualTo(false);
  }

  @Test
  public void maybeUpdateAppAnalyticsState_nullConfigurationBundle_notUpdated() {
    PackageConfiguration packageConfiguration = new PackageConfigurationBuilder().build();

    packageConfigurationHelper.maybeUpdateAppAnalyticsState(packageConfiguration);

    assertThat(exposureNotificationSharedPreferences.isAppAnalyticsSet()).isEqualTo(false);
    assertThat(exposureNotificationSharedPreferences.getAppAnalyticsState()).isEqualTo(false);
  }

  @Test
  public void maybeUpdateAppAnalyticsState_alreadySet_notUpdated() {
    exposureNotificationSharedPreferences.setAppAnalyticsState(false);
    Bundle bundle = new Bundle();
    bundle.putBoolean(PackageConfigurationHelper.METRICS_OPT_IN, true);
    PackageConfiguration packageConfiguration =
        new PackageConfigurationBuilder().setValues(bundle).build();

    packageConfigurationHelper.maybeUpdateAppAnalyticsState(packageConfiguration);

    assertThat(exposureNotificationSharedPreferences.isAppAnalyticsSet()).isEqualTo(true);
    assertThat(exposureNotificationSharedPreferences.getAppAnalyticsState()).isEqualTo(false);
  }

  @Test
  public void maybeUpdateAppAnalyticsState_metricsFalse_notUpdated() {
    Bundle bundle = new Bundle();
    bundle.putBoolean(PackageConfigurationHelper.METRICS_OPT_IN, false);
    PackageConfiguration packageConfiguration =
        new PackageConfigurationBuilder().setValues(bundle).build();

    packageConfigurationHelper.maybeUpdateAppAnalyticsState(packageConfiguration);

    assertThat(exposureNotificationSharedPreferences.isAppAnalyticsSet()).isEqualTo(false);
    assertThat(exposureNotificationSharedPreferences.getAppAnalyticsState()).isEqualTo(false);
  }

  @Test
  public void maybeUpdateAppAnalyticsState_metricsTrue_updated() {
    Bundle bundle = new Bundle();
    bundle.putBoolean(PackageConfigurationHelper.METRICS_OPT_IN, true);
    PackageConfiguration packageConfiguration =
        new PackageConfigurationBuilder().setValues(bundle).build();

    packageConfigurationHelper.maybeUpdateAppAnalyticsState(packageConfiguration);

    assertThat(exposureNotificationSharedPreferences.isAppAnalyticsSet()).isEqualTo(true);
    assertThat(exposureNotificationSharedPreferences.getAppAnalyticsState()).isEqualTo(true);
  }
}
