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
import android.util.Log;
import com.google.android.apps.exposurenotification.R;
import com.google.android.gms.nearby.exposurenotification.DailySummariesConfig;
import com.google.android.gms.nearby.exposurenotification.DailySummariesConfig.DailySummariesConfigBuilder;
import com.google.android.gms.nearby.exposurenotification.DiagnosisKeysDataMapping;
import com.google.android.gms.nearby.exposurenotification.Infectiousness;
import com.google.android.gms.nearby.exposurenotification.ReportType;
import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.components.ApplicationComponent;
import dagger.hilt.android.qualifiers.ApplicationContext;
import java.util.Arrays;

@Module
@InstallIn(ApplicationComponent.class)
public class RiskCalculationModule {

  private static final String TAG = "RiskCalculationModule";

  @Provides
  public DailySummaryRiskCalculator DailySummaryRiskCalculator(
      ClassificationThreshold[] classificationThresholds) {
    return new DailySummaryRiskCalculator(classificationThresholds);
  }

  @Provides
  public RevocationDetector provideRevocationDetector(DailySummariesConfig dailySummariesConfig) {
    return new RevocationDetector(dailySummariesConfig);
  }

  /*
   * The following code supplies all configuration objects required during risk-calculation based on
   * the Health Authority Config. This enables us to use an independent configuration during test.
   */

  /**
   * Provide config-based classification thresholds.
   */
  @Provides
  public ClassificationThreshold[] provideClassificationThresholdsArray(
      @ApplicationContext Context context) {
    return ClassificationThreshold.getClassificationThresholdsArrayFromConfig(context);
  }

  /**
   * Provide config-based DiagnosisKeyDataMapping.
   */
  @Provides
  public DiagnosisKeysDataMapping provideDiagnosisKeysDataMapping(
      @ApplicationContext Context context) {

    String symptomOnsetToInfectiousnessString =
        context.getResources().getString(R.string.enx_symptomOnsetToInfectiousnessMap);
    int reportTypeWhenMissing = context.getResources().getInteger(R.integer.enx_reportTypeNoneMap);

    DiagnosisKeysDataMapping diagnosisKeysDataMapping = DiagnosisKeyDataMappingHelper
        .createDiagnosisKeysDataMapping(symptomOnsetToInfectiousnessString, reportTypeWhenMissing);
    Log.d(TAG, "Created diagnosisKeysDataMapping: "+diagnosisKeysDataMapping);
    return diagnosisKeysDataMapping;
  }

  /*
   * In the configuration, weights are provided in integer percent, while the API expects
   * double values. We use a double factor to avoid unnecessary casts
   */
  private static final double WEIGHT_FACTOR = 0.01;

  /**
   * Provide config-based DailySummaryConfiguration for getDailySummaries()
   */
  @Provides
  public DailySummariesConfig provideDailySummaryConfig(@ApplicationContext Context context) {
    Resources res = context.getResources();
    DailySummariesConfig dailySummariesConfig = new DailySummariesConfigBuilder()
        /*
         * This puts each scan into four attenuation buckets: Immediate, Near, Medium and Other
         * Three Bluetooth attenuation thresholds (in dB) are used to define how exposure is divided
         * between the buckets. These buckets are each weighted with four customizable weights.
         */
        .setAttenuationBuckets(
            Arrays.asList( /*threshold in db*/
                res.getInteger(R.integer.enx_attenuationImmediateNearThreshold),
                res.getInteger(R.integer.enx_attenuationNearMedThreshold),
                res.getInteger(R.integer.enx_attenuationMedFarThreshold)),
            Arrays.asList( /*weight*/
                res.getInteger(R.integer.enx_attenuationImmediateWeight) * WEIGHT_FACTOR,
                res.getInteger(R.integer.enx_attenuationNearWeight) * WEIGHT_FACTOR,
                res.getInteger(R.integer.enx_attenuationMedWeight) * WEIGHT_FACTOR,
                res.getInteger(R.integer.enx_attenuationOtherWeight) * WEIGHT_FACTOR))
        /*
         * Each window gets assigned a Infectiousness (NONE, STANDARD, HIGH) depending on the days
         * since symptom onset. STANDARD and HIGH levels can be assigned a weight between 0 and 250%
         * (0.0 - 2.5), NONE is fixed at 0.0
         */
        .setInfectiousnessWeight(Infectiousness.STANDARD,
            res.getInteger(R.integer.enx_infectiousnessStandardWeight) * WEIGHT_FACTOR)
        .setInfectiousnessWeight(Infectiousness.HIGH,
            res.getInteger(R.integer.enx_infectiousnessHighWeight) * WEIGHT_FACTOR)
        /*
         * Each type of report can be assigned different weights. This is helpful
         * to e.g. prioritize confirmed clinical diagnoses over self-reported infections.
         * There are two additional ReportTypes that we don't configure here:
         *  - ReportType.REVOKED is used to revoke keys (and thus does not need a weight)
         *  - ReportType.RECURSIVE which is not supported by the configuration tool (yet)
         */
        .setReportTypeWeight(ReportType.CONFIRMED_TEST,
            res.getInteger(R.integer.enx_reportTypeConfirmedTestWeight) * WEIGHT_FACTOR)
        .setReportTypeWeight(ReportType.CONFIRMED_CLINICAL_DIAGNOSIS,
            res.getInteger(R.integer.enx_reportTypeConfirmedClinicalDiagnosisWeight)
                * WEIGHT_FACTOR)
        .setReportTypeWeight(ReportType.SELF_REPORT,
            res.getInteger(R.integer.enx_reportTypeSelfReportWeight) * WEIGHT_FACTOR)
        /*
         * Filtering: For how many days since exposure should exposure windows be included?
         * E.g. the value 10 only includes exposures from the last 10 days.
         */
        .setDaysSinceExposureThreshold(res.getInteger(R.integer.enx_daysSinceExposureThreshold))
        /*
         * Filtering: Remove windows with a score lower than x. This is not supplied by the
         * HA/configuration, so we don't set our own value and rather use defaults here.
         * .setMinimumWindowScore(0)
         */
        .build();
        Log.d(TAG, "Created dailySummaryConfig: "+dailySummariesConfig);
        return dailySummariesConfig;
  }

}
