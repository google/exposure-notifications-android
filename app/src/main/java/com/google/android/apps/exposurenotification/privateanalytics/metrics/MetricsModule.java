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

import com.google.android.apps.exposurenotification.common.Qualifiers.BackgroundExecutor;
import com.google.android.apps.exposurenotification.privateanalytics.PrivateAnalyticsMetricsRemoteConfig;
import com.google.android.libraries.privateanalytics.PrioDataPoint;
import com.google.android.libraries.privateanalytics.PrivateAnalyticsSubmitter.PrioDataPointsProvider;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListeningExecutorService;
import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.components.ApplicationComponent;
import dagger.hilt.components.SingletonComponent;
import java.util.Arrays;

@Module
@InstallIn(SingletonComponent.class)
public class MetricsModule {

  @Provides
  public PrioDataPointsProvider providesMetrics(
      PrivateAnalyticsMetricsRemoteConfig privateAnalyticsMetricsRemoteConfig,
      @BackgroundExecutor ListeningExecutorService backgroundExecutor,
      PeriodicExposureNotificationMetric periodicExposureNotificationMetric,
      HistogramMetric histogramMetric,
      PeriodicExposureNotificationInteractionMetric periodicExposureNotificationInteractionMetric,
      CodeVerifiedMetric codeVerifiedMetric,
      KeysUploadedMetric keysUploadedMetric,
      DateExposureMetric dateExposureMetric
  ) {
    return () -> Futures
        .transform(privateAnalyticsMetricsRemoteConfig.fetchUpdatedConfigs(), remoteConfigs -> {
          double notificationCountSampleRate = remoteConfigs.notificationCountPrioSamplingRate();
          double notificationCountEpsilon = remoteConfigs.notificationCountPrioEpsilon();
          double riskScoreSampleRate = remoteConfigs.riskScorePrioSamplingRate();
          double riskScoreEpsilon = remoteConfigs.riskScorePrioEpsilon();
          double interactionCountSamplingRate = remoteConfigs.interactionCountPrioSamplingRate();
          double interactionCountEpsilon = remoteConfigs.interactionCountPrioEpsilon();
          double codeVerifiedSamplingRate = remoteConfigs.codeVerifiedPrioSamplingRate();
          double codeVerifiedEpsilon = remoteConfigs.codeVerifiedPrioEpsilon();
          double keysUploadedSamplingRate = remoteConfigs.keysUploadedPrioSamplingRate();
          double keysUploadedEpsilon = remoteConfigs.keysUploadedPrioEpsilon();
          double dateExposureSamplingRate = remoteConfigs.dateExposurePrioSamplingRate();
          double dateExposureEpsilon = remoteConfigs.dateExposurePrioEpsilon();

          return Arrays.asList(
              new PrioDataPoint(periodicExposureNotificationMetric, notificationCountEpsilon,
                  notificationCountSampleRate),
              new PrioDataPoint(histogramMetric, riskScoreEpsilon, riskScoreSampleRate),
              new PrioDataPoint(periodicExposureNotificationInteractionMetric,
                  interactionCountEpsilon, interactionCountSamplingRate),
              new PrioDataPoint(codeVerifiedMetric, codeVerifiedEpsilon, codeVerifiedSamplingRate),
              new PrioDataPoint(keysUploadedMetric, keysUploadedEpsilon, keysUploadedSamplingRate),
              new PrioDataPoint(dateExposureMetric, dateExposureEpsilon, dateExposureSamplingRate)
          );
        }, backgroundExecutor);
  }
}
