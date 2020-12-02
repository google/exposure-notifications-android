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

import android.net.Uri;
import android.util.Log;
import androidx.concurrent.futures.CallbackToFutureAdapter;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Response;
import com.android.volley.Response.ErrorListener;
import com.android.volley.Response.Listener;
import com.google.android.apps.exposurenotification.common.Qualifiers.LightweightExecutor;
import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.android.apps.exposurenotification.logging.AnalyticsLogger;
import com.google.android.apps.exposurenotification.network.RequestQueueWrapper;
import com.google.android.apps.exposurenotification.network.RespondableJsonObjectRequest;
import com.google.android.apps.exposurenotification.network.VolleyUtils;
import com.google.android.apps.exposurenotification.privateanalytics.Qualifiers.RemoteConfigUri;
import com.google.android.apps.exposurenotification.privateanalytics.RemoteConfigs.Builder;
import com.google.android.apps.exposurenotification.proto.RpcCall.RpcCallType;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import javax.inject.Inject;
import org.json.JSONException;
import org.json.JSONObject;
import org.threeten.bp.Duration;

/**
 * A class for fetching remote config values from a world-readable/verifiable static URL
 */
public class PrivateAnalyticsRemoteConfig {

  // Logging TAG
  private static final String TAG = "PrioRemoteConfig";

  private static final int MAX_RETRIES = 3;
  private static final float RETRY_BACKOFF = 1.0f;

  // General configuration
  private static final String CONFIG_ENABLED_KEY = "enpa_enabled";
  private static final String CONFIG_COLLECTION_FREQUENCY_KEY = "enpa_collection_frequency";

  // Metric "Periodic Exposure Interaction"
  private static final String CONFIG_METRIC_INTERACTION_COUNT_SAMPLING_PROB_KEY =
      "enpa_metric_interaction_count_v1_sampling_prob";
  private static final String CONFIG_METRIC_INTERACTION_COUNT_PRIO_EPSILON_KEY =
      "enpa_metric_interaction_count_v1_prio_epsilon";

  // Metric "Periodic Exposure Notification"
  private static final String CONFIG_METRIC_NOTIFICATION_COUNT_SAMPLING_PROB_KEY =
      "enpa_metric_notification_count_v1_sampling_prob";
  private static final String CONFIG_METRIC_NOTIFICATION_COUNT_PRIO_EPSILON_KEY =
      "enpa_metric_notification_count_v1_prio_epsilon";

  // Metric "Histogram"
  private static final String CONFIG_METRIC_RISK_HISTOGRAM_SAMPLING_PROB_KEY =
      "enpa_metric_risk_histogram_v1_sampling_prob";
  private static final String CONFIG_METRIC_RISK_HISTOGRAM_PRIO_EPSILON_KEY =
      "enpa_metric_risk_histogram_v1_prio_epsilon";

  // Other flags
  private static final String CONFIG_DEVICE_ATTESTATION_REQUIRED_KEY =
      "device_attestation_required";

  // Certificate Keys
  private static final String CONFIG_PHA_CERTIFICATE_KEY = "certificate_pha";
  private static final String CONFIG_FACILITATOR_CERTIFICATE_KEY = "certificate_facilitator";

  // Encryption Keys
  private static final String CONFIG_PHA_ENCRYPTION_KEY_ID = "encryption_key_id_pha";
  private static final String CONFIG_FACILITATOR_ENCRYPTION_KEY_ID = "encryption_key_id_facilitator";

  private static final Duration FETCH_CONFIG_TIMEOUT = Duration.ofSeconds(10);
  private final static RemoteConfigs DEFAULT_REMOTE_CONFIGS = RemoteConfigs.newBuilder().build();
  private final Clock clock;
  private final RequestQueueWrapper queue;
  private final Uri remoteConfigUri;
  private final ListeningExecutorService lightweightExecutor;
  private final AnalyticsLogger logger;

  @Inject
  PrivateAnalyticsRemoteConfig(
      @LightweightExecutor ListeningExecutorService lightweightExecutor,
      @RemoteConfigUri Uri remoteConfigUri,
      RequestQueueWrapper queue,
      AnalyticsLogger logger,
      Clock clock) {
    this.lightweightExecutor = lightweightExecutor;
    this.remoteConfigUri = remoteConfigUri;
    this.clock = clock;
    this.logger = logger;
    this.queue = queue;
  }

