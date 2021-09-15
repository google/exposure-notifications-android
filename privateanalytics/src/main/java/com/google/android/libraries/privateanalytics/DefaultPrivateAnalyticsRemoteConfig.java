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

import android.net.Uri;
import androidx.annotation.VisibleForTesting;
import androidx.concurrent.futures.CallbackToFutureAdapter;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.Response.ErrorListener;
import com.android.volley.Response.Listener;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.BasicNetwork;
import com.android.volley.toolbox.HurlStack;
import com.android.volley.toolbox.NoCache;
import com.google.android.libraries.privateanalytics.Qualifiers.RemoteConfigUri;
import com.google.android.libraries.privateanalytics.RemoteConfigs.Builder;
import com.google.android.libraries.privateanalytics.utils.RequestQueueWrapper;
import com.google.android.libraries.privateanalytics.utils.RespondableJsonObjectRequest;
import com.google.android.libraries.privateanalytics.utils.VolleyUtils;
import com.google.common.base.Optional;
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
public class DefaultPrivateAnalyticsRemoteConfig implements PrivateAnalyticsRemoteConfig {

  // Logging TAG
  private static final String TAG = "PAPrioRemoteConfig";

  private static final int MAX_RETRIES = 3;
  private static final float RETRY_BACKOFF = 1.0f;

  // General configuration
  private static final String CONFIG_ENABLED_KEY = "enpa_enabled";
  private static final String CONFIG_COLLECTION_FREQUENCY_KEY = "enpa_collection_frequency";

  // Other flags
  private static final String CONFIG_DEVICE_ATTESTATION_REQUIRED_KEY =
      "device_attestation_required";

  // Certificate Keys
  private static final String CONFIG_PHA_CERTIFICATE_KEY = "certificate_pha";
  private static final String CONFIG_FACILITATOR_CERTIFICATE_KEY = "certificate_facilitator";

  // Encryption Key Id's
  private static final String CONFIG_PHA_ENCRYPTION_KEY_ID = "encryption_key_id_pha";
  private static final String CONFIG_FACILITATOR_ENCRYPTION_KEY_ID = "encryption_key_id_facilitator";

  private static final Duration FETCH_CONFIG_TIMEOUT = Duration.ofSeconds(10);
  private final static RemoteConfigs DEFAULT_REMOTE_CONFIGS = RemoteConfigs.newBuilder().build();
  private RequestQueueWrapper queue;
  private final Uri remoteConfigUri;
  private final ListeningExecutorService lightweightExecutor = Executors.getLightweightListeningExecutor();
  private final Optional<PrivateAnalyticsEventListener> listener;
  private final PrivateAnalyticsLogger logger;

  @Inject
  public DefaultPrivateAnalyticsRemoteConfig(
      @RemoteConfigUri Uri remoteConfigUri,
      Optional<PrivateAnalyticsEventListener> listener,
      PrivateAnalyticsLogger.Factory loggerFactory) {
    this.remoteConfigUri = remoteConfigUri;
    this.listener = listener;
    this.logger = loggerFactory.create(TAG);

    RequestQueue queue = new RequestQueue(new NoCache(), new BasicNetwork(new HurlStack()));
    queue.start();
    this.queue = RequestQueueWrapper.wrapping(queue);
  }

  @Override
  public ListenableFuture<RemoteConfigs> fetchUpdatedConfigs() {
    return FluentFuture.from(fetchUpdatedConfigsJson())
        .transform(this::convertToRemoteConfig, lightweightExecutor)
        .catching(Exception.class, e -> {
          // Output the default RemoteConfigs for any exception thrown.
          logger.e("Failed to fetch or convert remote configuration.", e);
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
    if (listener.isPresent()) {
      listener.get()
          .onPrivateAnalyticsRemoteConfigCallSuccess(
              response.toString().length());
    }
    logger.d("Successfully fetched remote configs.");
  }

  private void logFailure(VolleyError err) {
    if (listener.isPresent()) {
      listener.get().onPrivateAnalyticsRemoteConfigCallFailure(err);
    }
    logger.d(
        "Remote Config Fetch Failed: "
            + VolleyUtils.getErrorBody(err).toString());
  }

  private RemoteConfigs convertToRemoteConfig(JSONObject jsonObject) {
    if (jsonObject == null) {
      logger.e("Invalid jsonObj, using default remote configs");
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

      // Certificates
      if (jsonObject.has(CONFIG_PHA_CERTIFICATE_KEY)) {
        remoteConfigBuilder.setPhaCertificate(
            jsonObject.getString(CONFIG_PHA_CERTIFICATE_KEY));
      }
      if (jsonObject.has(CONFIG_FACILITATOR_CERTIFICATE_KEY)) {
        remoteConfigBuilder.setFacilitatorCertificate(
            jsonObject.getString(CONFIG_FACILITATOR_CERTIFICATE_KEY));
      }

      // Encryption Key Id's
      if (jsonObject.has(CONFIG_PHA_ENCRYPTION_KEY_ID)) {
        remoteConfigBuilder.setPhaEncryptionKeyId(
            jsonObject.getString(CONFIG_PHA_ENCRYPTION_KEY_ID));
      }
      if (jsonObject.has(CONFIG_FACILITATOR_ENCRYPTION_KEY_ID)) {
        remoteConfigBuilder.setFacilitatorEncryptionKeyId(
            jsonObject.getString(CONFIG_FACILITATOR_ENCRYPTION_KEY_ID));
      }
    } catch (JSONException e) {
      logger.e("Failed to parse remote config json, using defaults", e);
      return DEFAULT_REMOTE_CONFIGS;
    }
    return remoteConfigBuilder.build();
  }

  /**
   * Construction of a Remote Config Request
   */
  public static class FetchRemoteConfigRequest extends RespondableJsonObjectRequest {

    public FetchRemoteConfigRequest(
        Uri endpoint,
        Response.Listener<JSONObject> listener,
        Response.ErrorListener errorListener) {
      super(Method.GET, endpoint.toString(), /* jsonRequest= */ null, listener, errorListener);
      setRetryPolicy(new DefaultRetryPolicy((int) FETCH_CONFIG_TIMEOUT.toMillis(), MAX_RETRIES,
          RETRY_BACKOFF));
    }
  }

  @VisibleForTesting
  void setRequestQueue(RequestQueueWrapper requestQueueWrapper) {
    this.queue = requestQueueWrapper;
  }
}
