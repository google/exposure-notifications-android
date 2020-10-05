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

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.apps.exposurenotification.nearby.DailySummaryWrapper;
import com.google.android.apps.exposurenotification.nearby.DailySummaryWrapper.ExposureSummaryDataWrapper;
import com.google.android.apps.exposurenotification.testsupport.HAConfigObjects;
import com.google.android.gms.nearby.exposurenotification.ReportType;
import com.google.common.collect.ImmutableList;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;
import org.threeten.bp.LocalDate;
import org.threeten.bp.ZoneOffset;

@RunWith(AndroidJUnit4.class)
@HiltAndroidTest
@Config(application = HiltTestApplication.class)
public class DailySummaryRiskCalculatorTest {

  private static final LocalDate TODAY = LocalDate.now(ZoneOffset.UTC);
  /*
   * EXPOSURE_SUMMARY_DATA_SHORT/LONG are ExposureSummaryData objects with durations exactly on the
   * edge of the test config threshold that classifies the exposure length.
   * The config threshold is set to 2700 exposure seconds. 2699 thus still classifies as short,
   * while 2700 and more should be detected as a long exposure.
   */
  private final ExposureSummaryDataWrapper EXPOSURE_SUMMARY_DATA_SHORT =
      ExposureSummaryDataWrapper.newBuilder()
      .setWeightedDurationSum(2699.0)
      .setMaximumScore(2699.0)
      .setScoreSum(2699.0)
      .build();
  private final ExposureSummaryDataWrapper EXPOSURE_SUMMARY_DATA_LONG =
      ExposureSummaryDataWrapper.newBuilder()
      .setWeightedDurationSum(2700.0)
      .setMaximumScore(2700.0)
      .setScoreSum(2700.0)
      .build();

  private DailySummaryRiskCalculator dailySummaryRiskCalculator;

  @Before
  public void setUp() {
    this.dailySummaryRiskCalculator =
        new DailySummaryRiskCalculator(HAConfigObjects.CLASSIFICATION_THRESHOLDS_ARRAY);
  }

  /**
   * Test correct classifications on a single-day single-exposure inputs
   */
  @Test
  public void classifyExposure_noExposure_returnsNoExposure() {
    List<DailySummaryWrapper> input = ImmutableList.of();

    ExposureClassification result = dailySummaryRiskCalculator.classifyExposure(input);

    assertThat(result).isEqualTo(ExposureClassification.createNoExposureClassification());
  }

  @Test
  public void classifyExposure_unsupportedSelfReportedExposure_returnsNoExposure() {
    List<DailySummaryWrapper> input = ImmutableList.of(
        DailySummaryWrapper.newBuilder()
            .setDaysSinceEpoch((int)TODAY.toEpochDay())
            .setReportSummary(ReportType.SELF_REPORT, EXPOSURE_SUMMARY_DATA_LONG)
            .setSummaryData(EXPOSURE_SUMMARY_DATA_LONG)
            .build()
    );

    ExposureClassification result = dailySummaryRiskCalculator.classifyExposure(input);

    assertThat(result)
        .isEqualTo(ExposureClassification.createNoExposureClassification());
  }

  @Test
  public void classifyExposure_unsupportedRecursiveExposure_returnsNoExposure() {
    List<DailySummaryWrapper> input = ImmutableList.of(
        DailySummaryWrapper.newBuilder()
            .setDaysSinceEpoch((int)TODAY.toEpochDay())
            .setReportSummary(ReportType.RECURSIVE, EXPOSURE_SUMMARY_DATA_LONG)
            .setSummaryData(EXPOSURE_SUMMARY_DATA_LONG)
            .build()
    );

    ExposureClassification result = dailySummaryRiskCalculator.classifyExposure(input);

    assertThat(result)
        .isEqualTo(ExposureClassification.createNoExposureClassification());
  }

