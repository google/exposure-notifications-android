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
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity.HasSymptoms;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity.Shared;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity.TravelStatus;

/**
 * Helper class for defining the flows.
 *
 * <pre>{@code
 *       /-----------------------------------------\
 *      /------------------------\                 \
 *     /                         \                 \
 * VIEW --------------\          \                 \
 *                    v          v                 v
 * BEGIN --> CODE --> ONSET --> TRAVEL_STATUS --> REVIEW --> SHARED
 *           \         \            ^               ^  \
 *            \         \----------/---------------/    \
 *             \                  /               /      \------> NOT_SHARED
 *              \--------------- /               /
 *               \------------------------------/
 * }</pre>
 */
public class ShareDiagnosisFlowHelper {

  /**
   * Computes the previous step in the flow.
   *
   * @param currentStep     the current {@link Step} we are at
   * @param diagnosisEntity the current {@link DiagnosisEntity} state
   * @return the previous step, null if there is no previous step
   */
  @Nullable
  public static Step getPreviousStep(
      Step currentStep, DiagnosisEntity diagnosisEntity, Context context) {
    switch (currentStep) {
      case CODE:
        return Step.BEGIN;
      case ONSET:
        return isCodeStepSkippable(diagnosisEntity) ? Step.BEGIN : Step.CODE;
      case TRAVEL_STATUS:
        if (diagnosisEntity.getIsServerOnsetDate()) {
          return isCodeStepSkippable(diagnosisEntity) ? Step.BEGIN : Step.CODE;
        } else {
          return Step.ONSET;
        }
      case REVIEW:
        if (isTravelStatusStepSkippable(context)) {
          return diagnosisEntity.getIsServerOnsetDate() ? Step.CODE : Step.ONSET;
        } else {
          return Step.TRAVEL_STATUS;
        }
      case BEGIN:
      case SHARED:
      case NOT_SHARED:
      case VIEW:
      default:
        return null;
    }
  }

  /**
   * Computes the next step in the flow.
   *
   * @param currentStep     the current {@link Step} we are at
   * @param diagnosisEntity the current {@link DiagnosisEntity} state
   * @return the next step, null if there is no next step
   */
  @Nullable
  public static Step getNextStep(
      Step currentStep, DiagnosisEntity diagnosisEntity, Context context) {
    switch (currentStep) {
      case BEGIN:
        if (!isCodeStepSkippable(diagnosisEntity)) {
          return Step.CODE;
        }
        // Fall through to skip CODE step.
      case CODE:
        if (diagnosisEntity.getIsServerOnsetDate()) {
          return isTravelStatusStepSkippable(context) ? Step.REVIEW : Step.TRAVEL_STATUS;
        } else {
          return Step.ONSET;
        }
      case ONSET:
        return isTravelStatusStepSkippable(context) ? Step.REVIEW : Step.TRAVEL_STATUS;
      case TRAVEL_STATUS:
        return Step.REVIEW;
      case VIEW:
      case REVIEW:
      case SHARED:
      case NOT_SHARED:
      default:
        return null;
    }
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
