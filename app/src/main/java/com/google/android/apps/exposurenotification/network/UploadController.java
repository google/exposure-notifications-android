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

import android.util.Log;
import com.google.android.apps.exposurenotification.common.StringUtils;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences.NetworkMode;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.threeten.bp.LocalDate;
import org.threeten.bp.ZoneId;

/**
 * A facade to server interactions to upload diagnosis keys upon positive diagnosis of COVID-19.
 *
 * <p>This controller is responsible to:
 * <ul>
 *   <li>Submit a verification code and diagnosis keys to the verification server to request it
 *   sign the keys, attesting that the user's diagnosis is genuine.
 *   <li>Submit the diagnosis keys and the verification server's signature to the diagnosis key
 *   server for sharing to other participants in the Exposure Notifications program.
 * </ul>
 *
 * <p>The caller is expected to be a ViewModel which:
 * <ul>
 *   <li>Collects user input such as the verification code and date of symptom onset
 *   <li>Obtains diagnosis keys from the Exposure Notifications API
 *   <li>Writes to storage any records the app needs to retain about an upload.
 * </ul>
 */
public final class UploadController {
  private static final String TAG = "UploadController";

  private final CountryCodes countries;
  private final DiagnosisAttestor diagnosisAttestor;
  private final DiagnosisKeyUploader uploader;
  private final ExposureNotificationSharedPreferences prefs;

  public UploadController(
      CountryCodes countries,
      DiagnosisAttestor diagnosisAttestor,
      DiagnosisKeyUploader uploader,
      ExposureNotificationSharedPreferences prefs) {
    this.countries = countries;
    this.diagnosisAttestor = diagnosisAttestor;
    this.uploader = uploader;
    this.prefs = prefs;
  }

  /**
   * Request the verification server to sign our diagnosis keys, given a valid verification code.
   *
   * <p>Called first, before {@link #upload(Upload)}.
   *
   * @param upload with at minimum: a verification code, set of diagnosis keys, and applicable
   *               country/region codes where the keys were used.
   * @return a future with an {@link Upload} populated with the verification server's certificate,
   * and any metadata it returned along with it (such as onset date).
   */
  public ListenableFuture<Upload> verify(Upload upload) {
    // Add applicable country/region codes:
    upload = upload.toBuilder()
        .setHomeRegion(countries.getHomeCountryCode())
        .setRegions(countries.getExposureRelevantCountryCodes())
        .build();

    if (prefs.getVerificationNetworkMode(NetworkMode.DISABLED).equals(NetworkMode.DISABLED)) {
      Log.i(TAG, "Verification server disabled. Returning dummy verification response.");
      return Futures.immediateFuture(disabledVerification(upload));
    }

    Log.i(TAG, "Verification server enabled. Obtaining verification...");
    return diagnosisAttestor.attestFor(upload);
  }

  /**
   * Submits signed diagnosis keys and metadata in the given {@link Upload} to the diagnosis key
   * server.
   *
   * <p>Called second, after {@link #verify(Upload)}, with the {@link Upload} returned by that
   * method. Between the two calls, the caller may have added some user-supplied metadata to the
   * {@link Upload} such as onset date.
   *
   * <p>Alternatively, if there is no additional input needed from the user, the caller may
   * continue straight from {@link #verify(Upload)} to {@link #upload(Upload)} with no user
   * interaction between.
   */
  public ListenableFuture<Upload> upload(Upload upload) {
    if (prefs.getKeySharingNetworkMode(NetworkMode.DISABLED).equals(NetworkMode.DISABLED)) {
      Log.i(TAG, "Diagnosis Key Server disabled. returning dummy upload response.");
      return Futures.immediateFuture(disabledUpload(upload));
    }

    return uploader.upload(upload);
  }

  /**
   * Returns the given {@link Upload} with some fields populate as if we'd actually called the
   * verification server. This is just for testing without that server.
   */
  private final Upload disabledVerification(Upload upload) {
    return upload.toBuilder()
        // TODO: figure out the right sizes for these random data fields.
        .setCertificate(StringUtils.randomBase64Data(1024))
        .setLongTermToken(StringUtils.randomBase64Data(64))
        .setTestType("DEFAULT")
        .setDiagnosisDate(LocalDate.now(ZoneId.systemDefault()).minusDays(3))
        .build();
  }

  /**
   * Returns the given {@link Upload} with some fields populate as if we'd actually called the
   * verification server. This is just for testing without that server.
   */
  private final Upload disabledUpload(Upload upload) {
    return upload.toBuilder()
        // TODO: figure out the right size for this random data.
        .setHmacKeyBase64(StringUtils.randomBase64Data(100))
        .build();
  }

  /**
   * An exception with which to fail the future returned from {@link #verify(Upload)} when the
   * verification server rejects our verification code.
   */
  public static class InvalidVerificationCodeException extends Exception {

  }

  /**
   * An exception indicating that there was a failure to submit the verification code. This is a
   * permanent failure; any retries that may be worthwhile have already been exhausted.
   */
  public static class VerificationCodeFailureException extends Exception {
    public VerificationCodeFailureException(Throwable cause) {
      super(cause);
    }
  }

}
