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
import com.google.android.apps.exposurenotification.storage.ExposureEntity;
import com.google.android.gms.nearby.exposurenotification.DailySummariesConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.threeten.bp.LocalDate;
import org.threeten.bp.ZoneOffset;

/**
 * The idea of this revocation detector is to recognize a revocation by observing changes in
 * exposure scores.
 *
 * For that purpose, every time we check for possible exposures, we store minimal exposure
 * information (score per day).
 * This minimal information is stored as a list of ExposureEntities.
 */
public class RevocationDetector {

  private static final String TAG = "RevocationDetector";

  private static final long DEFAULT_DAYS_SINCE_EXPOSURE_THRESHOLD = 14;

  private final DailySummariesConfig dailySummariesConfig;

  public RevocationDetector(DailySummariesConfig dailySummariesConfig) {
    this.dailySummariesConfig = dailySummariesConfig;
  }

  /**
   * Defines the exact score to persist for each day in the ExposureEntity objects.
   */
  public List<ExposureEntity> dailySummaryToExposureEntity(
      List<DailySummaryWrapper> dailySummaries) {

      List<ExposureEntity> exposureEntities = new ArrayList<>();
      for (DailySummaryWrapper dailySummary : dailySummaries) {
        exposureEntities.add(
            ExposureEntity
                .create(dailySummary.getDaysSinceEpoch(),
                    dailySummary.getSummaryData().getScoreSum()
                )
        );
      }

    return exposureEntities;
  }

  /**
   * Heuristic to detect revocations based on changes in daily risk scores.
   */
  public boolean isRevocation(List<ExposureEntity> previousExposureEntityList,
      List<ExposureEntity> currentExposureEntityList) {

    // Convert currentExposureEntityList to map for easier lookup
    Map<Long, Double> currentExposureEntityMap = new HashMap<>();
    for (ExposureEntity currentExposureEntity : currentExposureEntityList) {
      currentExposureEntityMap.put(currentExposureEntity.getDateDaysSinceEpoch(),
          currentExposureEntity.getExposureScore());
    }

    /*
     * Get days since exposure threshold as set by the health authority.
     * For no threshold, this value currently defaults to 0. For our calculation, we replace
     * it by DEFAULT_DAYS_SINCE_EXPOSURE_THRESHOLD
     */
    long daysSinceExposureThreshold =
        dailySummariesConfig.getDaysSinceExposureThreshold();
    if (daysSinceExposureThreshold == 0) {
      daysSinceExposureThreshold = DEFAULT_DAYS_SINCE_EXPOSURE_THRESHOLD;
    }

    Log.d(TAG, "Checking for possible revocation with "
        + "previousExposureEntities [" + TextUtils.join(", ", previousExposureEntityList) + "], "
        + "currentExposureEntities [" + TextUtils.join(", ", currentExposureEntityList) + "]"
        + " and daysSinceExposureThreshold " + daysSinceExposureThreshold);

    /*
     * daysSinceExposureThreshold
     *    |                                       Today
     *  15|14 13 12 11 10 09 08 07 06 05 04 03 02 01 00       daysSinceExposure
     * /-----------------------------------------------\
     * |10|  |  |  |  |  |10|  |10|  |  |  |  |  |  |10|      previousExposureEntityList
     * \--------------------------------------------------\
     *    |  |  |  |  |  |  |  | 5|  |  |  |  |  |  |10|  |   currentExposureEntityList
     *    \-----------------------------------------------/   (both containing exposure scores)
     * \/                 \/    \/             \/    \/
     * (1) Fade-out      (2) Revocations      No revocations
     * (no revocation)
     *
     * We try to only recognize (2) Revocations, where
     *  - The exposure score from previousExposureEntityList is either SMALLER or NOT EXISTING
     *    in currentExposureEntityList
     *  - AND daysSinceExposure is still UNDER the daysSinceExposureThreshold
     *    (to filter out (1) Fade-out cases, where the current exposure score just timed-out
     *     instead of being revoked)
     */
    for (ExposureEntity previousExposureEntity : previousExposureEntityList) {
      long daysSinceExposure = LocalDate.now(ZoneOffset.UTC)
          .minusDays(previousExposureEntity.getDateDaysSinceEpoch()).toEpochDay();
      // Only check previousExposureEntity if it is still within the daysSinceExposureThreshold
      if (daysSinceExposure <= daysSinceExposureThreshold) {
        /*
         * If for such a previousExposureEntity, the currentExposureEntity on the same date does
         * not exist or is lower in ExposureScore, this is a revocation
         */
        Double currentExposureScore =
            currentExposureEntityMap.get(previousExposureEntity.getDateDaysSinceEpoch());
        if (currentExposureScore == null
            || currentExposureScore < previousExposureEntity.getExposureScore()) {
          Log.d(TAG, "Revocation detected on day "
              + previousExposureEntity.getDateDaysSinceEpoch());
          return true;
        }
      }
    }

    Log.d(TAG, "No revocation detected");
    return false;
  }

}
