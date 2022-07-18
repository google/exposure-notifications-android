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

package com.google.android.apps.exposurenotification.privateanalytics.metrics;

import androidx.annotation.VisibleForTesting;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.libraries.privateanalytics.PrivateAnalyticsMetric;
import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;
import javax.inject.Inject;
import org.threeten.bp.Instant;

/**
 * Class for generating an output vector that represents the count of exposure notifications of a
 * given period
 */
public class PeriodicExposureNotificationBiweeklyMetric implements PrivateAnalyticsMetric {

  private static final String VERSION = "v3";
  public static final String METRIC_NAME = "PeriodicExposureNotification14d-" + VERSION;
  @VisibleForTesting
  static final int NO_EXPOSURE_BIN_ID = 0;
  @VisibleForTesting
  static final int NUM_SEVERITY_BINS = 4;
  @VisibleForTesting
  static final int BIN_LENGTH = 1 + NUM_SEVERITY_BINS;

  private final ExposureNotificationSharedPreferences exposureNotificationSharedPreferences;

  @Inject
  PeriodicExposureNotificationBiweeklyMetric(
      ExposureNotificationSharedPreferences exposureNotificationSharedPreferences) {
    this.exposureNotificationSharedPreferences = exposureNotificationSharedPreferences;
  }

  @Override
  public ListenableFuture<List<Integer>> getDataVector() {
    Instant exposureNotificationTime = exposureNotificationSharedPreferences
        .getExposureNotificationLastShownTime();
    Instant privateAnalyticsWorkerLastTime = exposureNotificationSharedPreferences
        .getPrivateAnalyticsWorkerLastTimeForBiweekly();

    int index = exposureNotificationSharedPreferences
        .getExposureNotificationLastShownClassification();
    if (exposureNotificationTime.equals(Instant.EPOCH)) {
      // If Instant.EPOCH is returned, it means that no notification was shown.
      index = NO_EXPOSURE_BIN_ID;
    } else if (exposureNotificationTime.isBefore(privateAnalyticsWorkerLastTime)) {
      // The notification should have been reported at the last Private Analytics worker run.
      // We report no exposure.
      index = NO_EXPOSURE_BIN_ID;
    } else if (index >= BIN_LENGTH || index < 0) {
      // The index is out of band, we set it to the NO_EXPOSURE_BIN.
      index = NO_EXPOSURE_BIN_ID;
    }

    int[] data = new int[BIN_LENGTH];
    data[index] = 1;
    return Futures.immediateFuture(Ints.asList(data));
  }

  public String getMetricName() {
    return METRIC_NAME;
  }

  public int getMetricHammingWeight() {
    return 1;
  }
}
