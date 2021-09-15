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
public class PeriodicExposureNotificationBiweeklyMetricTest {

  @Rule
  public ExposureNotificationRules rules = ExposureNotificationRules.forTest(this).build();
  @Inject
  PeriodicExposureNotificationBiweeklyMetric periodicExposureNotificationBiweeklyTracker;
  @Inject
  ExposureNotificationSharedPreferences exposureNotificationSharedPreferences;
  @BindValue
  Clock clock = new FakeClock();

  @Before
  public void setup() {
    rules.hilt().inject();
    exposureNotificationSharedPreferences.setPrivateAnalyticsState(true);
  }

  // When the last worker was run before the last notification, the metric reports
  // the output according to the test vectors.
  @Test
  public void testVectorsWhenWorkerBeforeNotification() throws Exception {
    for (TestVector testVector : getTestVectors()) {
      // GIVEN
      Instant workerTime = testVector.notificationTime.minus(Duration.ofDays(1));
      exposureNotificationSharedPreferences
          .setPrivateAnalyticsWorkerLastTimeForBiweekly(workerTime);
      exposureNotificationSharedPreferences
          .setExposureNotificationLastShownClassification(testVector.notificationTime,
              testVector.exposureClassification);

      // WHEN
      List<Integer> data = periodicExposureNotificationBiweeklyTracker.getDataVector().get();

      // THEN
      assertThat(data).containsExactlyElementsIn(testVector.data).inOrder();
    }
  }

  // When the last worker was run after the last notification, the metric reports
  // no notification.
  @Test
  public void testVectorsWhenWorkerAfterNotification() throws Exception {
    for (TestVector testVector : getTestVectors()) {
      // GIVEN
      Instant workerTime = testVector.notificationTime.plus(Duration.ofDays(1));
      exposureNotificationSharedPreferences
          .setPrivateAnalyticsWorkerLastTimeForBiweekly(workerTime);
      exposureNotificationSharedPreferences
          .setExposureNotificationLastShownClassification(testVector.notificationTime,
              testVector.exposureClassification);

      // WHEN
      List<Integer> data = periodicExposureNotificationBiweeklyTracker.getDataVector().get();

      // THEN
      assertThat(data).containsExactly(1, 0, 0, 0, 0).inOrder();
    }
  }

  // When private analytics are disabled, the metric reports no notification.
  @Test
  public void testVectorsNotCachedWhenNotEnabled() throws Exception {
    exposureNotificationSharedPreferences.setPrivateAnalyticsState(false);

    for (TestVector testVector : getTestVectors()) {
      // GIVEN
      Instant workerTime = testVector.notificationTime.minus(Duration.ofDays(1));
      exposureNotificationSharedPreferences
          .setPrivateAnalyticsWorkerLastTimeForBiweekly(workerTime);
      exposureNotificationSharedPreferences
          .setExposureNotificationLastShownClassification(testVector.notificationTime,
              testVector.exposureClassification);

      // WHEN
      List<Integer> data = periodicExposureNotificationBiweeklyTracker.getDataVector().get();

      // THEN
      assertThat(data).containsExactly(1, 0, 0, 0, 0).inOrder();
    }
  }

  @Test
  public void testHammingWeight() {
    assertThat(periodicExposureNotificationBiweeklyTracker.getMetricHammingWeight()).isEqualTo(1);
  }

  // Test vectors
  private List<TestVector> getTestVectors() {
    List<TestVector> ret = new ArrayList<>();
    Instant notificationTime;
    long exposureDay;
    ExposureClassification exposureClassification;

    // Notification of type 1
    notificationTime = Instant.ofEpochMilli(1000000000L);
    exposureDay = (notificationTime.getEpochSecond() / TimeUnit.DAYS.toSeconds(1)) - 1;
    exposureClassification = ExposureClassification.create(1, "", exposureDay);
    ret.add(new TestVector(notificationTime, exposureClassification,
        Lists.newArrayList(0, 1, 0, 0, 0)));

    // Notification of type 2
    notificationTime = Instant.ofEpochMilli(1100000000L);
    exposureDay = (notificationTime.getEpochSecond() / TimeUnit.DAYS.toSeconds(1)) - 2;
    exposureClassification = ExposureClassification.create(2, "", exposureDay);
    ret.add(new TestVector(notificationTime, exposureClassification,
        Lists.newArrayList(0, 0, 1, 0, 0)));

    // Notification of type 3
    notificationTime = Instant.ofEpochMilli(900000000L);
    exposureDay = (notificationTime.getEpochSecond() / TimeUnit.DAYS.toSeconds(1)) - 4;
    exposureClassification = ExposureClassification.create(3, "", exposureDay);
    ret.add(new TestVector(notificationTime, exposureClassification,
        Lists.newArrayList(0, 0, 0, 1, 0)));

    // Notification of type 4
    notificationTime = Instant.ofEpochMilli(1200000000L);
    exposureDay = (notificationTime.getEpochSecond() / TimeUnit.DAYS.toSeconds(1));
    exposureClassification = ExposureClassification.create(4, "", exposureDay);
    ret.add(new TestVector(notificationTime, exposureClassification,
        Lists.newArrayList(0, 0, 0, 0, 1)));

    return ret;
  }

  static class TestVector {

    // Input
    public Instant notificationTime;
    public ExposureClassification exposureClassification;
    // Output
    public List<Integer> data;

    // Constructor
    TestVector(Instant notificationTime, ExposureClassification exposureClassification,
        List<Integer> data) {
      this.notificationTime = notificationTime;
      this.exposureClassification = exposureClassification;
      this.data = data;
    }
  }

}
