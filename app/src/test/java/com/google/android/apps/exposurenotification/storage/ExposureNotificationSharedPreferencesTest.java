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
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences.OnboardingStatus;
import com.google.android.apps.exposurenotification.testsupport.ExposureNotificationRules;
import com.google.android.apps.exposurenotification.testsupport.FakeClock;
import com.google.common.base.Optional;
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
import org.threeten.bp.Duration;
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
        new ExposureNotificationSharedPreferences(ApplicationProvider.getApplicationContext(),
            clock);
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
  public void exposureNotificationLastShownClassification() {
    exposureNotificationSharedPreferences.setPrivateAnalyticsState(true);
    Instant notificationTime = Instant.ofEpochMilli(123L);
    exposureNotificationSharedPreferences
        .setExposureNotificationLastShownClassification(notificationTime, /* classificationIndex= */
            1);

    assertThat(
        exposureNotificationSharedPreferences.getExposureNotificationLastShownClassification())
        .isEqualTo(1);
    assertThat(exposureNotificationSharedPreferences.getExposureNotificationLastShownTime())
        .isEqualTo(notificationTime);
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
  public void getPrivateAnalyticsStateLiveData_default_isFalse() {
    LiveData<Boolean> privateAnalyticsStateLiveData =
        exposureNotificationSharedPreferences.getPrivateAnalyticsStateLiveData();
    List<Boolean> values = new ArrayList<>();
    privateAnalyticsStateLiveData.observeForever(values::add);

    assertThat(values).containsExactly(false);
  }

  @Test
  public void setPrivateAnalyticsState_trueThenFalse_observedInLiveData() {
    LiveData<Boolean> privateAnalyticsStateLiveData =
        exposureNotificationSharedPreferences.getPrivateAnalyticsStateLiveData();
    List<Boolean> values = new ArrayList<>();
    privateAnalyticsStateLiveData.observeForever(values::add);

    exposureNotificationSharedPreferences.setPrivateAnalyticsState(true);
    exposureNotificationSharedPreferences.setPrivateAnalyticsState(false);

    // Observe default, then true, then false
    assertThat(values).containsExactly(false, true, false).inOrder();
  }

  @Test
  public void isPrivateAnalyticsStateSetLiveData_default_isFalse() {
    LiveData<Boolean> privateAnalyticsStateLiveData =
        exposureNotificationSharedPreferences.isPrivateAnalyticsStateSetLiveData();
    List<Boolean> values = new ArrayList<>();
    privateAnalyticsStateLiveData.observeForever(values::add);

    assertThat(values).containsExactly(false);
  }

  @Test
  public void isPrivateAnalyticsStateSetLiveData_setTwice_observedInLiveDataOnce() {
    LiveData<Boolean> privateAnalyticsStateLiveData =
        exposureNotificationSharedPreferences.isPrivateAnalyticsStateSetLiveData();
    List<Boolean> values = new ArrayList<>();
    privateAnalyticsStateLiveData.observeForever(values::add);

    exposureNotificationSharedPreferences.setPrivateAnalyticsState(true);
    exposureNotificationSharedPreferences.setPrivateAnalyticsState(false);

    assertThat(values).containsExactly(false, true);
  }

  @Test
  public void isPrivateAnalyticsStateSet_default_isFalse() {
    assertThat(exposureNotificationSharedPreferences.isPrivateAnalyticsStateSet()).isFalse();
  }

  @Test
  public void isPrivateAnalyticsStateSet_set_isTrue() {
    exposureNotificationSharedPreferences.setPrivateAnalyticsState(true);

    assertThat(exposureNotificationSharedPreferences.isPrivateAnalyticsStateSet()).isTrue();
  }

  @Test
  public void setAndGetLoggingLastTimeStamp() {
    clock.set(Instant.ofEpochMilli(2));
    Optional<Instant> instant =
        exposureNotificationSharedPreferences.maybeGetAnalyticsLoggingLastTimestamp();
    assertThat(instant.isPresent()).isFalse();

    exposureNotificationSharedPreferences.resetAnalyticsLoggingLastTimestamp();
    instant = exposureNotificationSharedPreferences.maybeGetAnalyticsLoggingLastTimestamp();
    assertThat(instant.get()).isEqualTo(Instant.ofEpochMilli(2));

    clock.advanceBy(Duration.ofMillis(2));
    instant = exposureNotificationSharedPreferences.maybeGetAnalyticsLoggingLastTimestamp();
    assertThat(instant.get()).isEqualTo(Instant.ofEpochMilli(2));

    exposureNotificationSharedPreferences.resetAnalyticsLoggingLastTimestamp();
    instant = exposureNotificationSharedPreferences.maybeGetAnalyticsLoggingLastTimestamp();
    assertThat(instant.get()).isEqualTo(Instant.ofEpochMilli(4));
  }
}
