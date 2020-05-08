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
  private static final String SAFETYNET_API_KEY = "ExposureNotificationSharedPreferences.SAFETYNET_API_KEY";

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

  public ExposureNotificationSharedPreferences(Context context) {
    sharedPreferences = context.getSharedPreferences(SHARED_PREFERENCES_FILE, Context.MODE_PRIVATE);
  }

  public void setOnboardedState(boolean onboardedState) {
    sharedPreferences.edit().putInt(ONBOARDING_STATE_KEY,
        onboardedState ? OnboardingStatus.ONBOARDED.value() : OnboardingStatus.SKIPPED.value())
        .apply();
  }

  public OnboardingStatus getOnboardedState() {
    return OnboardingStatus.fromValue(sharedPreferences.getInt(ONBOARDING_STATE_KEY, 0));
  }

  public String getSafetyNetApiKey(String defaultKey) {
    return sharedPreferences.getString(SAFETYNET_API_KEY, defaultKey);
  }

  public void setSafetyNetApiKey(String key) {
    sharedPreferences.edit().putString(SAFETYNET_API_KEY, key).commit();
  }

}
