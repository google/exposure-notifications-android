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

import android.content.Context;
import androidx.annotation.VisibleForTesting;
import com.google.android.apps.exposurenotification.common.Qualifiers.ScheduledExecutor;
import com.google.android.apps.exposurenotification.common.TaskToFutureAdapter;
import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.android.apps.exposurenotification.nearby.ExposureNotificationClientWrapper;
import com.google.android.apps.exposurenotification.privateanalytics.MetricsSnapshot;
import com.google.android.gms.nearby.exposurenotification.DailySummariesConfig;
import com.google.android.gms.nearby.exposurenotification.ExposureWindow;
import com.google.android.gms.nearby.exposurenotification.ScanInstance;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.hilt.android.qualifiers.ApplicationContext;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import org.threeten.bp.Duration;
import org.threeten.bp.Instant;

/**
 * Class for generating an output vector that represents a risk score histogram of (attenuation X
 * infectiousness X duration X day bin)
 */
public class HistogramMetric implements PrivateAnalyticsMetric {

  private static final String VERSION = "v1";
  public static final String METRIC_NAME = "histogramMetric-" + VERSION;
  private static final ImmutableList<Double> attenuationBinLowerEdges =
      ImmutableList.of(50.1, 55.1, 60.1, 65.1, 70.1, 75.1, 80.1);
  // Note +1 in bin edges is to just be unambiguous about where integer values like 5 minutes fall
  private static final ImmutableList<Double> durationBinLowerEdges =
      ImmutableList.of(
          5 * 60 + 1.0,
          10 * 60 + 1.0,
          15 * 60 + 1.0,
          22.5 * 60 + 1.0,
          30 * 60 + 1.0,
          60 * 60 * 1.0,
          120 * 60 + 1.0);
  private static final ImmutableList<Duration> exposureDayBinLowerEdges =
      ImmutableList.of(
          Duration.ofDays(2),
          Duration.ofDays(4),
          Duration.ofDays(6),
          Duration.ofDays(8),
          Duration.ofDays(10),
          Duration.ofDays(12));
  @VisibleForTesting static final int NUM_ATTENUATION_BINS = attenuationBinLowerEdges.size() + 1;
  @VisibleForTesting static final int NUM_EXPOSURE_DAY_BINS = exposureDayBinLowerEdges.size() + 1;
  @VisibleForTesting static final int NUM_DURATION_BINS = durationBinLowerEdges.size() + 1;
  @VisibleForTesting static final int NUM_INFECTIOUSNESS_BINS = 3;
  // 0 →  INFECTIOUS_NONE 1 → STANDARD 2 → HIGH
  // these are defined in API
  // Pipeline latency
  // On any given day T, the k-hot vector for T - NUM_DAYS_TO_UPLOAD is calculated and uploaded. By
  // having a one-to-one mapping, we prevent duplicate uploads corresponding to the same day.
  private static final Duration NUM_DAYS_TO_UPLOAD = Duration.ofDays(14);
  private static final Duration API_TIMEOUT = Duration.ofSeconds(30);
  private final ScheduledExecutorService scheduledExecutor;
  private final ExposureNotificationClientWrapper exposureNotificationClientWrapper;
  private final Map<Integer, Double> reportToWeightMapping;
  private final Clock clock;

  @Inject
  HistogramMetric(
      @ApplicationContext Context context,
      @ScheduledExecutor ScheduledExecutorService scheduledExecutor,
      ExposureNotificationClientWrapper exposureNotificationClientWrapper,
      DailySummariesConfig dailySummariesConfig,
      Clock clock) {
    this.scheduledExecutor = scheduledExecutor;
    this.exposureNotificationClientWrapper = exposureNotificationClientWrapper;
    this.reportToWeightMapping = dailySummariesConfig.getReportTypeWeights();
    this.clock = clock;
  }

