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
import android.util.Log;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import com.google.android.apps.exposurenotification.common.BooleanSharedPreferenceLiveData;
import com.google.android.apps.exposurenotification.common.ContainsSharedPreferenceLiveData;
import com.google.android.apps.exposurenotification.common.SharedPreferenceLiveData;
import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.android.apps.exposurenotification.riskcalculation.ExposureClassification;
import com.google.common.base.Optional;
import org.threeten.bp.Instant;

/**
 * Key value storage for ExposureNotification.
 *
 * <p>Partners should implement a daily TTL/expiry, for on-device storage of this data, and must
 * ensure compliance with all applicable laws and requirements with respect to encryption, storage,
 * and retention polices for end user data.
 */
public class ExposureNotificationSharedPreferences {

  private static final String TAG = "Preferences"; // Logging TAG

  private static final String SHARED_PREFERENCES_FILE =
      "ExposureNotificationSharedPreferences.SHARED_PREFERENCES_FILE";

  private static final String ONBOARDING_STATE_KEY =
      "ExposureNotificationSharedPreferences.ONBOARDING_STATE_KEY";
  private static final String SHARE_ANALYTICS_KEY =
      "ExposureNotificationSharedPreferences.SHARE_ANALYTICS_KEY";
  private static final String SHARE_PRIVATE_ANALYTICS_KEY =
      "ExposureNotificationSharedPreferences.SHARE_PRIVATE_ANALYTICS_KEY";
  private static final String KEY_SHARING_NETWORK_MODE_KEY =
      "ExposureNotificationSharedPreferences.KEY_SHARING_NETWORK_MODE_KEY";
  private static final String IS_ENABLED_CACHE_KEY =
      "ExposureNotificationSharedPreferences.IS_ENABLED_CACHE_KEY";
  private static final String ATTENUATION_THRESHOLD_1_KEY =
      "ExposureNotificationSharedPreferences.ATTENUATION_THRESHOLD_1_KEY";
  private static final String ATTENUATION_THRESHOLD_2_KEY =
      "ExposureNotificationSharedPreferences.ATTENUATION_THRESHOLD_2_KEY";
  private static final String EXPOSURE_CLASSIFICATION_INDEX_KEY =
      "ExposureNotificationSharedPreferences.EXPOSURE_CLASSIFICATION_INDEX_KEY";
  private static final String EXPOSURE_CLASSIFICATION_NAME_KEY =
      "ExposureNotificationSharedPreferences.EXPOSURE_CLASSIFICATION_NAME_KEY";
  private static final String EXPOSURE_CLASSIFICATION_DATE_KEY =
      "ExposureNotificationSharedPreferences.EXPOSURE_CLASSIFICATION_DATE_KEY";
  private static final String EXPOSURE_CLASSIFICATION_IS_REVOKED_KEY =
      "ExposureNotificationSharedPreferences.EXPOSURE_CLASSIFICATION_IS_REVOKED_KEY";
  private static final String EXPOSURE_CLASSIFICATION_IS_CLASSIFICATION_NEW_KEY =
      "ExposureNotificationSharedPreferences.EXPOSURE_CLASSIFICATION_IS_CLASSIFICATION_NEW_KEY";
  private static final String EXPOSURE_CLASSIFICATION_IS_DATE_NEW_KEY =
      "ExposureNotificationSharedPreferences.EXPOSURE_CLASSIFICATION_IS_DATE_NEW_KEY";
  private static final String ANALYTICS_LOGGING_LAST_TIMESTAMP =
      "ExposureNotificationSharedPreferences.ANALYTICS_LOGGING_LAST_TIMESTAMP";
  private static final String EXPOSURE_NOTIFICATION_LAST_SHOWN_TIME =
      "ExposureNotificationSharedPreferences.EXPOSURE_NOTIFICATION_LAST_SHOWN_TIME_KEY";
  private static final String EXPOSURE_NOTIFICATION_LAST_SHOWN_CLASSIFICATION =
      "ExposureNotificationSharedPreferences.EXPOSURE_NOTIFICATION_LAST_SHOWN_CLASSIFICATION_KEY";
  private static final String EXPOSURE_NOTIFICATION_LAST_INTERACTION_TIME =
      "ExposureNotificationSharedPreferences.EXPOSURE_NOTIFICATION_ACTIVE_INTERACTION_TIME_KEY";
  private static final String EXPOSURE_NOTIFICATION_LAST_INTERACTION_TYPE =
      "ExposureNotificationSharedPreferences.EXPOSURE_NOTIFICATION_ACTIVE_INTERACTION_TYPE_KEY";

