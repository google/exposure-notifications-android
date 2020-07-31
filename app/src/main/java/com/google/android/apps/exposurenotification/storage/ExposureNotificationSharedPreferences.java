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

package com.google.android.apps.exposurenotification.storage;

import android.content.Context;
import android.content.SharedPreferences;
import org.threeten.bp.Duration;

/**
 * Key value storage for ExposureNotification.
 *
 * <p>Partners should implement a daily TTL/expiry, for on-device storage of this data, and must
 * ensure compliance with all applicable laws and requirements with respect to encryption, storage,
 * and retention polices for end user data.
 */
public class ExposureNotificationSharedPreferences {

  private static final String SHARED_PREFERENCES_FILE =
      "ExposureNotificationSharedPreferences.SHARED_PREFERENCES_FILE";

  private static final String ONBOARDING_STATE_KEY =
      "ExposureNotificationSharedPreferences.ONBOARDING_STATE_KEY";
  private static final String KEY_SHARING_NETWORK_MODE_KEY =
      "ExposureNotificationSharedPreferences.KEY_SHARING_NETWORK_MODE_KEY";
  private static final String VERIFICATION_NETWORK_MODE_KEY =
      "ExposureNotificationSharedPreferences.VERIFICATION_NETWORK_MODE_KEY";
  private static final String IS_ENABLED_CACHE_KEY =
      "ExposureNotificationSharedPreferences.IS_ENABLED_CACHE_KEY";
  private static final String ATTENUATION_THRESHOLD_1_KEY =
      "ExposureNotificationSharedPreferences.ATTENUATION_THRESHOLD_1_KEY";
  private static final String ATTENUATION_THRESHOLD_2_KEY =
      "ExposureNotificationSharedPreferences.ATTENUATION_THRESHOLD_2_KEY";
  private static final String DOWNLOAD_SERVER_ADDRESS_KEY =
      "ExposureNotificationSharedPreferences.DOWNLOAD_SERVER_ADDRESS_KEY";
  private static final String UPLOAD_SERVER_ADDRESS_KEY =
      "ExposureNotificationSharedPreferences.UPLOAD_SERVER_ADDRESS_KEY";
  private static final String DOWNLOAD_PAST_X_MINUTES_KEY =
      "ExposureNotificationSharedPreferences.DOWNLOAD_PAST_X_MINUTES_KEY";
  private static final String VERIFICATION_SERVER_ADDRESS_KEY_1 =
      "ExposureNotificationSharedPreferences.VERIFICATION_SERVER_ADDRESS_KEY";
  private static final String VERIFICATION_SERVER_ADDRESS_KEY_2 =
      "ExposureNotificationSharedPreferences.VERIFICATION_SERVER_ADDRESS_KEY_2";

  private final SharedPreferences sharedPreferences;

  /** Enum for onboarding status. */
  public enum OnboardingStatus {
    UNKNOWN(0),
    ONBOARDED(1),
    SKIPPED(2);

    private final int value;

    OnboardingStatus(int value) {
      this.value = value;
    }

    public int value() {
      return value;
    }

    public static OnboardingStatus fromValue(int value) {
      switch (value) {
        case 1:
          return ONBOARDED;
        case 2:
          return SKIPPED;
        default:
          return UNKNOWN;
      }
    }
  }

  /** Enum for network handling. */
  public enum NetworkMode {
    // Uses live but test instances of the diagnosis verification, key upload and download servers.
    LIVE,
    // Bypasses diagnosis verification, key uploads and downloads; no actual network calls.
    // Useful to test other components of Exposure Notifications in isolation from the servers.
    DISABLED
  }

  public ExposureNotificationSharedPreferences(Context context) {
    // These shared preferences are stored in {@value Context#MODE_PRIVATE} to be made only
    // accessible by the app.
    sharedPreferences = context.getSharedPreferences(SHARED_PREFERENCES_FILE, Context.MODE_PRIVATE);
  }

  public void setOnboardedState(boolean onboardedState) {
    sharedPreferences
        .edit()
        .putInt(
            ONBOARDING_STATE_KEY,
            onboardedState ? OnboardingStatus.ONBOARDED.value() : OnboardingStatus.SKIPPED.value())
        .apply();
  }

  public OnboardingStatus getOnboardedState() {
    return OnboardingStatus.fromValue(sharedPreferences.getInt(ONBOARDING_STATE_KEY, 0));
  }

  public NetworkMode getKeySharingNetworkMode(NetworkMode defaultMode) {
    try {
      return NetworkMode.valueOf(
          sharedPreferences.getString(KEY_SHARING_NETWORK_MODE_KEY, defaultMode.toString()));
    } catch (IllegalArgumentException e) {
      // In case of enum value changes causing errors parsing existing stored string values.
      return NetworkMode.DISABLED;
    }
  }

