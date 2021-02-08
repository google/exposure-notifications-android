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
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.apps.exposurenotification.testsupport.ExposureNotificationRules;
import com.google.android.apps.exposurenotification.testsupport.FakeClock;
import dagger.hilt.android.testing.BindValue;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import dagger.hilt.android.testing.UninstallModules;
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

/**
 * Tests of {@link PeriodicExposureNotificationMetric}.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner.class)
@Config(application = HiltTestApplication.class)
@UninstallModules({RealTimeModule.class})
public class PeriodicExposureNotificationMetricTest {

  @Rule
  public ExposureNotificationRules rules = ExposureNotificationRules.forTest(this).build();
  @Inject
  PeriodicExposureNotificationMetric periodicExposureNotificationTracker;
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
  public void testDailyExposureNotificationInRange() throws Exception {
    Instant fiveHoursAgo = clock.now().minus(Duration.ofHours(5));
    exposureNotificationSharedPreferences
        .setExposureNotificationLastShownClassification(fiveHoursAgo, /* classificationIndex= */ 1);
    List<Integer> dataVector = periodicExposureNotificationTracker
        .getDataVector().get();

    assertThat(dataVector).containsExactly(0, 1, 0, 0, 0).inOrder();
  }

  @Test
  public void testDailyExposureNotificationNotInRange() throws Exception {
    Instant fiveDaysAgo = clock.now().minus(Duration.ofDays(5));
    exposureNotificationSharedPreferences
        .setExposureNotificationLastShownClassification(fiveDaysAgo, /* classificationIndex= */ 1);

    List<Integer> dataVector = periodicExposureNotificationTracker
        .getDataVector().get();

    assertThat(dataVector).containsExactly(0, 1, 0, 0, 0).inOrder();
  }

  @Test
  public void testHammingWeight() {
    assertThat(periodicExposureNotificationTracker.getMetricHammingWeight()).isEqualTo(1);
  }

  @Test
  public void testDataNotCachedWhenNotEnabled() throws ExecutionException, InterruptedException {
    exposureNotificationSharedPreferences.setPrivateAnalyticsState(false);

    Instant fiveHoursAgo = clock.now().minus(Duration.ofHours(5));
    exposureNotificationSharedPreferences
        .setExposureNotificationLastShownClassification(fiveHoursAgo, /* classificationIndex= */ 1);
    List<Integer> dataVector = periodicExposureNotificationTracker
        .getDataVector().get();

    assertThat(dataVector).containsExactly(1, 0, 0, 0, 0).inOrder();
  }
}
