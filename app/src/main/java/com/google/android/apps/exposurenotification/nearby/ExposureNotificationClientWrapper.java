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

import com.google.android.apps.exposurenotification.common.logging.Logger;
import com.google.android.apps.exposurenotification.logging.AnalyticsLogger;
import com.google.android.apps.exposurenotification.nearby.DailySummaryWrapper.ExposureSummaryDataWrapper;
import com.google.android.apps.exposurenotification.proto.ApiCall.ApiCallType;
import com.google.android.gms.nearby.exposurenotification.DailySummariesConfig;
import com.google.android.gms.nearby.exposurenotification.DailySummary;
import com.google.android.gms.nearby.exposurenotification.DailySummary.ExposureSummaryData;
import com.google.android.gms.nearby.exposurenotification.DiagnosisKeysDataMapping;
import com.google.android.gms.nearby.exposurenotification.ExposureNotificationClient;
import com.google.android.gms.nearby.exposurenotification.ExposureNotificationStatus;
import com.google.android.gms.nearby.exposurenotification.ExposureWindow;
import com.google.android.gms.nearby.exposurenotification.PackageConfiguration;
import com.google.android.gms.nearby.exposurenotification.ReportType;
import com.google.android.gms.nearby.exposurenotification.TemporaryExposureKey;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Wrapper around {@link com.google.android.gms.nearby.Nearby} APIs.
 */
public class ExposureNotificationClientWrapper {

  private static final Logger logcat = Logger.getLogger("ENClientWrapper");

  public static final String GMSCORE_PACKAGE_NAME = "com.google.android.gms";

  public static final String EN_MODULE_PERMISSION =
      "com.google.android.gms.nearby.exposurenotification.EXPOSURE_CALLBACK";

  public static final String ACTION_WAKE_UP =
      "com.google.android.gms.exposurenotification.ACTION_WAKE_UP";

  public static final String ACTION_VERIFICATION_LINK =
      "com.google.android.gms.nearby.exposurenotification.ACTION_VERIFICATION_LINK";

  public static final String ACTION_PRE_AUTHORIZE_RELEASE_PHONE_UNLOCKED =
      "com.google.android.gms.exposurenotification.ACTION_PRE_AUTHORIZE_RELEASE_PHONE_UNLOCKED";

  private final ExposureNotificationClient exposureNotificationClient;
  private final AnalyticsLogger logger;

  ExposureNotificationClientWrapper(
      ExposureNotificationClient exposureNotificationClient,
      AnalyticsLogger logger) {
    this.exposureNotificationClient = exposureNotificationClient;
    this.logger = logger;
  }

  public Task<Void> start() {
    return exposureNotificationClient.start()
        .addOnFailureListener(e -> logger.logApiCallFailureAsync(ApiCallType.CALL_START, e))
        .addOnSuccessListener(aVoid -> logger.logApiCallSuccessAsync(ApiCallType.CALL_START));
  }

  public Task<Void> stop() {
    return exposureNotificationClient.stop()
        .addOnFailureListener(e -> logger.logApiCallFailureAsync(ApiCallType.CALL_STOP, e))
        .addOnSuccessListener(aVoid -> logger.logApiCallSuccessAsync(ApiCallType.CALL_STOP));
  }

  public Task<Boolean> isEnabled() {
    return exposureNotificationClient.isEnabled()
        .addOnFailureListener(
            e -> logger.logApiCallFailureAsync(ApiCallType.CALL_IS_ENABLED, e))
        .addOnSuccessListener(aVoid -> logger.logApiCallSuccessAsync(ApiCallType.CALL_IS_ENABLED));
  }

  public Task<Set<ExposureNotificationStatus>> getStatus() {
    return exposureNotificationClient.getStatus();
  }

  public Task<List<TemporaryExposureKey>> getTemporaryExposureKeyHistory() {
    return exposureNotificationClient.getTemporaryExposureKeyHistory();
  }

  /**
   * Provides diagnosis key files to the API for matching.
   */
  public Task<Void> provideDiagnosisKeys(List<File> files) {
    Task<Void> task = exposureNotificationClient.provideDiagnosisKeys(files);
    task.addOnFailureListener(
        e -> logger.logApiCallFailureAsync(ApiCallType.CALL_PROVIDE_DIAGNOSIS_KEYS, e))
        .addOnSuccessListener(
            aVoid -> logger.logApiCallSuccessAsync(ApiCallType.CALL_PROVIDE_DIAGNOSIS_KEYS));
    return task;
  }

  public Task<List<ExposureWindow>> getExposureWindows() {
    return exposureNotificationClient.getExposureWindows();
  }

  public Task<Void> setDiagnosisKeysDataMapping(DiagnosisKeysDataMapping diagnosisKeysDataMapping) {
    return exposureNotificationClient.setDiagnosisKeysDataMapping(diagnosisKeysDataMapping)
        .addOnFailureListener(
            e -> logger.logApiCallFailureAsync(ApiCallType.CALL_SET_DIAGNOSIS_KEYS_DATA_MAPPING, e))
        .addOnSuccessListener(
            aVoid -> logger.logApiCallSuccessAsync(ApiCallType.CALL_SET_DIAGNOSIS_KEYS_DATA_MAPPING));
  }

