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
import com.google.common.primitives.Ints;
import dagger.hilt.android.testing.BindValue;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import dagger.hilt.android.testing.UninstallModules;
import java.util.ArrayList;
import java.util.Arrays;
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
 * Tests of {@link DateExposureBiweeklyMetric}.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner.class)
@Config(application = HiltTestApplication.class)
@UninstallModules({RealTimeModule.class})

public class DateExposureBiweeklyMetricTest {

  @Rule
  public ExposureNotificationRules rules = ExposureNotificationRules.forTest(this).build();

  @Inject
  DateExposureBiweeklyMetric dateExposureBiweeklyMetric;

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
    for (DateExposureBiweeklyMetricTest.TestVector testVector : getTestVectors()) {
      // GIVEN
      Instant workerTime = testVector.notificationTime.minus(Duration.ofDays(1));
      exposureNotificationSharedPreferences
          .setPrivateAnalyticsWorkerLastTimeForBiweekly(workerTime);
      exposureNotificationSharedPreferences
          .setExposureNotificationLastShownClassification(testVector.notificationTime,
              testVector.exposureClassification);

      // WHEN
      List<Integer> data = dateExposureBiweeklyMetric.getDataVector().get();

      // THEN
      assertThat(data).containsExactlyElementsIn(testVector.data).inOrder();
    }
  }

  // When the last worker was run after the last notification, the metric reports
  // no notification.
  @Test
  public void testVectorsWhenWorkerAfterNotification() throws Exception {
    for (DateExposureBiweeklyMetricTest.TestVector testVector : getTestVectors()) {
      // GIVEN
      Instant workerTime = testVector.notificationTime.plus(Duration.ofDays(1));
      exposureNotificationSharedPreferences
          .setPrivateAnalyticsWorkerLastTimeForBiweekly(workerTime);
      exposureNotificationSharedPreferences
          .setExposureNotificationLastShownClassification(testVector.notificationTime,
              testVector.exposureClassification);
      // WHEN
      List<Integer> data = dateExposureBiweeklyMetric.getDataVector().get();

      // THEN
      int[] emptyArray = new int[DateExposureBiweeklyMetric.BIN_LENGTH];
      assertThat(data).containsExactlyElementsIn(Ints.asList(emptyArray));
    }
  }

  // When private analytics are disabled, the metric reports no notification.
  @Test
  public void testVectorsNotCachedWhenNotEnabled() throws Exception {
    exposureNotificationSharedPreferences.setPrivateAnalyticsState(false);

    for (DateExposureBiweeklyMetricTest.TestVector testVector : getTestVectors()) {
      // GIVEN
      Instant workerTime = testVector.notificationTime.minus(Duration.ofDays(1));
      exposureNotificationSharedPreferences
          .setPrivateAnalyticsWorkerLastTimeForBiweekly(workerTime);
      exposureNotificationSharedPreferences
          .setExposureNotificationLastShownClassification(testVector.notificationTime,
              testVector.exposureClassification);

      // WHEN
      List<Integer> data = dateExposureBiweeklyMetric.getDataVector().get();

      // THEN
      int[] emptyArray = new int[DateExposureBiweeklyMetric.BIN_LENGTH];
      assertThat(data).containsExactlyElementsIn(Ints.asList(emptyArray));
    }
  }

  @Test
  public void testHammingWeight() {
    assertThat(dateExposureBiweeklyMetric.getMetricHammingWeight()).isEqualTo(0);
  }

  // Test vectors
  private List<DateExposureBiweeklyMetricTest.TestVector> getTestVectors() {
    List<DateExposureBiweeklyMetricTest.TestVector> ret = new ArrayList<>();
    Instant notificationTime;
    long exposureDay;
    ExposureClassification exposureClassification;

    // Test vector 1
    notificationTime = Instant.ofEpochSecond(1612051199L); // 2021-01-30 23:59:59 UTC
    exposureDay = 1611964800L / 86400; // 2021-01-30 00:00:00 UTC
    exposureClassification = ExposureClassification.create(1, "", exposureDay);
    // Number of days is 0 with classification 1, so the metric should report "1" in bins 0...11
    int[] expectedMetric = new int[DateExposureBiweeklyMetric.BIN_LENGTH];
    for (int i = 0; i <= 11; i++) {
      expectedMetric[i] = 1;
    }
    ret.add(new TestVector(notificationTime, exposureClassification, Ints.asList(expectedMetric)));

    // Test vector 2
    notificationTime = Instant.ofEpochSecond(1612018800L); // 2021-01-30 15:00:00 UTC
    exposureDay = 1611619200L / 86400; // 2021-01-26 00:00:00 UTC
    exposureClassification = ExposureClassification.create(2, "", exposureDay);
    // Number of days is 4 with classification 2, so the metric should report "1" in bins 16...23
    expectedMetric = new int[DateExposureBiweeklyMetric.BIN_LENGTH];
    for (int i = 16; i <= 23; i++) {
      expectedMetric[i] = 1;
    }
    ret.add(new TestVector(notificationTime, exposureClassification, Ints.asList(expectedMetric)));

    // Test vector 3
    notificationTime = Instant.ofEpochSecond(1612018800L); // 2021-01-30 15:00:00 UTC
    exposureDay = 1611878400L / 86400; // 2021-01-29 00:00:00 UTC
    exposureClassification = ExposureClassification.create(3, "", exposureDay);
    // Number of days is 1 with classification 3, so the metric should report "1" in bins 25...35
    expectedMetric = new int[DateExposureBiweeklyMetric.BIN_LENGTH];
    for (int i = 25; i <= 35; i++) {
      expectedMetric[i] = 1;
    }
    ret.add(new TestVector(notificationTime, exposureClassification, Ints.asList(expectedMetric)));

    // Test vector 4
    notificationTime = Instant.ofEpochSecond(1612018800L); // 2021-01-30 15:00:00 UTC
    exposureDay = 1610755200L / 86400; // 2021-01-16 00:00:00 UTC
    exposureClassification = ExposureClassification.create(4, "", exposureDay);
    // Number of days is 14 with classification 4, so the metric should report "1" in bin 47
    expectedMetric = new int[DateExposureBiweeklyMetric.BIN_LENGTH];
    expectedMetric[47] = 1;
    ret.add(new TestVector(notificationTime, exposureClassification, Ints.asList(expectedMetric)));

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
