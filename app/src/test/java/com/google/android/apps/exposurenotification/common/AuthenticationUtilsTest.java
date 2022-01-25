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

import static com.google.common.truth.Truth.assertThat;
import static org.robolectric.Shadows.shadowOf;

import android.app.KeyguardManager;
import android.content.Context;
import android.hardware.biometrics.BiometricManager;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import androidx.biometric.BiometricPrompt;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowBiometricManager;
import org.robolectric.shadows.ShadowFingerprintManager;

@RunWith(AndroidJUnit4.class)
@HiltAndroidTest
@Config(application = HiltTestApplication.class,
    minSdk = VERSION_CODES.LOLLIPOP, maxSdk = VERSION_CODES.S)
public class AuthenticationUtilsTest {

  private Context context;
  private KeyguardManager keyguardManager;

  @Before
  public void setup() {
    context = ApplicationProvider.getApplicationContext();
    keyguardManager = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
  }

  @Test
  public void isAuthenticationAvailable_onlyDeviceCredentials_returnsTrue() {
    shadowOf(keyguardManager).setIsDeviceSecure(true);
    shadowOf(keyguardManager).setIsKeyguardSecure(true);
    maybeSetUpShadowManagers(false);

    assertThat(AuthenticationUtils.isAuthenticationAvailable(context)).isTrue();
  }

  @Test
  public void isAuthenticationAvailable_noBiometricNoDeviceCredentials_returnsFalse() {
    shadowOf(keyguardManager).setIsDeviceSecure(false);
    shadowOf(keyguardManager).setIsKeyguardSecure(false);
    maybeSetUpShadowManagers(false);

    assertThat(AuthenticationUtils.isAuthenticationAvailable(context)).isFalse();
  }

  @Test
  @Config(minSdk = VERSION_CODES.R)
  public void isAuthenticationAvailable_onlyBiometricAvailable_returnsTrue() {
    // We run this test only on R+ devices because "androidx.biometric.BiometricManager" uses the
    // framework BiometricManager on R+ devices but uses to FingerprintManagerCompat on
    // older versions. Currently there is no shadow implementation for FingerprintManagerCompat
    // in roboelectric, so we can't run this test on pre R devices now.
    // See setUpShadowBiometricManager() below for more information.
    shadowOf(keyguardManager).setIsDeviceSecure(false);
    shadowOf(keyguardManager).setIsKeyguardSecure(false);
    maybeSetUpShadowManagers(true);

    assertThat(AuthenticationUtils.isAuthenticationAvailable(context)).isTrue();
  }

  @Test
  @Config(minSdk = VERSION_CODES.M)
  public void isDeviceSecure_onlyDeviceSecure_returnsTrue() {
    // KeyguardManager.isDeviceSecure() is only available on M+ devices, use
    // KeyguardManager.isKeyguardSecure() instead for pre M devices.
    shadowOf(keyguardManager).setIsDeviceSecure(true);

    assertThat(AuthenticationUtils.isDeviceSecure(context)).isTrue();
  }

  @Test
  @Config(minSdk = VERSION_CODES.LOLLIPOP, maxSdk = VERSION_CODES.LOLLIPOP_MR1)
  public void isDeviceSecure_onlyKeyguardSecure_returnsTrue() {
    // KeyguardManager.isKeyguardSecure() is only used for pre M devices. On M+,
    // KeyguardManager.isDeviceSecure() is used.
    shadowOf(keyguardManager).setIsKeyguardSecure(true);

    assertThat(AuthenticationUtils.isDeviceSecure(context)).isTrue();
  }

  @Test
  public void isDeviceSecure_noDeviceCredentials_returnsFalse() {
    shadowOf(keyguardManager).setIsDeviceSecure(false);
    shadowOf(keyguardManager).setIsKeyguardSecure(false);

    assertThat(AuthenticationUtils.isDeviceSecure(context)).isFalse();
  }

  @Test
  public void isBiometricErrorSkippable_noBiometricHardware_returnsTrue() {
    int errorCode =  BiometricPrompt.ERROR_HW_NOT_PRESENT;

    assertThat(AuthenticationUtils.isBiometricErrorSkippable(errorCode)).isTrue();
  }

  @Test
  public void isBiometricErrorSkippable_userLockoutPermanent_returnsFalse() {
    int errorCode =  BiometricPrompt.ERROR_LOCKOUT_PERMANENT;

    assertThat(AuthenticationUtils.isBiometricErrorSkippable(errorCode)).isFalse();
  }

  // Should only be called for Android M+
  private void maybeSetUpShadowManagers(boolean canAuthenticate) {
    if (VERSION.SDK_INT >= VERSION_CODES.M) {
      if (VERSION.SDK_INT >= VERSION_CODES.Q) {
        setUpShadowBiometricManager(canAuthenticate);
      } else {
        setUpShadowFingerprintManager(canAuthenticate, canAuthenticate);
      }
    }
  }

  // Should only be called for Android M+
  private void setUpShadowFingerprintManager(
      boolean hardwareDetected, boolean fingerprintsEnrolled) {
    ShadowFingerprintManager shadowFingerprintManager =
        shadowOf((FingerprintManager) context.getSystemService(Context.FINGERPRINT_SERVICE));
    shadowFingerprintManager.setIsHardwareDetected(hardwareDetected);
    shadowFingerprintManager.setDefaultFingerprints(fingerprintsEnrolled ? 1 : 0);
  }

  // Should only be called for Android Q+
  private void setUpShadowBiometricManager(boolean canAuthenticate) {
    // We are using the BiometricManager from "android.hardware.biometrics.BiometricManager"
    // and not the one from "androidx.biometric.BiometricManager", this is because Roboelectric's
    // ShadowBiometricManager is a shadow of "android.hardware.biometrics.BiometricManager".
    // This should not be a problem because "androidx.biometric.BiometricManager" always
    // uses the correct framework version of BiometricManager
    // i.e "android.hardware.biometrics.BiometricManager".
    BiometricManager biometricManager =
        (BiometricManager) context.getSystemService(Context.BIOMETRIC_SERVICE);
    ((ShadowBiometricManager) Shadow.extract(biometricManager)).setCanAuthenticate(canAuthenticate);
  }

}