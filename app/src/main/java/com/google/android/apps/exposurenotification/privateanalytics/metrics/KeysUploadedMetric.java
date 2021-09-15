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
 * Class for generating an output vector that captures, when keys are uploaded, whether a
 * notification was shown in the past 14 days.
 * <p><ul>
 * Bins signification:
 * <li> 0: Error
 * <li> 1: No notification in the past 14 days
 * <li> 2: classification 1 exposure in past 14 days
 * <li> 3: classification 2 exposure in past 14 days
 * <li> 4: classification 3 exposure in past 14 days
 * <li> 5: classification 4 exposure in past 14 days
 * </ul>
 */
public class KeysUploadedMetric implements PrivateAnalyticsMetric {

  private static final String VERSION = "v1";
  public static final String METRIC_NAME = "KeysUploaded-" + VERSION;

  private static final Duration NUM_DAYS = Duration.ofDays(14);

  private static final int ERROR_BIN = 0; // Unused on Android.
  @VisibleForTesting
  static final int NO_NOTIFICATION_SHOWN_BIN = 1;
  @VisibleForTesting
  static final int NUM_CLASSIFICATION_BINS = 4;
  @VisibleForTesting
  static final int BIN_LENGTH = 2 + NUM_CLASSIFICATION_BINS;

  private final ExposureNotificationSharedPreferences exposureNotificationSharedPreferences;

  @Inject
  KeysUploadedMetric(
      ExposureNotificationSharedPreferences exposureNotificationSharedPreferences) {
    this.exposureNotificationSharedPreferences = exposureNotificationSharedPreferences;
  }

  @Override
  public ListenableFuture<List<Integer>> getDataVector() {
    int[] data = new int[BIN_LENGTH];

    Instant lastSubmittedKeysTime = exposureNotificationSharedPreferences
        .getPrivateAnalyticsLastSubmittedKeysTime();
    Instant workerLastTime = exposureNotificationSharedPreferences
        .getPrivateAnalyticsWorkerLastTimeForDaily();
    if (lastSubmittedKeysTime.isAfter(workerLastTime)) {
      // Keys have been submitted since the last analytics upload.
      // Check whether a notification was shown in the past NUM_DAYS and report its classification.
      Instant notificationLastShownTime = exposureNotificationSharedPreferences
          .getExposureNotificationLastShownTime();
      if (notificationLastShownTime.isAfter(lastSubmittedKeysTime.minus(NUM_DAYS))) {
        int index = exposureNotificationSharedPreferences
            .getExposureNotificationLastShownClassification() + 1;
        if (index >= 0 && index < BIN_LENGTH) {
          data[index] = 1;
        }
      } else {
        data[NO_NOTIFICATION_SHOWN_BIN] = 1;
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
