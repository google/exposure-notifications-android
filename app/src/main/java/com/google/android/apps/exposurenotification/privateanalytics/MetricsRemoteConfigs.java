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

import com.google.auto.value.AutoValue;

/**
 * A value class for holding firebase remote configs.
 */
@AutoValue
public abstract class MetricsRemoteConfigs {

  private static final double DEFAULT_NOTIFICATION_COUNT_METRIC_SAMPLING_RATE = 1.0;
  private static final double DEFAULT_NOTIFICATION_COUNT_METRIC_EPSILON = 8.0;

  private static final double DEFAULT_INTERACTION_COUNT_METRIC_SAMPLING_RATE = 1.0;
  private static final double DEFAULT_INTERACTION_COUNT_METRIC_EPSILON = 8.0;

  private static final double DEFAULT_RISK_SCORE_METRIC_SAMPLING_RATE = 1.0;
  private static final double DEFAULT_RISK_SCORE_COUNT_METRIC_EPSILON = 8.0;

  private static final double DEFAULT_CODE_VERIFIED_METRIC_SAMPLING_RATE = 1.0;
  private static final double DEFAULT_CODE_VERIFIED_METRIC_EPSILON = 8.0;

  private static final double DEFAULT_CODE_VERIFIED_WITH_REPORT_TYPE_METRIC_SAMPLING_RATE = 1.0;
  private static final double DEFAULT_CODE_VERIFIED_WITH_REPORT_TYPE_METRIC_EPSILON = 10.2;

  private static final double DEFAULT_KEYS_UPLOADED_METRIC_SAMPLING_RATE = 1.0;
  private static final double DEFAULT_KEYS_UPLOADED_METRIC_EPSILON = 8.0;

  private static final double DEFAULT_KEYS_UPLOADED_WITH_REPORT_TYPE_METRIC_SAMPLING_RATE = 1.0;
  private static final double DEFAULT_KEYS_UPLOADED_WITH_REPORT_TYPE_METRIC_EPSILON = 10.2;

  private static final double DEFAULT_DATE_EXPOSURE_METRIC_SAMPLING_RATE = 1.0;
  private static final double DEFAULT_DATE_EXPOSURE_METRIC_EPSILON = 8.0;

  private static final double DEFAULT_KEYS_UPLOADED_VACCINE_STATUS_METRIC_SAMPLING_RATE = 1.0;
  private static final double DEFAULT_KEYS_UPLOADED_VACCINE_STATUS_METRIC_EPSILON = 8.0;

  private static final double DEFAULT_KEYS_UPLOADED_AFTER_NOTIFICATION_METRIC_SAMPLING_RATE = 1.0;
  private static final double DEFAULT_KEYS_UPLOADED_AFTER_NOTIFICATION_METRIC_EPSILON = 10.2;

  private static final double DEFAULT_PERIODIC_EXPOSURE_NOTIFICATION_BIWEEKLY_METRIC_SAMPLING_RATE = 1.0;
  private static final double DEFAULT_PERIODIC_EXPOSURE_NOTIFICATION_BIWEEKLY_METRIC_EPSILON = 10.2;

  public abstract double interactionCountPrioEpsilon();

  public abstract double interactionCountPrioSamplingRate();

  public abstract double notificationCountPrioEpsilon();

  public abstract double notificationCountPrioSamplingRate();

  public abstract double riskScorePrioEpsilon();

  public abstract double riskScorePrioSamplingRate();

  public abstract double codeVerifiedPrioEpsilon();

  public abstract double codeVerifiedPrioSamplingRate();

  public abstract double codeVerifiedWithReportTypePrioEpsilon();

  public abstract double codeVerifiedWithReportTypePrioSamplingRate();

  public abstract double keysUploadedPrioEpsilon();

  public abstract double keysUploadedPrioSamplingRate();

  public abstract double keysUploadedWithReportTypePrioEpsilon();

  public abstract double keysUploadedWithReportTypePrioSamplingRate();

  public abstract double dateExposurePrioEpsilon();

  public abstract double dateExposurePrioSamplingRate();

  public abstract double keysUploadedVaccineStatusPrioEpsilon();

  public abstract double keysUploadedVaccineStatusPrioSamplingRate();

  public abstract double keysUploadedAfterNotificationPrioEpsilon();

  public abstract double keysUploadedAfterNotificationPrioSamplingRate();

  public abstract double periodicExposureNotificationBiweeklyPrioEpsilon();

  public abstract double periodicExposureNotificationBiweeklyPrioSamplingRate();

