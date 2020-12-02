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

package com.google.android.apps.exposurenotification.privateanalytics;

import static com.google.common.util.concurrent.Futures.immediateFuture;

import android.os.Build.VERSION_CODES;
import android.util.Log;
import androidx.annotation.RequiresApi;
import androidx.work.ListenableWorker.Result;
import com.google.android.apps.exposurenotification.common.Qualifiers.BackgroundExecutor;
import com.google.android.apps.exposurenotification.privateanalytics.metrics.HistogramMetric;
import com.google.android.apps.exposurenotification.privateanalytics.metrics.PeriodicExposureNotificationInteractionMetric;
import com.google.android.apps.exposurenotification.privateanalytics.metrics.PeriodicExposureNotificationMetric;
import com.google.android.apps.exposurenotification.privateanalytics.metrics.PrivateAnalyticsMetric;
import com.google.android.apps.exposurenotification.proto.CreatePacketsParameters;
import com.google.android.apps.exposurenotification.proto.PrioAlgorithmParameters;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import javax.inject.Inject;

/**
 * A thin class for submitting encrypted private analytic packets to the ingestion server.
 *
 * <p>This implementation happens to make use of Firebase Firestore as a convenient way to send up
 * the packets to a scalable NoSQL db for subsequent batching and aggregation. Alternative
 * implementations might operate a custom backend endpoint to accumulate the packets, or use a
 * pubsub mechanism. Since the packets are encrypted on device, the channel over which the packets
 * travel need not be trusted.
 */
public class PrivateAnalyticsSubmitter {

  // PrioAlgorithmParameters default values.
  private static final int NUMBER_SERVERS = 2;
  private static final long PRIME = 4293918721L;
  // Logging TAG
  private static final String TAG = "PrioSubmitter";
  private final ExecutorService backgroundExecutor;
  private final SecureRandom secureRandom;
  private final PrivateAnalyticsRemoteConfig privateAnalyticsRemoteConfig;
  private final PrivateAnalyticsFirestoreRepository privateAnalyticsFirestoreRepository;
  private final ExposureNotificationSharedPreferences exposureNotificationSharedPreferences;
  private final Prio prio;

  // Metrics
  private final HistogramMetric histogramMetric;
  private final PeriodicExposureNotificationMetric periodicExposureNotificationMetric;
  private final PeriodicExposureNotificationInteractionMetric
      periodicExposureNotificationInteractionMetric;

  @Inject
  PrivateAnalyticsSubmitter(
      @BackgroundExecutor ExecutorService backgroundExecutor,
      SecureRandom secureRandom,
      HistogramMetric histogramMetric,
      PeriodicExposureNotificationMetric periodicExposureNotificationMetric,
      PrivateAnalyticsRemoteConfig privateAnalyticsRemoteConfig,
      PrivateAnalyticsFirestoreRepository privateAnalyticsFirestoreRepository,
      ExposureNotificationSharedPreferences exposureNotificationSharedPreferences,
      PeriodicExposureNotificationInteractionMetric periodicExposureNotificationInteractionMetric,
      Prio prio) {
    this.backgroundExecutor = backgroundExecutor;
    this.secureRandom = secureRandom;
    this.histogramMetric = histogramMetric;
    this.periodicExposureNotificationMetric = periodicExposureNotificationMetric;
    this.privateAnalyticsRemoteConfig = privateAnalyticsRemoteConfig;
    this.privateAnalyticsFirestoreRepository = privateAnalyticsFirestoreRepository;
    this.exposureNotificationSharedPreferences = exposureNotificationSharedPreferences;
    this.periodicExposureNotificationInteractionMetric =
        periodicExposureNotificationInteractionMetric;
    this.prio = prio;
  }

