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

package com.google.android.apps.exposurenotification.privateanalytics.metrics;

import static com.google.common.truth.Truth.assertThat;

import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.android.apps.exposurenotification.common.time.RealTimeModule;
import com.google.android.apps.exposurenotification.riskcalculation.ExposureClassification;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences.NotificationInteraction;
import com.google.android.apps.exposurenotification.testsupport.ExposureNotificationRules;
import com.google.android.apps.exposurenotification.testsupport.FakeClock;
import com.google.common.collect.Lists;
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
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.threeten.bp.Duration;
import org.threeten.bp.Instant;

/**
 * Tests of {@link PeriodicExposureNotificationMetric}.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner.class)
@Config(application = HiltTestApplication.class)
@UninstallModules({RealTimeModule.class})
public class PeriodicExposureNotificationInteractionMetricTest {

  @Rule
  public ExposureNotificationRules rules = ExposureNotificationRules.forTest(this).build();

  @Inject
  PeriodicExposureNotificationInteractionMetric periodicExposureNotificationInteractionMetric;

  @Inject
  ExposureNotificationSharedPreferences exposureNotificationSharedPreferences;
  @BindValue
  Clock clock = new FakeClock();

  @Before
  public void setup() {
    rules.hilt().inject();
    exposureNotificationSharedPreferences.setPrivateAnalyticsState(true);
  }

  // When the last worker was run before the last interaction, the metric reports
  // the output according to the test vectors.
  @Test
  public void testVectorsWhenWorkerBeforeInteraction() throws Exception {
    for (TestVector testVector : getTestVectors()) {
      // GIVEN
      Instant workerTime = testVector.interactionTime.minus(Duration.ofDays(1));
      exposureNotificationSharedPreferences.setPrivateAnalyticsWorkerLastTime(workerTime);
      exposureNotificationSharedPreferences
          .setExposureNotificationLastInteraction(testVector.interactionTime,
              testVector.notificationInteraction,
              testVector.exposureClassification.getClassificationIndex());

      // WHEN
      List<Integer> data = periodicExposureNotificationInteractionMetric.getDataVector().get();

      // THEN
      assertThat(data).containsExactlyElementsIn(testVector.data).inOrder();
    }
  }

  // When the last worker was run after the last interaction, the metric reports
  // no interaction.
  @Test
  public void testVectorsWhenWorkerAfterInteraction() throws Exception {
    for (TestVector testVector : getTestVectors()) {
      // GIVEN
      Instant workerTime = testVector.interactionTime.plus(Duration.ofDays(1));
      exposureNotificationSharedPreferences.setPrivateAnalyticsWorkerLastTime(workerTime);
      exposureNotificationSharedPreferences
          .setExposureNotificationLastInteraction(testVector.interactionTime,
              testVector.notificationInteraction,
              testVector.exposureClassification.getClassificationIndex());

      // WHEN
      List<Integer> data = periodicExposureNotificationInteractionMetric.getDataVector().get();

      // THEN
      assertThat(data).containsExactly(1, 0, 0, 0, 0, 0, 0, 0, 0).inOrder();
    }
  }

  // When private analytics are disabled, the metric reports no interaction.
  @Test
  public void testVectorsNotCachedWhenNotEnabled() throws Exception {
    for (TestVector testVector : getTestVectors()) {
      // GIVEN
      Instant workerTime = testVector.interactionTime.plus(Duration.ofDays(1));
      exposureNotificationSharedPreferences.setPrivateAnalyticsWorkerLastTime(workerTime);
      exposureNotificationSharedPreferences
          .setExposureNotificationLastInteraction(testVector.interactionTime,
              testVector.notificationInteraction,
              testVector.exposureClassification.getClassificationIndex());

      // WHEN
      List<Integer> data = periodicExposureNotificationInteractionMetric.getDataVector().get();

      // THEN
      assertThat(data).containsExactly(1, 0, 0, 0, 0, 0, 0, 0, 0).inOrder();
    }
  }

  @Test
  public void testHammingWeight() {
    assertThat(periodicExposureNotificationInteractionMetric.getMetricHammingWeight()).isEqualTo(1);
  }

  // Test vectors
  private List<PeriodicExposureNotificationInteractionMetricTest.TestVector> getTestVectors() {
    List<PeriodicExposureNotificationInteractionMetricTest.TestVector> ret = new ArrayList<>();
    Instant interactionTime;
    long exposureDay;
    ExposureClassification exposureClassification;

    // Notification of type 1
    interactionTime = Instant.ofEpochMilli(1000000000L);
    exposureDay = (interactionTime.getEpochSecond() / TimeUnit.DAYS.toSeconds(1)) - 1;
    exposureClassification = ExposureClassification.create(1, "", exposureDay);
    // Clicked
    ret.add(new PeriodicExposureNotificationInteractionMetricTest.TestVector(interactionTime,
        exposureClassification, NotificationInteraction.CLICKED,
        Lists.newArrayList(0, 1, 0, 0, 0, 0, 0, 0, 0)));
    // Dismissed
    ret.add(new PeriodicExposureNotificationInteractionMetricTest.TestVector(interactionTime,
        exposureClassification, NotificationInteraction.DISMISSED,
        Lists.newArrayList(0, 0, 1, 0, 0, 0, 0, 0, 0)));

    // Notification of type 2
    interactionTime = Instant.ofEpochMilli(1100000000L);
    exposureDay = (interactionTime.getEpochSecond() / TimeUnit.DAYS.toSeconds(1)) - 2;
    exposureClassification = ExposureClassification.create(2, "", exposureDay);
    // Clicked
    ret.add(new PeriodicExposureNotificationInteractionMetricTest.TestVector(interactionTime,
        exposureClassification, NotificationInteraction.CLICKED,
        Lists.newArrayList(0, 0, 0, 1, 0, 0, 0, 0, 0)));
    // Dismissed
    ret.add(new PeriodicExposureNotificationInteractionMetricTest.TestVector(interactionTime,
        exposureClassification, NotificationInteraction.DISMISSED,
        Lists.newArrayList(0, 0, 0, 0, 1, 0, 0, 0, 0)));

    // Notification of type 3
    interactionTime = Instant.ofEpochMilli(900000000L);
    exposureDay = (interactionTime.getEpochSecond() / TimeUnit.DAYS.toSeconds(1)) - 4;
    exposureClassification = ExposureClassification.create(3, "", exposureDay);
    // Clicked
    ret.add(new PeriodicExposureNotificationInteractionMetricTest.TestVector(interactionTime,
        exposureClassification, NotificationInteraction.CLICKED,
        Lists.newArrayList(0, 0, 0, 0, 0, 1, 0, 0, 0)));
    // Dismissed
    ret.add(new PeriodicExposureNotificationInteractionMetricTest.TestVector(interactionTime,
        exposureClassification, NotificationInteraction.DISMISSED,
        Lists.newArrayList(0, 0, 0, 0, 0, 0, 1, 0, 0)));

    // Notification of type 4
    interactionTime = Instant.ofEpochMilli(1200000000L);
    exposureDay = (interactionTime.getEpochSecond() / TimeUnit.DAYS.toSeconds(1));
    exposureClassification = ExposureClassification.create(4, "", exposureDay);
    // Clicked
    ret.add(new PeriodicExposureNotificationInteractionMetricTest.TestVector(interactionTime,
        exposureClassification, NotificationInteraction.CLICKED,
        Lists.newArrayList(0, 0, 0, 0, 0, 0, 0, 1, 0)));
    // Dismissed
    ret.add(new PeriodicExposureNotificationInteractionMetricTest.TestVector(interactionTime,
        exposureClassification, NotificationInteraction.DISMISSED,
        Lists.newArrayList(0, 0, 0, 0, 0, 0, 0, 0, 1)));

    return ret;
  }


  static class TestVector {

    // Input
    public Instant interactionTime;
    public ExposureClassification exposureClassification;
    public NotificationInteraction notificationInteraction;
    // Output
    public List<Integer> data;

    // Constructor
    TestVector(Instant interactionTime, ExposureClassification exposureClassification,
        NotificationInteraction notificationInteraction,
        List<Integer> data) {
      this.interactionTime = interactionTime;
      this.exposureClassification = exposureClassification;
      this.notificationInteraction = notificationInteraction;
      this.data = data;
    }
  }


}
