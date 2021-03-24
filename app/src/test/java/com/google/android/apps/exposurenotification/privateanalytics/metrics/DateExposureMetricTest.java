/*
 * Copyright 2021 Google LLC
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
 * Tests of {@link DateExposureMetric}.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner.class)
@Config(application = HiltTestApplication.class)
@UninstallModules({RealTimeModule.class})

public class DateExposureMetricTest {

  @Rule
  public ExposureNotificationRules rules = ExposureNotificationRules.forTest(this).build();

  @Inject
  DateExposureMetric dateExposureMetric;

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
    for (DateExposureMetricTest.TestVector testVector : getTestVectors()) {
      // GIVEN
      Instant workerTime = testVector.notificationTime.minus(Duration.ofDays(1));
      exposureNotificationSharedPreferences.setPrivateAnalyticsWorkerLastTime(workerTime);
      exposureNotificationSharedPreferences
          .setExposureNotificationLastShownClassification(testVector.notificationTime,
              testVector.exposureClassification);

      // WHEN
      List<Integer> data = dateExposureMetric.getDataVector().get();

      // THEN
      assertThat(data).containsExactlyElementsIn(testVector.data).inOrder();
    }
  }

  // When the last worker was run after the last notification, the metric reports
  // no notification.
  @Test
  public void testVectorsWhenWorkerAfterNotification() throws Exception {
    for (DateExposureMetricTest.TestVector testVector : getTestVectors()) {
      // GIVEN
      Instant workerTime = testVector.notificationTime.plus(Duration.ofDays(1));
      exposureNotificationSharedPreferences.setPrivateAnalyticsWorkerLastTime(workerTime);
      exposureNotificationSharedPreferences
          .setExposureNotificationLastShownClassification(testVector.notificationTime,
              testVector.exposureClassification);
      // WHEN
      List<Integer> data = dateExposureMetric.getDataVector().get();

      // THEN
      assertThat(data).containsExactly(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }
  }

  // When private analytics are disabled, the metric reports no notification.
  @Test
  public void testVectorsNotCachedWhenNotEnabled() throws Exception {
    exposureNotificationSharedPreferences.setPrivateAnalyticsState(false);

    for (DateExposureMetricTest.TestVector testVector : getTestVectors()) {
      // GIVEN
      Instant workerTime = testVector.notificationTime.minus(Duration.ofDays(1));
      exposureNotificationSharedPreferences.setPrivateAnalyticsWorkerLastTime(workerTime);
      exposureNotificationSharedPreferences
          .setExposureNotificationLastShownClassification(testVector.notificationTime,
              testVector.exposureClassification);

      // WHEN
      List<Integer> data = dateExposureMetric.getDataVector().get();

      // THEN
      assertThat(data).containsExactly(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }
  }

  @Test
  public void testHammingWeight() {
    assertThat(dateExposureMetric.getMetricHammingWeight()).isEqualTo(0);
  }

  // Test vectors
  private List<DateExposureMetricTest.TestVector> getTestVectors() {
    List<DateExposureMetricTest.TestVector> ret = new ArrayList<>();
    Instant notificationTime;
    long exposureDay;
    ExposureClassification exposureClassification;

    // Test vector 1
    notificationTime = Instant.ofEpochSecond(1612051199L); // 2021-01-30 23:59:59 UTC
    exposureDay = 18657; // 2021-01-30 00:00:00 UTC
    exposureClassification = ExposureClassification.create(1, "", exposureDay);
    // Number of days is floor(1612051199 / 86400) - 18657 = 0,
    // so the metric should report "1" in the bin
    // "0-3 days" for notifications of classification 1.
    ret.add(new TestVector(notificationTime, exposureClassification,
        Lists.newArrayList(1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)));
    // Test vector 2
    notificationTime = Instant.ofEpochSecond(1612018800L); // 2021-01-30 15:00:00 UTC
    exposureDay = 18653; // 2021-01-26 00:00:00 UTC
    exposureClassification = ExposureClassification.create(2, "", exposureDay);
    // Number of days is floor(1612018800 / 86400) - 18653 = 4,
    // so the metric should report "1" in the bin
    // "4-6 days" for notifications of classification 2.
    ret.add(new TestVector(notificationTime, exposureClassification,
        Lists.newArrayList(0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)));
    // Test vector 3
    notificationTime = Instant.ofEpochSecond(1612018800L); // 2021-01-30 15:00:00 UTC
    exposureDay = 18656; // 2021-01-29 00:00:00 UTC
    exposureClassification = ExposureClassification.create(3, "", exposureDay);
    // Number of days is floor(1612018800 / 86400) - 18656 = 1,
    // so the metric should report "1" in the bin
    // "0-3 days" for notifications of classification 3.
    ret.add(new TestVector(notificationTime, exposureClassification,
        Lists.newArrayList(0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0)));
    // Test vector 4
    notificationTime = Instant.ofEpochSecond(1612018800L); // 2021-01-30 15:00:00 UTC
    exposureDay = 18643; // 2021-01-16 00:00:00 UTC
    exposureClassification = ExposureClassification.create(4, "", exposureDay);
    // Number of days is floor(1612018800 / 86400) - 18643 = 14,
    // so the metric should report "1" in the bin
    // "11+ days" for notifications of classification 4.
    ret.add(new TestVector(notificationTime, exposureClassification,
        Lists.newArrayList(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1)));

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
