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

package com.google.android.apps.exposurenotification.debug;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import androidx.concurrent.futures.CallbackToFutureAdapter;
import com.android.volley.AuthFailureError;
import com.android.volley.Response;
import com.android.volley.Response.ErrorListener;
import com.android.volley.Response.Listener;
import com.android.volley.toolbox.JsonObjectRequest;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.network.RequestQueueWrapper;
import com.google.android.apps.exposurenotification.network.UploadController.VerificationCodeFailureException;
import com.google.android.apps.exposurenotification.network.VolleyUtils;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.auto.value.AutoValue;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;
import org.threeten.bp.Instant;
import org.threeten.bp.LocalDate;
import org.threeten.bp.ZoneOffset;
import org.threeten.bp.format.DateTimeFormatter;

/**
 * Uses a test-only API on the verification server to create new verification codes for use in the
 * "submit diagnosis" flow. This takes the place of a human Health Authority representative who
 * would check that the user has a real diagnosis, and issue them a verification code.
 */
class VerificationCodeCreator {

  private static final String TAG = "VerificationCodeCreator";
  private static final int DEFAULT_ONSET_DAYS_AGO = 3;
  private static final String DEFAULT_TEST_TYPE = "confirmed";

  private static final DateTimeFormatter EXPIRY_PARSER =
      DateTimeFormatter.RFC_1123_DATE_TIME.withLocale(Locale.ENGLISH).withZone(ZoneOffset.UTC);

  private final ExposureNotificationSharedPreferences prefs;
  private final RequestQueueWrapper queue;
  private final Uri createCodeUri;
  private final String apiKey;

  VerificationCodeCreator(
      Context context,
      ExposureNotificationSharedPreferences prefs,
      RequestQueueWrapper queue) {
    this.prefs = prefs;
    this.queue = queue;
    createCodeUri = Uri.parse(context.getString(R.string.verification_server_uri_create_code));
    apiKey = context.getString(R.string.verification_code_api_key);
  }

  /**
   * Creates a new verification code whose onset date was {@code DEFAULT_ONSET_DAYS_AGO} days ago
   * (currently 3), and the {@code DEFAULT_TEST_TYPE}, currently "confirmed".
   */
  ListenableFuture<VerificationCode> create() {
    return create(LocalDate.now(ZoneOffset.UTC).minusDays(DEFAULT_ONSET_DAYS_AGO));
  }

  /**
   * Creates a new verification code whose onset date is the given {@code onsetDate}, and the {@code
   * DEFAULT_TEST_TYPE}, currently "confirmed".
   */
  ListenableFuture<VerificationCode> create(LocalDate onsetDate) {
    return create(onsetDate, DEFAULT_TEST_TYPE);
  }

  /**
   * Creates a new verification code with the given {@code onsetDate} and {@code testType}.
   */
  ListenableFuture<VerificationCode> create(LocalDate onsetDate, String testType) {
    Log.d(TAG, "Creating a verification code with onset date [" + onsetDate + "] and test type ["
        + testType + "].");
    return CallbackToFutureAdapter.getFuture(completer -> {
      Listener<JSONObject> responseListener =
          response -> {
            Log.d(TAG, "Verification code obtained: " + response);
            completer.set(parseResponse(response, onsetDate));
          };

      ErrorListener errorListener =
          err -> {
            // TODO: deal with different http statuses differently (4xx vs 5xx).
            String msg = VolleyUtils.getErrorMessage(err, "Call failed; network problem?");
            Log.e(TAG, String.format("Verification code error: [%s]", msg));
            completer.setException(new VerificationCodeFailureException(err));
          };

      JSONObject requestBody = requestBody(onsetDate, testType);
      Log.d(TAG, "Submitting request for verification code: " + requestBody);

      CreateCodeRequest request = new CreateCodeRequest(
          apiKey, createCodeUri, requestBody, responseListener, errorListener);
      queue.add(request);
      return request;

    });
  }

  private static JSONObject requestBody(LocalDate onsetDate, String testType) throws JSONException {
    JSONObject body = new JSONObject();
    // TODO: Extract string keys to consts (here and elsewhere).
    body.put("symptomDate", onsetDate.toString());
    body.put("testType", testType);
    return body;
  }

  private static VerificationCode parseResponse(JSONObject response, LocalDate onsetDate) {
    // Both code and expiry are required.
    if (!(response.has("code") && response.has("expiresAt"))) {
      throw new RuntimeException(
          "Unexpected response to create verification code. Response: " + response);
    }
    try {
      return VerificationCode.of(
          response.getString("code"), response.getLong("expiresAtTimestamp"), onsetDate);
    } catch (JSONException e) {
      throw new RuntimeException(
          "Unexpected response to create verification code. Response: " + response);
    }
  }

  private static class CreateCodeRequest extends JsonObjectRequest {

    private final String apiKey;

    CreateCodeRequest(
        String apiKey,
        Uri endpoint,
        JSONObject jsonRequest,
        Response.Listener<JSONObject> listener,
        Response.ErrorListener errorListener) {
      super(Method.POST, endpoint.toString(), jsonRequest, listener, errorListener);
      this.apiKey = apiKey;
    }

    @Override
    public Map<String, String> getHeaders() throws AuthFailureError {
      Map<String, String> headers = new HashMap<>();
      headers.put("X-API-Key", apiKey);
      Log.d(TAG, "Headers: " + headers);
      return headers;
    }

    @Override
    protected void deliverResponse(JSONObject response) {
      super.deliverResponse(response);
    }
  }

  /**
   * Value object for a verification code and its associated metadata, having been created by the
   * verification server in test scenarios (as opposed to being user input in prod scenarios).
   */
  @AutoValue
  abstract static class VerificationCode {
    static VerificationCode EMPTY = of("", 0L, LocalDate.MIN);

    abstract String code();

    abstract Instant expiry();

    abstract LocalDate symptomOnset();

    static VerificationCode of(String code, long expirySecs, LocalDate symptomOnset) {
      return new AutoValue_VerificationCodeCreator_VerificationCode(
          code, Instant.ofEpochSecond(expirySecs), symptomOnset);
    }
  }
}
