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
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.notify.ShareDiagnosisViewModel.Step;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity.Shared;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity.TestResult;
import com.google.common.base.Optional;

/**
 * Helper class for defining the "Share Diagnosis" flows and providing helper information for it.
 *
 * <pre>{@code
 * VIEW --------------------------------------\
 *                                            \
 *     /----------------------------\         \
 *    /                             v         v
 * BEGIN --> IS_CODE_NEED ---------> CODE --> UPLOAD --> SHARED
 *            \                      ^           \
 *             \--> GET_CODE -------/             \--> PRE-AUTH --\
 *              \                                  \              v
 *               \--> ALREADY_REPORTED              \---> VACCINATION
 *                                                   \
 *                                                    \----> NOT_SHARED
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
      case UPLOAD:
        if (isCodeStepSkippable(diagnosisEntity)) {
          return Step.BEGIN;
        }
        return Step.CODE;
      case BEGIN:
      case SHARED:
      case NOT_SHARED:
      case VIEW:
      case ALREADY_REPORTED:
      case PRE_AUTH:
      case VACCINATION:
      default:
        return null;
    }
  }

  /**
   * Computes the next step in the flow.
   *
   * <p>Note that the next step for {@link Step#UPLOAD} should be determined with the help of
   * the alternative overloaded {@link ShareDiagnosisFlowHelper#getNextStep} that takes an
   * additional boolean value for whether the diagnosis has been shared.
   *
   * @param currentStep             the current {@link Step} we are at
   * @param diagnosisEntity         the current {@link DiagnosisEntity} state
   * @param shareDiagnosisFlow      the current sharing flow type
   * @param showVaccinationQuestion whether to show a vaccination question or not (relevant for the
   *                                {@link Step#PRE_AUTH} only)
   * @param context                 the current context
   * @return the next step, null if there is no next step
   */
  @Nullable
  public static Step getNextStep(Step currentStep, DiagnosisEntity diagnosisEntity,
      ShareDiagnosisFlow shareDiagnosisFlow, boolean showVaccinationQuestion, Context context) {
    return getNextStep(currentStep, diagnosisEntity, shareDiagnosisFlow, showVaccinationQuestion,
        Optional.absent(), context);
  }

  /**
   * Computes the next step in the flow for the cases when we know (and need) the "shared" status of
   * the diagnosis.
   *
   * @param currentStep             the current {@link Step} we are at
   * @param diagnosisEntity         the current {@link DiagnosisEntity} state
   * @param shareDiagnosisFlow      the current sharing flow type
   * @param showVaccinationQuestion whether to show a vaccination question or not (relevant for the
   *                                {@link Step#PRE_AUTH} only)
   * @param isShared                whether the diagnosis has been shared or not
   * @param context                 the current context
   * @return the next step, null if there is no next step
   */
  @Nullable
  public static Step getNextStep(Step currentStep, DiagnosisEntity diagnosisEntity,
      ShareDiagnosisFlow shareDiagnosisFlow, boolean showVaccinationQuestion,
      boolean isShared, Context context) {
    return getNextStep(currentStep, diagnosisEntity, shareDiagnosisFlow, showVaccinationQuestion,
        Optional.of(isShared), context);
  }

  @Nullable
  private static Step getNextStep(Step currentStep, DiagnosisEntity diagnosisEntity,
      ShareDiagnosisFlow shareDiagnosisFlow, boolean showVaccinationQuestion,
      Optional<Boolean> isSharedOptional, Context context) {
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
        return Step.UPLOAD;
      case GET_CODE:
        return Step.CODE;
      case UPLOAD:
        if (!isSharedOptional.isPresent()) {
          return null;
        }
        if (!isSharedOptional.get()) {
          return Step.NOT_SHARED;
        }
        if (shouldDisplayPreAuthStep(diagnosisEntity, context)) {
          // Always move to the Pre-auth step first if it's enabled.
          return Step.PRE_AUTH;
        } else if (showVaccinationQuestion) {
          // If not, check if we should move to the Vaccination Question step.
          return Step.VACCINATION;
        }
        // If neither holds, just let a user know that their test result has been shared.
        return Step.SHARED;
      case PRE_AUTH:
        return showVaccinationQuestion ? Step.VACCINATION : null;
      case IS_CODE_NEEDED:
      case VIEW:
      case SHARED:
      case NOT_SHARED:
      case ALREADY_REPORTED:
      case VACCINATION:
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
   * Shall we ask the user if they want to pre-authorize TEKs release in the Self-Report flow
   * depending on the HA's config value.
   */
  public static boolean isPreAuthForSelfReportEnabled(Context context) {
    return context.getResources().getBoolean(R.bool.enx_preAuthorizeAfterSelfReport);
  }

  /**
   * Checks whether reading a verification code from the intercepted SMS is enabled.
   */
  public static boolean isSmsInterceptEnabled(Context context) {
    return context.getResources().getBoolean(R.bool.enx_enableTextMessageVerification)
        && !TextUtils.isEmpty(context.getString(R.string.enx_testVerificationNotificationBody));
  }

  /**
   * Calculates the maximum step a given diagnosis entity can be in. Useful for resuming a flow at
   * the right point.
   */
  public static Step getMaxStepForDiagnosisEntity(DiagnosisEntity diagnosisEntity) {
    if (Shared.SHARED.equals(diagnosisEntity.getSharedStatus())) {
      return Step.VIEW;
    } else {
      return Step.UPLOAD;
    }
  }

  /**
   * Returns the current step depending on the kind of diagnosis flow the user is in.
   * Useful for showing user feedback on progress so far.
   */
  public static int getNumberForCurrentStepInDiagnosisFlow(
      ShareDiagnosisFlow diagnosisFlow, Step currentStep) {
    switch (diagnosisFlow) {
      case DEFAULT:
      case DEEP_LINK:
        if (currentStep == Step.CODE) {
          return 1;
        } else if (currentStep == Step.UPLOAD) {
          return 2;
        }
      case SELF_REPORT:
        if (currentStep == Step.GET_CODE) {
          return 1;
        } else if (currentStep == Step.CODE) {
          return 2;
        } else if (currentStep == Step.UPLOAD){
          return 3;
        }
      default:
        return 0;
    }
  }

  /**
   * Returns the total number of steps of a given diagnosis entity can be in.
   * Useful for showing user feedback on progress so far.
   */
  public static int getTotalNumberOfStepsInDiagnosisFlow(ShareDiagnosisFlow diagnosisFlow) {
    if (diagnosisFlow == ShareDiagnosisFlow.SELF_REPORT) {
      return 3;
    } else {
      return 2;
    }
  }

  /**
   * Checks whether we should display the {@link Step#PRE_AUTH} after the diagnosis has been
   * shared.
   *
   * <p>Pre-auth step should be displayed only if all of the following apply:
   * <ul>
   *   <li> the current diagnosis carries a self-reported test result
   *   <li> the self-report feature is enabled
   *   <li> the pre-auth for self-report feature is enabled
   *   <li> the SMS intercept feature is enabled
   * </ul>
   * @param diagnosis the current diagnosis
   * @param context application context
   * @return true if we should display Pre-Auth screen and false otherwise.
   */
  public static boolean shouldDisplayPreAuthStep(DiagnosisEntity diagnosis, Context context) {
    return TestResult.USER_REPORT.equals(diagnosis.getTestResult())
        && ShareDiagnosisFlowHelper.isSelfReportEnabled(context)
        && ShareDiagnosisFlowHelper.isPreAuthForSelfReportEnabled(context)
        && ShareDiagnosisFlowHelper.isSmsInterceptEnabled(context);
  }

  /**
   * Returns the value of the self-report timeout interval in days.
   *
   * <p>This value should be provided by the Health Authority. Otherwise, the app build script
   * will set it to a default value of 30 days.
   */
  public static int getSelfReportTimeoutDays(Context context) {
    return context.getResources().getInteger(R.integer.enx_selfReportTimeoutDays);
  }

  private ShareDiagnosisFlowHelper() {
  }
}
