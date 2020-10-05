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

package com.google.android.apps.exposurenotification.riskcalculation;

import android.content.Context;
import android.content.res.Resources;
import com.google.android.apps.exposurenotification.R;
import com.google.common.base.MoreObjects;
import org.jetbrains.annotations.NotNull;

/**
 * Convenience class to get classifications thresholds as a list of objects.
 * This does not contain any localized strings (classificationName is only seen by HA and not used
 * in the UI)
 */
public class ClassificationThreshold {
  int classificationIndex;
  String classificationName;
  int confirmedTestPerDaySumERVThreshold;
  int clinicalDiagnosisPerDaySumERVThreshold;
  int selfReportPerDaySumERVThreshold;
  int recursivePerDaySumERVThreshold;
  int perDaySumERVThreshold;
  int perDayMaxERVThreshold;
  int weightedDurationAtAttenuationThreshold;

  /**
   * Conventional constructor to manually create risk thresholds. Mainly used for testing.
   */
  public ClassificationThreshold(int classificationIndex, String classificationName,
      int confirmedTestPerDaySumERVThreshold, int clinicalDiagnosisPerDaySumERVThreshold,
      int selfReportPerDaySumERVThreshold, int recursivePerDaySumERVThreshold,
      int perDaySumERVThreshold, int perDayMaxERVThreshold,
      int weightedDurationAtAttenuationThreshold) {
    this.classificationIndex = classificationIndex;
    this.classificationName = classificationName;
    this.confirmedTestPerDaySumERVThreshold = confirmedTestPerDaySumERVThreshold;
    this.clinicalDiagnosisPerDaySumERVThreshold = clinicalDiagnosisPerDaySumERVThreshold;
    this.selfReportPerDaySumERVThreshold = selfReportPerDaySumERVThreshold;
    this.recursivePerDaySumERVThreshold = recursivePerDaySumERVThreshold;
    this.perDaySumERVThreshold = perDaySumERVThreshold;
    this.perDayMaxERVThreshold = perDayMaxERVThreshold;
    this.weightedDurationAtAttenuationThreshold = weightedDurationAtAttenuationThreshold;
  }

