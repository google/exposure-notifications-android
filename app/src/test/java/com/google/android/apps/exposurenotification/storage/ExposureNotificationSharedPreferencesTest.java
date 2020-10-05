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

import androidx.lifecycle.LiveData;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.android.apps.exposurenotification.testsupport.FakeClock;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences.OnboardingStatus;
import com.google.android.apps.exposurenotification.testsupport.ExposureNotificationRules;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.threeten.bp.Instant;

/**
 * Test for {@link ExposureNotificationSharedPreferences} key value storage.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner.class)
@Config(application = HiltTestApplication.class)
public class ExposureNotificationSharedPreferencesTest {

  @Rule
  public ExposureNotificationRules rules = ExposureNotificationRules.forTest(this).build();

  private ExposureNotificationSharedPreferences exposureNotificationSharedPreferences;
  private FakeClock clock;

  @Before
  public void setUp() {
    clock = new FakeClock();
    exposureNotificationSharedPreferences =
        new ExposureNotificationSharedPreferences(ApplicationProvider.getApplicationContext(), clock);
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
  public void getAppAnalyticsStateLiveData_default_isFalse() {
    LiveData<Boolean> appAnalyticsStateLiveData =
        exposureNotificationSharedPreferences.getAppAnalyticsStateLiveData();
    List<Boolean> values = new ArrayList<>();
    appAnalyticsStateLiveData.observeForever(values::add);

    assertThat(values).containsExactly(false);
  }

  @Test
  public void setAppAnalyticsState_trueThenFalse_observedInLiveData() {
    LiveData<Boolean> appAnalyticsStateLiveData =
        exposureNotificationSharedPreferences.getAppAnalyticsStateLiveData();
    List<Boolean> values = new ArrayList<>();
    appAnalyticsStateLiveData.observeForever(values::add);

    exposureNotificationSharedPreferences.setAppAnalyticsState(true);
    exposureNotificationSharedPreferences.setAppAnalyticsState(false);

    // Observe default, then true, then false
    assertThat(values).containsExactly(false, true, false).inOrder();
  }

  @Test
  public void setAndGetLoggingLastTimeStamp() {
    clock.setMs(2);
    exposureNotificationSharedPreferences.getAndResetAnalyticsLoggingLastTimestamp();
    Instant instant = exposureNotificationSharedPreferences.getAnalyticsLoggingLastTimestamp();
    assertThat(instant).isEqualTo(Instant.ofEpochMilli(2));

    clock.advanceMs(2);
    instant = exposureNotificationSharedPreferences.getAndResetAnalyticsLoggingLastTimestamp();
    assertThat(instant).isEqualTo(Instant.ofEpochMilli(2));

    instant = exposureNotificationSharedPreferences.getAnalyticsLoggingLastTimestamp();
    assertThat(instant).isEqualTo(Instant.ofEpochMilli(4));
  }
}