  public Task<DiagnosisKeysDataMapping> getDiagnosisKeysDataMapping() {
    return exposureNotificationClient.getDiagnosisKeysDataMapping();
  }

  public Task<List<DailySummaryWrapper>> getDailySummaries(DailySummariesConfig dailySummariesConfig) {
    return exposureNotificationClient.getDailySummaries(dailySummariesConfig)
        .addOnFailureListener(
            e -> logger.logApiCallFailureAsync(ApiCallType.CALL_GET_DAILY_SUMMARIES, e))
        .addOnSuccessListener(
            dailySummaries -> logger.logApiCallSuccessAsync(ApiCallType.CALL_GET_DAILY_SUMMARIES))
        .continueWith(task -> wrapDailySummaries(task.getResult()));
  }

  private List<DailySummaryWrapper> wrapDailySummaries(List<DailySummary> dailySummaries) {
    List<DailySummaryWrapper> dailySummaryWrappers = new ArrayList<>(dailySummaries.size());

    for (DailySummary dailySummary : dailySummaries) {
      DailySummaryWrapper.Builder dailySummaryWrapperBuilder = DailySummaryWrapper.newBuilder();

      // Copy over daysSinceEpoch
      dailySummaryWrapperBuilder.setDaysSinceEpoch(dailySummary.getDaysSinceEpoch());

      // Copy over overall ExposureSummaryData
      ExposureSummaryData exposureSummaryData = dailySummary.getSummaryData();
      ExposureSummaryDataWrapper exposureSummaryDataWrapper = ExposureSummaryDataWrapper.newBuilder()
          .setMaximumScore(exposureSummaryData.getMaximumScore())
          .setScoreSum(exposureSummaryData.getScoreSum())
          .setWeightedDurationSum(exposureSummaryData.getWeightedDurationSum())
          .build();
      dailySummaryWrapperBuilder.setSummaryData(exposureSummaryDataWrapper);

      // Copy over ReportType-specific ExposureSummaryData
      for (int reportType : ImmutableList.of(ReportType.UNKNOWN, ReportType.CONFIRMED_TEST,
          ReportType.CONFIRMED_CLINICAL_DIAGNOSIS, ReportType.SELF_REPORT, ReportType.RECURSIVE,
          ReportType.REVOKED)) {
        ExposureSummaryData reportTypeESD = dailySummary.getSummaryDataForReportType(reportType);
        ExposureSummaryDataWrapper reportTypeESDWrapper = ExposureSummaryDataWrapper.newBuilder()
            .setMaximumScore(reportTypeESD.getMaximumScore())
            .setScoreSum(reportTypeESD.getScoreSum())
            .setWeightedDurationSum(reportTypeESD.getWeightedDurationSum())
            .build();
        dailySummaryWrapperBuilder.setReportSummary(reportType, reportTypeESDWrapper);
      }

      // Add to overall list
      dailySummaryWrappers.add(dailySummaryWrapperBuilder.build());
    }
    return ImmutableList.copyOf(dailySummaryWrappers);
  }

  /**
   * Returns {@link PackageConfiguration} if API is available, null otherwise.
   */
  public Task<PackageConfiguration> getPackageConfiguration() {
    return isAtLeastEnModuleVersion1Pt7().onSuccessTask(is1Pt7APIAvailable -> {
      if (is1Pt7APIAvailable) {
        return exposureNotificationClient.getPackageConfiguration();
      } else {
        return Tasks.forResult(null);
      }
    });
  }

  public Task<Long> getVersion() {
    return exposureNotificationClient.getVersion();
  }

  public Task<Void> requestPreAuthorizedTemporaryExposureKeyHistory() {
    return exposureNotificationClient.requestPreAuthorizedTemporaryExposureKeyHistory();
  }

  public Task<Void> requestPreAuthorizedTemporaryExposureKeyHistoryForSelfReport() {
    return exposureNotificationClient
        .requestPreAuthorizedTemporaryExposureKeyHistoryForSelfReport();
  }

  public Task<Void> requestPreAuthorizedTemporaryExposureKeyRelease() {
    return exposureNotificationClient.requestPreAuthorizedTemporaryExposureKeyRelease();
  }

  /**
   * Checks first 2 chars of the version to obtain the module version.
   */
  private Task<Boolean> isAtLeastEnModuleVersion1Pt7() {
    return getVersion().continueWith((result) -> {
      if (result.isSuccessful()) {
        try {
          // Parse version as string, extract first 2 chars to get the en module version, then
          // convert back to a long for easy comparison.
          return Long.parseLong(Long.toString(result.getResult()).substring(0, 2)) >= 17;
        } catch (NumberFormatException nfe) {
          logcat.e("Unable to parse version");
        }
      }
      return false;
    });
  }
}