  @Test
  public void classifyExposure_longConfirmedExposure_returnsC1() {
    List<DailySummaryWrapper> input = ImmutableList.of(
        DailySummaryWrapper.newBuilder()
        .setDaysSinceEpoch((int)TODAY.toEpochDay())
        .setReportSummary(ReportType.CONFIRMED_TEST, EXPOSURE_SUMMARY_DATA_LONG)
        .setSummaryData(EXPOSURE_SUMMARY_DATA_LONG)
        .build()
    );

    ExposureClassification result = dailySummaryRiskCalculator.classifyExposure(input);

    assertThat(result)
        .isEqualTo(ExposureClassification.create(1, "Classification 1", TODAY.toEpochDay()));
  }

  @Test
  public void classifyExposure_shortConfirmedExposure_returnsC2() {
    List<DailySummaryWrapper> input = ImmutableList.of(
        DailySummaryWrapper.newBuilder()
            .setDaysSinceEpoch((int)TODAY.toEpochDay())
            .setReportSummary(ReportType.CONFIRMED_TEST, EXPOSURE_SUMMARY_DATA_SHORT)
            .setSummaryData(EXPOSURE_SUMMARY_DATA_SHORT)
            .build()
    );

    ExposureClassification result = dailySummaryRiskCalculator.classifyExposure(input);

    assertThat(result)
        .isEqualTo(ExposureClassification.create(2, "Classification 2", TODAY.toEpochDay()));
  }

  @Test
  public void classifyExposure_longLikelyExposure_returnsC3() {
    List<DailySummaryWrapper> input = ImmutableList.of(
        DailySummaryWrapper.newBuilder()
            .setDaysSinceEpoch((int)TODAY.toEpochDay())
            .setReportSummary(ReportType.CONFIRMED_CLINICAL_DIAGNOSIS, EXPOSURE_SUMMARY_DATA_LONG)
            .setSummaryData(EXPOSURE_SUMMARY_DATA_LONG)
            .build()
    );

    ExposureClassification result = dailySummaryRiskCalculator.classifyExposure(input);

    assertThat(result)
        .isEqualTo(ExposureClassification.create(3, "Classification 3", TODAY.toEpochDay()));
  }

  @Test
  public void classifyExposure_shortLikelyExposure_returnsC4() {
    List<DailySummaryWrapper> input = ImmutableList.of(
        DailySummaryWrapper.newBuilder()
            .setDaysSinceEpoch((int)TODAY.toEpochDay())
            .setReportSummary(ReportType.CONFIRMED_CLINICAL_DIAGNOSIS, EXPOSURE_SUMMARY_DATA_SHORT)
            .setSummaryData(EXPOSURE_SUMMARY_DATA_SHORT)
            .build()
    );

    ExposureClassification result = dailySummaryRiskCalculator.classifyExposure(input);

    assertThat(result)
        .isEqualTo(ExposureClassification.create(4, "Classification 4", TODAY.toEpochDay()));
  }

  // Multiple classifications: Returns highest classification
  @Test
  public void classifyExposure_multipleExposuresPerDay_returnsMostImportantClassification() {
    ExposureSummaryDataWrapper reportSummary = ExposureSummaryDataWrapper.newBuilder()
        .setWeightedDurationSum(3000.0)
        .setMaximumScore(3000.0)
        .setScoreSum(3000.0)
        .build();
    ExposureSummaryDataWrapper summaryData = ExposureSummaryDataWrapper.newBuilder()
        .setWeightedDurationSum(6000.0)
        .setMaximumScore(3000.0)
        .setScoreSum(6000.0)
        .build();
    List<DailySummaryWrapper> input = ImmutableList.of(
        DailySummaryWrapper.newBuilder()
            .setDaysSinceEpoch((int)TODAY.toEpochDay())
            .setReportSummary(ReportType.CONFIRMED_TEST, reportSummary)
            .setReportSummary(ReportType.CONFIRMED_CLINICAL_DIAGNOSIS, reportSummary)
            .setSummaryData(summaryData)
            .build()
    );

    ExposureClassification result = dailySummaryRiskCalculator.classifyExposure(input);

    assertThat(result)
        .isEqualTo(ExposureClassification.create(1, "Classification 1", TODAY.toEpochDay()));
  }

