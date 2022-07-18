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

import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import java.util.Random;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@HiltAndroidTest
@RunWith(RobolectricTestRunner.class)
@Config(application = HiltTestApplication.class)
public class PrivateAnalyticsMetricsRemoteConfigTest {

  private PrivateAnalyticsMetricsRemoteConfig privateAnalyticsMetricsRemoteConfig;

  @Before
  public void setup() {
    privateAnalyticsMetricsRemoteConfig = new PrivateAnalyticsMetricsRemoteConfig(null, null, null,
        null);
  }

  @Test
  public void testRemoteJsonObj_parsedCorrectly() throws JSONException {
    JSONObject remoteJsonObj = new JSONObject()
        .put(PrivateAnalyticsMetricsRemoteConfig.CONFIG_METRIC_INTERACTION_COUNT_SAMPLING_PROB_KEY,
            new Random().nextDouble())
        .put(PrivateAnalyticsMetricsRemoteConfig.CONFIG_METRIC_INTERACTION_COUNT_PRIO_EPSILON_KEY,
            new Random().nextDouble())

        .put(PrivateAnalyticsMetricsRemoteConfig.CONFIG_METRIC_NOTIFICATION_COUNT_SAMPLING_PROB_KEY,
            new Random().nextDouble())
        .put(PrivateAnalyticsMetricsRemoteConfig.CONFIG_METRIC_NOTIFICATION_COUNT_PRIO_EPSILON_KEY,
            new Random().nextDouble())

        .put(PrivateAnalyticsMetricsRemoteConfig.CONFIG_METRIC_RISK_HISTOGRAM_SAMPLING_PROB_KEY,
            new Random().nextDouble())
        .put(PrivateAnalyticsMetricsRemoteConfig.CONFIG_METRIC_RISK_HISTOGRAM_PRIO_EPSILON_KEY,
            new Random().nextDouble())

        .put(PrivateAnalyticsMetricsRemoteConfig.CONFIG_METRIC_CODE_VERIFIED_SAMPLING_PROB_KEY,
            new Random().nextDouble())
        .put(PrivateAnalyticsMetricsRemoteConfig.CONFIG_METRIC_CODE_VERIFIED_PRIO_EPSILON_KEY,
            new Random().nextDouble())

        .put(
            PrivateAnalyticsMetricsRemoteConfig.CONFIG_METRIC_CODE_VERIFIED_WITH_REPORT_TYPE_SAMPLING_PROB_KEY,
            new Random().nextDouble())
        .put(
            PrivateAnalyticsMetricsRemoteConfig.CONFIG_METRIC_CODE_VERIFIED_WITH_REPORT_TYPE_PRIO_EPSILON_KEY,
            new Random().nextDouble())

        .put(PrivateAnalyticsMetricsRemoteConfig.CONFIG_METRIC_KEYS_UPLOADED_SAMPLING_PROB_KEY,
            new Random().nextDouble())
        .put(PrivateAnalyticsMetricsRemoteConfig.CONFIG_METRIC_KEYS_UPLOADED_PRIO_EPSILON_KEY,
            new Random().nextDouble())

        .put(
            PrivateAnalyticsMetricsRemoteConfig.CONFIG_METRIC_KEYS_UPLOADED_WITH_REPORT_TYPE_SAMPLING_PROB_KEY,
            new Random().nextDouble())
        .put(
            PrivateAnalyticsMetricsRemoteConfig.CONFIG_METRIC_KEYS_UPLOADED_WITH_REPORT_TYPE_PRIO_EPSILON_KEY,
            new Random().nextDouble())

        .put(PrivateAnalyticsMetricsRemoteConfig.CONFIG_METRIC_DATE_EXPOSURE_SAMPLING_PROB_KEY,
            new Random().nextDouble())
        .put(PrivateAnalyticsMetricsRemoteConfig.CONFIG_METRIC_DATE_EXPOSURE_PRIO_EPSILON_KEY,
            new Random().nextDouble())

        .put(
            PrivateAnalyticsMetricsRemoteConfig
                .CONFIG_METRIC_KEYS_UPLOADED_VACCINE_STATUS_SAMPLING_PROB_KEY,
            new Random().nextDouble())
        .put(
            PrivateAnalyticsMetricsRemoteConfig
                .CONFIG_METRIC_KEYS_UPLOADED_VACCINE_STATUS_PRIO_EPSILON_KEY,
            new Random().nextDouble())

        .put(
            PrivateAnalyticsMetricsRemoteConfig
                .CONFIG_METRIC_KEYS_UPLOADED_AFTER_NOTIFICATION_SAMPLING_PROB_KEY,
            new Random().nextDouble())
        .put(
            PrivateAnalyticsMetricsRemoteConfig
                .CONFIG_METRIC_KEYS_UPLOADED_AFTER_NOTIFICATION_PRIO_EPSILON_KEY,
            new Random().nextDouble())

        .put(
            PrivateAnalyticsMetricsRemoteConfig
                .CONFIG_METRIC_PERIODIC_EXPOSURE_NOTIFICATION_BIWEEKLY_SAMPLING_PROB_KEY,
            new Random().nextDouble())
        .put(
            PrivateAnalyticsMetricsRemoteConfig
                .CONFIG_METRIC_PERIODIC_EXPOSURE_NOTIFICATION_BIWEEKLY_PRIO_EPSILON_KEY,
            new Random().nextDouble())

        .put(
            PrivateAnalyticsMetricsRemoteConfig
                .CONFIG_METRIC_DATE_EXPOSURE_BIWEEKLY_SAMPLING_PROB_KEY,
            new Random().nextDouble())
        .put(
            PrivateAnalyticsMetricsRemoteConfig
                .CONFIG_METRIC_DATE_EXPOSURE_BIWEEKLY_PRIO_EPSILON_KEY,
            new Random().nextDouble())

        .put(
            PrivateAnalyticsMetricsRemoteConfig
                .CONFIG_METRIC_KEYS_UPLOADED_VACCINE_STATUS_BIWEEKLY_SAMPLING_PROB_KEY,
            new Random().nextDouble())
        .put(
            PrivateAnalyticsMetricsRemoteConfig
                .CONFIG_METRIC_KEYS_UPLOADED_VACCINE_STATUS_BIWEEKLY_PRIO_EPSILON_KEY,
            new Random().nextDouble());

    MetricsRemoteConfigs metricsRemoteConfig = privateAnalyticsMetricsRemoteConfig
        .convertToRemoteConfig(remoteJsonObj);

    assertThat(metricsRemoteConfig.interactionCountPrioSamplingRate()).isEqualTo(remoteJsonObj.get(
        PrivateAnalyticsMetricsRemoteConfig.CONFIG_METRIC_INTERACTION_COUNT_SAMPLING_PROB_KEY));
    assertThat(metricsRemoteConfig.interactionCountPrioEpsilon()).isEqualTo(remoteJsonObj
        .get(PrivateAnalyticsMetricsRemoteConfig.CONFIG_METRIC_INTERACTION_COUNT_PRIO_EPSILON_KEY));

    assertThat(metricsRemoteConfig.notificationCountPrioSamplingRate()).isEqualTo(remoteJsonObj.get(
        PrivateAnalyticsMetricsRemoteConfig.CONFIG_METRIC_NOTIFICATION_COUNT_SAMPLING_PROB_KEY));
    assertThat(metricsRemoteConfig.notificationCountPrioEpsilon()).isEqualTo(remoteJsonObj.get(
        PrivateAnalyticsMetricsRemoteConfig.CONFIG_METRIC_NOTIFICATION_COUNT_PRIO_EPSILON_KEY));

    assertThat(metricsRemoteConfig.riskScorePrioSamplingRate()).isEqualTo(remoteJsonObj
        .get(PrivateAnalyticsMetricsRemoteConfig.CONFIG_METRIC_RISK_HISTOGRAM_SAMPLING_PROB_KEY));
    assertThat(metricsRemoteConfig.riskScorePrioEpsilon()).isEqualTo(remoteJsonObj
        .get(PrivateAnalyticsMetricsRemoteConfig.CONFIG_METRIC_RISK_HISTOGRAM_PRIO_EPSILON_KEY));

    assertThat(metricsRemoteConfig.codeVerifiedPrioSamplingRate()).isEqualTo(remoteJsonObj
        .get(PrivateAnalyticsMetricsRemoteConfig.CONFIG_METRIC_CODE_VERIFIED_SAMPLING_PROB_KEY));
    assertThat(metricsRemoteConfig.codeVerifiedPrioEpsilon()).isEqualTo(remoteJsonObj
        .get(PrivateAnalyticsMetricsRemoteConfig.CONFIG_METRIC_CODE_VERIFIED_PRIO_EPSILON_KEY));

    assertThat(metricsRemoteConfig.codeVerifiedWithReportTypePrioSamplingRate())
        .isEqualTo(remoteJsonObj
            .get(
                PrivateAnalyticsMetricsRemoteConfig.CONFIG_METRIC_CODE_VERIFIED_WITH_REPORT_TYPE_SAMPLING_PROB_KEY));
    assertThat(metricsRemoteConfig.codeVerifiedWithReportTypePrioEpsilon()).isEqualTo(remoteJsonObj
        .get(
            PrivateAnalyticsMetricsRemoteConfig.CONFIG_METRIC_CODE_VERIFIED_WITH_REPORT_TYPE_PRIO_EPSILON_KEY));

    assertThat(metricsRemoteConfig.keysUploadedPrioSamplingRate()).isEqualTo(remoteJsonObj
        .get(PrivateAnalyticsMetricsRemoteConfig.CONFIG_METRIC_KEYS_UPLOADED_SAMPLING_PROB_KEY));
    assertThat(metricsRemoteConfig.keysUploadedPrioEpsilon()).isEqualTo(remoteJsonObj
        .get(PrivateAnalyticsMetricsRemoteConfig.CONFIG_METRIC_KEYS_UPLOADED_PRIO_EPSILON_KEY));

    assertThat(metricsRemoteConfig.keysUploadedWithReportTypePrioSamplingRate())
        .isEqualTo(remoteJsonObj
            .get(
                PrivateAnalyticsMetricsRemoteConfig.CONFIG_METRIC_KEYS_UPLOADED_WITH_REPORT_TYPE_SAMPLING_PROB_KEY));
    assertThat(metricsRemoteConfig.keysUploadedWithReportTypePrioEpsilon()).isEqualTo(remoteJsonObj
        .get(
            PrivateAnalyticsMetricsRemoteConfig.CONFIG_METRIC_KEYS_UPLOADED_WITH_REPORT_TYPE_PRIO_EPSILON_KEY));

    assertThat(metricsRemoteConfig.dateExposurePrioSamplingRate()).isEqualTo(remoteJsonObj
        .get(PrivateAnalyticsMetricsRemoteConfig.CONFIG_METRIC_DATE_EXPOSURE_SAMPLING_PROB_KEY));
    assertThat(metricsRemoteConfig.dateExposurePrioEpsilon()).isEqualTo(remoteJsonObj
        .get(PrivateAnalyticsMetricsRemoteConfig.CONFIG_METRIC_DATE_EXPOSURE_PRIO_EPSILON_KEY));

    assertThat(metricsRemoteConfig.keysUploadedVaccineStatusPrioSamplingRate()).isEqualTo(
        remoteJsonObj.get(
            PrivateAnalyticsMetricsRemoteConfig
                .CONFIG_METRIC_KEYS_UPLOADED_VACCINE_STATUS_SAMPLING_PROB_KEY));
    assertThat(metricsRemoteConfig.keysUploadedVaccineStatusPrioEpsilon()).isEqualTo(remoteJsonObj
        .get(
            PrivateAnalyticsMetricsRemoteConfig
                .CONFIG_METRIC_KEYS_UPLOADED_VACCINE_STATUS_PRIO_EPSILON_KEY));

    assertThat(metricsRemoteConfig.keysUploadedAfterNotificationPrioSamplingRate()).isEqualTo(
        remoteJsonObj.get(
            PrivateAnalyticsMetricsRemoteConfig
                .CONFIG_METRIC_KEYS_UPLOADED_AFTER_NOTIFICATION_SAMPLING_PROB_KEY));
    assertThat(metricsRemoteConfig.keysUploadedAfterNotificationPrioEpsilon())
        .isEqualTo(remoteJsonObj
            .get(
                PrivateAnalyticsMetricsRemoteConfig
                    .CONFIG_METRIC_KEYS_UPLOADED_AFTER_NOTIFICATION_PRIO_EPSILON_KEY));

    assertThat(metricsRemoteConfig.periodicExposureNotificationBiweeklyPrioSamplingRate())
        .isEqualTo(
            remoteJsonObj.get(
                PrivateAnalyticsMetricsRemoteConfig
                    .CONFIG_METRIC_PERIODIC_EXPOSURE_NOTIFICATION_BIWEEKLY_SAMPLING_PROB_KEY));
    assertThat(metricsRemoteConfig.periodicExposureNotificationBiweeklyPrioEpsilon())
        .isEqualTo(remoteJsonObj
            .get(
                PrivateAnalyticsMetricsRemoteConfig
                    .CONFIG_METRIC_PERIODIC_EXPOSURE_NOTIFICATION_BIWEEKLY_PRIO_EPSILON_KEY));

    assertThat(metricsRemoteConfig.dateExposureBiweeklyPrioSamplingRate())
        .isEqualTo(
            remoteJsonObj.get(
                PrivateAnalyticsMetricsRemoteConfig
                    .CONFIG_METRIC_DATE_EXPOSURE_BIWEEKLY_SAMPLING_PROB_KEY));
    assertThat(metricsRemoteConfig.dateExposureBiweeklyPrioEpsilon())
        .isEqualTo(remoteJsonObj
            .get(
                PrivateAnalyticsMetricsRemoteConfig
                    .CONFIG_METRIC_DATE_EXPOSURE_BIWEEKLY_PRIO_EPSILON_KEY));

    assertThat(metricsRemoteConfig.keysUploadedVaccineStatusBiweeklyPrioSamplingRate())
        .isEqualTo(
            remoteJsonObj.get(
                PrivateAnalyticsMetricsRemoteConfig
                    .CONFIG_METRIC_KEYS_UPLOADED_VACCINE_STATUS_BIWEEKLY_SAMPLING_PROB_KEY));
    assertThat(metricsRemoteConfig.keysUploadedVaccineStatusBiweeklyPrioEpsilon())
        .isEqualTo(remoteJsonObj
            .get(
                PrivateAnalyticsMetricsRemoteConfig
                    .CONFIG_METRIC_KEYS_UPLOADED_VACCINE_STATUS_BIWEEKLY_PRIO_EPSILON_KEY));
  }

  @Test
  public void testMissingRemoteJsonObj_usesDefaultCorrectly() {
    MetricsRemoteConfigs metricsRemoteConfig = privateAnalyticsMetricsRemoteConfig
        .convertToRemoteConfig(null);

    assertThat(metricsRemoteConfig).isEqualTo(MetricsRemoteConfigs.newBuilder().build());
  }
}