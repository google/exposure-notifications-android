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

package com.google.android.apps.exposurenotification.keyupload;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import androidx.concurrent.futures.CallbackToFutureAdapter;
import com.android.volley.Response;
import com.android.volley.Response.ErrorListener;
import com.android.volley.Response.Listener;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.Qualifiers.BackgroundExecutor;
import com.google.android.apps.exposurenotification.common.Qualifiers.LightweightExecutor;
import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.android.apps.exposurenotification.keyupload.ApiConstants.UploadV1;
import com.google.android.apps.exposurenotification.keyupload.Qualifiers.UploadUri;
import com.google.android.apps.exposurenotification.keyupload.UploadController.KeysSubmitFailureException;
import com.google.android.apps.exposurenotification.keyupload.UploadController.KeysSubmitServerFailureException;
import com.google.android.apps.exposurenotification.logging.AnalyticsLogger;
import com.google.android.apps.exposurenotification.network.DiagnosisKey;
import com.google.android.apps.exposurenotification.network.Padding;
import com.google.android.apps.exposurenotification.network.RequestQueueWrapper;
import com.google.android.apps.exposurenotification.network.RespondableJsonObjectRequest;
import com.google.android.apps.exposurenotification.network.VolleyUtils;
import com.google.android.apps.exposurenotification.proto.RpcCall.RpcCallType;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.BaseEncoding;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.hilt.android.qualifiers.ApplicationContext;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import javax.inject.Inject;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.threeten.bp.Duration;
import org.threeten.bp.ZoneOffset;

/**
 * A class to encapsulate uploading Diagnosis Keys to one or more key sharing servers.
 *
 * <p>Some assumptions are made here about design elements not yet known to the author to be final.
 * These include:
 *
 * <ul>
 *   <li>The roaming plan, which handles users who move between countries/regions, so that their
 *       diagnosis keys may need to be shared with more than one service.
 *   <li>The diagnosis verification plan.
 * </ul>
 *
 * <p>Note to implementors: This example code focuses on demonstrating the server API and helping
 * you test end to end operation of your app along with a server and Google Play Services Exposure
 * Notifications API while in development. A production implementation might consider additional
 * privacy and security practices, such as randomly scheduled fake uploads, logging changes, etc.
 */
class DiagnosisKeyUploader {

  private static final String TAG = "KeyUploader";
  // NOTE: The server expects padding.
  private static final BaseEncoding BASE64 = BaseEncoding.base64();
  // All requests to the server take at least 5s by design, so time out far longer than that.
  private static final Duration TIMEOUT = Duration.ofSeconds(30);
  private static final int MAX_RETRIES = 3;
  private static final float RETRY_BACKOFF = 1.0f;

  private final Context context;
  private final Uri uri;
  private final RequestQueueWrapper queue;

  private final ExecutorService backgroundExecutor;
  private final ExecutorService lightweightExecutor;
  private final Clock clock;
  private final AnalyticsLogger logger;

  @Inject
  DiagnosisKeyUploader(
      @ApplicationContext Context context,
      @UploadUri Uri uri,
      RequestQueueWrapper queue,
      @BackgroundExecutor ExecutorService backgroundExecutor,
      @LightweightExecutor ExecutorService lightweightExecutor,
      Clock clock,
      AnalyticsLogger logger) {
    this.context = context;
    this.uri = uri;
    this.queue = queue;
    this.backgroundExecutor = backgroundExecutor;
    this.lightweightExecutor = lightweightExecutor;
    this.clock = clock;
    this.logger = logger;
  }

  /**
   * Uploads the given keys to the key server(s) for all of the currently relevant
   * countries/regions. For simplicity, we upload all keys to all relevant countries/regions. For
   * most users there will be one relevant country.
   *
   * @param upload with the keys to submit, having been previously signed by the validation server.
   */
  public ListenableFuture<Upload> upload(Upload upload) {
    if (upload.keys().isEmpty()) {
      Log.d(TAG, "Zero keys given, skipping.");
      return Futures.immediateFuture(upload);
    }
    Log.d(TAG, "Uploading keys: [" + upload.keys().size() + "]");

    // Start by creating a JSON request payload from the given Upload.
    return FluentFuture.from(createPayload(upload))
        // Submit to the key server.
        .transformAsync(
            payload -> submitToServer(payload, upload.isCoverTraffic()), backgroundExecutor)
        // Extract the revision token from the response into the Upload object and return it.
        .transformAsync(
            response -> captureRevisionToken(response, upload), lightweightExecutor);
  }

