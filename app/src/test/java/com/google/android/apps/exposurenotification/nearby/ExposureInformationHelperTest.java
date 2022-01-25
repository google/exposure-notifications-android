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

package com.google.android.apps.exposurenotification.nearby;

import static com.google.android.apps.exposurenotification.nearby.ExposureInformationHelper.EXPOSURE_EXPIRATION_DEFAULT_THRESHOLD;
import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.android.apps.exposurenotification.common.time.RealTimeModule;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.apps.exposurenotification.testsupport.ExposureClassificationUtils;
import com.google.android.apps.exposurenotification.testsupport.ExposureNotificationRules;
import com.google.android.apps.exposurenotification.testsupport.FakeClock;
import com.google.android.gms.nearby.exposurenotification.DailySummariesConfig;
import com.google.android.gms.nearby.exposurenotification.DailySummariesConfig.DailySummariesConfigBuilder;
import dagger.hilt.android.testing.BindValue;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import dagger.hilt.android.testing.UninstallModules;
import java.util.Arrays;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;
import org.threeten.bp.Duration;

/**
 * The unit test for ExposureInformationHelper.
 */
@RunWith(AndroidJUnit4.class)
@HiltAndroidTest
@Config(application = HiltTestApplication.class)
@UninstallModules({RealTimeModule.class})
public class ExposureInformationHelperTest {

  private static final DailySummariesConfig DAILY_SUMMARIES_CONFIG =
      new DailySummariesConfigBuilder()
          .setDaysSinceExposureThreshold((int) EXPOSURE_EXPIRATION_DEFAULT_THRESHOLD.toDays())
          .setAttenuationBuckets(Arrays.asList(1, 2, 3), Arrays.asList(0.0, 0.0, 0.0, 0.0))
          .build();

  @Rule
  public ExposureNotificationRules rules = ExposureNotificationRules.forTest(this)
      .withMocks()
      .build();

  @Inject
  ExposureNotificationSharedPreferences exposureNotificationSharedPreferences;

  @BindValue
  Clock clock = new FakeClock();

  ExposureInformationHelper exposureInformationHelper;

  @Before
  public void setup() {
    rules.hilt().inject();

    exposureInformationHelper = new ExposureInformationHelper(
        exposureNotificationSharedPreferences,
        DAILY_SUMMARIES_CONFIG,
        clock);
  }

  @Test
  public void isOutdatedExposurePresent_noExposurePresent_returnsFalse() {
    assertThat(exposureInformationHelper.isOutdatedExposurePresent()).isFalse();
  }

  @Test
  public void isOutdatedExposurePresent_activeExposurePresent_returnsFalse() {
    exposureNotificationSharedPreferences.setExposureClassification(
        ExposureClassificationUtils.getActiveExposureHappenedXDaysAgo(1));

    assertThat(exposureInformationHelper.isOutdatedExposurePresent()).isFalse();
  }

  @Test
  public void isOutdatedExposurePresent_outDatedExposurePresent_returnsTrue() {
    exposureNotificationSharedPreferences.setExposureClassification(
        ExposureClassificationUtils.getOutdatedExposure());

    assertThat(exposureInformationHelper.isOutdatedExposurePresent()).isTrue();
  }

  @Test
  public void isActiveExposurePresent_noExposurePresent_returnsFalse() {
    assertThat(exposureInformationHelper.isActiveExposurePresent()).isFalse();
  }

  @Test
  public void isActiveExposurePresent_outdatedExposurePresent_returnsFalse() {
    exposureNotificationSharedPreferences.setExposureClassification(
        ExposureClassificationUtils.getOutdatedExposure());

    assertThat(exposureInformationHelper.isActiveExposurePresent()).isFalse();
  }

  @Test
  public void isActiveExposurePresent_activeExposurePresent_returnsTrue() {
    exposureNotificationSharedPreferences.setExposureClassification(
        ExposureClassificationUtils.getActiveExposureHappenedXDaysAgo(1));

    assertThat(exposureInformationHelper.isActiveExposurePresent()).isTrue();
  }

  @Test
  public void clearSharedPrefsExposureInfo_activeExposureWasPresentButExposureInformationWipedOut() {
    exposureNotificationSharedPreferences.setExposureClassification(
        ExposureClassificationUtils.getActiveExposureHappenedXDaysAgo(1));

    assertThat(exposureInformationHelper.isActiveExposurePresent()).isTrue();
    assertThat(exposureInformationHelper.isOutdatedExposurePresent()).isFalse();
    exposureInformationHelper.deleteExposures();

    assertThat(exposureInformationHelper.isActiveExposurePresent()).isFalse();
    assertThat(exposureInformationHelper.isOutdatedExposurePresent()).isFalse();
  }

  @Test
  public void clearSharedPrefsExposureInfo_outDatedExposureWasPresentButExposureInformationWipedOut() {
    exposureNotificationSharedPreferences.setExposureClassification(
        ExposureClassificationUtils.getOutdatedExposure());

    assertThat(exposureInformationHelper.isActiveExposurePresent()).isFalse();
    assertThat(exposureInformationHelper.isOutdatedExposurePresent()).isTrue();
    exposureInformationHelper.deleteExposures();

    assertThat(exposureInformationHelper.isActiveExposurePresent()).isFalse();
    assertThat(exposureInformationHelper.isOutdatedExposurePresent()).isFalse();
  }

  @Test
  public void getDaysUntilExposureExpires_noExposurePresent_returns0Days() {
    Duration daysUntilExposureExpires = exposureInformationHelper.getDaysUntilExposureExpires();

    assertThat(daysUntilExposureExpires.toDays()).isEqualTo(0);
  }

  @Test
  public void getDaysUntilExposureExpires_outdatedExposurePresent_returns0Days() {
    exposureNotificationSharedPreferences.setExposureClassification(
        ExposureClassificationUtils.getOutdatedExposure());

    Duration daysUntilExposureExpires = exposureInformationHelper.getDaysUntilExposureExpires();

    assertThat(daysUntilExposureExpires.toDays()).isEqualTo(0);
  }

  @Test
  public void getDaysUntilExposureExpires_activeExposurePresentAndHappenedXDaysAgo_returnsNumberOfDaysAsExpected() {
    int[] numbersOfDaysAgo = {1, 5, 7, 14};
    for (int numberOfDaysAgo : numbersOfDaysAgo) {
      exposureNotificationSharedPreferences.setExposureClassification(
          ExposureClassificationUtils.getActiveExposureHappenedXDaysAgo(numberOfDaysAgo));

      Duration daysUntilExposureExpires = exposureInformationHelper.getDaysUntilExposureExpires();

      assertThat(daysUntilExposureExpires.toDays())
          .isEqualTo(EXPOSURE_EXPIRATION_DEFAULT_THRESHOLD.toDays() - numberOfDaysAgo + 1L);
    }
  }

}
