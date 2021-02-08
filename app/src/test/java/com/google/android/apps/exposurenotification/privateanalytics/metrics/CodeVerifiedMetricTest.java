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

import static com.google.android.apps.exposurenotification.privateanalytics.metrics.CodeVerifiedMetric.BIN_LENGTH;
import static com.google.android.apps.exposurenotification.privateanalytics.metrics.CodeVerifiedMetric.NO_NOTIFICATION_SHOWN_BIN;
import static com.google.common.truth.Truth.assertThat;
import static org.threeten.bp.temporal.ChronoUnit.DAYS;
import static org.threeten.bp.temporal.ChronoUnit.HOURS;
import static org.threeten.bp.temporal.ChronoUnit.MINUTES;

import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.android.apps.exposurenotification.common.time.RealTimeModule;
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
 * Tests of {@link CodeVerifiedMetric}.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner.class)
@Config(application = HiltTestApplication.class)
@UninstallModules({RealTimeModule.class})
public class CodeVerifiedMetricTest {

  @Rule
  public ExposureNotificationRules rules = ExposureNotificationRules.forTest(this).build();

  @Inject
  CodeVerifiedMetric codeVerifiedMetric;

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
  public void testCodeVerifiedNoNotificationSinceLastWorkerRun() throws Exception {
    // GIVEN
    Instant submittedCodeTime = clock.now().minus(Duration.ofDays(1));
    Instant workerRunTime = clock.now().minus(Duration.ofDays(2));

    exposureNotificationSharedPreferences.setPrivateAnalyticsWorkerLastTime(workerRunTime);
    exposureNotificationSharedPreferences
        .setPrivateAnalyticsLastSubmittedCodeTime(submittedCodeTime);

    // WHEN
    List<Integer> vector = codeVerifiedMetric.getDataVector().get();

    // THEN
    assertThat(vector)
        .containsExactlyElementsIn(vectorNoNotification())
        .inOrder();
  }

  @Test
  public void testCodeVerifiedNotificationInTimePeriodSinceLastWorkerRun() throws Exception {
    // GIVEN
    Instant submittedCodeTime = clock.now().minus(Duration.ofDays(1));
    Instant workerRunTime = clock.now().minus(Duration.ofDays(2));
    // notification shown 13 days and 23h earlier than submitted code
    Instant notificationTime = submittedCodeTime.minus(13, DAYS).minus(23, HOURS);

    exposureNotificationSharedPreferences.setPrivateAnalyticsWorkerLastTime(workerRunTime);
    exposureNotificationSharedPreferences
        .setPrivateAnalyticsLastSubmittedCodeTime(submittedCodeTime);

    for (int classificationIndex = 1; classificationIndex <= 4; classificationIndex++) {
      exposureNotificationSharedPreferences
          .setExposureNotificationLastShownClassification(notificationTime, classificationIndex);

      // WHEN
      List<Integer> vector = codeVerifiedMetric.getDataVector().get();

      // THEN
      assertThat(vector)
          .containsExactlyElementsIn(vectorForClassification(classificationIndex))
          .inOrder();
    }
  }

  @Test
  public void testCodeVerifiedNoNotificationInTimePeriodSinceLastWorkerRun() throws Exception {
    // GIVEN
    Instant submittedCodeTime = clock.now().minus(Duration.ofDays(1));
    Instant workerRunTime = clock.now().minus(Duration.ofDays(2));
    // notification shown 14 days and 1 minute earlier than submitted code
    Instant notificationTime = submittedCodeTime.minus(14, DAYS).minus(1, MINUTES);

    exposureNotificationSharedPreferences.setPrivateAnalyticsWorkerLastTime(workerRunTime);
    exposureNotificationSharedPreferences
        .setPrivateAnalyticsLastSubmittedCodeTime(submittedCodeTime);

    for (int classificationIndex = 1; classificationIndex <= 4; classificationIndex++) {
      exposureNotificationSharedPreferences
          .setExposureNotificationLastShownClassification(notificationTime, classificationIndex);

      // WHEN
      List<Integer> vector = codeVerifiedMetric.getDataVector().get();

      // THEN
      assertThat(vector)
          .containsExactlyElementsIn(vectorNoNotification())
          .inOrder();
    }
  }

  @Test
  public void testCodeVerifiedBeforeLastWorkerRun() throws Exception {
    // GIVEN
    Instant workerRunTime = clock.now().minus(Duration.ofDays(1));
    Instant submittedCodeTime = clock.now().minus(Duration.ofDays(2));

    exposureNotificationSharedPreferences.setPrivateAnalyticsWorkerLastTime(workerRunTime);
    exposureNotificationSharedPreferences
        .setPrivateAnalyticsLastSubmittedCodeTime(submittedCodeTime);

    // WHEN
    List<Integer> vector = codeVerifiedMetric.getDataVector().get();

    // THEN
    assertThat(vector)
        .containsExactlyElementsIn(vectorEmpty())
        .inOrder();
  }

  @Test
  public void testNoCodeVerifiedSinceLastWorkerRun() throws Exception {
    // GIVEN
    Instant workerRunTime = clock.now().minus(Duration.ofDays(1));

    exposureNotificationSharedPreferences.setPrivateAnalyticsWorkerLastTime(workerRunTime);

    // WHEN
    List<Integer> vector = codeVerifiedMetric.getDataVector().get();

    // THEN
    assertThat(vector)
        .containsExactlyElementsIn(vectorEmpty())
        .inOrder();
  }

  @Test
  public void testHammingWeight() {
    assertThat(codeVerifiedMetric.getMetricHammingWeight()).isEqualTo(0);
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

  private static List<Integer> vectorForClassification(int classification) {
    List<Integer> retVector = vectorEmpty();
    retVector.set(1 + classification, 1);
    return retVector;
  }

}