  @Override
  public ListenableFuture<List<Integer>> getDataVector(MetricsSnapshot metricsSnapshot) {
    // Compute total durations in each [infectiousnessBin, attenuationBin] pair
    // Now go over all exposureWindows, and put durations into corresponding bins
    return FluentFuture.from(
            TaskToFutureAdapter.getFutureWithTimeout(
                exposureNotificationClientWrapper.getExposureWindows(),
                API_TIMEOUT,
                scheduledExecutor))
        .transformAsync(
            windowList -> {
              // First initialize a 2D array to 0s
              double[][][] totalDurations =
                  new double[NUM_EXPOSURE_DAY_BINS][NUM_INFECTIOUSNESS_BINS]
                      [NUM_ATTENUATION_BINS];
              for (int i = 0; i < NUM_EXPOSURE_DAY_BINS; i++) {
                for (int j = 0; j < NUM_INFECTIOUSNESS_BINS; j++) {
                  for (int k = 0; k < NUM_ATTENUATION_BINS; k++) {
                    totalDurations[i][j][k] = 0;
                  }
                }
              }
              for (ExposureWindow exposureWindow : windowList) {
                if (reportToWeightMapping.get(exposureWindow.getReportType()) == 0.0) {
                  continue;
                }
                // skip exposures older than NUM_DAYS_TO_UPLOAD
                if (Instant.ofEpochMilli(exposureWindow.getDateMillisSinceEpoch())
                    .isBefore(clock.now().minus(NUM_DAYS_TO_UPLOAD))) {
                  continue;
                }
                int dayBin =
                    computeDayBin(
                        clock.now(),
                        Instant.ofEpochMilli(exposureWindow.getDateMillisSinceEpoch()));

                int infectiousnessBin = exposureWindow.getInfectiousness();
                for (ScanInstance scanInstance : exposureWindow.getScanInstances()) {
                  int attenuationBin =
                      computeAttenuationBin(scanInstance.getTypicalAttenuationDb());
                  totalDurations[dayBin][infectiousnessBin][attenuationBin] +=
                      scanInstance.getSecondsSinceLastScan();
                }
              }
              // Get a k-hot vector for upload
              int[] uploadVector =
                  new int
                      [NUM_EXPOSURE_DAY_BINS
                          * NUM_INFECTIOUSNESS_BINS
                          * NUM_ATTENUATION_BINS
                          * NUM_DURATION_BINS];
              for (int i = 0; i < NUM_EXPOSURE_DAY_BINS; i++) {
                for (int j = 0; j < NUM_INFECTIOUSNESS_BINS; j++) {
                  for (int l = 0; l < NUM_ATTENUATION_BINS; l++) {
                    int k = computeDurationBin(totalDurations[i][j][l]);
                    uploadVector[
                            i * NUM_INFECTIOUSNESS_BINS * NUM_ATTENUATION_BINS * NUM_DURATION_BINS
                                + j * NUM_ATTENUATION_BINS * NUM_DURATION_BINS
                                + l * NUM_DURATION_BINS
                                + k] =
                        1;
                  }
                }
              }
              return Futures.immediateFuture(Ints.asList(uploadVector));
            },
            scheduledExecutor);
  }

  // Helper functions to get which bin things fall into
  private static int computeAttenuationBin(double attenuation) {
    int i = 0;
    while (i < NUM_ATTENUATION_BINS - 1 && attenuation > attenuationBinLowerEdges.get(i)) {
      i++;
    }
    return i;
  }

  private static int computeDurationBin(double duration) {
    int i = 0;
    while (i < NUM_DURATION_BINS - 1 && duration > durationBinLowerEdges.get(i)) {
      i++;
    }
    return i;
  }

  private static int computeDayBin(Instant now, Instant exposureTime) {
    int i = 0;
    while (i < NUM_EXPOSURE_DAY_BINS - 1
        && exposureTime.isBefore(now.minus(exposureDayBinLowerEdges.get(i)))) {
      i++;
    }
    return i;
  }

  @Override
  public String getMetricName() {
    return METRIC_NAME;
  }

  @Override
  public int getMetricHammingWeight() {
    return NUM_EXPOSURE_DAY_BINS * NUM_INFECTIOUSNESS_BINS * NUM_ATTENUATION_BINS;
  }
}
