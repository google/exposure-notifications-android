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

package com.google.android.apps.exposurenotification.storage;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.core.app.ApplicationProvider;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences.NetworkMode;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences.OnboardingStatus;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Test for {@link ExposureNotificationSharedPreferences} key value storage. */
@RunWith(RobolectricTestRunner.class)
public class ExposureNotificationSharedPreferencesTest {

  private ExposureNotificationSharedPreferences exposureNotificationSharedPreferences;

  @Before
  public void setUp() {
    exposureNotificationSharedPreferences =
        new ExposureNotificationSharedPreferences(ApplicationProvider.getApplicationContext());
  }

  @Test
  public void onboardedState_default_isUnknown() {
    assertThat(exposureNotificationSharedPreferences.getOnboardedState())
        .isEqualTo(OnboardingStatus.UNKNOWN);
  }

  @Test
  public void onboardedState_skipped() {
    exposureNotificationSharedPreferences.setOnboardedState(false);

    assertThat(exposureNotificationSharedPreferences.getOnboardedState())
        .isEqualTo(OnboardingStatus.SKIPPED);
  }

  @Test
  public void onboardedState_onboarded() {
    exposureNotificationSharedPreferences.setOnboardedState(true);

    assertThat(exposureNotificationSharedPreferences.getOnboardedState())
        .isEqualTo(OnboardingStatus.ONBOARDED);
  }

  @Test
  public void keySharingNetworkMode_default() {
    assertThat(exposureNotificationSharedPreferences.getKeySharingNetworkMode(NetworkMode.DISABLED))
        .isEqualTo(NetworkMode.DISABLED);
  }

  @Test
  public void keySharingNetworkMode_update() {
    exposureNotificationSharedPreferences.setKeySharingNetworkMode(NetworkMode.LIVE);

    assertThat(exposureNotificationSharedPreferences.getKeySharingNetworkMode(NetworkMode.DISABLED))
        .isEqualTo(NetworkMode.LIVE);
  }

  @Test
  public void verificationNetworkMode_default() {
    assertThat(exposureNotificationSharedPreferences.getVerificationNetworkMode(NetworkMode.DISABLED))
        .isEqualTo(NetworkMode.DISABLED);
  }

  @Test
  public void verificationNetworkMode_update() {
    exposureNotificationSharedPreferences.setVerificationNetworkMode(NetworkMode.LIVE);

    assertThat(exposureNotificationSharedPreferences.getVerificationNetworkMode(NetworkMode.DISABLED))
        .isEqualTo(NetworkMode.LIVE);
  }

  @Test
  public void verificationServerUri1_default() {
    assertThat(exposureNotificationSharedPreferences.getVerificationServerAddress1("default"))
        .isEqualTo("default");
  }

  @Test
  public void verificationServerUri1_update() {
    exposureNotificationSharedPreferences.setVerificationServerAddress1("updated");

    assertThat(exposureNotificationSharedPreferences.getVerificationServerAddress1("default"))
        .isEqualTo("updated");
  }

  @Test
  public void verificationServerUri2_default() {
    assertThat(exposureNotificationSharedPreferences.getVerificationServerAddress2("default"))
        .isEqualTo("default");
  }

  @Test
  public void verificationServerUri2_update() {
    exposureNotificationSharedPreferences.setVerificationServerAddress2("updated");

    assertThat(exposureNotificationSharedPreferences.getVerificationServerAddress2("default"))
        .isEqualTo("updated");
  }
}
