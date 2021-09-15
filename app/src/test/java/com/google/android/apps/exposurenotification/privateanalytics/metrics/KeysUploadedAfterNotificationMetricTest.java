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

import static com.google.android.apps.exposurenotification.privateanalytics.metrics.CodeVerifiedMetric.NO_NOTIFICATION_SHOWN_BIN;
import static com.google.android.apps.exposurenotification.privateanalytics.metrics.KeysUploadedAfterNotificationMetric.BIN_LENGTH;
import static com.google.android.apps.exposurenotification.privateanalytics.metrics.KeysUploadedAfterNotificationMetric.CONFIRMED_CLINICAL_DIAGNOSIS_BIN;
import static com.google.android.apps.exposurenotification.privateanalytics.metrics.KeysUploadedAfterNotificationMetric.CONFIRMED_TEST_BIN;
import static com.google.android.apps.exposurenotification.privateanalytics.metrics.KeysUploadedAfterNotificationMetric.REVOKED_BIN;
import static com.google.android.apps.exposurenotification.privateanalytics.metrics.KeysUploadedAfterNotificationMetric.SELF_REPORTED_BIN;
import static com.google.common.truth.Truth.assertThat;

import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.android.apps.exposurenotification.common.time.RealTimeModule;
import com.google.android.apps.exposurenotification.privateanalytics.metrics.PeriodicExposureNotificationMetricTest.TestVector;
import com.google.android.apps.exposurenotification.riskcalculation.ExposureClassification;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity.TestResult;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.apps.exposurenotification.testsupport.ExposureNotificationRules;
import com.google.android.apps.exposurenotification.testsupport.FakeClock;
import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;
import dagger.hilt.android.testing.BindValue;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import dagger.hilt.android.testing.UninstallModules;
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
 * Tests of {@link KeysUploadedAfterNotificationMetric}.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner.class)
@Config(application = HiltTestApplication.class)
@UninstallModules({RealTimeModule.class})
public class KeysUploadedAfterNotificationMetricTest {

  @Rule
  public ExposureNotificationRules rules = ExposureNotificationRules.forTest(this).build();

  @Inject
  KeysUploadedAfterNotificationMetric keysUploadedAfterNotificationMetric;

  @Inject
  ExposureNotificationSharedPreferences exposureNotificationSharedPreferences;
  @BindValue
  Clock clock = new FakeClock();

  @Before
  public void setup() {
    rules.hilt().inject();
    exposureNotificationSharedPreferences.setPrivateAnalyticsState(true);
  }

  private TestVector getTestVector(int classificationIndex) {
    Instant notificationTime = Instant.ofEpochMilli(1000000000L);
    long exposureDay = (notificationTime.getEpochSecond() / TimeUnit.DAYS.toSeconds(1)) - 1;
    ExposureClassification exposureClassification = ExposureClassification
        .create(classificationIndex, "", exposureDay);
    List<Integer> data = Lists.newArrayList(0, 0, 0, 0, 0);
    data.set(classificationIndex, 1);
    return new TestVector(notificationTime, exposureClassification, data);
  }


  @Test
  public void keysUploadedAfterNotification_noNotificationSinceLastWorker_vectorEmpty()
      throws Exception {
    // GIVEN
    Instant workerRunTime = clock.now().minus(Duration.ofDays(1));
    Instant lastNotificationTime = clock.now().minus(Duration.ofDays(2));
    TestVector testVector = getTestVector(1);

    exposureNotificationSharedPreferences
        .setPrivateAnalyticsWorkerLastTimeForBiweekly(workerRunTime);
    exposureNotificationSharedPreferences
        .setExposureNotificationLastShownClassification(lastNotificationTime,
            testVector.exposureClassification);

    // also add a timely code verification to ensure the notification is the reason for skipping.
    Instant submittedCodeTime = clock.now();
    exposureNotificationSharedPreferences
        .setPrivateAnalyticsLastSubmittedKeysTime(submittedCodeTime);

    // WHEN
    List<Integer> vector = keysUploadedAfterNotificationMetric.getDataVector().get();

    // THEN
    assertThat(vector)
        .containsExactlyElementsIn(vectorEmpty())
        .inOrder();
  }

