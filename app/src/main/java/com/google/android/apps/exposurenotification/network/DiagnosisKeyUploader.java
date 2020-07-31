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
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.ParseError;
import com.android.volley.Response;
import com.android.volley.Response.ErrorListener;
import com.android.volley.Response.Listener;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.JsonRequest;
import com.google.android.apps.exposurenotification.common.AppExecutors;
import com.google.android.apps.exposurenotification.common.StringUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.io.BaseEncoding;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.threeten.bp.Duration;
import org.threeten.bp.LocalDate;
import org.threeten.bp.ZoneId;
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
  // The server recommends including randomly sized random padding content between 1kb and 2kb.
  private static final int PADDING_SIZE_MIN = 1024;
  private static final int PADDING_SIZE_MAX = 2048;
  private static final SecureRandom RAND = new SecureRandom();
  private static final String DEFAULT_VERIFICATION_CODE = "POSITIVE_TEST_123456";
  private static final String PLATFORM = "android";
  // All requests to the server take at least 5s by design, so time out far longer than that.
  private static final Duration TIMEOUT = Duration.ofSeconds(30);
  private static final int MAX_RETRIES = 3;
  private static final float RETRY_BACKOFF = 1.0f;

  private final Context context;
  private final Uris uris;
  private final RequestQueueWrapper queue;

  public DiagnosisKeyUploader(
      Context context,
      Uris uris,
      RequestQueueWrapper queue) {
    this.context = context;
    this.uris = uris;
    this.queue = queue;
  }

  /**
   * Uploads the given keys to the key server(s) for all of the currently relevant
   * countries/regions. For simplicity, we upload all keys to all relevant countries/regions. For
   * most users there will be one relevant country.
   *
   * <p>The returned future represents success/fail of all the key server submissions.
   *
   * <p>TODO: Perhaps it would be good to support partial success with retry of failed submissions.
   *
   * @param upload with the keys to submit, having been previously signed by the validation server.
   */
  public ListenableFuture<Upload> upload(Upload upload) {
    if (upload.keys().isEmpty()) {
      Log.d(TAG, "Zero keys given, skipping.");
      return Futures.immediateFuture(null);
    }
    Log.d(TAG, "Uploading keys: [" + upload.keys().size() + "]");

    // The flow below assumes a certain scheme for users roaming between countries/regions, but the
    // true roaming plan is not known to the author at the time of writing. The temporary scheme
    // implemented here is:
    // 1. Get country codes where the user may have broadcast RPIs the past N days.
    // 2. Form URIs for upload servers for that set of countries/regions (not necessarily a one to
    //    one relationship between countries and URIs).
    // 3. Send all Temporary Tracing Keys from the past N days to all servers.

    // We start with that list of countries/regions.
    return FluentFuture.from(Futures.immediateFuture(upload.regions()))
        // From these we find the URIs for key servers for that list of countries. There need not be
        // a one-to-one relationship between country codes and server URIs.
        .transformAsync(uris::getUploadUris, AppExecutors.getLightweightExecutor())
        // For each URI, we start a KeySubmission into which we will add all the necessary parts,
        // like the payload, countries, keys, etc,
        .transformAsync(
            uris -> startSubmissionsForUris(uris, upload),
            AppExecutors.getLightweightExecutor())
        // To each of these KeySubmissions we add the full list of applicable country codes.
        .transformAsync(
            submissions -> addCountryCodes(submissions, upload.regions()),
            AppExecutors.getLightweightExecutor())
        // To each KeySubmission, add all the keys. All keys go in all submissions.
        .transformAsync(
            submissions -> addKeys(submissions, upload.keys()),
            AppExecutors.getLightweightExecutor())
        // Now we have all we need to create the JSON body of the request. In addPayloads we also
        // obtain a SafetyNet attestation. The SafetyNet RPCs go on the background executor
        // internally to DeviceAttestor, so getLightweightExecutor() is fine here.
        .transformAsync(this::addPayloads, AppExecutors.getLightweightExecutor())
        // Ok, now we can submit all the key submission requests to the key server(s).
        .transformAsync(this::submitToServers, AppExecutors.getBackgroundExecutor())
        // Finally return the input Upload. Seems odd to return this unchanged, but we'll likely
        // want to add some info from the diagnosis server in future.
        .transform(x -> upload, AppExecutors.getLightweightExecutor());
  }

  private ListenableFuture<List<KeySubmission>> startSubmissionsForUris(
      List<Uri> serverUris, Upload upload) {
    Log.d(TAG, "Composing diagnosis key uploads to " + serverUris.size() + " server(s).");
    List<KeySubmission> submissions = new ArrayList<>();
    for (Uri uri : serverUris) {
      KeySubmission s = new KeySubmission();
      s.uri = uri;
      s.hmacKey = upload.hmacKeyBase64();
      s.verificationCert = upload.certificate();
      if (upload.symptomOnset() != null) {
        s.onsetDateInterval = DiagnosisKey.instantToInterval(
            upload.symptomOnset().atStartOfDay(ZoneOffset.UTC).toInstant());
      }
      submissions.add(s);
    }
    return Futures.immediateFuture(submissions);
  }

  private ListenableFuture<List<KeySubmission>> addCountryCodes(
      List<KeySubmission> submissions, List<String> countries) {
    for (KeySubmission submission : submissions) {
      // All our submissions claim to be applicable to all countries the user was in during the
      // applicable period of time, because we don't track what country a single key was used in.
      // Also, a given key could have been used in more than one country, as the user travels.
      submission.applicableCountryCodes = countries;
    }
    return Futures.immediateFuture(submissions);
  }

  private ListenableFuture<List<KeySubmission>> addKeys(
      List<KeySubmission> submissions, ImmutableList<DiagnosisKey> diagnosisKeys) {
    for (KeySubmission submission : submissions) {
      // All our submissions include all keys.
      submission.diagnosisKeys = diagnosisKeys;
    }
    return Futures.immediateFuture(submissions);
  }

  private ListenableFuture<List<KeySubmission>> addPayloads(List<KeySubmission> submissions) {
    List<ListenableFuture<KeySubmission>> withPayloads = new ArrayList<>();
    for (KeySubmission submission : submissions) {
      withPayloads.add(addPayload(submission));
    }
    return Futures.allAsList(withPayloads);
  }

  private ListenableFuture<KeySubmission> addPayload(KeySubmission submission) {

    JSONArray keysJson = new JSONArray();
    try {
      for (DiagnosisKey k : submission.diagnosisKeys) {
        Log.d(TAG, "Adding key: " + k + " to submission.");
        keysJson.put(
            new JSONObject()
                .put("key", BASE64.encode(k.getKeyBytes()))
                .put("rollingStartNumber", k.getIntervalNumber())
                .put("rollingPeriod", k.getRollingPeriod())
                .put("transmissionRisk", k.getTransmissionRisk()));
      }

      JSONArray regionCodesJson = new JSONArray();
      for (String r : submission.applicableCountryCodes) {
        regionCodesJson.put(r);
      }

      int paddingLength =
          PADDING_SIZE_MIN + RAND.nextInt(PADDING_SIZE_MAX - PADDING_SIZE_MIN);
      submission.payload =
          new JSONObject()
              .put("temporaryExposureKeys", keysJson)
              .put("regions", regionCodesJson)
              .put("appPackageName", context.getPackageName())
              .put("hmackey", submission.hmacKey)
              .put("symptomOnsetInterval", submission.onsetDateInterval)
              .put("verificationPayload", submission.verificationCert)
              .put("padding", StringUtils.randomBase64Data(paddingLength));
    } catch (JSONException e) {
      return Futures.immediateFailedFuture(e);
    }
    return Futures.immediateFuture(submission);
  }

  private ListenableFuture<List<Void>> submitToServers(List<KeySubmission> submissions) {
    List<ListenableFuture<Void>> submitted = new ArrayList<>();
    for (KeySubmission submission : submissions) {
      submitted.add(
          CallbackToFutureAdapter.getFuture(
              completer -> {
                Listener<String> responseListener =
                    response -> {
                      Log.i(TAG, "Diagnosis Key upload succeeded.");
                      completer.set(null);
                    };

                ErrorListener errorListener =
                    err -> {
                      String msg =
                          (err.networkResponse == null || err.networkResponse.data == null)
                              ? "call failed; network problem?"
                              : new String(err.networkResponse.data, StandardCharsets.UTF_8);
                      Log.e(TAG, String.format("Diagnosis Key upload error: [%s]", msg));
                      completer.setCancelled();
                    };

                Log.d(TAG, "Submitting " + submission.payload);

                SubmitKeysRequest request =
                    new SubmitKeysRequest(
                        submission.uri, submission.payload, responseListener, errorListener);
                queue.add(request);
                return request;
              }));
    }
    return Futures.allAsList(submitted);
  }

  /**
   * A private value class to help assembling the elements needed to upload keys to a given server.
   *
   * TODO: As the protocol evolves, more and more of the data carried here is the same in all
   * submissions of a given set of keys. Refactor?
   */
  private static class KeySubmission {
    // Uri and payload will be different in each submission.
    private Uri uri;
    private JSONObject payload;
    // All keys will be the same in all submissions.
    private ImmutableList<DiagnosisKey> diagnosisKeys;
    // All countries will be the same in all submissions.
    private List<String> applicableCountryCodes;
    // The following verification data is the same in all submissions.
    private String hmacKey;
    private String verificationCert;
    private int onsetDateInterval;
  }

  /**
   * Simple construction of a Diagnosis Keys submission.
   */
  private static class SubmitKeysRequest extends JsonRequest<String> {

    SubmitKeysRequest(
        Uri endpoint,
        JSONObject jsonRequest,
        Response.Listener<String> listener,
        Response.ErrorListener errorListener) {
      super(Method.POST, endpoint.toString(), jsonRequest.toString(), listener, errorListener);
      setRetryPolicy(new DefaultRetryPolicy((int) TIMEOUT.toMillis(), MAX_RETRIES, RETRY_BACKOFF));
    }

    @Override
    protected Response<String> parseNetworkResponse(NetworkResponse response) {
      try {
        String responseString =
            new String(response.data, HttpHeaderParser.parseCharset(response.headers, "utf-8"));
        return response.statusCode < 400
            ? Response.success(responseString, HttpHeaderParser.parseCacheHeaders(response))
            : Response.error(new VolleyError(response));
      } catch (UnsupportedEncodingException e) {
        return Response.error(new ParseError(e));
      }
    }

    @Override
    public String getBodyContentType() {
      return "application/json";
    }
  }
}
