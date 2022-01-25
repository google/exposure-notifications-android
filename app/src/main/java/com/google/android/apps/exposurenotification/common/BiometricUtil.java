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

import android.app.Activity;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.biometric.BiometricPrompt;
import androidx.biometric.BiometricPrompt.PromptInfo;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import com.google.android.apps.exposurenotification.R;

/**
 * Utility class for dealing with BiometricPrompt and BiometricPrompt.AuthenticationCallback
 */
public class BiometricUtil {

  private BiometricUtil() {} // Prevent instantiation

  private static BiometricPrompt.AuthenticationCallback getBiometricAuthenticationCallback(
      BiometricAuthenticationCallback authenticationCallback) {
    return new BiometricPrompt.AuthenticationCallback() {
      @Override
      public void onAuthenticationError(int errorCode,
          @NonNull CharSequence errString) {
        super.onAuthenticationError(errorCode, errString);

        if (AuthenticationUtils.isBiometricErrorSkippable(errorCode)) {
          /*
           * We should not get here as we've checked that biometric/device lock is available
           * before calling authenticate() but if there is a biometric setup issue on the
           * phone and we can't authenticate the device. We shouldn't stop the user
           * from accessing their share history.
           */
          authenticationCallback.onAuthenticationError(true);
        }
        /*
         * Do nothing, the dialog/device credentials screen will show the appropriate error
         * messages to the user. We got here because the user dismissed the lock screen
         * or the biometric dialog timed out after waiting for too long for the user input, or
         * the user got locked out because incorrect credentials were entered too many times.
         */
        authenticationCallback.onAuthenticationError(false);
      }

      @Override
      public void onAuthenticationSucceeded(
          @NonNull BiometricPrompt.AuthenticationResult result) {
        super.onAuthenticationSucceeded(result);
        authenticationCallback.onAuthenticationSucceeded();
      }

      @Override
      public void onAuthenticationFailed() {
        super.onAuthenticationFailed();
        authenticationCallback.onAuthenticationFailed();
      }
    };
  }

  /**
   * Creates biometric prompt. This should be called from a fragment.
   * This method is called for the user to authenticate when the share history button is clicked.
   * This method is also called in onViewCreated() to resume an ongoing authentication after the
   * activity is restarted, e.g a screen rotation.
   */
  public static BiometricPrompt createBiometricPrompt(Fragment fragment,
      BiometricAuthenticationCallback callback) {
    return new BiometricPrompt(fragment, getBiometricAuthenticationCallback(callback));
  }

  /**
   * Creates biometric prompt. This should be called from an activity.
   * This method is called for the user to authenticate when the share history button is clicked.
   * This method is also called in onCreate() to resume an ongoing authentication after the
   * activity is restarted, e.g a screen rotation.
   */
  public static BiometricPrompt createBiometricPrompt(FragmentActivity activity,
      BiometricAuthenticationCallback callback) {
    return new BiometricPrompt(activity, getBiometricAuthenticationCallback(callback));
  }

  /**
   * Ask the user to enter their password, pin, or pattern before accessing their share history.
   * If none is set up we will be notified in the callback below.
   */
  public static void startBiometricPromptAuthentication(Context context,
      BiometricPrompt biometricPrompt) {
    PromptInfo promptInfo = new PromptInfo.Builder()
        .setTitle(context.getResources()
            .getString(R.string.see_history_device_authentication_title))
        .setAllowedAuthenticators(AuthenticationUtils.BIO_AUTH_AUTHENTICATOR_FLAG)
        .build();
    biometricPrompt.authenticate(promptInfo);
  }

  /**
   * Listener interface that can be implemented to observe events from BiometricPrompt
   * during authentication.
   */
  public interface BiometricAuthenticationCallback {
    /**
     * Called when a biometric (e.g. fingerprint, face, etc.) is recognized, indicating
     * that the user has successfully authenticated.
     */
    void onAuthenticationSucceeded();

    /**
     * Called when an unrecoverable error has been encountered and authentication has stopped.
     * @param isBiometricErrorSkippable true if error is due to a hardware/device problem
     *        and users should not be blocked.
     */
    void onAuthenticationError(boolean isBiometricErrorSkippable);

    /**
     * Called when a biometric (e.g. fingerprint, face, etc.) is presented but not recognized
     * as belonging to the user.
     */
    void onAuthenticationFailed();
  }
}