  private ListenableFuture<JSONObject> createPayload(Upload upload) {
    JSONObject payload = new JSONObject();

    JSONArray keysJson = new JSONArray();
    try {
      for (DiagnosisKey k : upload.keys()) {
        Log.d(TAG, "Adding key: " + k + " to submission.");
        keysJson.put(
            new JSONObject()
                .put(UploadV1.KEY, BASE64.encode(k.getKeyBytes()))
                .put(UploadV1.ROLLING_START_NUM, k.getIntervalNumber())
                .put(UploadV1.ROLLING_PERIOD, k.getRollingPeriod())
                .put(UploadV1.TRANSMISSION_RISK, k.getTransmissionRisk()));
      }

      JSONArray regionCodesJson = new JSONArray();
      for (String r : upload.regions()) {
        regionCodesJson.put(r);
      }

      payload
          .put(UploadV1.KEYS, keysJson)
          .put(UploadV1.APP_PACKAGE, context.getString(R.string.health_authority_id))
          .put(UploadV1.HMAC_KEY, upload.hmacKeyBase64())
          .put(UploadV1.VERIFICATION_CERT, upload.certificate())
          .put(UploadV1.TRAVELER, upload.hasTraveled());

      // Onset date is optional
      if (upload.symptomOnset() != null) {
        int onsetDateInterval = DiagnosisKey.instantToInterval(
            upload.symptomOnset().atStartOfDay(ZoneOffset.UTC).toInstant());
        payload.put(UploadV1.ONSET, onsetDateInterval);
      }

      // We have a revision token only on second and subsequent uploads.
      if (upload.revisionToken() != null) {
        payload
            .put(ApiConstants.UploadV1.REVISION_TOKEN, upload.revisionToken());
      }

      payload = Padding.addPadding(payload);
      return Futures.immediateFuture(payload);
    } catch (JSONException e) {
      return Futures.immediateFailedFuture(e);
    }
  }

  /**
   * Submits the build request to the key server and returns the server's response.
   */
  private ListenableFuture<JSONObject> submitToServer(JSONObject payload, boolean isCoverTraffic) {
    return CallbackToFutureAdapter.getFuture(
        completer -> {
          Listener<JSONObject> responseListener =
              response -> {
                logger.logRpcCallSuccessAsync(RpcCallType.RPC_TYPE_KEYS_UPLOAD,
                    payload.toString().length());
                completer.set(response);
              };

          ErrorListener errorListener =
              err -> {
                logger.logRpcCallFailureAsync(RpcCallType.RPC_TYPE_KEYS_UPLOAD, err);
                Log.d(TAG, VolleyUtils.getErrorBodyWithoutPadding(err).toString());
                if (VolleyUtils.getHttpStatus(err) >= 500) {
                  completer.setException(new KeysSubmitServerFailureException(err));
                } else {
                  completer.setException(new KeysSubmitFailureException(err));
                }
              };

          Log.d(TAG, "Submitting " + payload);

          SubmitKeysRequest request =
              new SubmitKeysRequest(
                  uri, payload, responseListener, errorListener, clock, isCoverTraffic);
          queue.add(request);
          return request;
        });
  }

  /**
   * Extract the revision token from the server's response, into the given {@link Upload}.
   *
   * <p>Returns a Future so it can act as an AsyncFunction which is allowed to throw an exception
   * whereas a regular Function may not.
   */
  private ListenableFuture<Upload> captureRevisionToken(JSONObject response, Upload upload)
      throws KeysSubmitFailureException {
    if (upload.isCoverTraffic()) {
      return Futures.immediateFuture(upload);
    }
    try {
      return Futures.immediateFuture(upload.toBuilder()
          .setRevisionToken(response.getString(UploadV1.REVISION_TOKEN))
          .build());
    } catch (JSONException e) {
      // "Server error" here is maybe a bit optimistic: it assumes that the response body was
      // incorrect, but it could be that the app's interpretation of the response is incorrect.
      throw new KeysSubmitFailureException(UploadError.SERVER_ERROR);
    }
  }

  /**
   * Simple construction of a Diagnosis Keys submission.
   */
  private static class SubmitKeysRequest extends RespondableJsonObjectRequest {

    SubmitKeysRequest(
        Uri endpoint,
        JSONObject jsonRequest,
        Response.Listener<JSONObject> listener,
        Response.ErrorListener errorListener,
        Clock clock,
        boolean isCoverTraffic) {
      super(Method.POST,
          endpoint.toString(), jsonRequest, listener, errorListener, clock, isCoverTraffic);
    }

    @Override
    public Map<String, String> getHeaders() {
      return isCoverTraffic ? ImmutableMap.of(ApiConstants.CHAFF_HEADER, "1") : ImmutableMap.of();
    }
  }
}