  @Test
  public void keysUploadedAfterNotification_noSubmittedCodeSinceLastWorker_vectorEmpty()
      throws Exception {
    // GIVEN
    Instant submittedCodeTime = clock.now().minus(Duration.ofDays(2));
    Instant workerRunTime = clock.now().minus(Duration.ofDays(1));

    exposureNotificationSharedPreferences
        .setPrivateAnalyticsWorkerLastTimeForBiweekly(workerRunTime);
    exposureNotificationSharedPreferences
        .setPrivateAnalyticsLastSubmittedKeysTime(submittedCodeTime);

    // also add a timely notification to ensure the submitted code is the reason for skipping.
    Instant lastNotificationTime = clock.now();
    TestVector testVector = getTestVector(1);
    exposureNotificationSharedPreferences
        .setExposureNotificationLastShownClassification(lastNotificationTime,
            testVector.exposureClassification);

    // WHEN
    List<Integer> vector = keysUploadedAfterNotificationMetric.getDataVector().get();

    // THEN
    assertThat(vector)
        .containsExactlyElementsIn(vectorEmpty())
        .inOrder();
  }

  @Test
  public void keysUploadedAfterNotification_codeBeforeNotification_vectorEmpty()
      throws Exception {
    // GIVEN
    Instant workerRunTime = clock.now().minus(Duration.ofDays(1));
    Instant exposureTime = workerRunTime.minus(Duration.ofDays(1));
    Instant submittedCodeTime = exposureTime.minus(Duration.ofDays(1));

    exposureNotificationSharedPreferences
        .setPrivateAnalyticsWorkerLastTimeForBiweekly(workerRunTime);
    exposureNotificationSharedPreferences
        .setPrivateAnalyticsLastSubmittedKeysTime(submittedCodeTime);
    exposureNotificationSharedPreferences
        .setExposureNotificationLastShownClassification(exposureTime,
            getTestVector(1).exposureClassification);

    // WHEN
    List<Integer> vector = keysUploadedAfterNotificationMetric.getDataVector().get();

    // THEN
    assertThat(vector)
        .containsExactlyElementsIn(vectorEmpty())
        .inOrder();
  }

  @Test
  public void keysUploadedAfterNotification_notificationInTimePeriodSinceLastWorkerRun_dataPresent()
      throws Exception {
    // GIVEN
    Instant submittedCodeTime = clock.now().minus(Duration.ofDays(1));
    Instant notificationTime = submittedCodeTime.minus(Duration.ofDays(1));
    Instant exposureTime = notificationTime.minus(Duration.ofDays(2));
    Instant workerRunTime = exposureTime.minus(Duration.ofDays(2));

    exposureNotificationSharedPreferences
        .setPrivateAnalyticsWorkerLastTimeForBiweekly(workerRunTime);
    exposureNotificationSharedPreferences
        .setPrivateAnalyticsLastSubmittedKeysTime(submittedCodeTime);

    for (TestResult testResult : TestResult.values()) {
      exposureNotificationSharedPreferences.setPrivateAnalyticsLastReportType(testResult);

      for (int classificationIndex = 1; classificationIndex <= 4; classificationIndex++) {
        ExposureClassification exposureClassification = ExposureClassification
            .create(classificationIndex, "", exposureTime.toEpochMilli());
        exposureNotificationSharedPreferences
            .setExposureNotificationLastShownClassification(notificationTime,
                exposureClassification);

        // WHEN
        List<Integer> vector = keysUploadedAfterNotificationMetric.getDataVector().get();

        // THEN
        assertThat(vector)
            .containsExactlyElementsIn(vectorFor(testResult, classificationIndex))
            .inOrder();
      }
    }
  }

  @Test
  public void testHammingWeight() {
    assertThat(keysUploadedAfterNotificationMetric.getMetricHammingWeight()).isEqualTo(0);
  }

  private static List<Integer> vectorEmpty() {
    int[] retVector = new int[BIN_LENGTH];
    return Ints.asList(retVector);
  }

  private static List<Integer> vectorNoNotification() {
    List<Integer> retVector = vectorEmpty();
    retVector.set(NO_NOTIFICATION_SHOWN_BIN, 1);
    return retVector;
  }

  private static List<Integer> vectorFor(TestResult testResult, int classification) {
    int reportTypeBin = 0;
    switch (testResult) {
      case CONFIRMED:
        reportTypeBin = CONFIRMED_TEST_BIN;
        break;
      case LIKELY:
        reportTypeBin = CONFIRMED_CLINICAL_DIAGNOSIS_BIN;
        break;
      case USER_REPORT:
        reportTypeBin = SELF_REPORTED_BIN;
        break;
      case NEGATIVE:
        reportTypeBin = REVOKED_BIN;
        break;
    }
    List<Integer> retVector = vectorEmpty();
    retVector.set(4 * (reportTypeBin - 1) + (classification - 1), 1);
    return retVector;
  }

}