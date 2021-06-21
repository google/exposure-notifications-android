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

import com.google.android.apps.exposurenotification.riskcalculation.ExposureClassification;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences.VaccinationStatus;
import com.google.android.libraries.privateanalytics.PrivateAnalyticsMetric;
import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;
import javax.inject.Inject;
import org.threeten.bp.Instant;

/**
 * Class for generating an output vector that indicates the vaccine status when users do a key
 * upload.
 */
public class KeysUploadedVaccineStatusMetric implements PrivateAnalyticsMetric {

  private static final String VERSION = "v1";
  public static final String METRIC_NAME = "KeysUploadedVaccineStatus-" + VERSION;
  public static final int BIN_LENGTH = 15;

  public static final int BIN_UPLOADED = 0;
  public static final int BIN_VACCINATED = 1;
  public static final int BIN_NOT_VACCINATED = 2;
  public static final int BIN_KEY_UPLOADED_NO_PAST_EN = 3;
  public static final int BIN_KEY_UPLOADED_NO_PAST_EN_UPLOADER_VACCINATED = 4;
  public static final int BIN_KEY_UPLOADED_NO_PAST_EN_UPLOADER_NOT_VACCINATED = 5;
  public static final int BIN_KEY_UPLOADED_PAST_EN = 6;
  public static final int BIN_KEY_UPLOADED_PAST_EN_UPLOADER_VACCINATED = 7;
  public static final int BIN_KEY_UPLOADED_PAST_EN_UPLOADER_NOT_VACCINATED = 8;
  public static final int BIN_KEY_UPLOADED_PAST_EN_CASE_VACCINATED = 9;
  public static final int BIN_KEY_UPLOADED_PAST_EN_UPLOADER_VACCINATED_CASE_VACCINATED = 10;
  public static final int BIN_KEY_UPLOADED_PAST_EN_UPLOADER_NOT_VACCINATED_CASE_VACCINATED = 11;
  public static final int BIN_KEY_UPLOADED_PAST_EN_CASE_NOT_VACCINATED = 12;
  public static final int BIN_KEY_UPLOADED_PAST_EN_UPLOADER_VACCINATED_CASE_NOT_VACCINATED = 13;
  public static final int BIN_KEY_UPLOADED_PAST_EN_UPLOADER_NOT_VACCINATED_CASE_NOT_VACCINATED = 14;

  private final ExposureNotificationSharedPreferences exposureNotificationSharedPreferences;

  @Inject
  KeysUploadedVaccineStatusMetric(
      ExposureNotificationSharedPreferences exposureNotificationSharedPreferences) {
    this.exposureNotificationSharedPreferences = exposureNotificationSharedPreferences;
  }

  @Override
  public ListenableFuture<List<Integer>> getDataVector() {
    int[] data = new int[BIN_LENGTH];
    VaccinationStatus lastVaccinationStatus = exposureNotificationSharedPreferences
        .getLastVaccinationStatus();
    Instant lastVaccinationStatusResponseTime = exposureNotificationSharedPreferences
        .getLastVaccinationStatusResponseTime();
    Instant privateAnalyticsWorkerLastTime = exposureNotificationSharedPreferences
        .getPrivateAnalyticsWorkerLastTime();
    ExposureClassification exposureClassification = exposureNotificationSharedPreferences
        .getExposureClassification();

    boolean uploaderVaccinated = lastVaccinationStatus == VaccinationStatus.VACCINATED;
    boolean encounteredEn = exposureClassification.getClassificationIndex()
        != ExposureClassification.NO_EXPOSURE_CLASSIFICATION_INDEX;

    boolean encounteredVaccinatedCase = false;

    if (lastVaccinationStatusResponseTime.isAfter(privateAnalyticsWorkerLastTime)) {
      data[BIN_UPLOADED] = 1;
      if (uploaderVaccinated) {
        data[BIN_VACCINATED] = 1;
        if (encounteredEn) {
          data[BIN_KEY_UPLOADED_PAST_EN_UPLOADER_VACCINATED] = 1;
          data[BIN_KEY_UPLOADED_PAST_EN] = 1;
          if (encounteredVaccinatedCase) {
            data[BIN_KEY_UPLOADED_PAST_EN_CASE_VACCINATED] = 1;
            data[BIN_KEY_UPLOADED_PAST_EN_UPLOADER_VACCINATED_CASE_VACCINATED] = 1;
          } else {
            data[BIN_KEY_UPLOADED_PAST_EN_CASE_NOT_VACCINATED] = 1;
            data[BIN_KEY_UPLOADED_PAST_EN_UPLOADER_VACCINATED_CASE_NOT_VACCINATED] = 1;
          }
        } else {
          data[BIN_KEY_UPLOADED_NO_PAST_EN] = 1;
          data[BIN_KEY_UPLOADED_NO_PAST_EN_UPLOADER_VACCINATED] = 1;
        }
      } else {
        data[BIN_NOT_VACCINATED] = 1;
        if (encounteredEn) {
          data[BIN_KEY_UPLOADED_PAST_EN] = 1;
          data[BIN_KEY_UPLOADED_PAST_EN_UPLOADER_NOT_VACCINATED] = 1;
          if (encounteredVaccinatedCase) {
            data[BIN_KEY_UPLOADED_PAST_EN_CASE_VACCINATED] = 1;
            data[BIN_KEY_UPLOADED_PAST_EN_UPLOADER_NOT_VACCINATED_CASE_VACCINATED] = 1;
          } else {
            data[BIN_KEY_UPLOADED_PAST_EN_CASE_NOT_VACCINATED] = 1;
            data[BIN_KEY_UPLOADED_PAST_EN_UPLOADER_NOT_VACCINATED_CASE_NOT_VACCINATED] = 1;
          }
        } else {
          data[BIN_KEY_UPLOADED_NO_PAST_EN] = 1;
          data[BIN_KEY_UPLOADED_NO_PAST_EN_UPLOADER_NOT_VACCINATED] = 1;
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
