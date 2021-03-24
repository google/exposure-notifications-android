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

package com.google.android.libraries.privateanalytics;

import com.google.auto.value.AutoValue;


/**
 * A value class for holding remote configs.
 */
@AutoValue
public abstract class RemoteConfigs {

  private static final boolean DEFAULT_ENABLED = false;
  private static final long DEFAULT_COLLECTION_FREQUENCY_HOURS = 24L;
  private static final boolean DEFAULT_DEVICE_ATTESTATION_REQUIRED = true;

  private static final String DEFAULT_PHA_CERTIFICATE = "dummy_cert";
  private static final String DEFAULT_FACILITATOR_CERTIFICATE = "dummy_cert";
  private static final String DEFAULT_PHA_ENCRYPTION_ID = "";
  private static final String DEFAULT_FACILITATOR_ENCRYPTION_ID = "";

  public abstract boolean enabled();

  public abstract long collectionFrequencyHours();

  public abstract boolean deviceAttestationRequired();

  public abstract String phaCertificate();

  public abstract String facilitatorCertificate();

  public abstract String phaEncryptionKeyId();

  public abstract String facilitatorEncryptionKeyId();

  public static RemoteConfigs.Builder newBuilder() {
    return new AutoValue_RemoteConfigs.Builder()
        .setEnabled(DEFAULT_ENABLED)
        .setCollectionFrequencyHours(DEFAULT_COLLECTION_FREQUENCY_HOURS)
        .setDeviceAttestationRequired(DEFAULT_DEVICE_ATTESTATION_REQUIRED)
        .setPhaCertificate(DEFAULT_PHA_CERTIFICATE)
        .setFacilitatorCertificate(DEFAULT_FACILITATOR_CERTIFICATE)
        .setPhaEncryptionKeyId(DEFAULT_PHA_ENCRYPTION_ID)
        .setFacilitatorEncryptionKeyId(DEFAULT_FACILITATOR_ENCRYPTION_ID);
  }

  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setEnabled(boolean value);

    public abstract Builder setCollectionFrequencyHours(long value);

    public abstract Builder setPhaCertificate(String value);

    public abstract Builder setFacilitatorCertificate(String value);

    public abstract Builder setPhaEncryptionKeyId(String value);

    public abstract Builder setFacilitatorEncryptionKeyId(String value);

    public abstract Builder setDeviceAttestationRequired(boolean value);

    public abstract RemoteConfigs build();
  }
}
