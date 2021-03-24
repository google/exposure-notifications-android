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
import com.google.common.collect.ImmutableList;
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
 */
public class DateExposureMetric implements PrivateAnalyticsMetric {

  private static final String VERSION = "v1";
  public static final String METRIC_NAME = "DateExposure-" + VERSION;
  private static final int NUMBER_CLASSIFICATIONS = 4;
  private static final ImmutableList<Integer> binsEdgesInDays = ImmutableList.of(0, 4, 7, 11);
  public static final int BIN_LENGTH = NUMBER_CLASSIFICATIONS * binsEdgesInDays.size();

  private final ExposureNotificationSharedPreferences exposureNotificationSharedPreferences;

  @Inject
  DateExposureMetric(
      ExposureNotificationSharedPreferences exposureNotificationSharedPreferences) {
    this.exposureNotificationSharedPreferences = exposureNotificationSharedPreferences;
  }

  @Override
  public ListenableFuture<List<Integer>> getDataVector() {
    int[] data = new int[BIN_LENGTH];

    Instant exposureNotificationTime = exposureNotificationSharedPreferences
        .getExposureNotificationLastShownTime();
    Instant privateAnalyticsWorkerLastTime = exposureNotificationSharedPreferences
        .getPrivateAnalyticsWorkerLastTime();

    if (exposureNotificationTime.isAfter(privateAnalyticsWorkerLastTime)) {
      // A notification has been shown since the last private analytics submission,
      // we will populate one of the bins.
      int classificationIndex = exposureNotificationSharedPreferences
          .getExposureNotificationLastShownClassification();
      if (classificationIndex >= 1 && classificationIndex <= 4) {
        int offset = (classificationIndex - 1) * binsEdgesInDays.size();
        Instant exposureTime = exposureNotificationSharedPreferences
            .getPrivateAnalyticsLastExposureTime();
        int index = -1;
        for (Integer edge : binsEdgesInDays) {
          if (exposureTime.isBefore(exposureNotificationTime.minus(Duration.ofDays(edge)))) {
            index++;
          }
        }
        if (index != -1 && index < binsEdgesInDays.size()) {
          data[offset + index] = 1;
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