  private final SharedPreferences sharedPreferences;
  private final Clock clock;
  private static AnalyticsStateListener analyticsStateListener;

  /**
   * Enum for onboarding status.
   */
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

  /**
   * Enum for "new" badge status.
   */
  public enum BadgeStatus {
    NEW(0),
    SEEN(1),
    DISMISSED(2);

    private final int value;

    BadgeStatus(int value) {
      this.value = value;
    }

    public int value() {
      return value;
    }

    public static BadgeStatus fromValue(int value) {
      switch (value) {
        case 1:
          return SEEN;
        case 2:
          return DISMISSED;
        default:
          return NEW;
      }
    }
  }

  /**
   * Enum for network handling.
   */
  public enum NetworkMode {
    // Uses live but test instances of the diagnosis verification, key upload and download servers.
    LIVE,
    // Bypasses diagnosis verification, key uploads and downloads; no actual network calls.
    // Useful to test other components of Exposure Notifications in isolation from the servers.
    DISABLED
  }

  /**
   * Enum for onboarding status.
   */
  public enum NotificationInteraction {
    UNKNOWN(0),
    CLICKED(1),
    DISMISSED(2);

    private final int value;

    NotificationInteraction(int value) {
      this.value = value;
    }

    public int value() {
      return value;
    }

    public static NotificationInteraction fromValue(int value) {
      switch (value) {
        case 1:
          return CLICKED;
        case 2:
          return DISMISSED;
        default:
          return UNKNOWN;
      }
    }
  }

