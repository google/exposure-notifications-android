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

package com.google.android.apps.exposurenotification.network;

import android.content.Context;
import android.util.Log;
import com.google.android.apps.exposurenotification.common.StringUtils;
import com.google.auto.value.AutoValue;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;

/**
 * Consults a diagnosis verification service who we hope will provide a cryptographic attestation
 * that our positive diagnosis is genuine, and should be trusted by the Diagnosis Key Server.
 *
 * <p>Such trust enable the Diagnosis Key Server to publish our Temporary Exposure Keys as Diagnosis
 * Keys for users to attempt matching on.
 */
public class DiagnosisAttestor {
  private static final String TAG = "DiagnosisAttestor";
  private static final int FAKE_ATTESTATION_LENGTH = 1024; // TODO: Measure from a real payload

  private final Context context;

  DiagnosisAttestor(Context context) {
    this.context = context;
  }

  ListenableFuture<Attestation> attestFor(
      List<DiagnosisKey> keys, List<String> regions, String verificationCode) {
    Log.d(TAG, "Attempting to get attestation from the verification server.");
    // TODO: make a real call to a real diagnosis verification server.
    return Futures.immediateFuture(
        DiagnosisAttestor.Attestation.newBuilder()
            .setToken(StringUtils.randomBase64Data(FAKE_ATTESTATION_LENGTH))
            .build());
  }

  /**
   * A value class representing the attestation made by the verification server that our diagnosis
   * is genuine.
   *
   * <p>Includes the cryptographic token as well as an "overlay" which carries adjustments to the
   * key and risk data we provided. This helps the Health Authority's verification server take
   * responsibility for the risk represented by our diagnosis.
   */
  @AutoValue
  abstract static class Attestation {
    abstract String token();

    abstract Overlay overlay();

    public static Attestation.Builder newBuilder() {
      return new AutoValue_DiagnosisAttestor_Attestation.Builder()
          .setOverlay(Overlay.newBuilder().build());
    }

    @AutoValue.Builder
    public abstract static class Builder {
      public abstract Builder setToken(String token);

      public abstract Builder setOverlay(Overlay overlay);

      public abstract Attestation build();
    }
  }

  @AutoValue
  abstract static class Overlay {

    public static Overlay.Builder newBuilder() {
      return new AutoValue_DiagnosisAttestor_Overlay.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
      public abstract Overlay build();
    }
  }
}
