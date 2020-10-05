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

package com.google.android.apps.exposurenotification.notify;

import android.text.TextUtils;
import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity;
import org.threeten.bp.LocalDate;
import org.threeten.bp.ZoneOffset;

/**
 * Helper class for functions around the {@link DiagnosisEntity}.
 */
public final class DiagnosisEntityHelper {

  /**
   * If a verification code is set and has a long term token.
   */
  public static boolean hasVerified(DiagnosisEntity diagnosisEntity) {
    return !TextUtils.isEmpty(diagnosisEntity.getVerificationCode()) && !TextUtils
        .isEmpty(diagnosisEntity.getLongTermToken());
  }

  /**
   * Returns true if all information is entered for the onset date and the date itself is valid,
   * meaning that symptom onset date is within past 14 days, false otherwise.
   */
  public static boolean hasCompletedOnset(DiagnosisEntity diagnosisEntity, Clock clock) {
    switch (diagnosisEntity.getHasSymptoms()) {
      case YES:
        LocalDate onsetDate = diagnosisEntity.getOnsetDate();
        return onsetDate != null && isWithinLast14Days(clock, onsetDate);
        // fall through
      case NO:
      case WITHHELD:
        return true;
      case UNSET:
      default:
        return false;
    }
  }

  /** Returns true if the entered epoch time in millis is within last 14 days, false otherwise. */
  public static boolean isWithinLast14Days(Clock clock, long timeInMillis) {
    long minAllowedDateWithinLast14Days =
        LocalDate.now(ZoneOffset.UTC)
            .atStartOfDay(ZoneOffset.UTC)
            .minusDays(14)
            .toInstant()
            .toEpochMilli();
    return timeInMillis >= minAllowedDateWithinLast14Days && isNotInFuture(clock, timeInMillis);
  }

  /** Returns true if the entered epoch time in millis is not in the future, false otherwise. */
  public static boolean isNotInFuture(Clock clock, long timeInMillis) {
    return timeInMillis <= clock.currentTimeMillis();
  }

  private static boolean isWithinLast14Days(Clock clock, LocalDate localDate) {
    return isWithinLast14Days(
        clock, localDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli());
  }

  private DiagnosisEntityHelper() {
  }

}
