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
import com.google.android.apps.exposurenotification.common.AppExecutors;
import com.google.android.apps.exposurenotification.common.TaskToFutureAdapter;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.gms.safetynet.SafetyNet;
import com.google.common.io.BaseEncoding;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListenableFuture;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.threeten.bp.Duration;

/**
 * Encapsulates getting attestation that the device we are currently running on is genuine, not
 * hacked, or otherwise compromised such that we should not trust it.
 *
 * <p>See https://developer.android.com/training/safetynet
 *
 * <p>In this example we compose the SafetyNet nonce from:
 * <ul>
 *   <li>The list of regions for which the keys are to be distributed, and
 *   <li>The diagnosis key data itself, and
 *   <li>The package name of this app.
 * </ul>
 *
 * There may be other useful ways to construct a nonce, depending on what elements of the key
 * submission the implementor's server considers important to validate.
 */
class DeviceAttestor {
  private static final String TAG = "DeviceAttestor";
  private static final BaseEncoding BASE64 = BaseEncoding.base64().omitPadding();
  private static final String DEFAULT_API_KEY = "REPLACE-ME";
  private static final String HASH_ALGO = "SHA-256";
  private static final Duration TIMEOUT = Duration.ofSeconds(10);

  private final Context context;
  private final ExposureNotificationSharedPreferences preferences;

  DeviceAttestor(Context context) {
    this.context = context;
    preferences = new ExposureNotificationSharedPreferences(context);
  }

  /**
   * Obtains from SafetyNet an attestation token using the given keys and regions, plus the app
   * package name as the nonce to sign.
   */
  ListenableFuture<String> attestFor(List<DiagnosisKey> keys, List<String> regions) {
    String cleartext = cleartextFor(keys, regions);
    String nonce = BASE64.encode(sha256(cleartext));
    return safetyNetAttestationFor(nonce);
  }

  private ListenableFuture<String> safetyNetAttestationFor(String nonce) {
    String apiKey = preferences.getSafetyNetApiKey(DEFAULT_API_KEY);

    return FluentFuture.from(
        TaskToFutureAdapter.getFutureWithTimeout(
            SafetyNet.getClient(context).attest(nonce.getBytes(), apiKey),
            TIMEOUT.toMillis(),
            TimeUnit.MILLISECONDS,
            AppExecutors.getScheduledExecutor()))
        .transform(response -> response.getJwsResult(), AppExecutors.getBackgroundExecutor());
  }

  private String cleartextFor(List<DiagnosisKey> keys, List<String> regions) {
    StringBuilder sb = new StringBuilder();
    sb.append(context.getPackageName());
    appendKeys(sb, keys);
    appendRegions(sb, regions);
    return sb.toString();
  }

  private void appendKeys(StringBuilder sb, List<DiagnosisKey> keys) {
    List<String> keysBase64 = new ArrayList<>();
    for (DiagnosisKey k : keys) {
      keysBase64.add(BASE64.encode(k.getKeyBytes()));
    }
    Collections.sort(keysBase64);
    for (String k : keysBase64) {
      sb.append(k);
    }
  }

  private void appendRegions(StringBuilder sb, List<String> regions) {
    // Careful: Collections.sort mutates the list in place, so make a defensive copy.
    List<String> mutableRegions = new ArrayList<>(regions);
    Collections.sort(mutableRegions);
    for (String r : regions) {
      // In case a caller sent us lowercase region codes (even though that wouldn't be ISO_Alpha-2).
      sb.append(r.toUpperCase(Locale.ENGLISH));
    }
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
