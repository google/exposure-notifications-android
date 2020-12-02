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

package com.google.android.apps.exposurenotification.privateanalytics;

import com.google.auto.value.AutoValue;


/**
 * A value class for holding firebase remote configs.
 */
@AutoValue
public abstract class RemoteConfigs {

  private static final boolean DEFAULT_ENABLED = false;
  private static final long DEFAULT_COLLECTION_FREQUENCY_HOURS = 24L;
  private static final boolean DEFAULT_DEVICE_ATTESTATION_REQUIRED = true;

  private static final double DEFAULT_NOTIFICATION_COUNT_METRIC_SAMPLING_RATE = 1.0;
  private static final double DEFAULT_NOTIFICATION_COUNT_METRIC_EPSILON = 12.0;

  private static final double DEFAULT_INTERACTION_COUNT_METRIC_SAMPLING_RATE = 1.0;
  private static final double DEFAULT_INTERACTION_COUNT_METRIC_EPSILON = 12.0;

  private static final double DEFAULT_RISK_SCORE_METRIC_SAMPLING_RATE = 1.0;
  private static final double DEFAULT_RISK_SCORE_COUNT_METRIC_EPSILON = 12.0;

  private static final String DEFAULT_PHA_CERTIFICATE = "dummy_cert";
  private static final String DEFAULT_FACILITATOR_CERTIFICATE = "dummy_cert";
  private static final String DEFAULT_PHA_ENCRYPTION_ID = "";
  private static final String DEFAULT_FACILITATOR_ENCRYPTION_ID = "";

  public abstract boolean enabled();

  public abstract long collectionFrequencyHours();

  public abstract boolean deviceAttestationRequired();

  public abstract double interactionCountPrioEpsilon();

  public abstract double interactionCountPrioSamplingRate();

  public abstract double notificationCountPrioEpsilon();

  public abstract double notificationCountPrioSamplingRate();

  public abstract double riskScorePrioEpsilon();

  public abstract double riskScorePrioSamplingRate();

  public abstract String phaCertificate();

  public abstract String facilitatorCertificate();

  public abstract String phaEncryptionKeyId();

  public abstract String facilitatorEncryptionKeyId();

  public static RemoteConfigs.Builder newBuilder() {
    return new AutoValue_RemoteConfigs.Builder()
        .setEnabled(DEFAULT_ENABLED)
        .setCollectionFrequencyHours(DEFAULT_COLLECTION_FREQUENCY_HOURS)
        .setDeviceAttestationRequired(DEFAULT_DEVICE_ATTESTATION_REQUIRED)
        .setNotificationCountPrioSamplingRate(DEFAULT_NOTIFICATION_COUNT_METRIC_SAMPLING_RATE)
        .setNotificationCountPrioEpsilon(DEFAULT_NOTIFICATION_COUNT_METRIC_EPSILON)
        .setInteractionCountPrioSamplingRate(DEFAULT_INTERACTION_COUNT_METRIC_SAMPLING_RATE)
        .setInteractionCountPrioEpsilon(DEFAULT_INTERACTION_COUNT_METRIC_EPSILON)
        .setRiskScorePrioSamplingRate(DEFAULT_RISK_SCORE_METRIC_SAMPLING_RATE)
        .setRiskScorePrioEpsilon(DEFAULT_RISK_SCORE_COUNT_METRIC_EPSILON)
        .setPhaCertificate(DEFAULT_PHA_CERTIFICATE)
        .setFacilitatorCertificate(DEFAULT_FACILITATOR_CERTIFICATE)
        .setPhaEncryptionKeyId(DEFAULT_PHA_ENCRYPTION_ID)
        .setFacilitatorEncryptionKeyId(DEFAULT_FACILITATOR_ENCRYPTION_ID);
  }

  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setEnabled(boolean value);

    public abstract Builder setCollectionFrequencyHours(long value);

    public abstract Builder setInteractionCountPrioEpsilon(double value);

    public abstract Builder setInteractionCountPrioSamplingRate(double value);

    public abstract Builder setNotificationCountPrioEpsilon(double value);

    public abstract Builder setNotificationCountPrioSamplingRate(double value);

    public abstract Builder setRiskScorePrioEpsilon(double value);

    public abstract Builder setRiskScorePrioSamplingRate(double value);

    public abstract Builder setPhaCertificate(String value);

    public abstract Builder setFacilitatorCertificate(String value);

    public abstract Builder setPhaEncryptionKeyId(String value);

    public abstract Builder setFacilitatorEncryptionKeyId(String value);

    public abstract Builder setDeviceAttestationRequired(boolean value);

    public abstract RemoteConfigs build();
  }
}
