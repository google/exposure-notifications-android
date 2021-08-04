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

import android.content.Context;
import android.text.TextUtils;
import androidx.annotation.Nullable;
import com.google.android.apps.exposurenotification.BuildConfig;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.BuildUtils;
import com.google.android.apps.exposurenotification.common.BuildUtils.Type;
import com.google.android.apps.exposurenotification.notify.ShareDiagnosisViewModel.Step;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity.HasSymptoms;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity.Shared;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity.TravelStatus;

/**
 * Helper class for defining the flows.
 *
 * <pre>{@code
 *       /-----------------------------------------------------------------\
 *      /------------------------------------------------\                 \
 *     /                                                 \                 \
 * VIEW --------------------------------------\          \                 \
 *                                            \          \                 \
 *     /----------------------------\         \          \                 \
 *    /                             v         v          v                 v
 * BEGIN --> IS_CODE_NEED ---------> CODE --> ONSET --> TRAVEL_STATUS --> REVIEW --> SHARED
 *            \                      ^ \         \        ^                ^  \
 *             \--> GET_CODE -------/   \         \------/----------------/    \
 *              \                        \              /                /      \------> NOT_SHARED
 *               \--> ALREADY_REPORTED    \----------- /                /
 *                                         \---------------------------/
 * }</pre>
 */
public class ShareDiagnosisFlowHelper {

  /** This enum expresses what type of sharing flow we are currently in. */
  enum ShareDiagnosisFlow {
    DEFAULT, DEEP_LINK, SELF_REPORT
  }

  /**
   * Computes the previous step in the flow.
   *
   * @param currentStep        the current {@link Step} we are at
   * @param diagnosisEntity    the current {@link DiagnosisEntity} state
   * @param shareDiagnosisFlow the current sharing flow type
   * @param context            the current context
   * @return the previous step, null if there is no previous step
   */
  @Nullable
  public static Step getPreviousStep(Step currentStep, DiagnosisEntity diagnosisEntity,
      ShareDiagnosisFlow shareDiagnosisFlow, Context context) {
    switch (currentStep) {
      case IS_CODE_NEEDED:
        return Step.BEGIN;
      case CODE:
        switch (shareDiagnosisFlow) {
          case DEEP_LINK:
            return Step.BEGIN;
          case SELF_REPORT:
            return Step.GET_CODE;
          default:
            // Even if Self-report is enabled, there's no need to show the "Did you receive a code?"
            // screen if we are in the flow for an already existing test result.
            return isSelfReportEnabled(context) && !isDiagnosisPresent(diagnosisEntity)
                ? Step.IS_CODE_NEEDED : Step.BEGIN;
        }
      case GET_CODE:
        return Step.IS_CODE_NEEDED;
      case ONSET:
        return isCodeStepSkippable(diagnosisEntity) ? Step.BEGIN : Step.CODE;
      case TRAVEL_STATUS:
        if (!diagnosisEntity.getIsServerOnsetDate()) {
          return Step.ONSET;
        }
        return isCodeStepSkippable(diagnosisEntity) ? Step.BEGIN : Step.CODE;
      case REVIEW:
        if (!isTravelStatusStepSkippable(context)) {
          return Step.TRAVEL_STATUS;
        }
        if (!diagnosisEntity.getIsServerOnsetDate()) {
          return Step.ONSET;
        }
        return isCodeStepSkippable(diagnosisEntity) ? Step.BEGIN : Step.CODE;
      case BEGIN:
      case SHARED:
      case NOT_SHARED:
      case VIEW:
      case ALREADY_REPORTED:
      default:
        return null;
    }
  }

