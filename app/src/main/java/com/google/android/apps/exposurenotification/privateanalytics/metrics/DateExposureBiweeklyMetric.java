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
 * Class for generating an output vector that indicates the date of exposure when a notification is
 * received.
 * <p>
 * We use the same calculation for the number of days of delay as DateExposure. However, we use a
 * cumulative distribution.  For each exposure notification classification, there are 12 day
 * buckets, and anywhere from one to all of them will be set. For classification 1 notifications,
 * buckets 0-11 are used:
 * <ul>
 * <li>0 - delay is 0 days
 * </li>1 - delay is 0-1 days
 * </li>2 - delay is 0-2 days
 * </li>3 - delay is 0-3 days
 * </li>...
 * </li>11 - delay is 0-11 days
 * </ul>
 * For a classification 2 exposure, buckets 12-23 are used, classification 3 uses buckets 24-35, and
 * classification 4 uses buckets 36-47.
 */
public class DateExposureBiweeklyMetric implements PrivateAnalyticsMetric {

  private static final String VERSION = "v2";
  public static final String METRIC_NAME = "DateExposure-" + VERSION;
  private static final int NUMBER_CLASSIFICATIONS = 4;
  private static final int DAY_BUCKETS = 12;
  public static final int BIN_LENGTH = NUMBER_CLASSIFICATIONS * DAY_BUCKETS;

  private final ExposureNotificationSharedPreferences exposureNotificationSharedPreferences;

  @Inject
  DateExposureBiweeklyMetric(
      ExposureNotificationSharedPreferences exposureNotificationSharedPreferences) {
    this.exposureNotificationSharedPreferences = exposureNotificationSharedPreferences;
  }

  @Override
  public ListenableFuture<List<Integer>> getDataVector() {
    int[] data = new int[BIN_LENGTH];

    Instant exposureNotificationTime = exposureNotificationSharedPreferences
        .getExposureNotificationLastShownTime();
    Instant privateAnalyticsWorkerLastTime = exposureNotificationSharedPreferences
        .getPrivateAnalyticsWorkerLastTimeForBiweekly();
    Instant exposureTime = exposureNotificationSharedPreferences
        .getPrivateAnalyticsLastExposureTime();

    if (exposureNotificationTime.isAfter(privateAnalyticsWorkerLastTime) && exposureNotificationTime
        .isAfter(exposureTime)) {
      // A notification has been shown since the last private analytics submission and last exposure,
      // we will populate one of the bins.
      int classificationIndex = exposureNotificationSharedPreferences
          .getExposureNotificationLastShownClassification();
      if (classificationIndex >= 1 && classificationIndex <= 4) {
        int offset = (classificationIndex - 1) * DAY_BUCKETS;

        Duration timeBetweenExposureAndNotification = Duration
            .between(exposureTime, exposureNotificationTime);
        long daysBetweenExposureAndNotification = timeBetweenExposureAndNotification.toDays();

        // We cap the max value at 11 (the highest possible bin):
        daysBetweenExposureAndNotification = Math.min(daysBetweenExposureAndNotification, 11);

        for (int i = (int) daysBetweenExposureAndNotification; i < DAY_BUCKETS; i++) {
          data[offset + i] = 1;
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
