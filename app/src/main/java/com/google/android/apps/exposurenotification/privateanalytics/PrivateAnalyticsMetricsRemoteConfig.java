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

package com.google.android.apps.exposurenotification.privateanalytics;

import android.net.Uri;
import androidx.annotation.VisibleForTesting;
import androidx.concurrent.futures.CallbackToFutureAdapter;
import com.android.volley.Response.ErrorListener;
import com.android.volley.Response.Listener;
import com.android.volley.VolleyError;
import com.google.android.apps.exposurenotification.common.Qualifiers.LightweightExecutor;
import com.google.android.apps.exposurenotification.common.logging.Logger;
import com.google.android.apps.exposurenotification.network.RequestQueueWrapper;
import com.google.android.apps.exposurenotification.privateanalytics.MetricsRemoteConfigs.Builder;
import com.google.android.libraries.privateanalytics.DefaultPrivateAnalyticsRemoteConfig.FetchRemoteConfigRequest;
import com.google.android.libraries.privateanalytics.PrivateAnalyticsEventListener;
import com.google.android.libraries.privateanalytics.Qualifiers.RemoteConfigUri;
import com.google.android.libraries.privateanalytics.utils.VolleyUtils;
import com.google.common.base.Optional;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import javax.inject.Inject;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Remote config for metrics values (namely sampling rate and epsilon).
 */
public class PrivateAnalyticsMetricsRemoteConfig {

  private static final Logger logcat = Logger.getLogger("ENPARemoteConfig");

  // Metric "Periodic Exposure Interaction"
  @VisibleForTesting
  static final String CONFIG_METRIC_INTERACTION_COUNT_SAMPLING_PROB_KEY =
      "enpa_metric_interaction_count_v1_sampling_prob";
  @VisibleForTesting
  static final String CONFIG_METRIC_INTERACTION_COUNT_PRIO_EPSILON_KEY =
      "enpa_metric_interaction_count_v1_prio_epsilon";

  // Metric "Periodic Exposure Notification"
  @VisibleForTesting
  static final String CONFIG_METRIC_NOTIFICATION_COUNT_SAMPLING_PROB_KEY =
      "enpa_metric_notification_count_v1_sampling_prob";
  @VisibleForTesting
  static final String CONFIG_METRIC_NOTIFICATION_COUNT_PRIO_EPSILON_KEY =
      "enpa_metric_notification_count_v1_prio_epsilon";

  // Metric "Histogram"
  @VisibleForTesting
  static final String CONFIG_METRIC_RISK_HISTOGRAM_SAMPLING_PROB_KEY =
      "enpa_metric_risk_histogram_v2_sampling_prob";
  @VisibleForTesting
  static final String CONFIG_METRIC_RISK_HISTOGRAM_PRIO_EPSILON_KEY =
      "enpa_metric_risk_histogram_v2_prio_epsilon";

  // Metric "Code Verified"
  @VisibleForTesting
  static final String CONFIG_METRIC_CODE_VERIFIED_SAMPLING_PROB_KEY =
      "enpa_metric_code_verified_v1_sampling_prob";
  @VisibleForTesting
  static final String CONFIG_METRIC_CODE_VERIFIED_PRIO_EPSILON_KEY =
      "enpa_metric_code_verified_v1_prio_epsilon";

  // Metric "Code Verified with Report Type"
  @VisibleForTesting
  static final String CONFIG_METRIC_CODE_VERIFIED_WITH_REPORT_TYPE_SAMPLING_PROB_KEY =
      "enpa_metric_code_verified_with_report_type_v2_sampling_prob";
  @VisibleForTesting
  static final String CONFIG_METRIC_CODE_VERIFIED_WITH_REPORT_TYPE_PRIO_EPSILON_KEY =
      "enpa_metric_code_verified_with_report_type_v2_prio_epsilon";

  // Metric "Keys Uploaded"
  @VisibleForTesting
  static final String CONFIG_METRIC_KEYS_UPLOADED_SAMPLING_PROB_KEY =
      "enpa_metric_keys_uploaded_v1_sampling_prob";
  @VisibleForTesting
  static final String CONFIG_METRIC_KEYS_UPLOADED_PRIO_EPSILON_KEY =
      "enpa_metric_keys_uploaded_v1_prio_epsilon";