  /**
   * Computes the next step in the flow.
   *
   * @param currentStep        the current {@link Step} we are at
   * @param diagnosisEntity    the current {@link DiagnosisEntity} state
   * @param shareDiagnosisFlow the current sharing flow type
   * @param context            the current context
   * @return the next step, null if there is no next step
   */
  @Nullable
  public static Step getNextStep(Step currentStep, DiagnosisEntity diagnosisEntity,
      ShareDiagnosisFlow shareDiagnosisFlow, Context context) {
    switch (currentStep) {
      case BEGIN:
        if (!isCodeStepSkippable(diagnosisEntity)) {
          // First always check whether we are in the deep link flow.
          if (shareDiagnosisFlow == ShareDiagnosisFlow.DEEP_LINK) {
            return Step.CODE;
          }
          // Even if Self-report is enabled, there's no need to show the "Did you receive a code?"
          // screen if we are in the flow for an already existing test result.
          return isSelfReportEnabled(context) && !isDiagnosisPresent(diagnosisEntity)
              ? Step.IS_CODE_NEEDED : Step.CODE;
        }
        // Fall through to skip CODE step.
      case CODE:
        if (diagnosisEntity.getIsServerOnsetDate()) {
          return isTravelStatusStepSkippable(context) ? Step.REVIEW : Step.TRAVEL_STATUS;
        } else {
          return Step.ONSET;
        }
      case GET_CODE:
        return Step.CODE;
      case ONSET:
        return isTravelStatusStepSkippable(context) ? Step.REVIEW : Step.TRAVEL_STATUS;
      case TRAVEL_STATUS:
        return Step.REVIEW;
      case IS_CODE_NEEDED:
      case VIEW:
      case REVIEW:
      case SHARED:
      case NOT_SHARED:
      case ALREADY_REPORTED:
      default:
        return null;
    }
  }

  /**
   * Checks if there is an existing test result in the upload flow.
   */
  public static boolean isDiagnosisPresent(@Nullable DiagnosisEntity diagnosisEntity) {
    long diagnosisId = diagnosisEntity != null ? diagnosisEntity.getId() : -1L;
    return diagnosisId > 0;
  }

  /**
   * Shall we skip CODE step for the given DiagnosisEntity.
   */
  public static boolean isCodeStepSkippable(DiagnosisEntity diagnosisEntity) {
    return diagnosisEntity.getIsCodeFromLink()
        && DiagnosisEntityHelper.hasVerified(diagnosisEntity)
        && diagnosisEntity.getSharedStatus() != Shared.SHARED;
  }

  /**
   * Shall we skip the TRAVEL_STATUS step depending on the HA's config value.
   */
  public static boolean isTravelStatusStepSkippable(Context context) {
    return TextUtils.isEmpty(context.getResources().getString(R.string.share_travel_detail));
  }

  /**
   * Shall we enable the Self-Report flow depending on the HA's config value.
   */
  public static boolean isSelfReportEnabled(Context context) {
    return !TextUtils.isEmpty(context.getString(R.string.self_report_website_url));
  }

  /**
   * Checks whether reading a verification code from the intercepted SMS is enabled.
   */
  public static boolean isSmsInterceptEnabled(Context context) {
    return context.getResources().getBoolean(R.bool.enx_enableTextMessageVerification)
        && !TextUtils.isEmpty(context.getString(R.string.enx_testVerificationNotificationBody))
        && BuildUtils.getType().equals(Type.V2);
  }

  /**
   * Calculates the maximum step a given diagnosis entity can be in. Useful for resuming a flow at
   * the right point.
   */
  public static Step getMaxStepForDiagnosisEntity(
      DiagnosisEntity diagnosisEntity, Context context) {
    if (Shared.SHARED.equals(diagnosisEntity.getSharedStatus())) {
      return Step.VIEW;
    } else if (HasSymptoms.UNSET.equals(diagnosisEntity.getHasSymptoms())
        || (HasSymptoms.YES.equals(diagnosisEntity.getHasSymptoms())
        && diagnosisEntity.getOnsetDate() == null)) {
      return Step.ONSET;
    } else if (!isTravelStatusStepSkippable(context)
        && TravelStatus.NOT_ATTEMPTED.equals(diagnosisEntity.getTravelStatus())) {
      return Step.TRAVEL_STATUS;
    } else {
      return Step.REVIEW;
    }
  }

  private ShareDiagnosisFlowHelper() {
  }
}
