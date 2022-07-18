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
import android.text.TextUtils;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.Qualifiers.BackgroundExecutor;
import com.google.android.apps.exposurenotification.privateanalytics.PrivateAnalyticsMetricsRemoteConfig;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.libraries.privateanalytics.MetricsCollection;
import com.google.android.libraries.privateanalytics.PrioDataPoint;
import com.google.android.libraries.privateanalytics.PrivateAnalyticsMetric;
import com.google.android.libraries.privateanalytics.PrivateAnalyticsSubmitter.PrioDataPointsProvider;
import com.google.android.libraries.privateanalytics.Qualifiers.BiweeklyMetricsUploadDay;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListeningExecutorService;
import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;
import java.util.ArrayList;
import java.util.List;

@Module
@InstallIn(SingletonComponent.class)
public class MetricsModule {

  @Provides
  @BiweeklyMetricsUploadDay
  public int providesBiweeklyMetricsUploadDay(
      ExposureNotificationSharedPreferences sharedPreferences) {
    return sharedPreferences.getBiweeklyMetricsUploadDay();
  }

  @Provides
  public PrioDataPointsProvider providesMetrics(
      @ApplicationContext Context context,
      PrivateAnalyticsMetricsRemoteConfig privateAnalyticsMetricsRemoteConfig,
      @BackgroundExecutor ListeningExecutorService backgroundExecutor,
      PeriodicExposureNotificationMetric periodicExposureNotificationMetric,
      HistogramMetric histogramMetric,
      PeriodicExposureNotificationInteractionMetric periodicExposureNotificationInteractionMetric,
      CodeVerifiedMetric codeVerifiedMetric,
      CodeVerifiedWithReportTypeMetric codeVerifiedWithReportTypeMetric,
      KeysUploadedMetric keysUploadedMetric,
      KeysUploadedWithReportTypeMetric keysUploadedWithReportTypeMetric,
      DateExposureMetric dateExposureMetric,
      KeysUploadedVaccineStatusMetric keysUploadedVaccineStatusMetric,
      KeysUploadedAfterNotificationMetric keysUploadedAfterNotificationMetric,
      PeriodicExposureNotificationBiweeklyMetric periodicExposureNotificationBiweeklyMetric,
      DateExposureBiweeklyMetric dateExposureBiweeklyMetric,
      KeysUploadedVaccineStatusBiweeklyMetric keysUploadedVaccineStatusBiweeklyMetric
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
          double codeVerifiedWithReportTypeSamplingRate = remoteConfigs
              .codeVerifiedWithReportTypePrioSamplingRate();
          double codeVerifiedWithReportTypeEpsilon = remoteConfigs
              .codeVerifiedWithReportTypePrioEpsilon();
          double keysUploadedSamplingRate = remoteConfigs.keysUploadedPrioSamplingRate();
          double keysUploadedEpsilon = remoteConfigs.keysUploadedPrioEpsilon();
          double keysUploadedWithReportTypeSamplingRate = remoteConfigs
              .keysUploadedWithReportTypePrioSamplingRate();
          double keysUploadedWithReportTypeEpsilon = remoteConfigs
              .keysUploadedWithReportTypePrioEpsilon();
          double dateExposureSamplingRate = remoteConfigs.dateExposurePrioSamplingRate();
          double dateExposureEpsilon = remoteConfigs.dateExposurePrioEpsilon();
          double keysUploadedVaccineStatusSamplingRate = remoteConfigs
              .keysUploadedVaccineStatusPrioSamplingRate();
          double keysUploadedVaccineStatusEpsilon = remoteConfigs
              .keysUploadedVaccineStatusPrioEpsilon();
          double keysUploadedAfterNotificationSamplingRate = remoteConfigs
              .keysUploadedAfterNotificationPrioSamplingRate();
          double keysUploadedAfterNotificationEpsilon = remoteConfigs
              .keysUploadedAfterNotificationPrioEpsilon();
          double periodicExposureBiweeklySamplingRate = remoteConfigs
              .periodicExposureNotificationBiweeklyPrioSamplingRate();
          double periodicExposureBiweeklyEpsilon = remoteConfigs
              .periodicExposureNotificationBiweeklyPrioEpsilon();
          double dateExposureBiweeklySamplingRate = remoteConfigs
              .dateExposureBiweeklyPrioSamplingRate();
          double dateExposureBiweeklyEpsilon = remoteConfigs
              .dateExposureBiweeklyPrioEpsilon();
          double keysUploadedVaccineStatusBiweeklySamplingRate = remoteConfigs
              .keysUploadedVaccineStatusBiweeklyPrioSamplingRate();
          double keysUploadedVaccineStatusBiweeklyEpsilon = remoteConfigs
              .keysUploadedVaccineStatusBiweeklyPrioEpsilon();

          List<PrioDataPoint> dailyMetricsList = new ArrayList<>();
          addDataPoint(dailyMetricsList, periodicExposureNotificationMetric,
              notificationCountEpsilon,
              notificationCountSampleRate);
          addDataPoint(dailyMetricsList, histogramMetric, riskScoreEpsilon, riskScoreSampleRate);
          addDataPoint(dailyMetricsList, periodicExposureNotificationInteractionMetric,
              interactionCountEpsilon, interactionCountSamplingRate);
          addDataPoint(dailyMetricsList, codeVerifiedMetric, codeVerifiedEpsilon,
              codeVerifiedSamplingRate);
          addDataPoint(dailyMetricsList, keysUploadedMetric, keysUploadedEpsilon,
              keysUploadedSamplingRate);
          addDataPoint(dailyMetricsList, dateExposureMetric, dateExposureEpsilon,
              dateExposureSamplingRate);
          if (!TextUtils
              .isEmpty(context.getResources().getString(R.string.share_vaccination_detail))) {
            addDataPoint(dailyMetricsList, keysUploadedVaccineStatusMetric,
                keysUploadedVaccineStatusEpsilon,
                keysUploadedVaccineStatusSamplingRate);
          }
          List<PrioDataPoint> biweeklyMetricsList = new ArrayList<>();
          addDataPoint(biweeklyMetricsList, codeVerifiedWithReportTypeMetric,
              codeVerifiedWithReportTypeEpsilon,
              codeVerifiedWithReportTypeSamplingRate);
          addDataPoint(biweeklyMetricsList, keysUploadedWithReportTypeMetric,
              keysUploadedWithReportTypeEpsilon,
              keysUploadedWithReportTypeSamplingRate);
          addDataPoint(biweeklyMetricsList, keysUploadedAfterNotificationMetric,
              keysUploadedAfterNotificationEpsilon,
              keysUploadedAfterNotificationSamplingRate);
          addDataPoint(biweeklyMetricsList, periodicExposureNotificationBiweeklyMetric,
              periodicExposureBiweeklyEpsilon,
              periodicExposureBiweeklySamplingRate);
          addDataPoint(biweeklyMetricsList, dateExposureBiweeklyMetric,
              dateExposureBiweeklyEpsilon,
              dateExposureBiweeklySamplingRate);
          addDataPoint(biweeklyMetricsList, keysUploadedVaccineStatusBiweeklyMetric,
              keysUploadedVaccineStatusBiweeklyEpsilon,
              keysUploadedVaccineStatusBiweeklySamplingRate);

          return new MetricsCollection() {
            @Override
            public List<PrioDataPoint> getDailyMetrics() {
              return dailyMetricsList;
            }

            @Override
            public List<PrioDataPoint> getBiweeklyMetrics() {
              return biweeklyMetricsList;
            }
          };
        }, backgroundExecutor);
  }

  private void addDataPoint(List<PrioDataPoint> metricsList, PrivateAnalyticsMetric metric,
      double epsilon, double sampleRate) {
    PrioDataPoint point = new PrioDataPoint(metric, epsilon, sampleRate);
    if (point.isValid()) {
      metricsList.add(point);
    }
  }

}