  public ListenableFuture<RemoteConfigs> fetchUpdatedConfigs() {
    return FluentFuture.from(fetchUpdatedConfigsJson())
        .transform(this::convertToRemoteConfig, lightweightExecutor);
  }

  private ListenableFuture<JSONObject> fetchUpdatedConfigsJson() {
    return CallbackToFutureAdapter.getFuture(
        completer -> {
          Listener<JSONObject> responseListener =
              response -> {
                logger.logRpcCallSuccessAsync(RpcCallType.RPC_TYPE_ENPA_REMOTE_CONFIG_FETCH,
                    response.toString().length());
                Log.d(TAG, "Successfully fetched remote configs.");
                completer.set(response);
              };
          ErrorListener errorListener =
              err -> {
                logger.logRpcCallFailureAsync(RpcCallType.RPC_TYPE_ENPA_REMOTE_CONFIG_FETCH, err);
                Log.d(TAG,
                    "Remote Config Fetch Failed: "
                        + VolleyUtils.getErrorBodyWithoutPadding(err).toString());
                completer.set(null);
              };
          FetchRemoteConfigRequest request = new FetchRemoteConfigRequest(remoteConfigUri,
              responseListener, errorListener, clock);
          queue.add(request);
          return request;
        });
  }

  private RemoteConfigs convertToRemoteConfig(JSONObject jsonObject) {
    if (jsonObject == null) {
      Log.e(TAG, "Invalid jsonObj, using default remote configs");
      return DEFAULT_REMOTE_CONFIGS;
    }

    Builder remoteConfigBuilder = RemoteConfigs.newBuilder();
    try {
      // General
      if (jsonObject.has(CONFIG_ENABLED_KEY)) {
        remoteConfigBuilder.setEnabled(
            jsonObject.getBoolean(CONFIG_ENABLED_KEY));
      }
      if (jsonObject.has(CONFIG_COLLECTION_FREQUENCY_KEY)) {
        remoteConfigBuilder.setCollectionFrequencyHours(
            jsonObject.getLong(CONFIG_COLLECTION_FREQUENCY_KEY));
      }
      if (jsonObject.has(CONFIG_DEVICE_ATTESTATION_REQUIRED_KEY)) {
        remoteConfigBuilder.setDeviceAttestationRequired(
            jsonObject.getBoolean(CONFIG_DEVICE_ATTESTATION_REQUIRED_KEY));
      }
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

      // Certificates
      if (jsonObject.has(CONFIG_PHA_CERTIFICATE_KEY)) {
        remoteConfigBuilder.setPhaCertificate(
            jsonObject.getString(CONFIG_PHA_CERTIFICATE_KEY));
      }
      if (jsonObject.has(CONFIG_FACILITATOR_CERTIFICATE_KEY)) {
        remoteConfigBuilder.setFacilitatorCertificate(
            jsonObject.getString(CONFIG_FACILITATOR_CERTIFICATE_KEY));
      }

      // Encryption Keys
      if (jsonObject.has(CONFIG_PHA_ENCRYPTION_KEY_ID)) {
        remoteConfigBuilder.setPhaEncryptionKeyId(
            jsonObject.getString(CONFIG_PHA_ENCRYPTION_KEY_ID));
      }
      if (jsonObject.has(CONFIG_FACILITATOR_ENCRYPTION_KEY_ID)) {
        remoteConfigBuilder.setFacilitatorEncryptionKeyId(
            jsonObject.getString(CONFIG_FACILITATOR_ENCRYPTION_KEY_ID));
      }
    } catch (JSONException e) {
      Log.e(TAG, "Failed to parse remote config json, using defaults", e);
      return DEFAULT_REMOTE_CONFIGS;
    }
    return remoteConfigBuilder.build();
  }

  /**
   * Construction of a Remote Config Request
   */
  private static class FetchRemoteConfigRequest extends RespondableJsonObjectRequest {

    FetchRemoteConfigRequest(
        Uri endpoint,
        Response.Listener<JSONObject> listener,
        Response.ErrorListener errorListener,
        Clock clock) {
      super(Method.GET, endpoint.toString(), /* jsonRequest= */ null, listener, errorListener,
          clock, /* isCoverTraffic= */ false);
      setRetryPolicy(new DefaultRetryPolicy((int) FETCH_CONFIG_TIMEOUT.toMillis(), MAX_RETRIES,
          RETRY_BACKOFF));
    }
  }
}