  // Metric "Keys Uploaded with Report Type"
  @VisibleForTesting
  static final String CONFIG_METRIC_KEYS_UPLOADED_WITH_REPORT_TYPE_SAMPLING_PROB_KEY =
      "enpa_metric_keys_uploaded_with_report_type_v2_sampling_prob";
  @VisibleForTesting
  static final String CONFIG_METRIC_KEYS_UPLOADED_WITH_REPORT_TYPE_PRIO_EPSILON_KEY =
      "enpa_metric_keys_uploaded_with_report_type_v2_prio_epsilon";

  // Metric "Date Exposure"
  @VisibleForTesting
  static final String CONFIG_METRIC_DATE_EXPOSURE_SAMPLING_PROB_KEY =
      "enpa_metric_date_exposure_v1_sampling_prob";
  @VisibleForTesting
  static final String CONFIG_METRIC_DATE_EXPOSURE_PRIO_EPSILON_KEY =
      "enpa_metric_date_exposure_v1_prio_epsilon";

  // Metric "Keys Uploaded Vaccine Status"
  @VisibleForTesting
  static final String CONFIG_METRIC_KEYS_UPLOADED_VACCINE_STATUS_SAMPLING_PROB_KEY =
      "enpa_metric_keys_uploaded_vaccine_status_v1_sampling_prob";
  @VisibleForTesting
  static final String CONFIG_METRIC_KEYS_UPLOADED_VACCINE_STATUS_PRIO_EPSILON_KEY =
      "enpa_metric_keys_uploaded_vaccine_status_v1_prio_epsilon";

  // Metric "Exposure Notification followed by keys upload"
  @VisibleForTesting
  static final String CONFIG_METRIC_KEYS_UPLOADED_AFTER_NOTIFICATION_SAMPLING_PROB_KEY =
      "enpa_metric_secondary_attack_v2_sampling_prob";
  @VisibleForTesting
  static final String CONFIG_METRIC_KEYS_UPLOADED_AFTER_NOTIFICATION_PRIO_EPSILON_KEY =
      "enpa_metric_secondary_attack_v2_prio_epsilon";

  // Metric "Periodic Exposure Notification followed by keys upload"
  @VisibleForTesting
  static final String CONFIG_METRIC_PERIODIC_EXPOSURE_NOTIFICATION_BIWEEKLY_SAMPLING_PROB_KEY =
      "enpa_metric_periodic_exposure_notification_biweekly_v2_sampling_prob";
  @VisibleForTesting
  static final String CONFIG_METRIC_PERIODIC_EXPOSURE_NOTIFICATION_BIWEEKLY_PRIO_EPSILON_KEY =
      "enpa_metric_periodic_exposure_notification_biweekly_v2_prio_epsilon";

  // Metric "Date Exposure Biweekly"
  @VisibleForTesting
  static final String CONFIG_METRIC_DATE_EXPOSURE_BIWEEKLY_SAMPLING_PROB_KEY =
      "enpa_metric_date_exposure_v2_sampling_prob";
  @VisibleForTesting
  static final String CONFIG_METRIC_DATE_EXPOSURE_BIWEEKLY_PRIO_EPSILON_KEY =
      "enpa_metric_date_exposure_v2_prio_epsilon";

  // Metric "Keys Uploaded Vaccine Status Biweekly"
  @VisibleForTesting
  static final String CONFIG_METRIC_KEYS_UPLOADED_VACCINE_STATUS_BIWEEKLY_SAMPLING_PROB_KEY =
      "enpa_metric_keys_uploaded_vaccine_status_v2_sampling_prob";
  @VisibleForTesting
  static final String CONFIG_METRIC_KEYS_UPLOADED_VACCINE_STATUS_BIWEEKLY_PRIO_EPSILON_KEY =
      "enpa_metric_keys_uploaded_vaccine_status_v2_prio_epsilon";

  private final static MetricsRemoteConfigs DEFAULT_REMOTE_CONFIGS = MetricsRemoteConfigs
      .newBuilder().build();

  private final ListeningExecutorService lightweightExecutor;
  private final Uri remoteConfigUri;
  private final Optional<PrivateAnalyticsEventListener> logger;
  private final RequestQueueWrapper queue;

  @Inject
  PrivateAnalyticsMetricsRemoteConfig(
      @LightweightExecutor ListeningExecutorService lightweightExecutor,
      @RemoteConfigUri Uri remoteConfigUri,
      RequestQueueWrapper queue,
      Optional<PrivateAnalyticsEventListener> logger) {
    this.lightweightExecutor = lightweightExecutor;
    this.remoteConfigUri = remoteConfigUri;
    this.logger = logger;
    this.queue = queue;
  }

