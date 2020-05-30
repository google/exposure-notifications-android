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
import com.google.common.collect.ImmutableList;
import com.google.common.io.BaseEncoding;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.UnsupportedEncodingException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.threeten.bp.Duration;

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
public class DiagnosisKeyUploader {

  private static final String TAG = "KeyUploader";
  // NOTE: The server expects padding.
  private static final BaseEncoding BASE64 = BaseEncoding.base64();
  // The server recommends including randomly sized random padding content between 1kb and 2kb.
  private static final int PADDING_SIZE_MIN = 1024;
  private static final int PADDING_SIZE_MAX = 2048;
  private static final SecureRandom RAND = new SecureRandom();
  // TODO: accept these as args instead of hard-coded.
  private static final int DEFAULT_PERIOD = DiagnosisKey.DEFAULT_PERIOD;
  private static final int DEFAULT_TRANSMISSION_RISK = 1;
  private static final String DEFAULT_VERIFICATION_CODE = "POSITIVE_TEST_123456";
  private static final String PLATFORM = "android";
  // All requests to the server take at least 5s by design, so time out far longer than that.
  private static final Duration TIMEOUT = Duration.ofSeconds(30);
  private static final int MAX_RETRIES = 3;
  private static final float RETRY_BACKOFF = 1.0f;
  // Some consts used when we make fake traffic.
  private static final int KEY_SIZE_BYTES = 16;
  private static final int FAKE_INTERVAL_NUM = 2650847; // Only size matters here, not the value.
  private static final int FAKE_SAFETYNET_ATTESTATION_LENGTH = 5394; // Measured from a real payload

  private final Context context;
  private final DeviceAttestor deviceAttestor;
  private final CountryCodes countryCodes;
  private final Uris uris;

