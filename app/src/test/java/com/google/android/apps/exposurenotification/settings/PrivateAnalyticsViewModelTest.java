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

package com.google.android.apps.exposurenotification.settings;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.android.apps.exposurenotification.common.time.RealTimeModule;
import com.google.android.apps.exposurenotification.riskcalculation.ExposureClassification;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences.NotificationInteraction;
import com.google.android.apps.exposurenotification.testsupport.ExposureNotificationRules;
import com.google.android.apps.exposurenotification.testsupport.FakeClock;
import com.google.android.libraries.privateanalytics.PrivateAnalyticsEnabledProvider;
import com.google.firebase.FirebaseApp;
import dagger.hilt.android.testing.BindValue;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import dagger.hilt.android.testing.UninstallModules;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.threeten.bp.Duration;
import org.threeten.bp.Instant;

@HiltAndroidTest
@RunWith(RobolectricTestRunner.class)
@Config(application = HiltTestApplication.class)
@UninstallModules({RealTimeModule.class})
public class PrivateAnalyticsViewModelTest {

  @Rule
  public ExposureNotificationRules rules = ExposureNotificationRules.forTest(this).withMocks()
      .build();

  @BindValue
  Clock clock = new FakeClock();

  @Inject
  ExposureNotificationSharedPreferences exposureNotificationSharedPreferences;
  @Mock
  PrivateAnalyticsEnabledProvider privateAnalyticsEnabledProvider;

  private final Context context = ApplicationProvider.getApplicationContext();

  private PrivateAnalyticsViewModel privateAnalyticsViewModel;

  @Before
  public void setup() {
    FirebaseApp.initializeApp(context);
    rules.hilt().inject();
    privateAnalyticsViewModel = new PrivateAnalyticsViewModel(exposureNotificationSharedPreferences,
        privateAnalyticsEnabledProvider);
  }

  @Test
  public void getPrivateAnalyticsLiveData_defaultFalse() {
    List<Boolean> observedValues = new ArrayList<>();
    privateAnalyticsViewModel.getPrivateAnalyticsLiveData().observeForever(observedValues::add);

    assertThat(observedValues).containsExactly(false);
  }

  @Test
  public void getPrivateAnalyticsLiveData_initialValueFalse() {
    List<Boolean> observedValues = new ArrayList<>();

    // Set the initial value.
    privateAnalyticsViewModel.setPrivateAnalyticsState(false);
    // Now begin observing.
    privateAnalyticsViewModel.getPrivateAnalyticsLiveData().observeForever(observedValues::add);

    assertThat(observedValues).containsExactly(false);
  }

  @Test
  public void setPrivateAnalyticsState_falseThenTrue_observedInLiveData() {
    List<Boolean> observedValues = new ArrayList<>();
    privateAnalyticsViewModel.getPrivateAnalyticsLiveData().observeForever(observedValues::add);

    privateAnalyticsViewModel.setPrivateAnalyticsState(false);
    privateAnalyticsViewModel.setPrivateAnalyticsState(true);

    assertThat(observedValues).containsExactly(false, false, true).inOrder();
  }

  @Test
  public void setPrivateAnalyticsState_false_clears_metrics() {
    // Generate a metric
    privateAnalyticsViewModel.setPrivateAnalyticsState(true);
    Instant notificationTime = clock.now();
    int classificationIndex = 1;
    long exposureDay = (notificationTime.getEpochSecond() / TimeUnit.DAYS.toSeconds(1)) - 2;
    ExposureClassification exposureClassification = ExposureClassification
        .create(classificationIndex, "", exposureDay);
    exposureNotificationSharedPreferences
        .setExposureNotificationLastShownClassification(notificationTime, exposureClassification);
    exposureNotificationSharedPreferences
        .setExposureNotificationLastInteraction(notificationTime, NotificationInteraction.CLICKED,
            classificationIndex);

    assertThat(exposureNotificationSharedPreferences.getExposureNotificationLastShownTime())
        .isEqualTo(notificationTime);
    assertThat(exposureNotificationSharedPreferences.getExposureNotificationLastInteractionTime())
        .isEqualTo(notificationTime);
    assertThat(exposureNotificationSharedPreferences.getExposureNotificationLastInteractionType())
        .isEqualTo(NotificationInteraction.CLICKED);
    assertThat(exposureNotificationSharedPreferences
        .getExposureNotificationLastInteractionClassification()).isEqualTo(classificationIndex);
    assertThat(
        exposureNotificationSharedPreferences.getExposureNotificationLastShownClassification())
        .isEqualTo(classificationIndex);
    assertThat(exposureNotificationSharedPreferences.getPrivateAnalyticsLastExposureTime())
        .isEqualTo(Instant.EPOCH.plus(Duration.ofDays(exposureDay)));

    // Toggle off
    privateAnalyticsViewModel.setPrivateAnalyticsState(false);

    // Metric is cleared
    assertThat(exposureNotificationSharedPreferences.getExposureNotificationLastShownTime()
    ).isEqualTo(Instant.EPOCH);
    assertThat(exposureNotificationSharedPreferences.getExposureNotificationLastInteractionTime()
    ).isEqualTo(Instant.EPOCH);
    assertThat(exposureNotificationSharedPreferences.getExposureNotificationLastInteractionType())
        .isEqualTo(NotificationInteraction.UNKNOWN);
    assertThat(exposureNotificationSharedPreferences
        .getExposureNotificationLastInteractionClassification()).isEqualTo(0);
    assertThat(
        exposureNotificationSharedPreferences.getExposureNotificationLastShownClassification())
        .isEqualTo(0);
    assertThat(exposureNotificationSharedPreferences.getPrivateAnalyticsLastExposureTime())
        .isEqualTo(Instant.EPOCH);
  }

}