  @RequiresApi(api = VERSION_CODES.N)
  public ListenableFuture<?> submitPackets() {
    return FluentFuture.from(privateAnalyticsRemoteConfig.fetchUpdatedConfigs())
        .transform(remoteConfigs -> {
          boolean remoteEnabled = remoteConfigs.enabled();
          double notificationCountSampleRate = remoteConfigs.notificationCountPrioSamplingRate();
          double notificationCountEpsilon = remoteConfigs.notificationCountPrioEpsilon();
          double riskScoreSampleRate = remoteConfigs.riskScorePrioSamplingRate();
          double riskScoreEpsilon = remoteConfigs.riskScorePrioEpsilon();
          double interactionCountSamplingRate = remoteConfigs.interactionCountPrioSamplingRate();
          double interactionCountEpsilon = remoteConfigs.interactionCountPrioEpsilon();
          MetricsSnapshot metricsSnapshot = MetricsSnapshot
              .fromPreferences(exposureNotificationSharedPreferences);

          if (!remoteEnabled) {
            Log.i(TAG, "Private analytics not enabled");
            return immediateFuture(null);
          }

          if (!exposureNotificationSharedPreferences.getPrivateAnalyticState()) {
            Log.i(TAG, "Private analytics enabled but not turned on");
            return immediateFuture(null);
          }

          Log.d(TAG, "Private analytics enabled, proceeding with packet submission.");

          List<ListenableFuture<Result>> submitMetricFutures = new ArrayList<>();

          if (sampleWithRate(notificationCountSampleRate)) {
            submitMetricFutures
                .add(generateAndSubmitMetric(periodicExposureNotificationMetric, metricsSnapshot,
                    notificationCountEpsilon, remoteConfigs));
          }

          if (sampleWithRate(riskScoreSampleRate)) {
            submitMetricFutures
                .add(generateAndSubmitMetric(histogramMetric, metricsSnapshot, riskScoreEpsilon,
                    remoteConfigs));
          }

          if (sampleWithRate(interactionCountSamplingRate)) {
            submitMetricFutures
                .add(generateAndSubmitMetric(
                    periodicExposureNotificationInteractionMetric, metricsSnapshot,
                    interactionCountEpsilon,
                    remoteConfigs));
          }
          return FluentFuture.from(Futures.successfulAsList(submitMetricFutures))
              .transform(ImmutableList::copyOf, backgroundExecutor);
        }, backgroundExecutor);
  }


  @RequiresApi(api = VERSION_CODES.N)
  public ListenableFuture<Result> generateAndSubmitMetric(PrivateAnalyticsMetric privateMetric,
      MetricsSnapshot metricsSnapshot, double epsilon, RemoteConfigs remoteConfigs) {
    String metricName = privateMetric.getMetricName();

    return FluentFuture
        .from(privateMetric.getDataVector(metricsSnapshot))
        .transform(
            dataVector ->
                generatePacketsParameters(dataVector, epsilon, remoteConfigs.phaCertificate(),
                    remoteConfigs.facilitatorCertificate()),
            backgroundExecutor)
        .transform(
            packetsParam ->
                PrioPacketPayload.newBuilder()
                    .setCreatePacketsParameters(packetsParam)
                    .setCreatePacketsResponse(prio.getPackets(packetsParam))
                    .build(),
            backgroundExecutor)
        .transform(
            payload ->
                privateAnalyticsFirestoreRepository.writeNewPacketsResponse(
                    metricName,
                    payload,
                    remoteConfigs),
            backgroundExecutor)
        .transform(
            unused -> {
              privateMetric.resetData();
              Log.d(TAG,
                  String.format("Workflow for metric %s finished successfully.", metricName));
              return Result.success();
            }, backgroundExecutor)
        .catching(
            Exception.class,
            e -> {
              Log.w(TAG, "Error submitting metric" + metricName, e);
              return Result.failure();
            },
            backgroundExecutor);
  }

  private boolean sampleWithRate(double sampleRate) {
    if (secureRandom.nextDouble() > sampleRate) {
      Log.i(TAG, "Skipping sample. samplingRate=" + sampleRate);
      return false;
    }
    return true;
  }

  private CreatePacketsParameters generatePacketsParameters(List<Integer> data, double epsilon,
      String phaCert, String facilitatorCert) {
    PrioAlgorithmParameters.Builder prioParamsBuilder =
        PrioAlgorithmParameters.newBuilder()
            .setBins(data.size())
            .setEpsilon(epsilon)
            .setNumberServers(NUMBER_SERVERS)
            .setPrime(PRIME);

    PrioAlgorithmParameters prioParams = prioParamsBuilder.build();
    Log.i(
        TAG,
        "Generating packets w/ params: bins="
            + prioParams.getBins()
            + " epsilon="
            + prioParams.getEpsilon());
    if (prioParams.hasHammingWeight()) {
      Log.i(TAG, "Hamming weight specified =" + prioParams.getHammingWeight());
    }

    // Construct the CreatePacketsParameters
    CreatePacketsParameters.Builder createParamsBuilder = CreatePacketsParameters.newBuilder();
    createParamsBuilder.addAllDataBits(data);
    createParamsBuilder.setPrioParameters(prioParams);

    createParamsBuilder.addPublicKeys(phaCert);
    createParamsBuilder.addPublicKeys(facilitatorCert);
    return createParamsBuilder.build();
  }

}
