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
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;
import com.google.android.libraries.privateanalytics.Qualifiers.BiweeklyMetricsUploadDay;
import com.google.android.libraries.privateanalytics.proto.CreatePacketsParameters;
import com.google.android.libraries.privateanalytics.proto.PrioAlgorithmParameters;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Calendar;
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
   * Instances of this interface are tasked with collecting and providing the
   * metrics data that will then be submitted through Prio.
   */
  public interface PrioDataPointsProvider {

    ListenableFuture<MetricsCollection> get();
  }

  public static class NotEnabledException extends Exception {

  }

  // PrioAlgorithmParameters default values.
  private static final int NUMBER_SERVERS = 2;
  private static final long PRIME = 4293918721L;
  // Logging TAG
  private static final String TAG = "PAPrioSubmitter";
  private final ExecutorService backgroundExecutor = Executors
      .getBackgroundListeningExecutor();
  private final SecureRandom secureRandom = new SecureRandom();
  private final PrivateAnalyticsRemoteConfig privateAnalyticsRemoteConfig;
  private final PrivateAnalyticsFirestoreRepository privateAnalyticsFirestoreRepository;
  private final PrivateAnalyticsEnabledProvider privateAnalyticsEnabledProvider;
  private final PrivateAnalyticsLogger logger;
  private Prio prio;
  private final int biweeklyMetricsUploadDay;

  // Metrics
  private final PrioDataPointsProvider prioDataPointsProvider;

  @Inject
  public PrivateAnalyticsSubmitter(
      PrioDataPointsProvider prioDataPointsProvider,
      PrivateAnalyticsRemoteConfig privateAnalyticsRemoteConfig,
      PrivateAnalyticsFirestoreRepository privateAnalyticsFirestoreRepository,
      PrivateAnalyticsEnabledProvider privateAnalyticsEnabledProvider,
      PrivateAnalyticsLogger.Factory loggerFactory,
      @BiweeklyMetricsUploadDay int biweeklyMetricsUploadDay) {
    this.prioDataPointsProvider = prioDataPointsProvider;
    this.privateAnalyticsRemoteConfig = privateAnalyticsRemoteConfig;
    this.privateAnalyticsFirestoreRepository = privateAnalyticsFirestoreRepository;
    this.privateAnalyticsEnabledProvider = privateAnalyticsEnabledProvider;
    this.logger = loggerFactory.create(TAG);
    this.prio = new PrioJni(loggerFactory);
    this.biweeklyMetricsUploadDay = biweeklyMetricsUploadDay;
  }

  @RequiresApi(api = VERSION_CODES.N)
  public ListenableFuture<ImmutableList<SubmissionStatus>> submitPackets() {
    return FluentFuture.from(privateAnalyticsRemoteConfig.fetchUpdatedConfigs())
        .transformAsync(remoteConfigs -> {
          boolean remoteEnabled = remoteConfigs.enabled();

          if (!remoteEnabled) {
            logger.i("Private analytics not enabled");
            return Futures.immediateFailedFuture(new NotEnabledException());
          }

          if (!privateAnalyticsEnabledProvider.isEnabledForUser()) {
            logger.i("Private analytics enabled but not turned on");
            return Futures.immediateFailedFuture(new NotEnabledException());
          }

          if (TextUtils.isEmpty(remoteConfigs.facilitatorCertificate())
              || TextUtils.isEmpty(remoteConfigs.phaCertificate())) {
            logger.i(
                "Private analytics enabled but missing a facilitator/PHA certificate");
            return Futures.immediateFailedFuture(new NotEnabledException());
          }

          logger.d("Private analytics enabled, proceeding with packet submission.");

          return FluentFuture.from(prioDataPointsProvider.get())
              .transformAsync(metricsCollection -> {
                List<ListenableFuture<SubmissionStatus>> submitMetricFutures = new ArrayList<>(
                    submitMetricsFromList(metricsCollection.getDailyMetrics(),
                        remoteConfigs));

                if (isCalendarTheBiweeklyMetricsUploadDay(
                    biweeklyMetricsUploadDay, Calendar.getInstance())) {
                  submitMetricFutures.addAll(
                      submitMetricsFromList(
                          metricsCollection.getBiweeklyMetrics(),
                          remoteConfigs));
                }

                return Futures
                    .transform(Futures.successfulAsList(submitMetricFutures),
                        ImmutableList::copyOf,
                        backgroundExecutor);
              }, backgroundExecutor);
        }, backgroundExecutor);
  }

  /**
   * Checks whether the {@code calendar} matches the {@code
   * biweeklyMetricsUpoadDay}
   * <p>
   * <p>
   * The biweekly metrics upload day should follow:<ul>
   * <li>upload_day % 7 + 1 == calendar.week_day</li>
   * <li>upload_day % 2 == calendar.week_of_year % 2</li>
   * </ul>
   */
  public static boolean isCalendarTheBiweeklyMetricsUploadDay(
      int biweeklyMetricsUploadDay, Calendar calendar) {
    if (calendar.get(Calendar.WEEK_OF_YEAR) % 2
        != biweeklyMetricsUploadDay / 7) {
      return false;
    }
    return calendar.get(Calendar.DAY_OF_WEEK) ==
        (biweeklyMetricsUploadDay % 7 + 1);
  }

  @RequiresApi(api = VERSION_CODES.N)
  private List<ListenableFuture<SubmissionStatus>> submitMetricsFromList(
      List<PrioDataPoint> metricsList,
      RemoteConfigs remoteConfigs) {
    List<ListenableFuture<SubmissionStatus>> pendingMetrics = new ArrayList<>(
        metricsList.size());
    for (PrioDataPoint metric : metricsList) {
      if (sampleWithRate(metric.getMetric(), metric.getSampleRate())) {
        pendingMetrics.add(generateAndSubmitMetric(metric, remoteConfigs));
      }
    }
    return pendingMetrics;
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
              logger.d(
                  String
                      .format("Workflow for prioDataPoint %s finished successfully.", metricName));
              return SubmissionStatus.SUCCESS;
            }, backgroundExecutor)
        .catching(
            Exception.class,
            e -> {
              logger.w("Error submitting prioDataPoint" + metricName, e);
              return SubmissionStatus.FAILURE;
            },
            backgroundExecutor);
  }

  private boolean sampleWithRate(PrivateAnalyticsMetric privateMetric, double sampleRate) {
    if (secureRandom.nextDouble() > sampleRate) {
      logger.d(
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

    logger.i(
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
