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

package com.google.android.apps.exposurenotification.common;

import androidx.annotation.VisibleForTesting;
import com.google.common.io.BaseEncoding;
import java.security.SecureRandom;

/**
 * Utility class for {@link SecureRandom} related operations.
 */
public final class SecureRandomUtil {

  private static final BaseEncoding BASE64 = BaseEncoding.base64();

  // "The key should be at least 128 bits of random data generated on the device"
  // https://github.com/google/exposure-notifications-server/blob/main/docs/design/verification_protocol.md
  @VisibleForTesting
  static final int HMAC_KEY_LEN_BYTES = 128 / 8;
  // "The nonce must be exactly 256 bytes of random data, base64 encoded" (and generated on
  // the device).
  // https://github.com/google/exposure-notifications-verification-server/blob/main/docs/api.md#apiuser-report
  @VisibleForTesting
  static final int NONCE_LEN_BYTES = 256;

  /**
   * Generates an HMAC key that is used as the key for re-calculating the HMAC that was signed by
   * the PHA verification server. Must be base64 encoded.
   */
  public static String newHmacKey(SecureRandom secureRandom) {
    byte[] bytes = new byte[HMAC_KEY_LEN_BYTES];
    secureRandom.nextBytes(bytes);
    return BASE64.encode(bytes);
  }

  /**
   * Generates a nonce that shall be used by the verification server to attest that the same
   * device is being used to request and later send a verification code (as part of the
   * self-report flow). Must be base64 encoded.
   */
  public static String newNonce(SecureRandom secureRandom) {
    byte[] bytes = new byte[NONCE_LEN_BYTES];
    secureRandom.nextBytes(bytes);
    return BASE64.encode(bytes);
  }

  private SecureRandomUtil() {
  }

}
