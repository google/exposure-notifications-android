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

import android.app.KeyguardManager;
import android.content.Context;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricManager.Authenticators;
import androidx.biometric.BiometricPrompt;

/**
 * Utilities for user authentication.
 */
public class AuthenticationUtils {

  public static final int BIO_AUTH_AUTHENTICATOR_FLAG =
      Authenticators.BIOMETRIC_WEAK | Authenticators.DEVICE_CREDENTIAL;

  private AuthenticationUtils() {}

  /**
   * Returns true if the device is secured with biometric or device credential.
   */
  public static boolean isAuthenticationAvailable(Context context) {
    BiometricManager biometricManager = BiometricManager.from(context);
    if (biometricManager.canAuthenticate(BIO_AUTH_AUTHENTICATOR_FLAG)
        == BiometricManager.BIOMETRIC_SUCCESS) {
      return true;
    }
    return isDeviceSecure(context);
  }

  /**
   * Returns true if the device is secured with a screen lock, e.g. PIN, pattern or password.
   */
  public static boolean isDeviceSecure(Context context) {
    KeyguardManager keyguardManager =
        (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
    if (VERSION.SDK_INT >= VERSION_CODES.M) {
      return keyguardManager != null && keyguardManager.isDeviceSecure();
    }
    return keyguardManager != null && keyguardManager.isKeyguardSecure();
  }

  /**
   * Returns true if the given biometric error (from onAuthenticationError) is due to
   * device setup or an hardware issue.
   */
  public static boolean isBiometricErrorSkippable(int errorCode) {
    switch (errorCode) {
      case BiometricPrompt.ERROR_HW_UNAVAILABLE:
      case BiometricPrompt.ERROR_UNABLE_TO_PROCESS:
      case BiometricPrompt.ERROR_NO_SPACE:
      case BiometricPrompt.ERROR_VENDOR:
      case BiometricPrompt.ERROR_NO_BIOMETRICS:
      case BiometricPrompt.ERROR_HW_NOT_PRESENT:
      case BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL:
        return true;
      default:
        return false;
    }
  }

}