  // Multiple classifications: Returns newest date of highest classification
  @Test
  public void
      classifyExposure_multiExposuresMultiDays_returnsNewestDateOfMostImportantClassification(){
    ExposureSummaryDataWrapper reportSummaryLong = ExposureSummaryDataWrapper.newBuilder()
        .setWeightedDurationSum(3000.0)
        .setMaximumScore(3000.0)
        .setScoreSum(3000.0)
        .build();
    ExposureSummaryDataWrapper reportSummaryShort = ExposureSummaryDataWrapper.newBuilder()
        .setWeightedDurationSum(1500.0)
        .setMaximumScore(1500.0)
        .setScoreSum(1500.0)
        .build();
    ExposureSummaryDataWrapper summaryData = ExposureSummaryDataWrapper.newBuilder()
        .setWeightedDurationSum(6000.0)
        .setMaximumScore(3000.0)
        .setScoreSum(6000.0)
        .build();
    List<DailySummaryWrapper> input = ImmutableList.of(
        DailySummaryWrapper.newBuilder() //C1, C3
            .setDaysSinceEpoch((int)TODAY.minusDays(14).toEpochDay())
            .setReportSummary(ReportType.CONFIRMED_TEST, reportSummaryLong)
            .setReportSummary(ReportType.CONFIRMED_CLINICAL_DIAGNOSIS, reportSummaryLong)
            .setSummaryData(summaryData)
            .build(),
        DailySummaryWrapper.newBuilder() //C1 (this it the one that should be returned)
            .setDaysSinceEpoch((int)TODAY.minusDays(2).toEpochDay())
            .setReportSummary(ReportType.CONFIRMED_TEST, reportSummaryLong)
            .setSummaryData(reportSummaryLong)
            .build(),
        DailySummaryWrapper.newBuilder() //C4
            .setDaysSinceEpoch((int)TODAY.minusDays(1).toEpochDay())
            .setReportSummary(ReportType.CONFIRMED_CLINICAL_DIAGNOSIS, reportSummaryShort)
            .setSummaryData(reportSummaryShort)
            .build(),
        DailySummaryWrapper.newBuilder() //C3
            .setDaysSinceEpoch((int)TODAY.toEpochDay())
            .setReportSummary(ReportType.CONFIRMED_TEST, reportSummaryShort)
            .setSummaryData(reportSummaryShort)
            .build()
    );

    ExposureClassification result = dailySummaryRiskCalculator.classifyExposure(input);

    assertThat(result)
        .isEqualTo(ExposureClassification.create(1, "Classification 1", TODAY.minusDays(2).toEpochDay()));
  }

   // The following methods check that classifications work correctly on different configurations
   // They cover the thresholds in the HA config that are not already tested by the tests above.

  @Test
  public void classifyExposure_configSelfReportPerDaySumERVThreshold_matchingExposure_returnsC1() {
    DailySummaryRiskCalculator dailySummaryRiskCalculator =
        new DailySummaryRiskCalculator(new ClassificationThreshold[] {
            new ClassificationThreshold(
                1,
                "Classification 1",
                0,
                0,
                2700,
                0,
                0,
                0,
                0)});

    List<DailySummaryWrapper> input = ImmutableList.of(
        DailySummaryWrapper.newBuilder()
            .setDaysSinceEpoch((int)TODAY.toEpochDay())
            .setReportSummary(ReportType.SELF_REPORT, EXPOSURE_SUMMARY_DATA_LONG)
            .setSummaryData(EXPOSURE_SUMMARY_DATA_LONG)
            .build()
    );

    ExposureClassification result = dailySummaryRiskCalculator.classifyExposure(input);

    assertThat(result)
        .isEqualTo(ExposureClassification.create(1, "Classification 1", TODAY.toEpochDay()));
  }

