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
import android.view.View;
import android.widget.TextView;
import androidx.annotation.IdRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.viewbinding.ViewBinding;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.logging.Logger;
import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity.Shared;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity.TestResult;
import org.threeten.bp.LocalDate;
import org.threeten.bp.ZoneOffset;

/**
 * Helper class for functions around the {@link DiagnosisEntity}.
 */
public final class DiagnosisEntityHelper {
  private static final Logger logger = Logger.getLogger("DiagnosisEntityHelper");
  /**
   * If a verification code is set and has a long term token.
   */
  public static boolean hasVerified(DiagnosisEntity diagnosisEntity) {
    return !TextUtils.isEmpty(diagnosisEntity.getVerificationCode()) && !TextUtils
        .isEmpty(diagnosisEntity.getLongTermToken());
  }

  /**
   * Check if the diagnosis has been shared.
   */
  public static boolean hasBeenShared(@Nullable DiagnosisEntity diagnosisEntity) {
    return diagnosisEntity != null && Shared.SHARED.equals(diagnosisEntity.getSharedStatus());
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
        LocalDate.from(clock.now().atZone(ZoneOffset.UTC))
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

  /**
   * Populates a given {@link ViewBinding} from the "Share Diagnosis" flow with the relevant
   * DiagnosisEntity field values.
   */
  public static void populateViewBinding(ViewBinding binding, DiagnosisEntity diagnosisEntity,
      String onsetDate, boolean skipTravelStatusStep) {
    View rootView = binding.getRoot();

    TextView shareReviewStatus = rootView.findViewById(R.id.share_review_status);
    TextView shareReviewDate = rootView.findViewById(R.id.share_review_date);
    TextView shareReviewTravel = rootView.findViewById(R.id.share_review_travel);
    TextView shareReviewTravelSubtitle = rootView.findViewById(R.id.share_review_travel_subtitle);

    if (diagnosisEntity.getTestResult() != null) {
      switch (diagnosisEntity.getTestResult()) {
        case LIKELY:
          shareReviewStatus.setText(R.string.share_review_status_likely);
          break;
        case NEGATIVE:
          shareReviewStatus.setText(R.string.share_review_status_negative);
          break;
        case CONFIRMED:
        case USER_REPORT:
        default:
          shareReviewStatus.setText(R.string.share_review_status_confirmed);
          break;
      }
    } else {
      // We "shouldn't" get here, but in case, default to the most likely value rather
      // than fail.
      shareReviewStatus.setText(R.string.share_review_status_confirmed);
    }

    if (!skipTravelStatusStep) {
      shareReviewTravel.setVisibility(View.VISIBLE);
      shareReviewTravelSubtitle.setVisibility(View.VISIBLE);

      if (diagnosisEntity.getTravelStatus() != null) {
        switch (diagnosisEntity.getTravelStatus()) {
          case TRAVELED:
            shareReviewTravel.setText(R.string.share_review_travel_confirmed);
            break;
          case NOT_TRAVELED:
            shareReviewTravel.setText(R.string.share_review_travel_no_travel);
            break;
          case NO_ANSWER:
          case NOT_ATTEMPTED:
          default:
            shareReviewTravel.setText(R.string.share_review_travel_no_answer);
        }
      } else {
        shareReviewTravel.setText(R.string.share_review_travel_no_answer);
      }
    }

    // HasSymptoms cannot be null.
    switch (diagnosisEntity.getHasSymptoms()) {
      case YES:
        shareReviewDate.setText(onsetDate);
        break;
      case NO:
        shareReviewDate.setText(R.string.share_review_onset_no_symptoms);
        break;
      case WITHHELD:
      case UNSET:
      default:
        shareReviewDate.setText(R.string.share_review_onset_no_answer);
        break;
    }
  }

  public static @StringRes int getTestResultStringResource(DiagnosisEntity diagnosisEntity) {
    if (diagnosisEntity.getTestResult() != null) {
      switch (diagnosisEntity.getTestResult()) {
        case LIKELY:
          return R.string.share_upload_status_likely;
        case NEGATIVE:
          return R.string.share_upload_status_negative;
        case CONFIRMED:
        case USER_REPORT:
        default:
          return R.string.share_upload_status_confirmed;
      }
    } else {
      // We "shouldn't" get here, but in case, default to the most likely value rather
      // than fail.
      return R.string.share_review_status_confirmed;
    }
  }

  private static boolean isWithinLast14Days(Clock clock, LocalDate localDate) {
    return isWithinLast14Days(
        clock, localDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli());
  }

  public static @StringRes int getDiagnosisTypeStringResourceFromTestResult(TestResult testResult) {
    if (testResult == null) {
      logger.e("Unknown TestResult=null");
      return R.string.test_result_type_confirmed;
    } else {
      switch (testResult) {
        case LIKELY:
          return R.string.test_result_type_likely;
        case NEGATIVE:
          return R.string.test_result_type_negative;
        case CONFIRMED:
        case USER_REPORT:
          return R.string.test_result_type_confirmed;
        default:
          logger.e("Unknown TestResult=" + testResult);
          return R.string.test_result_type_confirmed;
      }
    }
  }

  private DiagnosisEntityHelper() {
  }

}