  public void setKeySharingNetworkMode(NetworkMode key) {
    sharedPreferences.edit().putString(KEY_SHARING_NETWORK_MODE_KEY, key.toString()).commit();
  }

  public NetworkMode getVerificationNetworkMode(NetworkMode defaultMode) {
    try {
      return NetworkMode.valueOf(
          sharedPreferences.getString(VERIFICATION_NETWORK_MODE_KEY, defaultMode.toString()));
    } catch (IllegalArgumentException e) {
      // In case of enum value changes causing errors parsing existing stored string values.
      return NetworkMode.DISABLED;
    }
  }

  public void setVerificationNetworkMode(NetworkMode key) {
    sharedPreferences.edit().putString(VERIFICATION_NETWORK_MODE_KEY, key.toString()).commit();
  }

  public int getAttenuationThreshold1(int defaultThreshold) {
    return sharedPreferences.getInt(ATTENUATION_THRESHOLD_1_KEY, defaultThreshold);
  }

  public void setAttenuationThreshold1(int threshold) {
    sharedPreferences.edit().putInt(ATTENUATION_THRESHOLD_1_KEY, threshold).commit();
  }

  public int getAttenuationThreshold2(int defaultThreshold) {
    return sharedPreferences.getInt(ATTENUATION_THRESHOLD_2_KEY, defaultThreshold);
  }

  public void setAttenuationThreshold2(int threshold) {
    sharedPreferences.edit().putInt(ATTENUATION_THRESHOLD_2_KEY, threshold).commit();
  }

  public void clearUploadServerAddress() {
    sharedPreferences.edit().remove(DOWNLOAD_SERVER_ADDRESS_KEY).commit();
  }

  public String getUploadServerAddress(String defaultServerAddress) {
    return sharedPreferences.getString(DOWNLOAD_SERVER_ADDRESS_KEY, defaultServerAddress);
  }

  public void setUploadServerAddress(String serverAddress) {
    if (serverAddress.isEmpty()) {
      clearUploadServerAddress();
    } else {
      sharedPreferences.edit().putString(DOWNLOAD_SERVER_ADDRESS_KEY, serverAddress).commit();
    }
  }

  public void clearVerificationServerAddress1() {
    sharedPreferences.edit().remove(VERIFICATION_SERVER_ADDRESS_KEY_1).commit();
  }

  public String getVerificationServerAddress1(String defaultServerAddress) {
    return sharedPreferences.getString(VERIFICATION_SERVER_ADDRESS_KEY_1, defaultServerAddress);
  }

  public void setVerificationServerAddress1(String serverAddress) {
    if (serverAddress.isEmpty()) {
      clearUploadServerAddress();
    } else {
      sharedPreferences.edit().putString(VERIFICATION_SERVER_ADDRESS_KEY_1, serverAddress).commit();
    }
  }

  public void clearVerificationServerAddress2() {
    sharedPreferences.edit().remove(VERIFICATION_SERVER_ADDRESS_KEY_2).commit();
  }

  public String getVerificationServerAddress2(String defaultServerAddress) {
    return sharedPreferences.getString(VERIFICATION_SERVER_ADDRESS_KEY_2, defaultServerAddress);
  }

  public void setVerificationServerAddress2(String serverAddress) {
    if (serverAddress.isEmpty()) {
      clearUploadServerAddress();
    } else {
      sharedPreferences.edit().putString(VERIFICATION_SERVER_ADDRESS_KEY_2, serverAddress).commit();
    }
  }

  public void clearDownloadServerAddress() {
    sharedPreferences.edit().remove(UPLOAD_SERVER_ADDRESS_KEY).commit();
  }

  public String getDownloadServerAddress(String defaultServerAddress) {
    return sharedPreferences.getString(UPLOAD_SERVER_ADDRESS_KEY, defaultServerAddress);
  }

  public void setDownloadServerAddress(String serverAddress) {
    if (serverAddress.isEmpty()) {
      clearDownloadServerAddress();
    } else {
      sharedPreferences.edit().putString(UPLOAD_SERVER_ADDRESS_KEY, serverAddress).commit();
    }
  }

  public boolean getIsEnabledCache() {
    return sharedPreferences.getBoolean(IS_ENABLED_CACHE_KEY, false);
  }

  public void setIsEnabledCache(boolean isEnabled) {
    sharedPreferences.edit().putBoolean(IS_ENABLED_CACHE_KEY, isEnabled).apply();
  }

  public Duration getMaxDownloadAge(Duration defaultMaxAge) {
    return Duration.ofMinutes(
        sharedPreferences.getLong(DOWNLOAD_PAST_X_MINUTES_KEY, defaultMaxAge.toMinutes()));
  }

  public void setMaxDownloadAge(Duration maxAge) {
    sharedPreferences.edit().putLong(DOWNLOAD_PAST_X_MINUTES_KEY, maxAge.toMinutes()).apply();
  }
}