  public ListenableFuture<MetricsRemoteConfigs> fetchUpdatedConfigs() {
    return FluentFuture.from(fetchUpdatedConfigsJson())
        .transform(this::convertToRemoteConfig, lightweightExecutor)
        .catching(Exception.class, e -> {
          // Output the default RemoteConfigs for any exception thrown.
          logcat.e("Failed to fetch or convert remote configuration.", e);
          return DEFAULT_REMOTE_CONFIGS;
        }, lightweightExecutor);
  }

  private ListenableFuture<JSONObject> fetchUpdatedConfigsJson() {
    return CallbackToFutureAdapter.getFuture(
        completer -> {
          Listener<JSONObject> responseListener =
              response -> {
                logSuccess(response);
                completer.set(response);
              };
          ErrorListener errorListener =
              err -> {
                logFailure(err);
                completer.set(null);
              };
          FetchRemoteConfigRequest request = new FetchRemoteConfigRequest(remoteConfigUri,
              responseListener, errorListener);
          queue.add(request);
          return request;
        });
  }

  private void logSuccess(JSONObject response) {
    if (logger.isPresent()) {
      logger.get()
          .onPrivateAnalyticsRemoteConfigCallSuccess(
              response.toString().length());
    }
    logcat.d("Successfully fetched remote configs.");
  }

  private void logFailure(VolleyError err) {
    if (logger.isPresent()) {
      logger.get().onPrivateAnalyticsRemoteConfigCallFailure(err);
    }
    logcat.d("Remote Config Fetch Failed: " + VolleyUtils.getErrorBody(err).toString());
  }

