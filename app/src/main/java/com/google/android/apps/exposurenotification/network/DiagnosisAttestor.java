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

package com.google.android.apps.exposurenotification.network;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import androidx.concurrent.futures.CallbackToFutureAdapter;
import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Response;
import com.android.volley.Response.ErrorListener;
import com.android.volley.Response.Listener;
import com.android.volley.toolbox.JsonObjectRequest;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.AppExecutors;
import com.google.android.apps.exposurenotification.network.UploadController.VerificationCodeFailureException;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.io.BaseEncoding;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListenableFuture;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.json.JSONException;
import org.json.JSONObject;
import org.threeten.bp.Duration;
import org.threeten.bp.LocalDate;

/**
 * Consults a diagnosis verification service who we hope will provide a cryptographic attestation
 * that our positive diagnosis is genuine, and should be trusted by the Diagnosis Key Server.
 *
 * <p>Such trust enables the Diagnosis Key Server to publish our Temporary Exposure Keys as
 * Diagnosis Keys for other users to attempt matching with.
 */
class DiagnosisAttestor {

  private static final String TAG = "DiagnosisAttestor";
  private static final Joiner COMMAS = Joiner.on(',');
  private static final BaseEncoding BASE64 = BaseEncoding.base64();
  private static final String HASH_ALGO = "HmacSHA256";

  private final Uris uris;
  private final RequestQueueWrapper queue;
  private final String apiKey;

  DiagnosisAttestor(Context context, Uris uris, RequestQueueWrapper queue) {
    this.uris = uris;
    this.queue = queue;
    apiKey = context.getString(R.string.verification_api_key);
  }

  ListenableFuture<Upload> attestFor(Upload upload) {
    Log.d(TAG, "Attempting to get attestation from the verification server.");
    return FluentFuture.from(submitCode(upload))
        .transformAsync(this::submitKeysForCert, AppExecutors.getLightweightExecutor());
  }

  private ListenableFuture<Upload> submitCode(Upload upload) {
    Uri uri = uris.getVerificationUri1(upload.homeRegion());
    return CallbackToFutureAdapter.getFuture(completer -> {
      Listener<JSONObject> responseListener =
          response -> {
            Log.d(TAG, "Verification code submission succeeded: " + response);
            completer.set(captureVerificationCodeResponse(upload, response));
          };

      ErrorListener errorListener =
          err -> {
            // TODO: deal with different http statuses differently (4xx vs 5xx).
            String msg = VolleyUtils.getErrorMessage(err, "Call failed; network problem?");
            Log.e(TAG, String.format("Verification code submission error: [%s]", msg));
            completer.setException(new VerificationCodeFailureException(err));
          };

      JSONObject requestBody = verificationCodeRequestBody(upload);
      Log.d(TAG, "Submitting verification code: " + requestBody);

      VerificationRequest request =
          new VerificationRequest(
              apiKey, uri, requestBody, responseListener, errorListener);
      queue.add(request);
      return request;
    });
  }

  private static JSONObject verificationCodeRequestBody(Upload upload) throws JSONException {
    JSONObject payload = new JSONObject();
    payload.put("code", upload.verificationCode());
    return payload;
  }

  private static Upload captureVerificationCodeResponse(Upload upload, JSONObject response) {
    Upload.Builder withResponse = upload.toBuilder();
    // TODO: Extract string keys to consts (here and elsewhere).
    try {
      if (response.has("testtype") && !Strings.isNullOrEmpty(response.getString("testtype"))) {
        withResponse.setTestType(response.getString("testtype"));
      }
      if (response.has("token") && !Strings.isNullOrEmpty(response.getString("token"))) {
        withResponse.setLongTermToken(response.getString("token"));
      }
      if (response.has("symptomDate") && !Strings
          .isNullOrEmpty(response.getString("symptomDate"))) {
        // LocalDate.parse() defaults to iso-8601 date format "YYYY-MM-DD", as returned by
        // the verification server for symptomDate.
        withResponse.setSymptomOnset(LocalDate.parse(response.getString("symptomDate")));
      }
      return withResponse.build();
    } catch (JSONException e) {
      // TODO: Better exception.
      throw new RuntimeException(e);
    }
  }

