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
import com.google.android.apps.exposurenotification.R;

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

  private static final String ONBOARDING_STATE_KEY = "ExposureNotificationSharedPreferences.ONBOARDING_STATE_KEY";
  private static final String NETWORK_MODE_KEY = "ExposureNotificationSharedPreferences.NETWORK_MODE_KEY";
  private static final String ATTENUATION_THRESHOLD_1_KEY = "ExposureNotificationSharedPreferences.ATTENUATION_THRESHOLD_1_KEY";
  private static final String ATTENUATION_THRESHOLD_2_KEY = "ExposureNotificationSharedPreferences.ATTENUATION_THRESHOLD_2_KEY";
  private static final String DOWNLOAD_SERVER_ADDRESS_KEY =
      "ExposureNotificationSharedPreferences.DOWNLOAD_SERVER_ADDRESS_KEY";
  private static final String UPLOAD_SERVER_ADDRESS_KEY =
      "ExposureNotificationSharedPreferences.UPLOAD_SERVER_ADDRESS_KEY";

  private final SharedPreferences sharedPreferences;

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

  public enum NetworkMode {
    // Uses live but test instances of the diagnosis key upload and download servers.
    TEST,
    // Uses local faked implementations of the diagnosis key uploads and downloads; no actual
    // network calls.
    FAKE
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

  public NetworkMode getNetworkMode(NetworkMode defaultMode) {
    return NetworkMode.valueOf(
        sharedPreferences.getString(NETWORK_MODE_KEY, defaultMode.toString()));
  }

  public void setNetworkMode(NetworkMode key) {
    sharedPreferences.edit().putString(NETWORK_MODE_KEY, key.toString()).commit();
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
}
