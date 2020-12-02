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

import static com.google.android.apps.exposurenotification.privateanalytics.metrics.PeriodicExposureNotificationInteractionMetric.INTERACTION_TYPE_COUNT;
import static com.google.android.apps.exposurenotification.privateanalytics.metrics.PeriodicExposureNotificationInteractionMetric.NO_EXPOSURE_BIN_ID;
import static com.google.android.apps.exposurenotification.privateanalytics.metrics.PeriodicExposureNotificationInteractionMetric.VECTOR_LENGTH;
import static com.google.common.truth.Truth.assertThat;

import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.android.apps.exposurenotification.common.time.RealTimeModule;
import com.google.android.apps.exposurenotification.privateanalytics.MetricsSnapshot;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences.NotificationInteraction;
import com.google.android.apps.exposurenotification.testsupport.ExposureNotificationRules;
import com.google.android.apps.exposurenotification.testsupport.FakeClock;
import com.google.common.primitives.Ints;
import dagger.hilt.android.testing.BindValue;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import dagger.hilt.android.testing.UninstallModules;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.threeten.bp.Duration;
import org.threeten.bp.Instant;
import org.threeten.bp.Period;

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

  @Test
  public void testDailyExposureNotificationInRangeIgnored() throws Exception {
    // GIVEN
    Instant oneDayAgo = clock.now().minus(Period.ofDays(1));

    exposureNotificationSharedPreferences.setExposureNotificationLastInteraction(
        oneDayAgo, NotificationInteraction.CLICKED);

    // WHEN
    List<Integer> vector = periodicExposureNotificationInteractionMetric.getDataVector(
        getMetricsSnapshot()).get();

    // THEN
    assertThat(vector)
        .containsExactlyElementsIn(buildInteractionVector(1, NotificationInteraction.UNKNOWN))
        .inOrder();
  }

  @Test
  public void testDailyExposureNotificationInRangeClicked() throws Exception {
    // GIVEN
    Instant oneDayAgo = clock.now().minus(Period.ofDays(1));

    exposureNotificationSharedPreferences
        .setExposureNotificationLastShownClassification(oneDayAgo, /* classificationIndex= */ 1);
    exposureNotificationSharedPreferences.setExposureNotificationLastInteraction(
        clock.now(), NotificationInteraction.CLICKED);

    // WHEN
    List<Integer> vector = periodicExposureNotificationInteractionMetric.getDataVector(
        getMetricsSnapshot()).get();

    // THEN
    assertThat(vector)
        .containsExactlyElementsIn(buildInteractionVector(1, NotificationInteraction.CLICKED))
        .inOrder();
  }

  @Test
  public void testDailyExposureNotificationInRangeDismissed() throws Exception {
    // GIVEN
    Instant oneDayAgo = clock.now().minus(Period.ofDays(1));
    exposureNotificationSharedPreferences
        .setExposureNotificationLastShownClassification(oneDayAgo, /* classificationIndex= */ 1);
    exposureNotificationSharedPreferences.setExposureNotificationLastInteraction(
        clock.now(), NotificationInteraction.DISMISSED);

    // WHEN
    List<Integer> vector = periodicExposureNotificationInteractionMetric.getDataVector(
        getMetricsSnapshot()).get();

    // THEN
    assertThat(vector)
        .containsExactlyElementsIn(buildInteractionVector(1, NotificationInteraction.DISMISSED))
        .inOrder();
  }

  @Test
  public void testDailyExposureNotificationNotInRange() throws Exception {
    // GIVEN
    Instant twoDaysAgo = clock.now().minus(Period.ofDays(2));
    exposureNotificationSharedPreferences
        .setExposureNotificationLastShownClassification(twoDaysAgo, /* classificationIndex= */ 1);
    exposureNotificationSharedPreferences.setExposureNotificationLastInteraction(
        clock.now(), NotificationInteraction.DISMISSED);

    // WHEN
    List<Integer> vector = periodicExposureNotificationInteractionMetric.getDataVector(
        getMetricsSnapshot()).get();

    // THEN
    assertThat(vector)
        .containsExactlyElementsIn(buildInteractionVector(1, NotificationInteraction.DISMISSED))
        .inOrder();
  }

  @Test
  public void testHammingWeight() {
    assertThat(periodicExposureNotificationInteractionMetric.getMetricHammingWeight()).isEqualTo(1);
  }

  @Test
  public void testDataNotCachedWhenNotEnabled() throws ExecutionException, InterruptedException {
    exposureNotificationSharedPreferences.setPrivateAnalyticsState(false);

    Instant fiveHoursAgo = clock.now().minus(Duration.ofHours(5));
    exposureNotificationSharedPreferences
        .setExposureNotificationLastShownClassification(fiveHoursAgo, /* classificationIndex= */ 1);
    List<Integer> dataVector = periodicExposureNotificationInteractionMetric.getDataVector(
        getMetricsSnapshot()).get();

    // THEN
    assertThat(dataVector)
        .containsExactlyElementsIn(buildInteractionVector(1, NotificationInteraction.UNKNOWN))
        .inOrder();
  }

  private MetricsSnapshot getMetricsSnapshot() {
    return MetricsSnapshot.fromPreferences(exposureNotificationSharedPreferences);
  }

  private static List<Integer> buildInteractionVector(
      int severity, NotificationInteraction interaction) {
    int[] retVector = new int[VECTOR_LENGTH];
    Arrays.fill(retVector, 0);
    switch (interaction) {
      case UNKNOWN:
        retVector[NO_EXPOSURE_BIN_ID] = 1;
        break;
      case CLICKED:
        retVector[severity * INTERACTION_TYPE_COUNT - 1] = 1;
        break;
      case DISMISSED:
        retVector[severity * INTERACTION_TYPE_COUNT] = 1;
        break;
    }
    return Ints.asList(retVector);
  }

}
