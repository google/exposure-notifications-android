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
import com.android.volley.Response;
import com.android.volley.Response.ErrorListener;
import com.android.volley.Response.Listener;
import com.android.volley.toolbox.JsonObjectRequest;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.network.RequestQueueWrapper;
import com.google.android.apps.exposurenotification.network.VolleyUtils;
import com.google.auto.value.AutoValue;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.HashMap;
import java.util.Map;
import androidx.annotation.Nullable;
import org.json.JSONException;
import org.json.JSONObject;
import org.threeten.bp.Instant;
import org.threeten.bp.LocalDate;
import org.threeten.bp.ZoneOffset;

/**
 * Uses a test-only API on the verification server to create new verification codes for use in the
 * "submit diagnosis" flow. This takes the place of a human Health Authority representative who
 * would check that the user has a real diagnosis, and issue them a verification code.
 */
class VerificationCodeCreator {

  /** Confirmed test type value that backend understands. */
  public static final String TEST_TYPE_CONFIRMED = "confirmed";
  /** Likely test type value that backend understands. */
  public static final String TEST_TYPE_LIKELY = "likely";
  /** Negative test type value that backend understands. */
  public static final String TEST_TYPE_NEGATIVE = "negative";

  private static final String TAG = "VerificationCodeCreator";
  private static final int DEFAULT_ONSET_DAYS_AGO = 3;

  private final RequestQueueWrapper queue;
  private final Uri createCodeUri;
  private final String apiKey;

  VerificationCodeCreator(
      Context context,
      RequestQueueWrapper queue) {
    this.queue = queue;
    createCodeUri = Uri.parse(context.getString(R.string.enx_adminVerificationCreateCode));
    apiKey = context.getString(R.string.enx_adminVerificationApiKey);
  }

  /**
   * Creates a new verification code whose onset date was {@code DEFAULT_ONSET_DAYS_AGO} days ago
   * (currently 3), and the {@code DEFAULT_TEST_TYPE}, currently "confirmed".
   */
  ListenableFuture<VerificationCode> create() {
    return create(
        LocalDate.now(ZoneOffset.UTC).minusDays(DEFAULT_ONSET_DAYS_AGO), TEST_TYPE_CONFIRMED);
  }

  /**
   * Creates a new verification code with the given {@code onsetDate} and {@code testType}.
   */
  ListenableFuture<VerificationCode> create(@Nullable LocalDate onsetDate, String testType) {
    Log.d(TAG, "Creating a verification code with onset date [" + onsetDate + "] and test type ["
        + testType + "].");
    return CallbackToFutureAdapter.getFuture(
        completer -> {
          Listener<JSONObject> responseListener =
              response -> {
                Log.d(TAG, "Verification code obtained: " + response);
                try {
                  completer.set(parseResponse(response));
                } catch (Exception e) {
                  // Pass along the exception instead of crashing the app
                  Log.e(
                      TAG,
                      String.format(
                          "Failed to parse create verification code response: [%s]",
                          e.getMessage()));
                  completer.setException(new VerificationCodeParseResponseFailureException());
                }
              };

          ErrorListener errorListener =
              err -> {
                JSONObject errorBody = VolleyUtils.getErrorBodyWithoutPadding(err);
                Log.e(TAG, String.format("Create verification code error: [%s]", errorBody));
                if (VolleyUtils.getHttpStatus(err) >= 500) {
                  completer.setException(
                      new VerificationCodeCreateServerFailureException());
                } else {
                  completer.setException(new VerificationCodeCreateFailureException(err));
                }
              };

          JSONObject requestBody = requestBody(onsetDate, testType);
          Log.d(TAG, "Submitting request for verification code: " + requestBody);

          CreateCodeRequest request =
              new CreateCodeRequest(
                  apiKey, createCodeUri, requestBody, responseListener, errorListener);
          queue.add(request);
          return request;
        });
  }

  private static JSONObject requestBody(@Nullable LocalDate onsetDate, String testType)
      throws JSONException {
    JSONObject body = new JSONObject();
    // TODO: Extract string keys to consts (here and elsewhere).
    if (onsetDate != null) {
      body.put("symptomDate", onsetDate.toString());
    }
    body.put("testType", testType);
    return body;
  }

  private static VerificationCode parseResponse(JSONObject response) {
    // Both code and expiry are required.
    if (!(response.has("code") && response.has("expiresAt"))) {
      throw new RuntimeException(
          "Unexpected response to create verification code. Response: " + response);
    }
    try {
      return VerificationCode.of(
          response.getString("code"), response.getLong("expiresAtTimestamp"));
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
    public Map<String, String> getHeaders() {
      Map<String, String> headers = new HashMap<>();
      headers.put("X-API-Key", apiKey);
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
    static final VerificationCode EMPTY = of("", 0L);

    abstract String code();

    abstract Instant expiry();

    static VerificationCode of(String code, long expirySecs) {
      return new AutoValue_VerificationCodeCreator_VerificationCode(
          code, Instant.ofEpochSecond(expirySecs));
    }
  }

  /** An exception indicating that there was a failure to create verification code. */
  public static class VerificationCodeCreateFailureException extends Exception {
    public VerificationCodeCreateFailureException(Throwable cause) {
      super(cause);
    }
  }

  /**
   * An exception indicating that there was a failure to create verification code due to server
   * error.
   */
  public static class VerificationCodeCreateServerFailureException extends Exception {}

  /**
   * An exception indicating that there was a failure to parse response received from server for
   * create verification code request.
   */
  public static class VerificationCodeParseResponseFailureException extends Exception {}
}