  @VisibleForTesting
  MetricsRemoteConfigs convertToRemoteConfig(JSONObject jsonObject) {
    if (jsonObject == null) {
      logcat.e("Invalid jsonObj, using default remote configs");
      return DEFAULT_REMOTE_CONFIGS;
    }

    Builder remoteConfigBuilder = MetricsRemoteConfigs.newBuilder();
    try {

      // Notification Count Params
      if (jsonObject.has(CONFIG_METRIC_NOTIFICATION_COUNT_SAMPLING_PROB_KEY)) {
        remoteConfigBuilder.setNotificationCountPrioSamplingRate(
            jsonObject.getDouble(CONFIG_METRIC_NOTIFICATION_COUNT_SAMPLING_PROB_KEY));
      }
      if (jsonObject.has(CONFIG_METRIC_NOTIFICATION_COUNT_PRIO_EPSILON_KEY)) {
        remoteConfigBuilder.setNotificationCountPrioEpsilon(
            jsonObject.getDouble(CONFIG_METRIC_NOTIFICATION_COUNT_PRIO_EPSILON_KEY));
      }

      // Notification Interaction Count Params
      if (jsonObject.has(CONFIG_METRIC_INTERACTION_COUNT_SAMPLING_PROB_KEY)) {
        remoteConfigBuilder.setInteractionCountPrioSamplingRate(
            jsonObject.getDouble(CONFIG_METRIC_INTERACTION_COUNT_SAMPLING_PROB_KEY));
      }
      if (jsonObject.has(CONFIG_METRIC_INTERACTION_COUNT_PRIO_EPSILON_KEY)) {
        remoteConfigBuilder.setInteractionCountPrioEpsilon(
            jsonObject.getDouble(CONFIG_METRIC_INTERACTION_COUNT_PRIO_EPSILON_KEY));
      }

      // Risk score histogram Params
      if (jsonObject.has(CONFIG_METRIC_RISK_HISTOGRAM_SAMPLING_PROB_KEY)) {
        remoteConfigBuilder.setRiskScorePrioSamplingRate(
            jsonObject.getDouble(CONFIG_METRIC_RISK_HISTOGRAM_SAMPLING_PROB_KEY));
      }
      if (jsonObject.has(CONFIG_METRIC_RISK_HISTOGRAM_PRIO_EPSILON_KEY)) {
        remoteConfigBuilder.setRiskScorePrioEpsilon(
            jsonObject.getDouble(CONFIG_METRIC_RISK_HISTOGRAM_PRIO_EPSILON_KEY));
      }

      // Code verified metric params
      if (jsonObject.has(CONFIG_METRIC_CODE_VERIFIED_SAMPLING_PROB_KEY)) {
        remoteConfigBuilder.setCodeVerifiedPrioSamplingRate(
            jsonObject.getDouble(CONFIG_METRIC_CODE_VERIFIED_SAMPLING_PROB_KEY));
      }
      if (jsonObject.has(CONFIG_METRIC_CODE_VERIFIED_PRIO_EPSILON_KEY)) {
        remoteConfigBuilder.setCodeVerifiedPrioEpsilon(
            jsonObject.getDouble(CONFIG_METRIC_CODE_VERIFIED_PRIO_EPSILON_KEY));
      }

      // Code verified with report type metric params
      if (jsonObject.has(CONFIG_METRIC_CODE_VERIFIED_WITH_REPORT_TYPE_SAMPLING_PROB_KEY)) {
        remoteConfigBuilder.setCodeVerifiedWithReportTypePrioSamplingRate(
            jsonObject.getDouble(CONFIG_METRIC_CODE_VERIFIED_WITH_REPORT_TYPE_SAMPLING_PROB_KEY));
      }
      if (jsonObject.has(CONFIG_METRIC_CODE_VERIFIED_WITH_REPORT_TYPE_PRIO_EPSILON_KEY)) {
        remoteConfigBuilder.setCodeVerifiedWithReportTypePrioEpsilon(
            jsonObject.getDouble(CONFIG_METRIC_CODE_VERIFIED_WITH_REPORT_TYPE_PRIO_EPSILON_KEY));
      }

      // Keys uploaded metric params
      if (jsonObject.has(CONFIG_METRIC_KEYS_UPLOADED_SAMPLING_PROB_KEY)) {
        remoteConfigBuilder.setKeysUploadedPrioSamplingRate(
            jsonObject.getDouble(CONFIG_METRIC_KEYS_UPLOADED_SAMPLING_PROB_KEY));
      }
      if (jsonObject.has(CONFIG_METRIC_KEYS_UPLOADED_PRIO_EPSILON_KEY)) {
        remoteConfigBuilder.setKeysUploadedPrioEpsilon(
            jsonObject.getDouble(CONFIG_METRIC_KEYS_UPLOADED_PRIO_EPSILON_KEY));
      }

      // Keys uploaded with report type metric params
      if (jsonObject.has(CONFIG_METRIC_KEYS_UPLOADED_WITH_REPORT_TYPE_SAMPLING_PROB_KEY)) {
        remoteConfigBuilder.setKeysUploadedWithReportTypePrioSamplingRate(
            jsonObject.getDouble(CONFIG_METRIC_KEYS_UPLOADED_WITH_REPORT_TYPE_SAMPLING_PROB_KEY));
      }
      if (jsonObject.has(CONFIG_METRIC_KEYS_UPLOADED_WITH_REPORT_TYPE_PRIO_EPSILON_KEY)) {
        remoteConfigBuilder.setKeysUploadedWithReportTypePrioEpsilon(
            jsonObject.getDouble(CONFIG_METRIC_KEYS_UPLOADED_WITH_REPORT_TYPE_PRIO_EPSILON_KEY));
      }

      // Date Exposure metric params
      if (jsonObject.has(CONFIG_METRIC_DATE_EXPOSURE_SAMPLING_PROB_KEY)) {
        remoteConfigBuilder.setDateExposurePrioSamplingRate(
            jsonObject.getDouble(CONFIG_METRIC_DATE_EXPOSURE_SAMPLING_PROB_KEY));
      }
      if (jsonObject.has(CONFIG_METRIC_DATE_EXPOSURE_PRIO_EPSILON_KEY)) {
        remoteConfigBuilder.setDateExposurePrioEpsilon(
            jsonObject.getDouble(CONFIG_METRIC_DATE_EXPOSURE_PRIO_EPSILON_KEY));
      }

      // Keys uploaded vaccine status metric params
      if (jsonObject.has(CONFIG_METRIC_KEYS_UPLOADED_VACCINE_STATUS_SAMPLING_PROB_KEY)) {
        remoteConfigBuilder.setKeysUploadedVaccineStatusPrioSamplingRate(
            jsonObject.getDouble(CONFIG_METRIC_KEYS_UPLOADED_VACCINE_STATUS_SAMPLING_PROB_KEY));
      }
      if (jsonObject.has(CONFIG_METRIC_KEYS_UPLOADED_VACCINE_STATUS_PRIO_EPSILON_KEY)) {
        remoteConfigBuilder.setKeysUploadedVaccineStatusPrioEpsilon(
            jsonObject.getDouble(CONFIG_METRIC_KEYS_UPLOADED_VACCINE_STATUS_PRIO_EPSILON_KEY));
      }

      // Keys uploaded after notification metric params
      if (jsonObject.has(CONFIG_METRIC_KEYS_UPLOADED_AFTER_NOTIFICATION_SAMPLING_PROB_KEY)) {
        remoteConfigBuilder.setKeysUploadedAfterNotificationPrioSamplingRate(
            jsonObject.getDouble(CONFIG_METRIC_KEYS_UPLOADED_AFTER_NOTIFICATION_SAMPLING_PROB_KEY));
      }
      if (jsonObject.has(CONFIG_METRIC_KEYS_UPLOADED_AFTER_NOTIFICATION_PRIO_EPSILON_KEY)) {
        remoteConfigBuilder.setKeysUploadedAfterNotificationPrioEpsilon(
            jsonObject.getDouble(CONFIG_METRIC_KEYS_UPLOADED_AFTER_NOTIFICATION_PRIO_EPSILON_KEY));
      }

      // Periodic exposure notification biweekly metric params
      if (jsonObject.has(CONFIG_METRIC_PERIODIC_EXPOSURE_NOTIFICATION_BIWEEKLY_SAMPLING_PROB_KEY)) {
        remoteConfigBuilder.setPeriodicExposureNotificationBiweeklyPrioSamplingRate(
            jsonObject.getDouble(
                CONFIG_METRIC_PERIODIC_EXPOSURE_NOTIFICATION_BIWEEKLY_SAMPLING_PROB_KEY));
      }
      if (jsonObject.has(CONFIG_METRIC_PERIODIC_EXPOSURE_NOTIFICATION_BIWEEKLY_PRIO_EPSILON_KEY)) {
        remoteConfigBuilder.setPeriodicExposureNotificationBiweeklyPrioEpsilon(
            jsonObject
                .getDouble(CONFIG_METRIC_PERIODIC_EXPOSURE_NOTIFICATION_BIWEEKLY_PRIO_EPSILON_KEY));
      }

      // Date Exposure Biweekly metric params
      if (jsonObject.has(CONFIG_METRIC_DATE_EXPOSURE_BIWEEKLY_SAMPLING_PROB_KEY)) {
        remoteConfigBuilder.setDateExposureBiweeklyPrioSamplingRate(
            jsonObject.getDouble(CONFIG_METRIC_DATE_EXPOSURE_BIWEEKLY_SAMPLING_PROB_KEY));
      }
      if (jsonObject.has(CONFIG_METRIC_DATE_EXPOSURE_BIWEEKLY_PRIO_EPSILON_KEY)) {
        remoteConfigBuilder.setDateExposureBiweeklyPrioEpsilon(
            jsonObject.getDouble(CONFIG_METRIC_DATE_EXPOSURE_BIWEEKLY_PRIO_EPSILON_KEY));
      }

      // Keys uploaded vaccine status biweekly metric params
      if (jsonObject.has(CONFIG_METRIC_KEYS_UPLOADED_VACCINE_STATUS_BIWEEKLY_SAMPLING_PROB_KEY)) {
        remoteConfigBuilder.setKeysUploadedVaccineStatusBiweeklyPrioSamplingRate(
            jsonObject
                .getDouble(CONFIG_METRIC_KEYS_UPLOADED_VACCINE_STATUS_BIWEEKLY_SAMPLING_PROB_KEY));
      }
      if (jsonObject.has(CONFIG_METRIC_KEYS_UPLOADED_VACCINE_STATUS_BIWEEKLY_PRIO_EPSILON_KEY)) {
        remoteConfigBuilder.setKeysUploadedVaccineStatusBiweeklyPrioEpsilon(
            jsonObject
                .getDouble(CONFIG_METRIC_KEYS_UPLOADED_VACCINE_STATUS_BIWEEKLY_PRIO_EPSILON_KEY));
      }


    } catch (JSONException e) {
      logcat.e("Failed to parse remote config json, using defaults", e);
      return DEFAULT_REMOTE_CONFIGS;
    }
    return remoteConfigBuilder.build();
  }
}