  /**
   * Create classification threshold from HA config
   * @param context: Context to fetch resources
   * @param classificationIndex: Specifies which threshold to get
   */
  private ClassificationThreshold(Context context, int classificationIndex) {
    this.classificationIndex = classificationIndex;
    Resources res = context.getResources();

    switch (classificationIndex) {
      case 1:
        this.classificationName = res.getString(R.string.enx_classificationName_1);
        this.confirmedTestPerDaySumERVThreshold =
            res.getInteger(R.integer.enx_confirmedTestPerDaySumERVThreshold_1);
        this.clinicalDiagnosisPerDaySumERVThreshold =
            res.getInteger(R.integer.enx_clinicalDiagnosisPerDaySumERVThreshold_1);
        this.selfReportPerDaySumERVThreshold =
            res.getInteger(R.integer.enx_selfReportPerDaySumERVThreshold_1);
        this.recursivePerDaySumERVThreshold =
            res.getInteger(R.integer.enx_recursivePerDaySumERVThreshold_1);
        this.perDaySumERVThreshold = res.getInteger(R.integer.enx_perDaySumERVThreshold_1);
        this.perDayMaxERVThreshold = res.getInteger(R.integer.enx_perDayMaxERVThreshold_1);
        this.weightedDurationAtAttenuationThreshold =
            res.getInteger(R.integer.enx_weightedDurationAtAttenuationThreshold_1);
        break;

      case 2:
        this.classificationName = res.getString(R.string.enx_classificationName_2);
        this.confirmedTestPerDaySumERVThreshold =
            res.getInteger(R.integer.enx_confirmedTestPerDaySumERVThreshold_2);
        this.clinicalDiagnosisPerDaySumERVThreshold =
            res.getInteger(R.integer.enx_clinicalDiagnosisPerDaySumERVThreshold_2);
        this.selfReportPerDaySumERVThreshold =
            res.getInteger(R.integer.enx_selfReportPerDaySumERVThreshold_2);
        this.recursivePerDaySumERVThreshold =
            res.getInteger(R.integer.enx_recursivePerDaySumERVThreshold_2);
        this.perDaySumERVThreshold = res.getInteger(R.integer.enx_perDaySumERVThreshold_2);
        this.perDayMaxERVThreshold = res.getInteger(R.integer.enx_perDayMaxERVThreshold_2);
        this.weightedDurationAtAttenuationThreshold =
            res.getInteger(R.integer.enx_weightedDurationAtAttenuationThreshold_2);
        break;

      case 3:
        this.classificationName = res.getString(R.string.enx_classificationName_3);
        this.confirmedTestPerDaySumERVThreshold =
            res.getInteger(R.integer.enx_confirmedTestPerDaySumERVThreshold_3);
        this.clinicalDiagnosisPerDaySumERVThreshold =
            res.getInteger(R.integer.enx_clinicalDiagnosisPerDaySumERVThreshold_3);
        this.selfReportPerDaySumERVThreshold =
            res.getInteger(R.integer.enx_selfReportPerDaySumERVThreshold_3);
        this.recursivePerDaySumERVThreshold =
            res.getInteger(R.integer.enx_recursivePerDaySumERVThreshold_3);
        this.perDaySumERVThreshold = res.getInteger(R.integer.enx_perDaySumERVThreshold_3);
        this.perDayMaxERVThreshold = res.getInteger(R.integer.enx_perDayMaxERVThreshold_3);
        this.weightedDurationAtAttenuationThreshold =
            res.getInteger(R.integer.enx_weightedDurationAtAttenuationThreshold_3);
        break;

      case 4:
        this.classificationName = res.getString(R.string.enx_classificationName_4);
        this.confirmedTestPerDaySumERVThreshold =
            res.getInteger(R.integer.enx_confirmedTestPerDaySumERVThreshold_4);
        this.clinicalDiagnosisPerDaySumERVThreshold =
            res.getInteger(R.integer.enx_clinicalDiagnosisPerDaySumERVThreshold_4);
        this.selfReportPerDaySumERVThreshold =
            res.getInteger(R.integer.enx_selfReportPerDaySumERVThreshold_4);
        this.recursivePerDaySumERVThreshold =
            res.getInteger(R.integer.enx_recursivePerDaySumERVThreshold_4);
        this.perDaySumERVThreshold = res.getInteger(R.integer.enx_perDaySumERVThreshold_4);
        this.perDayMaxERVThreshold = res.getInteger(R.integer.enx_perDayMaxERVThreshold_4);
        this.weightedDurationAtAttenuationThreshold =
            res.getInteger(R.integer.enx_weightedDurationAtAttenuationThreshold_4);
        break;

      default:
        throw new
            IllegalArgumentException("classificationIndex must be between 1 and 4 (inclusive)");
    }
  }

  @NotNull
  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("classificationIndex", classificationIndex)
        .add("classificationName", classificationName)
        .add("confirmedTestPerDaySumERVThreshold", confirmedTestPerDaySumERVThreshold)
        .add("clinicalDiagnosisPerDaySumERVThreshold", clinicalDiagnosisPerDaySumERVThreshold)
        .add("selfReportPerDaySumERVThreshold", selfReportPerDaySumERVThreshold)
        .add("recursivePerDaySumERVThreshold", recursivePerDaySumERVThreshold)
        .add("perDaySumERVThreshold", perDaySumERVThreshold)
        .add("perDayMaxERVThreshold", perDayMaxERVThreshold)
        .add("weightedDurationAtAttenuationThreshold", weightedDurationAtAttenuationThreshold)
        .toString();
  }

  /**
   * Helper to automatically instantiate all config-based thresholds.
   * It instantiates the thresholds from the HA config in order of priority, with the
   * parameters ending in "*_1" (classificationIndex 1) being the most important
   */
  public static ClassificationThreshold[] getClassificationThresholdsArrayFromConfig(
      Context context) {
    return new ClassificationThreshold[] {
        new ClassificationThreshold(context, 1),
        new ClassificationThreshold(context, 2),
        new ClassificationThreshold(context, 3),
        new ClassificationThreshold(context, 4),
    };
  }

}