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

package com.google.android.apps.exposurenotification.privateanalytics;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

@RunWith(AndroidJUnit4.class)
@HiltAndroidTest
@Config(application = HiltTestApplication.class)
public class MetricsRemoteConfigsTest {

  private static final double DEFAULT_SAMPING_RATE = 1.0;
  private static final double DEFAULT_DAILY_METRIC_EPSILON = 8.0;
  private static final double DEFAULT_BIWEEKLY_METRIC_EPSILON = 8.0;

  @Test
  public void testDefaultRemoteConfigValues() {
    MetricsRemoteConfigs metricsRemoteConfigs = MetricsRemoteConfigs.newBuilder()
        .build();

    assertThat(metricsRemoteConfigs.notificationCountPrioSamplingRate())
        .isEqualTo(DEFAULT_SAMPING_RATE);
    assertThat(metricsRemoteConfigs.notificationCountPrioEpsilon())
        .isEqualTo(DEFAULT_DAILY_METRIC_EPSILON);

    assertThat(metricsRemoteConfigs.interactionCountPrioSamplingRate())
        .isEqualTo(DEFAULT_SAMPING_RATE);
    assertThat(metricsRemoteConfigs.interactionCountPrioEpsilon())
        .isEqualTo(DEFAULT_DAILY_METRIC_EPSILON);

    assertThat(metricsRemoteConfigs.riskScorePrioSamplingRate()).isEqualTo(DEFAULT_SAMPING_RATE);
    assertThat(metricsRemoteConfigs.riskScorePrioEpsilon()).isEqualTo(DEFAULT_DAILY_METRIC_EPSILON);

    assertThat(metricsRemoteConfigs.codeVerifiedPrioSamplingRate()).isEqualTo(DEFAULT_SAMPING_RATE);
    assertThat(metricsRemoteConfigs.codeVerifiedPrioEpsilon()).isEqualTo(
        DEFAULT_DAILY_METRIC_EPSILON);

    assertThat(metricsRemoteConfigs.codeVerifiedWithReportTypePrioSamplingRate())
        .isEqualTo(DEFAULT_SAMPING_RATE);
    assertThat(metricsRemoteConfigs.codeVerifiedWithReportTypePrioEpsilon())
        .isEqualTo(DEFAULT_BIWEEKLY_METRIC_EPSILON);

    assertThat(metricsRemoteConfigs.keysUploadedPrioSamplingRate()).isEqualTo(DEFAULT_SAMPING_RATE);
    assertThat(metricsRemoteConfigs.keysUploadedPrioEpsilon()).isEqualTo(
        DEFAULT_DAILY_METRIC_EPSILON);

    assertThat(metricsRemoteConfigs.keysUploadedWithReportTypePrioSamplingRate())
        .isEqualTo(DEFAULT_SAMPING_RATE);
    assertThat(metricsRemoteConfigs.keysUploadedWithReportTypePrioEpsilon())
        .isEqualTo(DEFAULT_BIWEEKLY_METRIC_EPSILON);

    assertThat(metricsRemoteConfigs.dateExposurePrioSamplingRate()).isEqualTo(DEFAULT_SAMPING_RATE);
    assertThat(metricsRemoteConfigs.dateExposurePrioEpsilon()).isEqualTo(
        DEFAULT_DAILY_METRIC_EPSILON);

    assertThat(metricsRemoteConfigs.keysUploadedVaccineStatusPrioSamplingRate())
        .isEqualTo(DEFAULT_SAMPING_RATE);
    assertThat(metricsRemoteConfigs.keysUploadedVaccineStatusPrioEpsilon())
        .isEqualTo(DEFAULT_DAILY_METRIC_EPSILON);

    assertThat(metricsRemoteConfigs.keysUploadedAfterNotificationPrioSamplingRate())
        .isEqualTo(DEFAULT_SAMPING_RATE);
    assertThat(metricsRemoteConfigs.keysUploadedAfterNotificationPrioEpsilon())
        .isEqualTo(DEFAULT_BIWEEKLY_METRIC_EPSILON);

    assertThat(metricsRemoteConfigs.periodicExposureNotificationBiweeklyPrioSamplingRate())
        .isEqualTo(DEFAULT_SAMPING_RATE);
    assertThat(metricsRemoteConfigs.periodicExposureNotificationBiweeklyPrioEpsilon())
        .isEqualTo(DEFAULT_BIWEEKLY_METRIC_EPSILON);

    assertThat(metricsRemoteConfigs.dateExposureBiweeklyPrioSamplingRate())
        .isEqualTo(DEFAULT_SAMPING_RATE);
    assertThat(metricsRemoteConfigs.dateExposureBiweeklyPrioEpsilon())
        .isEqualTo(DEFAULT_BIWEEKLY_METRIC_EPSILON);

    assertThat(metricsRemoteConfigs.keysUploadedVaccineStatusBiweeklyPrioSamplingRate())
        .isEqualTo(DEFAULT_SAMPING_RATE);
    assertThat(metricsRemoteConfigs.keysUploadedVaccineStatusBiweeklyPrioEpsilon())
        .isEqualTo(DEFAULT_BIWEEKLY_METRIC_EPSILON);
  }
}