  private ListenableFuture<Upload> submitKeysForCert(Upload upload) {
    Uri uri = uris.getVerificationUri2(upload.homeRegion());
    return CallbackToFutureAdapter.getFuture(completer -> {
      Listener<JSONObject> responseListener =
          response -> {
            Log.d(TAG, "Certificate obtained: " + response);
            completer.set(captureCertResponse(upload, response));
          };

      ErrorListener errorListener =
          err -> {
            // TODO: deal with different http statuses differently (4xx vs 5xx).
            String msg = VolleyUtils.getErrorMessage(err, "Call failed; network problem?");
            Log.e(TAG, String.format("Certificate error: [%s]", msg));
            completer.setException(new VerificationCodeFailureException(err));
          };

      JSONObject requestBody = certRequestBody(upload);
      Log.d(TAG, "Submitting request for certificate: " + requestBody);

      VerificationRequest request =
          new VerificationRequest(apiKey, uri, requestBody, responseListener, errorListener);
      queue.add(request);
      return request;

    });
  }

  private static JSONObject certRequestBody(Upload upload) throws JSONException {
    JSONObject payload = new JSONObject();
    payload.put("token", upload.longTermToken());
    payload.put("ekeyhmac", hashedKeys(upload));
    return payload;
  }

  private static String hashedKeys(Upload upload) {
    List<String> cleartextSegments = new ArrayList<>(upload.keys().size());
    for (DiagnosisKey k : upload.keys()) {
      cleartextSegments.add(String.format(
          Locale.ENGLISH,
          "%s.%d.%d.%d",
          BASE64.encode(k.getKeyBytes()),
          k.getIntervalNumber(),
          k.getRollingPeriod(),
          k.getTransmissionRisk()));
    }
    String cleartext = COMMAS.join(cleartextSegments);
    Log.d(TAG,
        upload.keys().size() + " keys for hashing prior to verification: [" + cleartext + "]");
    try {
      Mac mac = Mac.getInstance(HASH_ALGO);
      mac.init(new SecretKeySpec(BASE64.decode(upload.hmacKeyBase64()), HASH_ALGO));
      String hashedKeys = BASE64.encode(mac.doFinal(cleartext.getBytes(StandardCharsets.UTF_8)));
      return hashedKeys;
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      // TODO: Better exception
      throw new RuntimeException(e);
    }
  }

  private static Upload captureCertResponse(Upload upload, JSONObject response) {
    Upload.Builder withResponse = upload.toBuilder();
    try {
      if (response.has("certificate") && !Strings
          .isNullOrEmpty(response.getString("certificate"))) {
        withResponse.setCertificate(response.getString("certificate"));
      }
      return withResponse.build();
    } catch (JSONException e) {
      // TODO: Better exception
      throw new RuntimeException(e);
    }
  }

  /**
   * Simple construction of verification submissions, both the code/token exchange, and the
   * token/cert exchange.
   */
  private static class VerificationRequest extends JsonObjectRequest {

    // TODO set these values appropriately
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_RETRIES = 3;
    private static final float RETRY_BACKOFF = 1.0f;

    private final String apiKey;

    VerificationRequest(
        String apiKey,
        Uri endpoint,
        JSONObject jsonRequest,
        Response.Listener<JSONObject> listener,
        Response.ErrorListener errorListener) {
      super(Method.POST, endpoint.toString(), jsonRequest, listener, errorListener);
      setRetryPolicy(new DefaultRetryPolicy((int) TIMEOUT.toMillis(), MAX_RETRIES, RETRY_BACKOFF));
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
}
