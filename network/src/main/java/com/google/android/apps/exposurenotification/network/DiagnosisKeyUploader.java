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
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.threeten.bp.Duration;

/** A class to encapsulate uploading Diagnosis Keys to the key sharing server. */
public class DiagnosisKeyUploader {

  private static final String TAG = "KeyUploader";
  // NOTE: The server expects padding.
  private static final BaseEncoding BASE64 = BaseEncoding.base64();
  // TODO: accept these as args instead of hard-coded.
  private static final int DEFAULT_PERIOD = DiagnosisKey.DEFAULT_PERIOD;
  private static final int DEFAULT_TRANSMISSION_RISK = 1;
  private static final String DEFAULT_VERIFICATION_AUTHORITY = "PUBLIC_HEALTH_AUTHORITY";
  // All requests to the server take at least 5s by design, so time out far longer than that.
  private static final Duration TIMEOUT = Duration.ofSeconds(30);
  private static final int MAX_RETRIES = 3;
  private static final float RETRY_BACKOFF = 1.0f;

  private final Context context;
  private final DeviceAttestor deviceAttestor;
  private final CountryCodes countryCodes;
  private final Uris uris;

  DiagnosisKeyUploader(Context context) {
    this.context = context;
    deviceAttestor = new DeviceAttestor(context);
    countryCodes = new CountryCodes(context);
    uris = new Uris(context);
  }

  /**
   * Uploads the given keys to the key server(s) for all of the currently relevant
   * countries/regions. We do not attempt to track which keys were used in which countries, so we
   * upload all keys to all relevant countries/regions. For most users there will be one relevant
   * country.
   *
   * <p>The returned future represents success/fail of all the key server submissions.
   *
   * <p>TODO: Perhaps it would be good to support partial success with retry of failed submissions.
   *
   * @param diagnosisKeys the keys to submit, in an {@link ImmutableList} because internally we'll
   *     share this around between threads so immutability makes things safer.
   */
  public ListenableFuture<?> upload(ImmutableList<DiagnosisKey> diagnosisKeys) {
    if (diagnosisKeys.isEmpty()) {
      return Futures.immediateFuture(null);
    }

    // In several steps, we need all the relevant countries for the user's last N days.
    ListenableFuture<List<String>> countries = countryCodes.getExposureRelevantCountryCodes();

    // We start with that list of countries.
    return FluentFuture.from(countries)
        // From these we find the URIs for key servers for that list of countries. There need not be
        // a one-to-one relationship between country codes and server URIs.
        .transformAsync(uris::getUploadUris, AppExecutors.getLightweightExecutor())
        // For each URI, we start a KeySubmission into which we will add all the necessary parts,
        // like the payload, countries, keys, etc,
        .transformAsync(this::startSubmissionsForUris, AppExecutors.getLightweightExecutor())
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

  private ListenableFuture<List<KeySubmission>> startSubmissionsForUris(List<Uri> serverUris) {
    List<KeySubmission> submissions = new ArrayList<>();
    for (Uri uri : serverUris) {
      KeySubmission s = new KeySubmission();
      s.uri = uri;
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
                .put("intervalNumber", k.getIntervalNumber())
                .put("intervalCount", DEFAULT_PERIOD));
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
                DEFAULT_VERIFICATION_AUTHORITY,
                DEFAULT_TRANSMISSION_RISK))
        .transformAsync(
            attestation -> {
              submission.payload =
                  new JSONObject()
                      .put("exposureKeys", keysJson)
                      .put("regions", regionCodesJson)
                      .put("appPackageName", context.getPackageName())
                      .put("verificationPayload", attestation)
                      .put("transmissionRisk", DEFAULT_TRANSMISSION_RISK)
                      .put("verificationAuthorityName", DEFAULT_VERIFICATION_AUTHORITY);
              return Futures.immediateFuture(submission);
            },
            AppExecutors.getLightweightExecutor());
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
   * A private value class to make assembling the needed elements for a given key upload submission.
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
    private String verificationAuthority;
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
