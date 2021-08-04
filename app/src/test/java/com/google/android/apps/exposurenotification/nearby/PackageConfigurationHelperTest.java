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
  public void maybeUpdateAnalyticsState_nullConfiguration_nothingUpdated() {
    packageConfigurationHelper.maybeUpdateAnalyticsState(null);

    assertThat(exposureNotificationSharedPreferences.isAppAnalyticsSet()).isEqualTo(false);
    assertThat(exposureNotificationSharedPreferences.getAppAnalyticsState()).isEqualTo(false);
    assertThat(exposureNotificationSharedPreferences.isPrivateAnalyticsStateSet()).isEqualTo(false);
    assertThat(exposureNotificationSharedPreferences.getPrivateAnalyticState()).isEqualTo(false);
  }

  @Test
  public void maybeUpdateAnalyticsState_nullConfigurationBundle_nothingUpdated() {
    PackageConfiguration packageConfiguration = new PackageConfigurationBuilder().build();

    packageConfigurationHelper.maybeUpdateAnalyticsState(packageConfiguration);

    assertThat(exposureNotificationSharedPreferences.isAppAnalyticsSet()).isEqualTo(false);
    assertThat(exposureNotificationSharedPreferences.getAppAnalyticsState()).isEqualTo(false);
    assertThat(exposureNotificationSharedPreferences.isPrivateAnalyticsStateSet()).isEqualTo(false);
    assertThat(exposureNotificationSharedPreferences.getPrivateAnalyticState()).isEqualTo(false);
  }

  @Test
  public void getCheckboxConsentFromPackageConfiguration_nullPackageConfiguration_checkBoxConsentIsFalse() {
    boolean checkboxConsent =
        PackageConfigurationHelper.getCheckboxConsentFromPackageConfiguration(null);

    assertThat(checkboxConsent).isEqualTo(false);
  }

  @Test
  public void getCheckboxConsentFromPackageConfiguration_nullConfigurationBundle_checkBoxConsentIsFalse() {
    PackageConfiguration packageConfiguration = new PackageConfigurationBuilder().build();

    boolean checkboxConsent =
        PackageConfigurationHelper.getCheckboxConsentFromPackageConfiguration(packageConfiguration);

    assertThat(checkboxConsent).isEqualTo(false);
  }

  @Test
  public void maybeUpdateAnalyticsState_appAnalyticsAlreadySet_notUpdated() {
    exposureNotificationSharedPreferences.setAppAnalyticsState(false);
    Bundle bundle = new Bundle();
    bundle.putBoolean(PackageConfigurationHelper.APP_ANALYTICS_OPT_IN, true);
    PackageConfiguration packageConfiguration =
        new PackageConfigurationBuilder().setValues(bundle).build();

    packageConfigurationHelper.maybeUpdateAnalyticsState(packageConfiguration);

    assertThat(exposureNotificationSharedPreferences.isAppAnalyticsSet()).isEqualTo(true);
    assertThat(exposureNotificationSharedPreferences.getAppAnalyticsState()).isEqualTo(false);
  }

  @Test
  public void maybeUpdateAnalyticsState_privateAnalyticsAlreadySet_notUpdated() {
    exposureNotificationSharedPreferences.setPrivateAnalyticsState(false);
    Bundle bundle = new Bundle();
    bundle.putBoolean(PackageConfigurationHelper.PRIVATE_ANALYTICS_OPT_IN, true);
    PackageConfiguration packageConfiguration =
        new PackageConfigurationBuilder().setValues(bundle).build();

    packageConfigurationHelper.maybeUpdateAnalyticsState(packageConfiguration);

    assertThat(exposureNotificationSharedPreferences.isPrivateAnalyticsStateSet()).isEqualTo(true);
    assertThat(exposureNotificationSharedPreferences.getPrivateAnalyticState()).isEqualTo(false);
  }

  @Test
  public void maybeUpdateAnalyticsState_appAnalyticsFalse_notUpdated() {
    Bundle bundle = new Bundle();
    bundle.putBoolean(PackageConfigurationHelper.APP_ANALYTICS_OPT_IN, false);
    PackageConfiguration packageConfiguration =
        new PackageConfigurationBuilder().setValues(bundle).build();

    packageConfigurationHelper.maybeUpdateAnalyticsState(packageConfiguration);

    assertThat(exposureNotificationSharedPreferences.isAppAnalyticsSet()).isEqualTo(false);
    assertThat(exposureNotificationSharedPreferences.getAppAnalyticsState()).isEqualTo(false);
  }

  @Test
  public void maybeUpdateAnalyticsState_appAnalyticsTrue_updated() {
    Bundle bundle = new Bundle();
    bundle.putBoolean(PackageConfigurationHelper.APP_ANALYTICS_OPT_IN, true);
    PackageConfiguration packageConfiguration =
        new PackageConfigurationBuilder().setValues(bundle).build();

    packageConfigurationHelper.maybeUpdateAnalyticsState(packageConfiguration);

    assertThat(exposureNotificationSharedPreferences.isAppAnalyticsSet()).isEqualTo(true);
    assertThat(exposureNotificationSharedPreferences.getAppAnalyticsState()).isEqualTo(true);
  }

  @Test
  public void maybeUpdateAnalyticsState_privateAnalyticsFalse_updated() {
    Bundle bundle = new Bundle();
    bundle.putBoolean(PackageConfigurationHelper.PRIVATE_ANALYTICS_OPT_IN, false);
    PackageConfiguration packageConfiguration =
        new PackageConfigurationBuilder().setValues(bundle).build();

    packageConfigurationHelper.maybeUpdateAnalyticsState(packageConfiguration);

    assertThat(exposureNotificationSharedPreferences.isPrivateAnalyticsStateSet()).isEqualTo(true);
    assertThat(exposureNotificationSharedPreferences.getPrivateAnalyticState()).isEqualTo(false);
  }

  @Test
  public void maybeUpdateAnalyticsState_privateAnalyticsTrue_updated() {
    Bundle bundle = new Bundle();
    bundle.putBoolean(PackageConfigurationHelper.PRIVATE_ANALYTICS_OPT_IN, true);
    PackageConfiguration packageConfiguration =
        new PackageConfigurationBuilder().setValues(bundle).build();

    packageConfigurationHelper.maybeUpdateAnalyticsState(packageConfiguration);

    assertThat(exposureNotificationSharedPreferences.isPrivateAnalyticsStateSet()).isEqualTo(true);
    assertThat(exposureNotificationSharedPreferences.getPrivateAnalyticState()).isEqualTo(true);
  }

  @Test
  public void getCheckboxConsentFromPackageConfiguration_checkboxConsentFalse_checkBoxConsentIsFalse() {
    Bundle bundle = new Bundle();
    bundle.putBoolean(PackageConfigurationHelper.CHECK_BOX_API_KEY, false);
    PackageConfiguration packageConfiguration =
        new PackageConfigurationBuilder().setValues(bundle).build();

    boolean checkboxConsent =
        PackageConfigurationHelper.getCheckboxConsentFromPackageConfiguration(packageConfiguration);

    assertThat(checkboxConsent).isEqualTo(false);
  }

  @Test
  public void getCheckboxConsentFromPackageConfiguration_checkboxConsentTrue_checkBoxConsentIsTrue() {
    Bundle bundle = new Bundle();
    bundle.putBoolean(PackageConfigurationHelper.CHECK_BOX_API_KEY, true);
    PackageConfiguration packageConfiguration =
        new PackageConfigurationBuilder().setValues(bundle).build();

    boolean checkboxConsent =
        PackageConfigurationHelper.getCheckboxConsentFromPackageConfiguration(packageConfiguration);

    assertThat(checkboxConsent).isEqualTo(true);
  }

  @Test
  public void getSmsNoticeFromPackageConfiguration_smsNoticeNotPresent_returnsFalse() {
    Bundle bundle = new Bundle();
    PackageConfiguration packageConfiguration =
        new PackageConfigurationBuilder().setValues(bundle).build();

    boolean checkboxConsent =
        PackageConfigurationHelper.getSmsNoticeFromPackageConfiguration(packageConfiguration);

    assertThat(checkboxConsent).isEqualTo(false);
  }

  @Test
  public void getSmsNoticeFromPackageConfiguration_smsNoticeFalse_returnsFalse() {
    Bundle bundle = new Bundle();
    bundle.putBoolean(PackageConfigurationHelper.SMS_NOTICE, false);
    PackageConfiguration packageConfiguration =
        new PackageConfigurationBuilder().setValues(bundle).build();

    boolean checkboxConsent =
        PackageConfigurationHelper.getSmsNoticeFromPackageConfiguration(packageConfiguration);

    assertThat(checkboxConsent).isEqualTo(false);
  }

  @Test
  public void getSmsNoticeFromPackageConfiguration_smsNoticeTrue_returnsTrue() {
    Bundle bundle = new Bundle();
    bundle.putBoolean(PackageConfigurationHelper.SMS_NOTICE, true);
    PackageConfiguration packageConfiguration =
        new PackageConfigurationBuilder().setValues(bundle).build();

    boolean checkboxConsent =
        PackageConfigurationHelper.getSmsNoticeFromPackageConfiguration(packageConfiguration);

    assertThat(checkboxConsent).isEqualTo(true);
  }
}
