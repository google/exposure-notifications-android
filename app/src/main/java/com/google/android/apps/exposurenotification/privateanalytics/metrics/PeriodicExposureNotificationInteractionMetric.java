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

import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences.NotificationInteraction;
import com.google.android.libraries.privateanalytics.PrivateAnalyticsMetric;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;
import javax.inject.Inject;
import org.threeten.bp.Instant;

/**
 * Class for generating an output vector that represents the count of exposure notifications and its
 * interaction of a given period. This class only tracks the last notification seen in a period and
 * the interaction seen during that period. The vector returned is of length 9. The first bin
 * represents the no exposure, which also means implies no notification. The next 8 bins represent
 * the possible notification interaction for each exposure severity. 4 (severity bins) x 2 (possible
 * notification interactions) = 8 bins
 */
public class PeriodicExposureNotificationInteractionMetric implements PrivateAnalyticsMetric {

  private static final String VERSION = "v1";
  public static final String METRIC_NAME = "PeriodicExposureNotificationInteraction-" + VERSION;

  @VisibleForTesting
  static final int SEVERITY_BINS_COUNT = 4;
  @VisibleForTesting
  static final int INTERACTION_TYPE_COUNT = 2;

  @VisibleForTesting
  static final int VECTOR_LENGTH = 1 + SEVERITY_BINS_COUNT * INTERACTION_TYPE_COUNT;

  @VisibleForTesting
  static final int NO_EXPOSURE_BIN_ID = 0;

  private static final int CLICKED_BIN_OFFSET = 0;
  private static final int DISMISSED_BIN_OFFSET = 1;

  private final ExposureNotificationSharedPreferences exposureNotificationSharedPreferences;

  @Inject
  PeriodicExposureNotificationInteractionMetric(
      ExposureNotificationSharedPreferences exposureNotificationSharedPreferences) {
    this.exposureNotificationSharedPreferences = exposureNotificationSharedPreferences;
  }

  // returns the index of the bin id which can be either one of the 9 bins
  private int getExposureBinId() {
    Instant interactionLastTime = exposureNotificationSharedPreferences
        .getExposureNotificationLastInteractionTime();
    Instant privateAnalyticsWorkerLastTime = exposureNotificationSharedPreferences
        .getPrivateAnalyticsWorkerLastTimeForDaily();

    if (interactionLastTime.equals(Instant.EPOCH)) {
      // If Instant.EPOCH is returned, it means that no interaction was performed.
      return NO_EXPOSURE_BIN_ID;
    } else if (interactionLastTime.isBefore(privateAnalyticsWorkerLastTime)) {
      // The interaction should have been reported at the last Private Analytics worker run.
      // We report no exposure.
      return NO_EXPOSURE_BIN_ID;
    }

    int notificationLastInteractionClassification = exposureNotificationSharedPreferences
        .getExposureNotificationLastInteractionClassification();
    NotificationInteraction interaction = exposureNotificationSharedPreferences
        .getExposureNotificationLastInteractionType();

    int binId = (notificationLastInteractionClassification * INTERACTION_TYPE_COUNT +
        interactionToExposureBinOffset(interaction)) - 1;
    if (binId < 0 || binId >= VECTOR_LENGTH) {
      return NO_EXPOSURE_BIN_ID;
    }
    return binId;
  }

  private static int interactionToExposureBinOffset(NotificationInteraction interaction) {
    switch (interaction) {
      case CLICKED:
        return CLICKED_BIN_OFFSET;
      case DISMISSED:
        return DISMISSED_BIN_OFFSET;
    }
    throw new IllegalStateException("NotificationInteraction.UNKNOWN not accepted");
  }

  @Override
  public ListenableFuture<List<Integer>> getDataVector() {
    int index = getExposureBinId();
    int[] data = new int[VECTOR_LENGTH];
    data[index] = 1;
    return Futures.immediateFuture(Ints.asList(data));
  }

  @Override
  public String getMetricName() {
    return METRIC_NAME;
  }

  @Override
  public int getMetricHammingWeight() {
    return 1;
  }
}
