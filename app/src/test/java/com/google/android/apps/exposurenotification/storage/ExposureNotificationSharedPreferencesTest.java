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
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences.NotificationInteraction;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences.OnboardingStatus;
import com.google.android.apps.exposurenotification.testsupport.ExposureNotificationRules;
import com.google.android.apps.exposurenotification.testsupport.FakeClock;
import com.google.common.base.Optional;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
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
    int classificationIndex = 1;
    exposureNotificationSharedPreferences
        .setExposureNotificationLastShownClassification(notificationTime, classificationIndex);

    assertThat(
        exposureNotificationSharedPreferences.getExposureNotificationLastShownClassification())
        .isEqualTo(classificationIndex);
    assertThat(exposureNotificationSharedPreferences.getExposureNotificationLastShownTime())
        .isEqualTo(notificationTime);
  }

  @Test
  public void exposureNotificationLastInteraction() {
    exposureNotificationSharedPreferences.setPrivateAnalyticsState(true);
    Instant interactionTime = Instant.ofEpochMilli(123L);
    NotificationInteraction interaction = NotificationInteraction.DISMISSED;
    int classificationIndex = 2;
    exposureNotificationSharedPreferences
        .setExposureNotificationLastInteraction(interactionTime, interaction, classificationIndex);

    assertThat(
        exposureNotificationSharedPreferences.getExposureNotificationLastInteractionTime())
        .isEqualTo(interactionTime);
    assertThat(exposureNotificationSharedPreferences.getExposureNotificationLastInteractionType())
        .isEqualTo(interaction);
    assertThat(
        exposureNotificationSharedPreferences
            .getExposureNotificationLastInteractionClassification())
        .isEqualTo(classificationIndex);
  }

  @Test
  public void exposureNotificationLastTimes() {
    exposureNotificationSharedPreferences.setPrivateAnalyticsState(true);
    Instant workerTime = Instant.ofEpochMilli(123L);
    Instant codeMetricTime = Instant.ofEpochMilli(124L);
    Instant keysMetricTime = Instant.ofEpochMilli(125L);
    exposureNotificationSharedPreferences.setPrivateAnalyticsWorkerLastTime(workerTime);
    exposureNotificationSharedPreferences.setPrivateAnalyticsLastSubmittedCodeTime(codeMetricTime);
    exposureNotificationSharedPreferences.setPrivateAnalyticsLastSubmittedKeysTime(keysMetricTime);

    assertThat(exposureNotificationSharedPreferences.getPrivateAnalyticsWorkerLastTime())
        .isEqualTo(workerTime);
    assertThat(exposureNotificationSharedPreferences.getPrivateAnalyticsLastSubmittedCodeTime())
        .isEqualTo(codeMetricTime);
    assertThat(exposureNotificationSharedPreferences.getPrivateAnalyticsLastSubmittedKeysTime())
        .isEqualTo(keysMetricTime);
  }

  @Test
  public void clearPrivateAnalyticsFields() {
    exposureNotificationSharedPreferences.setPrivateAnalyticsState(true);
    Instant zeroTime = Instant.ofEpochMilli(0L);
    Instant time = Instant.ofEpochMilli(123L);
    exposureNotificationSharedPreferences.setPrivateAnalyticsWorkerLastTime(time);
    exposureNotificationSharedPreferences.setPrivateAnalyticsLastSubmittedCodeTime(time);
    exposureNotificationSharedPreferences.setPrivateAnalyticsLastSubmittedKeysTime(time);
    exposureNotificationSharedPreferences
        .setExposureNotificationLastShownClassification(time, /* classificationIndex= */
            1);
    exposureNotificationSharedPreferences
        .setExposureNotificationLastInteraction(time, NotificationInteraction.CLICKED, 2);

    assertThat(exposureNotificationSharedPreferences.getPrivateAnalyticsWorkerLastTime())
        .isEqualTo(time);
    assertThat(exposureNotificationSharedPreferences.getPrivateAnalyticsLastSubmittedCodeTime())
        .isEqualTo(time);
    assertThat(exposureNotificationSharedPreferences.getPrivateAnalyticsLastSubmittedKeysTime())
        .isEqualTo(time);
    assertThat(exposureNotificationSharedPreferences.getExposureNotificationLastShownTime())
        .isEqualTo(time);
    assertThat(
        exposureNotificationSharedPreferences.getExposureNotificationLastShownClassification())
        .isEqualTo(1);
    assertThat(exposureNotificationSharedPreferences.getExposureNotificationLastInteractionTime())
        .isEqualTo(time);
    assertThat(exposureNotificationSharedPreferences.getExposureNotificationLastInteractionType())
        .isEqualTo(NotificationInteraction.CLICKED);
    assertThat(
        exposureNotificationSharedPreferences
            .getExposureNotificationLastInteractionClassification())
        .isEqualTo(2);

    // Clear all the Private Analytics fields.
    exposureNotificationSharedPreferences.clearPrivateAnalyticsFields();

    assertThat(exposureNotificationSharedPreferences.getPrivateAnalyticsLastSubmittedCodeTime())
        .isEqualTo(zeroTime);
    assertThat(exposureNotificationSharedPreferences.getPrivateAnalyticsLastSubmittedKeysTime())
        .isEqualTo(zeroTime);
    assertThat(exposureNotificationSharedPreferences.getExposureNotificationLastShownTime())
        .isEqualTo(zeroTime);
    assertThat(
        exposureNotificationSharedPreferences.getExposureNotificationLastShownClassification())
        .isEqualTo(0);
    assertThat(exposureNotificationSharedPreferences.getExposureNotificationLastInteractionTime())
        .isEqualTo(zeroTime);
    assertThat(exposureNotificationSharedPreferences.getExposureNotificationLastInteractionType())
        .isEqualTo(NotificationInteraction.UNKNOWN);
    assertThat(
        exposureNotificationSharedPreferences.getExposureNotificationLastShownClassification())
        .isEqualTo(0);

    // The worker time is the only field not cleared.
    assertThat(exposureNotificationSharedPreferences.getPrivateAnalyticsWorkerLastTime())
        .isEqualTo(time);
  }

  @Test
  public void clearValidPrivateAnalyticsFieldsFails() {
    Instant time = clock.now().minus(Duration.ofDays(2));
    Instant validTime = time.plusSeconds(900);

    exposureNotificationSharedPreferences.setPrivateAnalyticsState(true);

    // Set valid private analytics times
    exposureNotificationSharedPreferences.setPrivateAnalyticsLastSubmittedCodeTime(validTime);
    exposureNotificationSharedPreferences.setPrivateAnalyticsLastSubmittedKeysTime(validTime);
    exposureNotificationSharedPreferences
        .setExposureNotificationLastShownClassification(validTime, /* classificationIndex= */
            1);
    exposureNotificationSharedPreferences
        .setExposureNotificationLastInteraction(validTime, NotificationInteraction.CLICKED, 2);

    // Clear all the expired Private Analytics fields.
    exposureNotificationSharedPreferences.clearPrivateAnalyticsFieldsBefore(time);

    // Valid times should be recovered.
    assertThat(exposureNotificationSharedPreferences.getPrivateAnalyticsLastSubmittedCodeTime())
        .isEqualTo(validTime);
    assertThat(exposureNotificationSharedPreferences.getPrivateAnalyticsLastSubmittedKeysTime())
        .isEqualTo(validTime);
    assertThat(exposureNotificationSharedPreferences.getExposureNotificationLastShownTime())
        .isEqualTo(validTime);
    assertThat(
        exposureNotificationSharedPreferences.getExposureNotificationLastShownClassification())
        .isEqualTo(1);
    assertThat(exposureNotificationSharedPreferences.getExposureNotificationLastInteractionTime())
        .isEqualTo(validTime);
    assertThat(exposureNotificationSharedPreferences.getExposureNotificationLastInteractionType())
        .isEqualTo(NotificationInteraction.CLICKED);
    assertThat(
        exposureNotificationSharedPreferences
            .getExposureNotificationLastInteractionClassification())
        .isEqualTo(2);
  }

  @Test
  public void clearExpiredPrivateAnalyticsFieldsSuceeds() {
    Instant time = clock.now().minus(Duration.ofDays(2));
    Instant expiredTime = time.minusSeconds(900);
    Instant zeroTime = Instant.EPOCH;

    exposureNotificationSharedPreferences.setPrivateAnalyticsState(true);

    // Set valid private analytics times
    exposureNotificationSharedPreferences.setPrivateAnalyticsLastSubmittedCodeTime(expiredTime);
    exposureNotificationSharedPreferences.setPrivateAnalyticsLastSubmittedKeysTime(expiredTime);
    exposureNotificationSharedPreferences
        .setExposureNotificationLastShownClassification(expiredTime, /* classificationIndex= */
            1);
    exposureNotificationSharedPreferences
        .setExposureNotificationLastInteraction(expiredTime, NotificationInteraction.CLICKED, 2);

    // Clear all the expired Private Analytics fields.
    exposureNotificationSharedPreferences.clearPrivateAnalyticsFieldsBefore(time);

    // Default values should be recovered.
    assertThat(exposureNotificationSharedPreferences.getPrivateAnalyticsLastSubmittedCodeTime())
        .isEqualTo(zeroTime);
    assertThat(exposureNotificationSharedPreferences.getPrivateAnalyticsLastSubmittedKeysTime())
        .isEqualTo(zeroTime);
    assertThat(exposureNotificationSharedPreferences.getExposureNotificationLastShownTime())
        .isEqualTo(zeroTime);
    assertThat(
        exposureNotificationSharedPreferences.getExposureNotificationLastShownClassification())
        .isEqualTo(0);
    assertThat(exposureNotificationSharedPreferences.getExposureNotificationLastInteractionTime())
        .isEqualTo(zeroTime);
    assertThat(exposureNotificationSharedPreferences.getExposureNotificationLastInteractionType())
        .isEqualTo(NotificationInteraction.UNKNOWN);
    assertThat(
        exposureNotificationSharedPreferences.getExposureNotificationLastShownClassification())
        .isEqualTo(0);
  }

  @Test
  public void classificationIndexMustBePositive() {
    exposureNotificationSharedPreferences.setPrivateAnalyticsState(true);
    Instant zeroTime = Instant.EPOCH;

    Instant time = Instant.ofEpochSecond(123L);
    int classificationIndex = 0;
    exposureNotificationSharedPreferences
        .setExposureNotificationLastShownClassification(time, classificationIndex);
    exposureNotificationSharedPreferences
        .setExposureNotificationLastInteraction(time, NotificationInteraction.CLICKED,
            classificationIndex);

    // Default values should be recovered.
    assertThat(exposureNotificationSharedPreferences.getExposureNotificationLastShownTime())
        .isEqualTo(Instant.EPOCH);
    assertThat(
        exposureNotificationSharedPreferences.getExposureNotificationLastShownClassification())
        .isEqualTo(0);
    assertThat(exposureNotificationSharedPreferences.getExposureNotificationLastInteractionTime())
        .isEqualTo(Instant.EPOCH);
    assertThat(exposureNotificationSharedPreferences.getExposureNotificationLastInteractionType())
        .isEqualTo(NotificationInteraction.UNKNOWN);
    assertThat(
        exposureNotificationSharedPreferences.getExposureNotificationLastShownClassification())
        .isEqualTo(0);
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

  @Test
  public void providedDiagnosisKeyToLog_isEmptyByDefault() {
    assertThat(exposureNotificationSharedPreferences.getProvidedDiagnosisKeyHexToLog()).isEmpty();
  }

  @Test
  public void shouldSetAndGetProvidedDiagnosisKeyToLog() {
    exposureNotificationSharedPreferences.setProvidedDiagnosisKeyHexToLog("the-key");
    assertThat(exposureNotificationSharedPreferences.getProvidedDiagnosisKeyHexToLog())
        .isEqualTo("the-key");
  }

  @Test
  public void shouldSetAndGetProvidedDiagnosisKeyToLogLiveData() {
    AtomicReference<String> observer = new AtomicReference<>();
    exposureNotificationSharedPreferences
        .getProvidedDiagnosisKeyHexToLogLiveData().observeForever(observer::set);

    exposureNotificationSharedPreferences.setProvidedDiagnosisKeyHexToLog("the-key");
    assertThat(observer.get()).isEqualTo("the-key");
  }
}
