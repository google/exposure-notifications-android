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

import android.text.TextUtils;
import android.util.Log;
import com.google.android.apps.exposurenotification.nearby.DailySummaryWrapper;
import com.google.android.gms.nearby.exposurenotification.ReportType;
import java.util.Arrays;
import java.util.List;

/**
 * Service that checks if the HA-provided classifications apply to a set of DailySummaries and
 * decides which date and classification is most important.
 */
public class DailySummaryRiskCalculator {

  private static final String TAG = "DailySummaryRiskCalc";

  ClassificationThreshold[] classificationThresholds;

  DailySummaryRiskCalculator(ClassificationThreshold[] classificationThresholds) {
    this.classificationThresholds = classificationThresholds;
  }

  /**
   * Apply the classifications provided by the health authority to the dailySummary objects.
   */
  public ExposureClassification classifyExposure(List<DailySummaryWrapper> dailySummaries) {

    Log.d(TAG, "Classifying dailySummaries [" + TextUtils.join(", ", dailySummaries)
        + "] using classificationThresholds " + Arrays.toString(classificationThresholds));

    // Find the global classification with the highest priority (the LOWEST classification index)
    ClassificationThreshold prioritizedClassification = null;
    long mostRecentDayWHighestClassification = 0;

    for (DailySummaryWrapper daySummary : dailySummaries) {
      for (ClassificationThreshold classificationThreshold : classificationThresholds) {
        if (doesClassificationApplyDailySummary(classificationThreshold, daySummary)) {
          // For strictly higher priority classifications, always update classification and date
          if (prioritizedClassification == null
              || classificationThreshold.classificationIndex
              < prioritizedClassification.classificationIndex) {
            prioritizedClassification = classificationThreshold;
            mostRecentDayWHighestClassification = daySummary.getDaysSinceEpoch();
            // For equal classifications, update date only if more recent
          } else if (prioritizedClassification.classificationIndex
              == classificationThreshold.classificationIndex) {
            mostRecentDayWHighestClassification =
                Math.max(mostRecentDayWHighestClassification, daySummary.getDaysSinceEpoch());
          }
        }
      }
    }

    if (prioritizedClassification == null) {
      return ExposureClassification.createNoExposureClassification();
    } else {
      return ExposureClassification.create(prioritizedClassification.classificationIndex,
          prioritizedClassification.classificationName, mostRecentDayWHighestClassification);
    }
  }

  /*
   * Check if the given ClassificationThreshold applies to a DailySummary
   */
  private boolean doesClassificationApplyDailySummary(ClassificationThreshold ct,
      DailySummaryWrapper ds) {
    return thresholdNonZero(ct.confirmedTestPerDaySumERVThreshold,
        ds.getSummaryDataForReportType(ReportType.CONFIRMED_TEST).getScoreSum())
        || thresholdNonZero(ct.clinicalDiagnosisPerDaySumERVThreshold,
        ds.getSummaryDataForReportType(ReportType.CONFIRMED_CLINICAL_DIAGNOSIS).getScoreSum())
        || thresholdNonZero(ct.selfReportPerDaySumERVThreshold,
        ds.getSummaryDataForReportType(ReportType.SELF_REPORT).getScoreSum())
        || thresholdNonZero(ct.recursivePerDaySumERVThreshold,
        ds.getSummaryDataForReportType(ReportType.RECURSIVE).getScoreSum())
        || thresholdNonZero(ct.perDaySumERVThreshold,
        ds.getSummaryData().getScoreSum())
        || thresholdNonZero(ct.perDayMaxERVThreshold,
        ds.getSummaryData().getMaximumScore())
        || thresholdNonZero(ct.weightedDurationAtAttenuationThreshold,
        ds.getSummaryData().getWeightedDurationSum());
  }

  private boolean thresholdNonZero(int threshold, double input) {
    return threshold != 0 && input >= threshold;
  }
}
