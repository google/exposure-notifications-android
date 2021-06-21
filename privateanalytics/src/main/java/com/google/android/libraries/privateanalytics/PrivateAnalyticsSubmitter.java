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

package com.google.android.libraries.privateanalytics;

import android.os.Build.VERSION_CODES;
import android.text.TextUtils;
import android.util.Log;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;
import com.google.android.libraries.privateanalytics.proto.CreatePacketsParameters;
import com.google.android.libraries.privateanalytics.proto.PrioAlgorithmParameters;
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

  public enum SubmissionStatus {
    SUCCESS,
    FAILURE
  }

  /**
   * Instances of this interface are tasked with collecting and providing the metrics data that will
   * then be submitted through Prio.
   */
  public interface PrioDataPointsProvider {

    ListenableFuture<List<PrioDataPoint>> get();
  }

  // PrioAlgorithmParameters default values.
  private static final int NUMBER_SERVERS = 2;
  private static final long PRIME = 4293918721L;
  // Logging TAG
  private static final String TAG = "PAPrioSubmitter";
  private final ExecutorService backgroundExecutor = Executors.getBackgroundListeningExecutor();
  private final SecureRandom secureRandom = new SecureRandom();
  private final PrivateAnalyticsRemoteConfig privateAnalyticsRemoteConfig;
  private final PrivateAnalyticsFirestoreRepository privateAnalyticsFirestoreRepository;
  private final PrivateAnalyticsEnabledProvider privateAnalyticsEnabledProvider;
  private Prio prio = new PrioJni();

  // Metrics
  private final PrioDataPointsProvider prioDataPointsProvider;

  @Inject
  public PrivateAnalyticsSubmitter(
      PrioDataPointsProvider prioDataPointsProvider,
      PrivateAnalyticsRemoteConfig privateAnalyticsRemoteConfig,
      PrivateAnalyticsFirestoreRepository privateAnalyticsFirestoreRepository,
      PrivateAnalyticsEnabledProvider privateAnalyticsEnabledProvider) {
    this.prioDataPointsProvider = prioDataPointsProvider;
    this.privateAnalyticsRemoteConfig = privateAnalyticsRemoteConfig;
    this.privateAnalyticsFirestoreRepository = privateAnalyticsFirestoreRepository;
    this.privateAnalyticsEnabledProvider = privateAnalyticsEnabledProvider;
  }

  @RequiresApi(api = VERSION_CODES.N)
  public ListenableFuture<?> submitPackets() {
    return FluentFuture.from(privateAnalyticsRemoteConfig.fetchUpdatedConfigs())
        .transform(remoteConfigs -> {
          boolean remoteEnabled = remoteConfigs.enabled();

          if (!remoteEnabled) {
            Log.i(TAG, "Private analytics not enabled");
            return Futures.immediateFuture(null);
          }

          if (!privateAnalyticsEnabledProvider.isEnabledForUser()) {
            Log.i(TAG, "Private analytics enabled but not turned on");
            return Futures.immediateFuture(null);
          }

          if (TextUtils.isEmpty(remoteConfigs.facilitatorCertificate())
              || TextUtils.isEmpty(remoteConfigs.phaCertificate())) {
            Log.i(TAG,
                "Private analytics enabled but missing a facilitator/PHA certificate");
            return Futures.immediateFuture(null);
          }

          Log.d(TAG, "Private analytics enabled, proceeding with packet submission.");

          return FluentFuture.from(prioDataPointsProvider.get())
              .transform(metricsList -> {
                List<ListenableFuture<SubmissionStatus>> submitMetricFutures = new ArrayList<>();
                for (PrioDataPoint metric : metricsList) {
                  if (sampleWithRate(metric.getMetric(), metric.getSampleRate())) {
                    submitMetricFutures.add(
                        generateAndSubmitMetric(metric, remoteConfigs));
                  }
                }
                return Futures
                    .transform(Futures.successfulAsList(submitMetricFutures), ImmutableList::copyOf,
                        backgroundExecutor);
          }, backgroundExecutor);
        }, backgroundExecutor);
  }


  @RequiresApi(api = VERSION_CODES.N)
  public ListenableFuture<SubmissionStatus> generateAndSubmitMetric(PrioDataPoint prioDataPoint,
      RemoteConfigs remoteConfigs) {
    String metricName = prioDataPoint.getMetric().getMetricName();

    return FluentFuture
        .from(prioDataPoint.getMetric().getDataVector())
        .transform(
            dataVector ->
                generatePacketsParameters(dataVector, prioDataPoint.getEpsilon(),
                    remoteConfigs.phaCertificate(),
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
              Log.d(TAG,
                  String
                      .format("Workflow for prioDataPoint %s finished successfully.", metricName));
              return SubmissionStatus.SUCCESS;
            }, backgroundExecutor)
        .catching(
            Exception.class,
            e -> {
              Log.w(TAG, "Error submitting prioDataPoint" + metricName, e);
              return SubmissionStatus.FAILURE;
            },
            backgroundExecutor);
  }

  private boolean sampleWithRate(PrivateAnalyticsMetric privateMetric, double sampleRate) {
    if (secureRandom.nextDouble() > sampleRate) {
      Log.d(TAG,
          "Skipping sample for metric " + privateMetric.getMetricName() + ". samplingRate="
              + sampleRate);
      return false;
    }
    return true;
  }

  private CreatePacketsParameters generatePacketsParameters(List<Integer> data, double epsilon,
      String phaCert, String facilitatorCert) {
    PrioAlgorithmParameters prioParams = PrioAlgorithmParameters.newBuilder()
        .setBins(data.size())
        .setEpsilon(epsilon)
        .setNumberServers(NUMBER_SERVERS)
        .setPrime(PRIME).build();

    Log.i(
        TAG,
        "Generating packets w/ params: bins="
            + prioParams.getBins()
            + " epsilon="
            + prioParams.getEpsilon());

    // Construct the CreatePacketsParameters
    CreatePacketsParameters.Builder createParamsBuilder = CreatePacketsParameters.newBuilder();
    createParamsBuilder.addAllDataBits(data);
    createParamsBuilder.setPrioParameters(prioParams);
    createParamsBuilder.addPublicKeys(phaCert);
    createParamsBuilder.addPublicKeys(facilitatorCert);
    return createParamsBuilder.build();
  }

  @VisibleForTesting
  public void setPrio(Prio prio) {
    this.prio = prio;
  }

}
