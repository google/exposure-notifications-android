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
import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.android.apps.exposurenotification.keyupload.ApiConstants.VerifyV1;
import com.google.android.apps.exposurenotification.keyupload.Qualifiers.VerificationCertUri;
import com.google.android.apps.exposurenotification.keyupload.Qualifiers.VerificationCodeUri;
import com.google.android.apps.exposurenotification.keyupload.UploadController.VerificationFailureException;
import com.google.android.apps.exposurenotification.keyupload.UploadController.VerificationServerFailureException;
import com.google.android.apps.exposurenotification.logging.AnalyticsLogger;
import com.google.android.apps.exposurenotification.network.DiagnosisKey;
import com.google.android.apps.exposurenotification.network.Padding;
import com.google.android.apps.exposurenotification.network.RequestQueueWrapper;
import com.google.android.apps.exposurenotification.network.RespondableJsonObjectRequest;
import com.google.android.apps.exposurenotification.network.VolleyUtils;
import com.google.android.apps.exposurenotification.proto.RpcCall.RpcCallType;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity.TestResult;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.io.BaseEncoding;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.hilt.android.qualifiers.ApplicationContext;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.inject.Inject;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
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
  private static final JSONArray SUPPORTED_TEST_TYPES = new JSONArray(
      ImmutableList.of(
          TestResult.CONFIRMED.toApiType(),
          TestResult.LIKELY.toApiType(),
          TestResult.NEGATIVE.toApiType()));

  private final Uri codeUri;
  private final Uri certUri;
  private final RequestQueueWrapper queue;
  private final String apiKey;
  private final Clock clock;
  private final AnalyticsLogger logger;

  @Inject
  DiagnosisAttestor(
      @ApplicationContext Context context,
      @VerificationCodeUri Uri codeUri,
      @VerificationCertUri Uri certUri,
      RequestQueueWrapper queue,
      Clock clock,
      AnalyticsLogger logger) {
    this.codeUri = codeUri;
    this.certUri = certUri;
    this.queue = queue;
    this.clock = clock;
    this.logger = logger;
    apiKey = context.getString(R.string.enx_testVerificationAPIKey);
  }

  ListenableFuture<Upload> submitCode(Upload upload) {
    Log.d(TAG, "Submitting verification code: " + upload);
    return CallbackToFutureAdapter.getFuture(completer -> {
      JSONObject requestBody = verificationCodeRequestBody(upload);
      Log.d(TAG, "Submitting verification code: " + requestBody);

      Listener<JSONObject> responseListener =
          response -> {
            logger.logRpcCallSuccessAsync(RpcCallType.RPC_TYPE_VERIFICATION,
                requestBody.toString().length());
            Log.d(TAG, "Verification code submission succeeded: " + response);
            completer.set(captureVerificationCodeResponse(upload, response));
          };

      ErrorListener errorListener =
          err -> {
            logger.logRpcCallFailureAsync(RpcCallType.RPC_TYPE_VERIFICATION, err);
            String msg = VolleyUtils.getErrorMessage(err);
            Log.e(TAG, String.format("Verification code submission error: [%s]", msg));
            if (VolleyUtils.getHttpStatus(err) >= 500) {
              completer.setException(new VerificationServerFailureException(err));
            } else {
              completer.setException(new VerificationFailureException(err));
            }
          };

      VerificationRequest request =
          new VerificationRequest(
              apiKey, codeUri, requestBody, responseListener, errorListener,
              clock, upload.isCoverTraffic());
      queue.add(request);
      return request;
    });
  }

  private static JSONObject verificationCodeRequestBody(Upload upload) throws JSONException {
    return Padding.addPadding(new JSONObject()
        .put(VerifyV1.VERIFICATION_CODE, upload.verificationCode())
        .put(VerifyV1.ACCEPT_TEST_TYPES, SUPPORTED_TEST_TYPES));
  }

  private static Upload captureVerificationCodeResponse(Upload upload, JSONObject response) {
    if (upload.isCoverTraffic()) {
      // Ignore responses for cover traffic requests.
      return upload;
    }
    Upload.Builder withResponse = upload.toBuilder();
    try {
      if (response.has(VerifyV1.TEST_TYPE) &&
          !Strings.isNullOrEmpty(response.getString(VerifyV1.TEST_TYPE))) {
        withResponse.setTestType(response.getString(VerifyV1.TEST_TYPE));
      }
      if (response.has(VerifyV1.VERIFICATION_TOKEN) &&
          !Strings.isNullOrEmpty(response.getString(VerifyV1.VERIFICATION_TOKEN))) {
        withResponse.setLongTermToken(response.getString(VerifyV1.VERIFICATION_TOKEN));
      }
      if (response.has(VerifyV1.ONSET_DATE)
          && !Strings.isNullOrEmpty(response.getString(VerifyV1.ONSET_DATE))) {
        // LocalDate.parse() defaults to iso-8601 date format "YYYY-MM-DD", as returned by
        // the verification server for symptomDate.
        withResponse.setSymptomOnset(LocalDate.parse(response.getString(VerifyV1.ONSET_DATE)));
      }
      return withResponse.build();
    } catch (JSONException e) {
      // TODO: Better exception.
      throw new RuntimeException(e);
    }
  }

  ListenableFuture<Upload> submitKeysForCert(Upload upload) {
    return CallbackToFutureAdapter.getFuture(
        completer -> {
          JSONObject requestBody = certRequestBody(upload);
          Log.d(TAG, "Submitting request for certificate: " + requestBody);

          Listener<JSONObject> responseListener =
              response -> {
                logger.logRpcCallSuccessAsync(RpcCallType.RPC_TYPE_VERIFICATION,
                    requestBody.toString().length());
                Log.d(TAG, "Certificate obtained: " + response);
                completer.set(captureCertResponse(upload, response));
              };

          ErrorListener errorListener =
              err -> {
                logger.logRpcCallFailureAsync(RpcCallType.RPC_TYPE_VERIFICATION, err);
                String msg = VolleyUtils.getErrorMessage(err);
                Log.e(TAG, String.format("Certificate error: [%s]", msg));
                if (VolleyUtils.getHttpStatus(err) >= 500) {
                  completer.setException(new VerificationServerFailureException(err));
                } else {
                  completer.setException(new VerificationFailureException(err));
                }
              };

          VerificationRequest request =
              new VerificationRequest(
                  apiKey, certUri, requestBody, responseListener, errorListener,
                  clock, upload.isCoverTraffic());
          queue.add(request);
          return request;
        });
  }

  private static JSONObject certRequestBody(Upload upload) throws JSONException {
    return Padding.addPadding(new JSONObject()
        .put(VerifyV1.VERIFICATION_TOKEN, upload.longTermToken())
        .put(VerifyV1.HMAC_KEY, hashedKeys(upload)));
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
    Collections.sort(cleartextSegments);
    String cleartext = COMMAS.join(cleartextSegments);
    Log.d(TAG,
        upload.keys().size() + " keys for hashing prior to verification: [" + cleartext + "]");
    try {
      Mac mac = Mac.getInstance(HASH_ALGO);
      mac.init(new SecretKeySpec(BASE64.decode(upload.hmacKeyBase64()), HASH_ALGO));
      return BASE64.encode(mac.doFinal(cleartext.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      // TODO: Better exception
      throw new RuntimeException(e);
    }
  }

  private static Upload captureCertResponse(Upload upload, JSONObject response) {
    if (upload.isCoverTraffic()) {
      // Ignore responses for cover traffic requests.
      return upload;
    }
    Upload.Builder withResponse = upload.toBuilder();
    try {
      if (response.has(VerifyV1.CERT)
          && !Strings.isNullOrEmpty(response.getString(VerifyV1.CERT))) {
        withResponse.setCertificate(response.getString(VerifyV1.CERT));
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
  private static class VerificationRequest extends RespondableJsonObjectRequest {

    private final String apiKey;

    VerificationRequest(
        String apiKey,
        Uri endpoint,
        JSONObject jsonRequest,
        Response.Listener<JSONObject> listener,
        Response.ErrorListener errorListener,
        Clock clock,
        boolean isCoverTraffic) {
      super(Method.POST,
          endpoint.toString(), jsonRequest, listener, errorListener, clock, isCoverTraffic);
      this.apiKey = apiKey;
    }

    @Override
    public Map<String, String> getHeaders() {
      Map<String, String> headers = new HashMap<>();
      headers.put(VerifyV1.API_KEY_HEADER, apiKey);
      if (isCoverTraffic) {
        headers.put(ApiConstants.CHAFF_HEADER, "1");
      }
      return headers;
    }
  }
}