  ExposureNotificationSharedPreferences(Context context, Clock clock) {
    // These shared preferences are stored in {@value Context#MODE_PRIVATE} to be made only
    // accessible by the app.
    sharedPreferences = context.getSharedPreferences(SHARED_PREFERENCES_FILE, Context.MODE_PRIVATE);
    this.clock = clock;
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

  public LiveData<Boolean> isOnboardingStateSetLiveData() {
    return Transformations.distinctUntilChanged(
        new ContainsSharedPreferenceLiveData(sharedPreferences, ONBOARDING_STATE_KEY));
  }

  public LiveData<Boolean> getAppAnalyticsStateLiveData() {
    return new BooleanSharedPreferenceLiveData(sharedPreferences, SHARE_ANALYTICS_KEY, false);
  }

  public void setAppAnalyticsState(boolean isEnabled) {
    sharedPreferences.edit().putBoolean(SHARE_ANALYTICS_KEY, isEnabled).commit();
    if (analyticsStateListener != null) {
      analyticsStateListener.onChanged(isEnabled);
    }
  }

  public synchronized void setAnalyticsStateListener(AnalyticsStateListener listener) {
    analyticsStateListener = listener;
  }

  public boolean getAppAnalyticsState() {
    return sharedPreferences.getBoolean(SHARE_ANALYTICS_KEY, false);
  }

  public Optional<Instant> maybeGetAnalyticsLoggingLastTimestamp() {
    if (!sharedPreferences.contains(ANALYTICS_LOGGING_LAST_TIMESTAMP)) {
      return Optional.absent();
    }
    return Optional.of(
        Instant.ofEpochMilli(sharedPreferences.getLong(ANALYTICS_LOGGING_LAST_TIMESTAMP, 0L)));
  }

  public void resetAnalyticsLoggingLastTimestamp() {
    sharedPreferences.edit().putLong(ANALYTICS_LOGGING_LAST_TIMESTAMP, clock.now().toEpochMilli())
        .commit();
  }

  public boolean isAppAnalyticsSet() {
    return sharedPreferences.contains(SHARE_ANALYTICS_KEY);
  }

  public LiveData<Boolean> getPrivateAnalyticsStateLiveData() {
    return new BooleanSharedPreferenceLiveData(sharedPreferences, SHARE_PRIVATE_ANALYTICS_KEY,
        false);
  }

  public boolean getPrivateAnalyticState() {
    return sharedPreferences.getBoolean(SHARE_PRIVATE_ANALYTICS_KEY, false);
  }

  public void setPrivateAnalyticsState(boolean isEnabled) {
    Log.d(TAG, "PrivateAnalyticsState changed, isEnabled= " + isEnabled);
    sharedPreferences.edit().putBoolean(SHARE_PRIVATE_ANALYTICS_KEY, isEnabled).commit();
  }

  public LiveData<Boolean> isPrivateAnalyticsStateSetLiveData() {
    return Transformations.distinctUntilChanged(
        new ContainsSharedPreferenceLiveData(sharedPreferences, SHARE_PRIVATE_ANALYTICS_KEY));
  }

  public boolean isPrivateAnalyticsStateSet() {
    return sharedPreferences.contains(SHARE_PRIVATE_ANALYTICS_KEY);
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

  public boolean getIsEnabledCache() {
    return sharedPreferences.getBoolean(IS_ENABLED_CACHE_KEY, false);
  }

  public void setIsEnabledCache(boolean isEnabled) {
    sharedPreferences.edit().putBoolean(IS_ENABLED_CACHE_KEY, isEnabled).apply();
  }

  public void setExposureClassification(ExposureClassification exposureClassification) {
    sharedPreferences
        .edit()
        .putInt(
            EXPOSURE_CLASSIFICATION_INDEX_KEY,
            exposureClassification.getClassificationIndex())
        .putString(
            EXPOSURE_CLASSIFICATION_NAME_KEY,
            exposureClassification.getClassificationName()
        )
        .putLong(
            EXPOSURE_CLASSIFICATION_DATE_KEY,
            exposureClassification.getClassificationDate()
        )
        .commit();
  }

  public ExposureClassification getExposureClassification() {
    return ExposureClassification.create(
        sharedPreferences.getInt(EXPOSURE_CLASSIFICATION_INDEX_KEY,
            ExposureClassification.NO_EXPOSURE_CLASSIFICATION_INDEX),
        sharedPreferences.getString(EXPOSURE_CLASSIFICATION_NAME_KEY,
            ExposureClassification.NO_EXPOSURE_CLASSIFICATION_NAME),
        sharedPreferences.getLong(EXPOSURE_CLASSIFICATION_DATE_KEY,
            ExposureClassification.NO_EXPOSURE_CLASSIFICATION_DATE));
  }

  public LiveData<ExposureClassification> getExposureClassificationLiveData() {
    return new SharedPreferenceLiveData<ExposureClassification>(
        this.sharedPreferences,
        EXPOSURE_CLASSIFICATION_INDEX_KEY,
        EXPOSURE_CLASSIFICATION_NAME_KEY,
        EXPOSURE_CLASSIFICATION_DATE_KEY) {
      @Override
      protected void updateValue() {
        setValue(getExposureClassification());
      }
    };
  }

  public void setIsExposureClassificationRevoked(boolean isRevoked) {
    sharedPreferences.edit().putBoolean(EXPOSURE_CLASSIFICATION_IS_REVOKED_KEY, isRevoked).commit();
  }

  public boolean getIsExposureClassificationRevoked() {
    return sharedPreferences.getBoolean(EXPOSURE_CLASSIFICATION_IS_REVOKED_KEY, false);
  }

  public LiveData<Boolean> getIsExposureClassificationRevokedLiveData() {
    return new BooleanSharedPreferenceLiveData(sharedPreferences,
        EXPOSURE_CLASSIFICATION_IS_REVOKED_KEY, false);
  }

  public void setIsExposureClassificationNewAsync(BadgeStatus badgeStatus) {
    sharedPreferences.edit()
        .putInt(EXPOSURE_CLASSIFICATION_IS_CLASSIFICATION_NEW_KEY, badgeStatus.value()).apply();
  }

  public void setExposureNotificationLastShownClassification(Instant exposureNotificationTime,
      int classificationIndex) {
    if (getPrivateAnalyticState()) {
      sharedPreferences.edit()
          .putInt(EXPOSURE_NOTIFICATION_LAST_SHOWN_CLASSIFICATION, classificationIndex)
          .putLong(EXPOSURE_NOTIFICATION_LAST_SHOWN_TIME, exposureNotificationTime.toEpochMilli())
          .apply();
    }
  }

  public int getExposureNotificationLastShownClassification() {
    return sharedPreferences.getInt(EXPOSURE_NOTIFICATION_LAST_SHOWN_CLASSIFICATION, 0);
  }

  public Instant getExposureNotificationLastShownTime() {
    return Instant
        .ofEpochMilli(sharedPreferences.getLong(EXPOSURE_NOTIFICATION_LAST_SHOWN_TIME, 0L));
  }

  public void clearLastShownExposureNotification() {
    sharedPreferences.edit()
        .remove(EXPOSURE_NOTIFICATION_LAST_SHOWN_TIME)
        .remove(EXPOSURE_NOTIFICATION_LAST_SHOWN_CLASSIFICATION)
        .apply();
  }

  public void setExposureNotificationLastInteraction(Instant exposureNotificationInteractionTime,
      NotificationInteraction interaction) {
    if (getPrivateAnalyticState()) {
      sharedPreferences.edit()
          .putLong(EXPOSURE_NOTIFICATION_LAST_INTERACTION_TIME,
              exposureNotificationInteractionTime.toEpochMilli())
          .putInt(EXPOSURE_NOTIFICATION_LAST_INTERACTION_TYPE, interaction.value()).apply();
    }
  }

  public Instant getExposureNotificationLastInteractionTime() {
    return Instant
        .ofEpochMilli(sharedPreferences.getLong(EXPOSURE_NOTIFICATION_LAST_INTERACTION_TIME, 0));
  }

  public NotificationInteraction getExposureNotificationLastInteractionType() {
    return NotificationInteraction.fromValue(sharedPreferences.getInt(
        EXPOSURE_NOTIFICATION_LAST_INTERACTION_TYPE,
        NotificationInteraction.UNKNOWN.value()));
  }

  public void clearLastShownExposureNotificationInteraction() {
    sharedPreferences.edit()
        .remove(EXPOSURE_NOTIFICATION_LAST_INTERACTION_TIME)
        .remove(EXPOSURE_NOTIFICATION_LAST_INTERACTION_TYPE)
        .apply();
  }

  public BadgeStatus getIsExposureClassificationNew() {
    return BadgeStatus.fromValue(
        sharedPreferences
            .getInt(EXPOSURE_CLASSIFICATION_IS_CLASSIFICATION_NEW_KEY, BadgeStatus.NEW.value()));
  }

  public LiveData<BadgeStatus> getIsExposureClassificationNewLiveData() {
    return new SharedPreferenceLiveData<BadgeStatus>(this.sharedPreferences,
        EXPOSURE_CLASSIFICATION_IS_CLASSIFICATION_NEW_KEY) {
      @Override
      protected void updateValue() {
        setValue(getIsExposureClassificationNew());
      }
    };
  }

  public void setIsExposureClassificationDateNewAsync(BadgeStatus badgeStatus) {
    sharedPreferences.edit()
        .putInt(EXPOSURE_CLASSIFICATION_IS_DATE_NEW_KEY, badgeStatus.value()).apply();
  }

  public BadgeStatus getIsExposureClassificationDateNew() {
    return BadgeStatus.fromValue(
        sharedPreferences
            .getInt(EXPOSURE_CLASSIFICATION_IS_DATE_NEW_KEY, BadgeStatus.NEW.value()));
  }

  public LiveData<BadgeStatus> getIsExposureClassificationDateNewLiveData() {
    return new SharedPreferenceLiveData<BadgeStatus>(this.sharedPreferences,
        EXPOSURE_CLASSIFICATION_IS_DATE_NEW_KEY) {
      @Override
      protected void updateValue() {
        setValue(getIsExposureClassificationDateNew());
      }
    };
  }

  public interface AnalyticsStateListener {

    void onChanged(boolean analyticsEnabled);
  }

}