  public DiagnosisKeyUploader(Context context) {
    this.context = context;
    deviceAttestor = new DeviceAttestor(context);
    countryCodes = new CountryCodes(context);
    uris = new Uris(context);
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
   * @param diagnosisKeys the keys to submit, in an {@link ImmutableList} because internally we'll
   *     share this around between threads so immutability makes things safer.
   */
  public ListenableFuture<?> upload(ImmutableList<DiagnosisKey> diagnosisKeys) {
    return doUpload(diagnosisKeys, false);
  }

  /**
   * Uploads realistically-sized fake traffic to the key sharing service(s), to help with privacy.
   *
   * <p>We use fake data for two things: The diagnosis keys and the safetynet attestation. Note
   * that we still make an RPC to SafetyNet, we just don't use its result.
   */
  public ListenableFuture<?> fakeUpload() {
    ImmutableList.Builder<DiagnosisKey> builder = ImmutableList.builder();
    // Build up 14 random diagnosis keys.
    for (int i = 0; i < 14; i++) {
      byte[] bytes = new byte[KEY_SIZE_BYTES];
      RAND.nextBytes(bytes);
      builder.add(
          DiagnosisKey.newBuilder()
              .setKeyBytes(bytes)
              .setIntervalNumber(FAKE_INTERVAL_NUM)
              .build());
    }
    return doUpload(builder.build(), true);
  }

  /**
   * Does the actual work of key uploads, supporting fake cover traffic to help with user privacy.
   */
  private ListenableFuture<?> doUpload(
      ImmutableList<DiagnosisKey> diagnosisKeys, boolean isFakeTraffic) {
    if (diagnosisKeys.isEmpty()) {
      Log.d(TAG, "Zero keys given, skipping.");
      return Futures.immediateFuture(null);
    }
    Log.d(TAG, "Uploading " + diagnosisKeys.size() + " keys...");

    // In several steps, we need all the relevant countries for the user's last N days.
    ListenableFuture<List<String>> countries = countryCodes.getExposureRelevantCountryCodes();

    // The flow below assumes a certain scheme for users roaming between countries/regions, but the
    // true roaming plan is not known to the author at the time of writing. The temporary scheme
    // implemented here is:
    // 1. Get country codes where the user may have broadcast RPIs the past N days.
    // 2. Form URIs for upload servers for that set of countries/regions (not necessarily a one to
    //    one relationship between countries and URIs).
    // 3. Send all Temporary Tracing Keys from the past N days to all servers.

    // We start with that list of countries.
    return FluentFuture.from(countries)
        // From these we find the URIs for key servers for that list of countries. There need not be
        // a one-to-one relationship between country codes and server URIs.
        .transformAsync(uris::getUploadUris, AppExecutors.getLightweightExecutor())
        // For each URI, we start a KeySubmission into which we will add all the necessary parts,
        // like the payload, countries, keys, etc,
        .transformAsync(
            uris -> startSubmissionsForUris(uris, isFakeTraffic),
            AppExecutors.getLightweightExecutor())
        // To each of these KeySubmissions we add the full list of applicable country codes.
        .transformAsync(
            submissions ->
                FluentFuture.from(countries)
                    .transformAsync(
                        countryCodes -> addCountryCodes(submissions, countryCodes),
                        AppExecutors.getLightweightExecutor()),
            AppExecutors.getLightweightExecutor())
        // To each KeySubmission, add all the keys. All keys go in all submissions.
        .transformAsync(
            submissions -> addKeys(submissions, diagnosisKeys),
            AppExecutors.getLightweightExecutor())
        // Now we have all we need to create the JSON body of the request. In addPayloads we also
        // obtain a SafetyNet attestation. The SafetyNet RPCs go on the background executor
        // internally to DeviceAttestor, so getLightweightExecutor() is fine here.
        .transformAsync(this::addPayloads, AppExecutors.getLightweightExecutor())
        // Ok, now we can submit all the key submission requests to the key server(s).
        .transformAsync(this::submitToServers, AppExecutors.getBackgroundExecutor());
  }

  private ListenableFuture<List<KeySubmission>> startSubmissionsForUris(
      List<Uri> serverUris, boolean isFakeTraffic) {
    Log.d(TAG, "Composing diagnosis key uploads to " + serverUris.size() + " server(s).");
    List<KeySubmission> submissions = new ArrayList<>();
    for (Uri uri : serverUris) {
      KeySubmission s = new KeySubmission();
      s.uri = uri;
      s.transmissionRisk = DEFAULT_TRANSMISSION_RISK;
      s.verificationCode = DEFAULT_VERIFICATION_CODE;
      s.isFakeTraffic = isFakeTraffic;
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
        keysJson.put(
            new JSONObject()
                .put("key", BASE64.encode(k.getKeyBytes()))
                .put("rollingStartNumber", k.getIntervalNumber())
                .put("rollingPeriod", DEFAULT_PERIOD)
                .put("transmissionRisk", submission.transmissionRisk));
      }
    } catch (JSONException e) {
      // TODO: Some better exception.
      throw new RuntimeException(e);
    }

    JSONArray regionCodesJson = new JSONArray();
    for (String r : submission.applicableCountryCodes) {
      regionCodesJson.put(r);
    }

    return FluentFuture.from(
            deviceAttestor.attestFor(
                submission.diagnosisKeys,
                submission.applicableCountryCodes,
                submission.verificationCode,
                submission.transmissionRisk))
        .transformAsync(
            attestation -> {
              int paddingLengthRange = PADDING_SIZE_MAX - PADDING_SIZE_MIN;
              int paddingLength = PADDING_SIZE_MIN + RAND.nextInt(paddingLengthRange);
              String deviceVerificationPayload =
                  submission.isFakeTraffic
                      ? randomBase64Data(FAKE_SAFETYNET_ATTESTATION_LENGTH)
                      : attestation;
              submission.payload =
                  new JSONObject()
                      .put("temporaryExposureKeys", keysJson)
                      .put("regions", regionCodesJson)
                      .put("appPackageName", context.getPackageName())
                      .put("platform", PLATFORM)
                      .put("verificationPayload", DEFAULT_VERIFICATION_CODE)
                      .put("deviceVerificationPayload", deviceVerificationPayload)
                      .put("padding", randomBase64Data(paddingLength));
              return Futures.immediateFuture(submission);
            },
            AppExecutors.getLightweightExecutor());
  }

  private static String randomBase64Data(int approximateLength) {
    // Approximate the base64 blowup.
    int numBytes = (int) (((double) approximateLength) * 0.75);
    byte[] bytes = new byte[numBytes];
    RAND.nextBytes(bytes);
    return BASE64.encode(bytes);
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
                      Log.e(TAG, String.format("Diagnosis Key upload error: [%s]", err));
                      completer.setCancelled();
                    };

                SubmitKeysRequest request =
                    new SubmitKeysRequest(
                        submission.uri, submission.payload, responseListener, errorListener);
                RequestQueueSingleton.get(context).add(request);
                return request;
              }));
    }
    return Futures.allAsList(submitted);
  }

  /**
   * A private value class to help assembling the elements needed to upload keys to a given server.
   */
  private static class KeySubmission {
    // Uri and payload will be different in each submission.
    private Uri uri;
    private JSONObject payload;
    // All keys will be the same in all submissions.
    private ImmutableList<DiagnosisKey> diagnosisKeys;
    // All countries will be the same in all submissions.
    private List<String> applicableCountryCodes;
    private int transmissionRisk;
    private String verificationCode;
    private boolean isFakeTraffic;
  }

  /** Simple construction of a Diagnosis Keys submission. */
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
