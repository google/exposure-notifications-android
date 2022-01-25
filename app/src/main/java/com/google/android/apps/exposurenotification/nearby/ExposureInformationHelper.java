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

package com.google.android.apps.exposurenotification.nearby;

import androidx.annotation.WorkerThread;
import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.android.apps.exposurenotification.riskcalculation.ExposureClassification;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.gms.nearby.exposurenotification.DailySummariesConfig;
import javax.inject.Inject;
import org.threeten.bp.Duration;
import org.threeten.bp.Instant;
import org.threeten.bp.LocalDate;
import org.threeten.bp.ZoneOffset;

/**
 * Utility class for obtaining various bits of information about the current exposure.
 */
public class ExposureInformationHelper {

  public static final Duration EXPOSURE_EXPIRATION_DEFAULT_THRESHOLD = Duration.ofDays(14L);

  private final ExposureNotificationSharedPreferences exposureNotificationSharedPreferences;
  private final DailySummariesConfig dailySummariesConfig;
  private final Clock clock;

  @Inject
  public ExposureInformationHelper(
      ExposureNotificationSharedPreferences exposureNotificationSharedPreferences,
      DailySummariesConfig dailySummariesConfig,
      Clock clock) {
    this.exposureNotificationSharedPreferences = exposureNotificationSharedPreferences;
    this.dailySummariesConfig = dailySummariesConfig;
    this.clock = clock;
  }

  /**
   * Checks if there is an outdated exposure present. An outdated exposure is the exposure happened
   * earlier than the PHA-set or default
   * ({@link ExposureInformationHelper#EXPOSURE_EXPIRATION_DEFAULT_THRESHOLD}) number of days ago.
   *
   * @return true if there is an outdated exposure present and false otherwise.
   */
  public boolean isOutdatedExposurePresent() {
    ExposureClassification exposureClassification = exposureNotificationSharedPreferences
        .getExposureClassification();
    return !isActiveExposure(exposureClassification) && isExposure(exposureClassification);
  }

  /**
   * Checks if there is an active exposure present. An active exposure is the most severe exposure
   * (i.e. exposure with the highest exposure score) happened within the last PHA-set or default
   * ({@link ExposureInformationHelper#EXPOSURE_EXPIRATION_DEFAULT_THRESHOLD}) number of days.
   *
   * @return true if there is an active exposure present and false otherwise.
   */
  public boolean isActiveExposurePresent() {
    ExposureClassification exposureClassification = exposureNotificationSharedPreferences
        .getExposureClassification();
    return isActiveExposure(exposureClassification) && isExposure(exposureClassification);
  }

  /**
   * Returns number of days until the current exposure expires. This method is meant to work only
   * with <b>actual and active</b> exposures.
   *
   * <p>The current exposure is actual and active if it has an exposure index, greater than
   * {@link ExposureClassification#NO_EXPOSURE_CLASSIFICATION_INDEX}, and if it happened within the
   * last PHA-set or default
   * ({@link ExposureInformationHelper#EXPOSURE_EXPIRATION_DEFAULT_THRESHOLD}) number of days.
   * For example, if the exposure expiration threshold is equal to 14 days and the current
   * exposure has an index 1 and happened 10 days ago, then this exposure is actual and active with
   * expiration in 5 days. So, this method will return 5.
   *
   * <p>For non-active or non-actual exposures, this method will always return 0.
   *
   * @return number of days until the current exposure expires if the current exposure is actual and
   * active or 0 otherwise.
   */
  public Duration getDaysUntilExposureExpires() {
    ExposureClassification exposureClassification = exposureNotificationSharedPreferences
        .getExposureClassification();
    if (!isExposure(exposureClassification) || !isActiveExposure(exposureClassification)) {
      return Duration.ofDays(0L);
    }
    // Calculate number of days since the start of an exposure.
    Duration daysFromStartOfExposure = getDaysFromStartOfExposure(exposureClassification);
    // Get the "days since exposure" threshold.
    Duration exposureExpirationThreshold = getExposureExpirationThresholdInDays();
    // Now calculate the days until the current exposure expires.
    long daysUntilExposureExpires =
        exposureExpirationThreshold.toDays() - daysFromStartOfExposure.toDays() + 1L;
    // We should never get a negative number here since we've checked above that we are calculating
    // daysSinceExposureExpires for an active exposure. However, just in case, ward off against
    // negatives.
    return Duration.ofDays(Math.max(daysUntilExposureExpires, 0L));
  }

  /**
   * Deletes the exposure information.
   */
  @WorkerThread
  public void deleteExposures() {
    exposureNotificationSharedPreferences.deleteExposureInformation();
  }

  private boolean isExposure(ExposureClassification exposureClassification) {
    return ExposureClassification.NO_EXPOSURE_CLASSIFICATION_INDEX !=
        exposureClassification.getClassificationIndex()
        || exposureNotificationSharedPreferences.getIsExposureClassificationRevoked();
  }

  private boolean isActiveExposure(ExposureClassification exposureClassification) {
    // Calculate number of days since the start of an exposure.
    Duration daysFromStartOfExposure = getDaysFromStartOfExposure(exposureClassification);
    // Get the exposure expiration threshold.
    Duration exposureExpirationThreshold = getExposureExpirationThresholdInDays();
    // Exposure is active if it started no earlier than the threshold.
    return daysFromStartOfExposure.toDays() <= exposureExpirationThreshold.toDays();
  }

  private Duration getDaysFromStartOfExposure(ExposureClassification exposureClassification) {
    Instant exposureStartOfDayUTC = LocalDate
        .ofEpochDay(exposureClassification.getClassificationDate())
        .atStartOfDay()
        .toInstant(ZoneOffset.UTC);
    Duration timeSinceExposure = Duration.between(exposureStartOfDayUTC, clock.now());
    if (timeSinceExposure.isNegative()) {
      return Duration.ofDays(1L);
    }
    return timeSinceExposure;
  }

  private Duration getExposureExpirationThresholdInDays() {
    Duration exposureExpirationThreshold =
        Duration.ofDays(dailySummariesConfig.getDaysSinceExposureThreshold());
    if (exposureExpirationThreshold.toDays() == 0) {
      // Exposure expiration threshold not set by the PHA. So, grab our default value.
      exposureExpirationThreshold = EXPOSURE_EXPIRATION_DEFAULT_THRESHOLD;
    }
    return exposureExpirationThreshold;
  }

}