  public static MetricsRemoteConfigs.Builder newBuilder() {
    return new AutoValue_MetricsRemoteConfigs.Builder()
        .setNotificationCountPrioSamplingRate(DEFAULT_NOTIFICATION_COUNT_METRIC_SAMPLING_RATE)
        .setNotificationCountPrioEpsilon(DEFAULT_NOTIFICATION_COUNT_METRIC_EPSILON)
        .setInteractionCountPrioSamplingRate(DEFAULT_INTERACTION_COUNT_METRIC_SAMPLING_RATE)
        .setInteractionCountPrioEpsilon(DEFAULT_INTERACTION_COUNT_METRIC_EPSILON)
        .setRiskScorePrioSamplingRate(DEFAULT_RISK_SCORE_METRIC_SAMPLING_RATE)
        .setRiskScorePrioEpsilon(DEFAULT_RISK_SCORE_COUNT_METRIC_EPSILON)
        .setCodeVerifiedPrioSamplingRate(DEFAULT_CODE_VERIFIED_METRIC_SAMPLING_RATE)
        .setCodeVerifiedPrioEpsilon(DEFAULT_CODE_VERIFIED_METRIC_EPSILON)
        .setCodeVerifiedWithReportTypePrioSamplingRate(
            DEFAULT_CODE_VERIFIED_WITH_REPORT_TYPE_METRIC_SAMPLING_RATE)
        .setCodeVerifiedWithReportTypePrioEpsilon(
            DEFAULT_CODE_VERIFIED_WITH_REPORT_TYPE_METRIC_EPSILON)
        .setKeysUploadedPrioSamplingRate(DEFAULT_KEYS_UPLOADED_METRIC_SAMPLING_RATE)
        .setKeysUploadedPrioEpsilon(DEFAULT_KEYS_UPLOADED_METRIC_EPSILON)
        .setKeysUploadedWithReportTypePrioSamplingRate(
            DEFAULT_KEYS_UPLOADED_WITH_REPORT_TYPE_METRIC_SAMPLING_RATE)
        .setKeysUploadedWithReportTypePrioEpsilon(
            DEFAULT_KEYS_UPLOADED_WITH_REPORT_TYPE_METRIC_EPSILON)
        .setDateExposurePrioSamplingRate(DEFAULT_DATE_EXPOSURE_METRIC_SAMPLING_RATE)
        .setDateExposurePrioEpsilon(DEFAULT_DATE_EXPOSURE_METRIC_EPSILON)
        .setKeysUploadedVaccineStatusPrioSamplingRate(
            DEFAULT_KEYS_UPLOADED_VACCINE_STATUS_METRIC_SAMPLING_RATE)
        .setKeysUploadedVaccineStatusPrioEpsilon(
            DEFAULT_KEYS_UPLOADED_VACCINE_STATUS_METRIC_EPSILON)
        .setKeysUploadedAfterNotificationPrioSamplingRate(
            DEFAULT_KEYS_UPLOADED_AFTER_NOTIFICATION_METRIC_SAMPLING_RATE)
        .setKeysUploadedAfterNotificationPrioEpsilon(
            DEFAULT_KEYS_UPLOADED_AFTER_NOTIFICATION_METRIC_EPSILON)
        .setPeriodicExposureNotificationBiweeklyPrioSamplingRate(
            DEFAULT_PERIODIC_EXPOSURE_NOTIFICATION_BIWEEKLY_METRIC_SAMPLING_RATE)
        .setPeriodicExposureNotificationBiweeklyPrioEpsilon(
            DEFAULT_PERIODIC_EXPOSURE_NOTIFICATION_BIWEEKLY_METRIC_EPSILON);
  }

  @AutoValue.Builder
  public abstract static class Builder {

    public abstract MetricsRemoteConfigs.Builder setInteractionCountPrioEpsilon(double value);

    public abstract MetricsRemoteConfigs.Builder setInteractionCountPrioSamplingRate(double value);

    public abstract MetricsRemoteConfigs.Builder setNotificationCountPrioEpsilon(double value);

    public abstract MetricsRemoteConfigs.Builder setNotificationCountPrioSamplingRate(double value);

    public abstract MetricsRemoteConfigs.Builder setRiskScorePrioEpsilon(double value);

    public abstract MetricsRemoteConfigs.Builder setRiskScorePrioSamplingRate(double value);

    public abstract MetricsRemoteConfigs.Builder setCodeVerifiedPrioEpsilon(double value);

    public abstract MetricsRemoteConfigs.Builder setCodeVerifiedPrioSamplingRate(double value);

    public abstract MetricsRemoteConfigs.Builder setCodeVerifiedWithReportTypePrioEpsilon(
        double value);

    public abstract MetricsRemoteConfigs.Builder setCodeVerifiedWithReportTypePrioSamplingRate(
        double value);

    public abstract MetricsRemoteConfigs.Builder setKeysUploadedPrioEpsilon(double value);

    public abstract MetricsRemoteConfigs.Builder setKeysUploadedPrioSamplingRate(double value);

    public abstract MetricsRemoteConfigs.Builder setKeysUploadedWithReportTypePrioEpsilon(
        double value);

    public abstract MetricsRemoteConfigs.Builder setKeysUploadedWithReportTypePrioSamplingRate(
        double value);

    public abstract MetricsRemoteConfigs.Builder setDateExposurePrioEpsilon(double value);

    public abstract MetricsRemoteConfigs.Builder setDateExposurePrioSamplingRate(double value);

    public abstract MetricsRemoteConfigs.Builder setKeysUploadedVaccineStatusPrioEpsilon(
        double value);

    public abstract MetricsRemoteConfigs.Builder setKeysUploadedVaccineStatusPrioSamplingRate(
        double value);

    public abstract MetricsRemoteConfigs.Builder setKeysUploadedAfterNotificationPrioEpsilon(
        double value);

    public abstract MetricsRemoteConfigs.Builder setKeysUploadedAfterNotificationPrioSamplingRate(
        double value);

    public abstract MetricsRemoteConfigs.Builder setPeriodicExposureNotificationBiweeklyPrioEpsilon(
        double value);

    public abstract MetricsRemoteConfigs.Builder setPeriodicExposureNotificationBiweeklyPrioSamplingRate(
        double value);

    public abstract MetricsRemoteConfigs build();
  }

}
