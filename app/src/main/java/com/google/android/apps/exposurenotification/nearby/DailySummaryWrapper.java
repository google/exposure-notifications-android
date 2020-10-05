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

package com.google.android.apps.exposurenotification.nearby;

import com.google.auto.value.AutoValue;
import java.util.ArrayList;
import java.util.List;

/*
 * AutoValue based interface-identical copy of DailySummary and ExposureDataSummary.
 */
@AutoValue
public abstract class DailySummaryWrapper {

  public abstract int getDaysSinceEpoch();

  public abstract ExposureSummaryDataWrapper getSummaryData();

  /**
   * We leave getReportSummaries() package-private and only make getSummaryDataForReportType(...)
   * public to accurately match GMScores DailySummary interface
   */
  abstract List<ExposureSummaryDataWrapper> getReportSummaries();

  public ExposureSummaryDataWrapper getSummaryDataForReportType(int reportType) {
    return getReportSummaries().get(reportType);
  }

  public static DailySummaryWrapper.Builder newBuilder() {
    // We have six different report types, so we initialize our ReportSummaryList with size 6
    List<ExposureSummaryDataWrapper> defaultReportSummaryList = new ArrayList<>(6);
    for (int i=0; i<6; i++) {
      defaultReportSummaryList.add(ExposureSummaryDataWrapper.newBuilder().build());
    }
    return new AutoValue_DailySummaryWrapper.Builder()
        .setDaysSinceEpoch(0)
        .setReportSummaries(defaultReportSummaryList);
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setDaysSinceEpoch(int daysSinceEpoch);
    public abstract Builder setSummaryData(ExposureSummaryDataWrapper exposureSummaryDataWrapper);

    abstract Builder setReportSummaries(List<ExposureSummaryDataWrapper> list);
    abstract List<ExposureSummaryDataWrapper> getReportSummaries();

    public Builder setReportSummary(int reportType,
        ExposureSummaryDataWrapper reportSummary) {
      getReportSummaries().set(reportType, reportSummary);
      return this;
    }

    public abstract DailySummaryWrapper build();
  }

  @AutoValue
  public static abstract class ExposureSummaryDataWrapper {

    public abstract double getMaximumScore();

    public abstract double getScoreSum();

    public abstract double getWeightedDurationSum();

    public static Builder newBuilder() {
      return new AutoValue_DailySummaryWrapper_ExposureSummaryDataWrapper.Builder()
          .setMaximumScore(0)
          .setScoreSum(0)
          .setWeightedDurationSum(0);
    }

    /** Builder for {@link ExposureSummaryDataWrapper}. */
    @AutoValue.Builder
    public abstract static class Builder {
      public abstract Builder setMaximumScore(double maximumScore);
      public abstract Builder setScoreSum(double scoreSum);
      public abstract Builder setWeightedDurationSum(
          double weightedDurationSum);

      public abstract ExposureSummaryDataWrapper build();
    }
  }

}
