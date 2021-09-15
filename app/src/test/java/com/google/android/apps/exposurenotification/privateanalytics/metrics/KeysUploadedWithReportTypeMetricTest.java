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

import static com.google.android.apps.exposurenotification.privateanalytics.metrics.CodeVerifiedWithReportTypeMetric.CONFIRMED_CLINICAL_DIAGNOSIS_BIN;
import static com.google.android.apps.exposurenotification.privateanalytics.metrics.CodeVerifiedWithReportTypeMetric.CONFIRMED_TEST_BIN;
import static com.google.android.apps.exposurenotification.privateanalytics.metrics.CodeVerifiedWithReportTypeMetric.REVOKED_BIN;
import static com.google.android.apps.exposurenotification.privateanalytics.metrics.CodeVerifiedWithReportTypeMetric.SELF_REPORTED_BIN;
import static com.google.android.apps.exposurenotification.privateanalytics.metrics.CodeVerifiedWithReportTypeMetric.UNKNOWN_BIN;
import static com.google.android.apps.exposurenotification.privateanalytics.metrics.KeysUploadedWithReportTypeMetric.BIN_LENGTH;
import static com.google.common.truth.Truth.assertThat;

import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.android.apps.exposurenotification.common.time.RealTimeModule;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity.TestResult;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.apps.exposurenotification.testsupport.ExposureNotificationRules;
import com.google.android.apps.exposurenotification.testsupport.FakeClock;
import com.google.common.primitives.Ints;
import dagger.hilt.android.testing.BindValue;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import dagger.hilt.android.testing.UninstallModules;
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
 * Tests of {@link KeysUploadedWithReportTypeMetric}.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner.class)
@Config(application = HiltTestApplication.class)
@UninstallModules({RealTimeModule.class})
public class KeysUploadedWithReportTypeMetricTest {

  @Rule
  public ExposureNotificationRules rules = ExposureNotificationRules.forTest(this).build();

  @Inject
  KeysUploadedWithReportTypeMetric keysUploadedWithReportTypeMetric;

  @Inject
  ExposureNotificationSharedPreferences exposureNotificationSharedPreferences;
  @BindValue
  Clock clock = new FakeClock();

  @Before
  public void setup() {
    rules.hilt().inject();
    exposureNotificationSharedPreferences.setPrivateAnalyticsState(true);
  }

  @Test
  public void testKeysUploadedNoReportSinceLastWorkerRun() throws Exception {
    // GIVEN
    Instant workerRunTime = clock.now().minus(Duration.ofDays(2));

    exposureNotificationSharedPreferences
        .setPrivateAnalyticsWorkerLastTimeForBiweekly(workerRunTime);

    // WHEN
    List<Integer> vector = keysUploadedWithReportTypeMetric.getDataVector().get();

    // THEN
    assertThat(vector)
        .containsExactlyElementsIn(vectorNoReport())
        .inOrder();
  }

  @Test
  public void testKeysUploadedReportInTimePeriodSinceLastWorkerRun() throws Exception {
    // GIVEN
    Instant submittedCodeTime = clock.now().minus(Duration.ofDays(1));
    Instant workerRunTime = clock.now().minus(Duration.ofDays(2));

    exposureNotificationSharedPreferences
        .setPrivateAnalyticsWorkerLastTimeForBiweekly(workerRunTime);
    exposureNotificationSharedPreferences
        .setPrivateAnalyticsLastSubmittedKeysTime(submittedCodeTime);

    for (TestResult testResult : TestResult.values()) {
      exposureNotificationSharedPreferences
          .setPrivateAnalyticsLastReportType(testResult);

      // WHEN
      List<Integer> vector = keysUploadedWithReportTypeMetric.getDataVector().get();

      // THEN
      assertThat(vector)
          .containsExactlyElementsIn(vectorForTestResult(testResult))
          .inOrder();
    }
  }

  @Test
  public void testKeysUploadedNoReportInTimePeriodSinceLastWorkerRun() throws Exception {
    // GIVEN
    Instant submittedCodeTime = clock.now().minus(Duration.ofDays(3));
    Instant workerRunTime = clock.now().minus(Duration.ofDays(2));

    exposureNotificationSharedPreferences
        .setPrivateAnalyticsWorkerLastTimeForBiweekly(workerRunTime);
    exposureNotificationSharedPreferences
        .setPrivateAnalyticsLastSubmittedKeysTime(submittedCodeTime);

    for (TestResult testResult : TestResult.values()) {
      exposureNotificationSharedPreferences
          .setPrivateAnalyticsLastReportType(testResult);

      // WHEN
      List<Integer> vector = keysUploadedWithReportTypeMetric.getDataVector().get();

      // THEN
      assertThat(vector)
          .containsExactlyElementsIn(vectorNoReport())
          .inOrder();
    }
  }

  @Test
  public void testKeysUploadedBeforeLastWorkerRun() throws Exception {
    // GIVEN
    Instant workerRunTime = clock.now().minus(Duration.ofDays(1));
    Instant submittedCodeTime = clock.now().minus(Duration.ofDays(2));

    exposureNotificationSharedPreferences
        .setPrivateAnalyticsWorkerLastTimeForBiweekly(workerRunTime);
    exposureNotificationSharedPreferences
        .setPrivateAnalyticsLastSubmittedKeysTime(submittedCodeTime);

    // WHEN
    List<Integer> vector = keysUploadedWithReportTypeMetric.getDataVector().get();

    // THEN
    assertThat(vector)
        .containsExactlyElementsIn(vectorEmpty())
        .inOrder();
  }

  @Test
  public void testNoKeysUploadedSinceLastWorkerRun() throws Exception {
    // GIVEN
    Instant workerRunTime = clock.now().minus(Duration.ofDays(1));

    exposureNotificationSharedPreferences
        .setPrivateAnalyticsWorkerLastTimeForBiweekly(workerRunTime);

    // WHEN
    List<Integer> vector = keysUploadedWithReportTypeMetric.getDataVector().get();

    // THEN
    assertThat(vector)
        .containsExactlyElementsIn(vectorEmpty())
        .inOrder();
  }

  @Test
  public void testHammingWeight() {
    assertThat(keysUploadedWithReportTypeMetric.getMetricHammingWeight()).isEqualTo(0);
  }

  private static List<Integer> vectorEmpty() {
    int[] retVector = new int[BIN_LENGTH];
    return Ints.asList(retVector);
  }

  private static List<Integer> vectorNoReport() {
    return vectorEmpty();
  }

  private static List<Integer> vectorForTestResult(TestResult testResult) {
    List<Integer> retVector = vectorEmpty();
    switch (testResult) {
      case CONFIRMED:
        retVector.set(CONFIRMED_TEST_BIN, 1);
        break;
      case LIKELY:
        retVector.set(CONFIRMED_CLINICAL_DIAGNOSIS_BIN, 1);
        break;
      case USER_REPORT:
        retVector.set(SELF_REPORTED_BIN, 1);
        break;
      case NEGATIVE:
        retVector.set(REVOKED_BIN, 1);
        break;
      default:
        retVector.set(UNKNOWN_BIN, 1);
    }
    return retVector;
  }

}
