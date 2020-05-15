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
import com.google.android.apps.exposurenotification.common.AppExecutors;
import com.google.android.apps.exposurenotification.common.TaskToFutureAdapter;
import com.google.android.gms.safetynet.SafetyNet;
import com.google.android.gms.safetynet.SafetyNetApi.AttestationResponse;
import com.google.common.base.Joiner;
import com.google.common.io.BaseEncoding;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListenableFuture;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.threeten.bp.Duration;

/**
 * Encapsulates getting attestation that the device we are currently running on is genuine, not
 * hacked, or otherwise compromised such that we should not trust it.
 *
 * <p>See https://developer.android.com/training/safetynet
 *
 * <p>In this example we compose the SafetyNet nonce from:
 *
 * <ul>
 *   <li>The package name of this app.
 *   <li>The transmission risk as a decimal
 *   <li>The diagnosis keys, sorted then comma separated, each in the format:
 *       <ul>
 *         <li>base64(key_bytes) + "." + interval_number + "." + interval_count
 *       </ul>
 *   <li>The comma separated sorted list of regions for which the keys are to be distributed, and
 *   <li>The name of the verification authority (e.g. a public health authority)
 * </ul>
 *
 * <p>All joined with the pipe ("|") character.
 */
class DeviceAttestor {
  private static final String TAG = "DeviceAttestor";
  // NOTE: The server expects padding.
  private static final BaseEncoding BASE64 = BaseEncoding.base64();
  private static final String HASH_ALGO = "SHA-256";
  private static final Duration TIMEOUT = Duration.ofSeconds(10);
  private static final Joiner COMMA_JOINER = Joiner.on(',');
  private static final Joiner PIPE_JOINER = Joiner.on('|');

  private final Context context;
  private final String safetyNetApiKey;

  DeviceAttestor(Context context) {
    this.context = context;
    // This api key must be set in gradle.properties.
    safetyNetApiKey = context.getString(R.string.safetynet_api_key);
  }

  /**
   * Obtains from SafetyNet an attestation token using the given keys and regions, plus the app
   * package name as the nonce to sign.
   */
  ListenableFuture<String> attestFor(
      List<DiagnosisKey> keys,
      List<String> regions,
      String verificationAuthority,
      int transmissionRisk) {
    Log.i(TAG, "Getting SafetyNet attestation.");
    String cleartext = cleartextFor(keys, regions, verificationAuthority, transmissionRisk);
    String nonce = BASE64.encode(sha256(cleartext));
    return safetyNetAttestationFor(nonce);
  }

  private ListenableFuture<String> safetyNetAttestationFor(String nonce) {
    return FluentFuture.from(
            TaskToFutureAdapter.getFutureWithTimeout(
                SafetyNet.getClient(context).attest(nonce.getBytes(), safetyNetApiKey),
                TIMEOUT.toMillis(),
                TimeUnit.MILLISECONDS,
                AppExecutors.getScheduledExecutor()))
        .transform(AttestationResponse::getJwsResult, AppExecutors.getBackgroundExecutor());
  }

  private String cleartextFor(
      List<DiagnosisKey> keys,
      List<String> regions,
      String verificationAuthority,
      int transmissionRisk) {
    List<String> parts = new ArrayList<>(5);
    // Order of the parts is important here. Don't shuffle them.
    parts.add(context.getPackageName());
    parts.add(String.valueOf(transmissionRisk));
    parts.add(keys(keys));
    parts.add(regions(regions));
    parts.add(verificationAuthority);
    return PIPE_JOINER.join(parts);
  }

  private String keys(List<DiagnosisKey> keys) {
    List<String> keysBase64 = new ArrayList<>();
    for (DiagnosisKey k : keys) {
      String keyInfo =
          BASE64.encode(k.getKeyBytes())
              + "."
              + k.getIntervalNumber()
              + "."
              + DiagnosisKey.DEFAULT_PERIOD;
      keysBase64.add(keyInfo);
    }
    Collections.sort(keysBase64);
    return COMMA_JOINER.join(keysBase64);
  }

  private String regions(List<String> regions) {
    // Careful: Collections.sort mutates the list in place, so make a defensive copy.
    List<String> mutableRegions = new ArrayList<>(regions);
    Collections.sort(mutableRegions);
    return COMMA_JOINER.join(mutableRegions);
  }

  private static byte[] sha256(String text) {
    try {
      MessageDigest sha256Digest = MessageDigest.getInstance(HASH_ALGO);
      byte[] textBytes = text.getBytes(StandardCharsets.UTF_8);
      sha256Digest.update(textBytes);
      return sha256Digest.digest();
    } catch (NoSuchAlgorithmException e) {
      // TODO: Some better exception.
      throw new RuntimeException(e);
    }
  }
}
