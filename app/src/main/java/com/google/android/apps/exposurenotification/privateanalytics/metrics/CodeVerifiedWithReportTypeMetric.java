/*
 * Copyright 2021 Google LLC
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

package com.google.android.apps.exposurenotification.privateanalytics.metrics;

import androidx.annotation.VisibleForTesting;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity.TestResult;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.libraries.privateanalytics.PrivateAnalyticsMetric;
import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;
import javax.inject.Inject;
import org.threeten.bp.Duration;
import org.threeten.bp.Instant;

/**
 * Class for generating an output vector that captures, when a code is submitted, what was the
 * report type associated with it.
 * <p><ul>
 * Bins signification:
 * <li> 0: report type unknown
 * <li> 1: report type confirmedTest
 * <li> 2: report type confirmedClinicalDiagnosis
 * <li> 3: report type selfReported
 * <li> 4: report type recursive
 * <li> 5: report type revoked
 * </ul>
 */
public class CodeVerifiedWithReportTypeMetric implements PrivateAnalyticsMetric {

  private static final String VERSION = "v2";
  public static final String METRIC_NAME = "CodeVerifiedWithReportType14d-" + VERSION;

  private static final Duration NUM_DAYS = Duration.ofDays(14);

  @VisibleForTesting static final int UNKNOWN_BIN = 0;
  @VisibleForTesting static final int CONFIRMED_TEST_BIN = 1;
  @VisibleForTesting static final int CONFIRMED_CLINICAL_DIAGNOSIS_BIN = 2;
  @VisibleForTesting static final int SELF_REPORTED_BIN = 3;
  private static final int RECURSIVE_BIN = 4; // Unused
  @VisibleForTesting static final int REVOKED_BIN = 5;

  @VisibleForTesting
  static final int BIN_LENGTH = 6;

  private final ExposureNotificationSharedPreferences exposureNotificationSharedPreferences;

  @Inject
  CodeVerifiedWithReportTypeMetric(
      ExposureNotificationSharedPreferences exposureNotificationSharedPreferences) {
    this.exposureNotificationSharedPreferences = exposureNotificationSharedPreferences;
  }

  @Override
  public ListenableFuture<List<Integer>> getDataVector() {
    int[] data = new int[BIN_LENGTH];

    Instant lastSubmittedCodeTime = exposureNotificationSharedPreferences
        .getPrivateAnalyticsLastSubmittedCodeTime();
    Instant workerLastTime = exposureNotificationSharedPreferences
        .getPrivateAnalyticsWorkerLastTimeForBiweekly();
    if (lastSubmittedCodeTime.isAfter(workerLastTime)) {
      // A code has been submitted since the last analytics upload.
      TestResult testResult = exposureNotificationSharedPreferences
          .getPrivateAnalyticsLastReportType();
      if (testResult == null) {
        data[UNKNOWN_BIN] = 1;
      } else {
        switch (testResult) {
          case CONFIRMED:
            data[CONFIRMED_TEST_BIN] = 1;
            break;
          case LIKELY:
            data[CONFIRMED_CLINICAL_DIAGNOSIS_BIN] = 1;
            break;
          case USER_REPORT:
            data[SELF_REPORTED_BIN] = 1;
            break;
          case NEGATIVE:
            data[REVOKED_BIN] = 1;
            break;
          default:
            data[UNKNOWN_BIN] = 1;
        }
      }
    }

    return Futures.immediateFuture(Ints.asList(data));
  }

  @Override
  public String getMetricName() {
    return METRIC_NAME;
  }

  @Override
  public int getMetricHammingWeight() {
    return 0;
  }
}