  @Test
  public void classifyExposure_configRecursivePerDaySumERVThreshold_matchingExposure_returnsC1() {
    DailySummaryRiskCalculator dailySummaryRiskCalculator =
        new DailySummaryRiskCalculator(new ClassificationThreshold[] {
            new ClassificationThreshold(
                1,
                "Classification 1",
                0,
                0,
                0,
                2700,
                0,
                0,
                0)});

    List<DailySummaryWrapper> input = ImmutableList.of(
        DailySummaryWrapper.newBuilder()
            .setDaysSinceEpoch((int)TODAY.toEpochDay())
            .setReportSummary(ReportType.RECURSIVE, EXPOSURE_SUMMARY_DATA_LONG)
            .setSummaryData(EXPOSURE_SUMMARY_DATA_LONG)
            .build()
    );

    ExposureClassification result = dailySummaryRiskCalculator.classifyExposure(input);

    assertThat(result)
        .isEqualTo(ExposureClassification.create(1, "Classification 1", TODAY.toEpochDay()));
  }

  @Test
  public void classifyExposure_configPerDaySumERVThreshold_matchingExposure_returnsC1() {
    DailySummaryRiskCalculator dailySummaryRiskCalculator =
        new DailySummaryRiskCalculator(new ClassificationThreshold[] {
            new ClassificationThreshold(
                1,
                "Classification 1",
                0,
                0,
                0,
                0,
                2700,
                0,
                0)});

    List<DailySummaryWrapper> input = ImmutableList.of(
        DailySummaryWrapper.newBuilder()
            .setDaysSinceEpoch((int)TODAY.toEpochDay())
            .setReportSummary(ReportType.CONFIRMED_TEST, EXPOSURE_SUMMARY_DATA_LONG)
            .setSummaryData(EXPOSURE_SUMMARY_DATA_LONG)
            .build()
    );

    ExposureClassification result = dailySummaryRiskCalculator.classifyExposure(input);

    assertThat(result)
        .isEqualTo(ExposureClassification.create(1, "Classification 1", TODAY.toEpochDay()));
  }

  @Test
  public void classifyExposure_configPerDayMaxERVThreshold_matchingExposure_returnsC1() {
    DailySummaryRiskCalculator dailySummaryRiskCalculator =
        new DailySummaryRiskCalculator(new ClassificationThreshold[] {
            new ClassificationThreshold(
                1,
                "Classification 1",
                0,
                0,
                0,
                0,
                0,
                2700,
                0)});

    List<DailySummaryWrapper> input = ImmutableList.of(
        DailySummaryWrapper.newBuilder()
            .setDaysSinceEpoch((int)TODAY.toEpochDay())
            .setReportSummary(ReportType.CONFIRMED_TEST, EXPOSURE_SUMMARY_DATA_LONG)
            .setSummaryData(EXPOSURE_SUMMARY_DATA_LONG)
            .build()
    );

    ExposureClassification result = dailySummaryRiskCalculator.classifyExposure(input);

    assertThat(result)
        .isEqualTo(ExposureClassification.create(1, "Classification 1", TODAY.toEpochDay()));
  }

  @Test
  public void
      classifyExposure_configWeightedDurationAtAttenuationThreshold_matchingExposure_returnsC1() {
    DailySummaryRiskCalculator dailySummaryRiskCalculator =
        new DailySummaryRiskCalculator(new ClassificationThreshold[] {
            new ClassificationThreshold(
                1,
                "Classification 1",
                0,
                0,
                0,
                0,
                0,
                0,
                2700)});

    List<DailySummaryWrapper> input = ImmutableList.of(
        DailySummaryWrapper.newBuilder()
            .setDaysSinceEpoch((int)TODAY.toEpochDay())
            .setReportSummary(ReportType.CONFIRMED_CLINICAL_DIAGNOSIS, EXPOSURE_SUMMARY_DATA_LONG)
            .setSummaryData(EXPOSURE_SUMMARY_DATA_LONG)
            .build()
    );

    ExposureClassification result = dailySummaryRiskCalculator.classifyExposure(input);

    assertThat(result)
        .isEqualTo(ExposureClassification.create(1, "Classification 1", TODAY.toEpochDay()));
  }


}
