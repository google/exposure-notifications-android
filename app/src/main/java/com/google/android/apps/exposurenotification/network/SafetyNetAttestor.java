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
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.AppExecutors;
import com.google.android.apps.exposurenotification.common.TaskToFutureAdapter;
import com.google.android.gms.safetynet.SafetyNet;
import com.google.android.gms.safetynet.SafetyNetApi.AttestationResponse;
import com.google.common.base.Joiner;
import com.google.common.io.BaseEncoding;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;
import org.threeten.bp.Duration;

/**
 * Encapsulates getting an attestation of confidence that the device we are currently running on is
 * genuine, not compromised such that we should not trust it.
 *
 * <p>See https://developer.android.com/training/safetynet
 *
 * <p>In this example we compose the SafetyNet nonce from:
 *
 * <ul>
 *   <li>The package name of this app.
 *   <li>The diagnosis keys, sorted then comma separated, each in the format:
 *       <ul>
 *         <li>base64(key_bytes) + "." + interval_number + "." + interval_count + "." + transmisionRisk(0-8)
 *       </ul>
 *   <li>The comma separated sorted list of regions for which the keys are to be distributed, and
 *   <li>The verification code indicating positive diagnosis.
 * </ul>
 *
 * <p>All joined with the pipe ("|") character.
 */
class SafetyNetAttestor {
  private static final String TAG = "DeviceAttestor";
  // NOTE: The server expects base64 with padding.
  private static final BaseEncoding BASE64 = BaseEncoding.base64();
  private static final String HASH_ALGO = "SHA-256";
  private static final Duration TIMEOUT = Duration.ofSeconds(10);
  private static final Joiner COMMA_JOINER = Joiner.on(',');
  private static final Joiner PIPE_JOINER = Joiner.on('|');

  private final Context context;
  private final String safetyNetApiKey;

  SafetyNetAttestor(Context context) {
    this.context = context;
    // This api key must be set in gradle.properties.
    safetyNetApiKey = context.getString(R.string.safetynet_api_key);
  }

  /**
   * Obtains from SafetyNet an attestation token using the given keys and regions, plus the app
   * package name as the nonce to sign.
   */
  public ListenableFuture<String> attestFor(
      List<DiagnosisKey> keys,
      List<String> regions,
      String verificationCode) {
    Log.i(TAG, "Getting SafetyNet attestation.");
    String cleartext = cleartextFor(keys, regions, verificationCode);
    String nonce = BASE64.encode(sha256(cleartext));
    return safetyNetAttestationFor(nonce);
  }

  private ListenableFuture<String> safetyNetAttestationFor(String nonce) {
    ListenableFuture<String> attestation = FluentFuture.from(
            TaskToFutureAdapter.getFutureWithTimeout(
                SafetyNet.getClient(context).attest(nonce.getBytes(), safetyNetApiKey),
                TIMEOUT.toMillis(),
                TimeUnit.MILLISECONDS,
                AppExecutors.getScheduledExecutor()))
        .transform(AttestationResponse::getJwsResult, AppExecutors.getBackgroundExecutor());

    // Add a callback just for success/fail logging for debug purposes.
    Futures.addCallback(attestation, new FutureCallback<String>() {
      @Override
      public void onSuccess(@NullableDecl String result) {
        Log.d(TAG, "SafetyNet attestation succeeded.");
      }

      @Override
      public void onFailure(Throwable t) {
        Log.e(TAG, "SafetyNet attestation failed.", t);
      }
    }, AppExecutors.getLightweightExecutor());

    return attestation;
  }

  private String cleartextFor(
      List<DiagnosisKey> keys,
      List<String> regions,
      String verificationCode) {
    List<String> parts = new ArrayList<>(5);
    // Order of the parts is important here. Don't shuffle them, or the server may not be able to
    // verify the attestation.
    parts.add(context.getPackageName());
    parts.add(keyInfo(keys));
    parts.add(regions(regions));
    parts.add(verificationCode);
    return PIPE_JOINER.join(parts);
  }

  private String keyInfo(List<DiagnosisKey> keys) {
    List<String> keysBase64 = new ArrayList<>();
    for (DiagnosisKey k : keys) {
      String keyInfo =
          BASE64.encode(k.getKeyBytes())
              + "."
              + k.getIntervalNumber()
              + "."
              + k.getRollingPeriod()
              + "."
              + k.getTransmissionRisk();
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
