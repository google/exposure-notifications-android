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
import com.google.android.apps.exposurenotification.privateanalytics.MetricsSnapshot;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.Arrays;
import java.util.List;
import javax.inject.Inject;
import org.threeten.bp.Duration;
import org.threeten.bp.Instant;

/**
 * Class for generating an output vector that represents the count of exposure notifications of a
 * given period
 */
public class PeriodicExposureNotificationMetric implements PrivateAnalyticsMetric {

  private static final String VERSION = "v1";
  public static final String METRIC_NAME = "PeriodicExposureNotification-" + VERSION;
  private static final Duration ONE_DAY = Duration.ofDays(1);
  @VisibleForTesting
  static final int NO_EXPOSURE_BIN_ID = 0;
  @VisibleForTesting
  static final int NUM_SEVERITY_BINS = 4;
  @VisibleForTesting
  static final int BIN_LENGTH = 1 + NUM_SEVERITY_BINS;

  private final ExposureNotificationSharedPreferences exposureNotificationSharedPreferences;

  @Inject
  PeriodicExposureNotificationMetric(
      ExposureNotificationSharedPreferences exposureNotificationSharedPreferences) {
    this.exposureNotificationSharedPreferences = exposureNotificationSharedPreferences;
  }

  @Override
  public ListenableFuture<List<Integer>> getDataVector(MetricsSnapshot metricsSnapshot) {
    int index = metricsSnapshot.exposureNotificationLastShownClassification();

    Instant exposureNotificationTime = metricsSnapshot.exposureNotificationLastShownTime();
    if (exposureNotificationTime.equals(Instant.EPOCH) || index >= BIN_LENGTH || index < 0) {
      index = NO_EXPOSURE_BIN_ID;
    }
    int[] data = new int[BIN_LENGTH];
    Arrays.fill(data, 0);
    data[index] = 1;
    return Futures.immediateFuture(Ints.asList(data));
  }

  @Override
  public void resetData() {
    exposureNotificationSharedPreferences.clearLastShownExposureNotification();
  }

  public String getMetricName() {
    return METRIC_NAME;
  }

  public int getMetricHammingWeight() {
    return 1;
  }
}
