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

import com.android.volley.VolleyError;
import com.google.android.apps.exposurenotification.common.logging.Logger;
import com.google.android.apps.exposurenotification.network.Connectivity;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import javax.inject.Inject;

/**
 * A facade to server interactions to upload diagnosis keys upon positive diagnosis of COVID-19.
 *
 * <p>This controller is responsible to:
 * <ul>
 *   <li>Submit a verification code and diagnosis keys to the verification server to request it
 *   sign the keys, attesting that the user's diagnosis is genuine.
 *   <li>Submit a phone number and a device-generated nonce to request a verification code as part
 *   of the self-report flow i.e. to allow users submit an unconfirmed test report.
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

  private static final Logger logger = Logger.getLogger("UploadController");

  private final DiagnosisAttestor diagnosisAttestor;
  private final DiagnosisKeyUploader uploader;
  private final Connectivity connectivity;

  @Inject
  public UploadController(
      DiagnosisAttestor diagnosisAttestor,
      DiagnosisKeyUploader uploader,
      Connectivity connectivity) {
    this.diagnosisAttestor = diagnosisAttestor;
    this.uploader = uploader;
    this.connectivity = connectivity;
  }

  /**
   * Request a verification code for the self-report flow.
   *
   * <p>Called first in the self-report flow, prior to {@link #submitCode(Upload)}.
   *
   * @param upload with a user-provided phone number and test date as well as user's time zone
   *               offset and on-device-generated nonce.
   */
  public ListenableFuture<UserReportUpload> requestCode(UserReportUpload upload) {
    if (!connectivity.hasInternet()) {
      return Futures.immediateFailedFuture(new NoInternetException());
    }
    return diagnosisAttestor.requestCode(upload);
  }

  /**
   * Exchanges a short-lived verification code for a long-lived token.
   *
   * <p>Called first, prior to {@link #submitKeysForCert(Upload)}.
   */
  public ListenableFuture<Upload> submitCode(Upload upload) {
    if (!connectivity.hasInternet()) {
      return Futures.immediateFailedFuture(new NoInternetException());
    }
    logger.i("Submitting code...");
    return diagnosisAttestor.submitCode(upload);
  }

  /**
   * Request the verification server to sign our diagnosis keys, given a valid verification code.
   *
   * <p>Called second, after {@link #submitCode(Upload)} and before {@link #upload(Upload)}.
   *
   * @param upload with a long lived verification token, a set of diagnosis keys, and certain
   *               metadata about the user's diagnosis, like symptom onset date.
   * @return a future with an {@link Upload} populated with the verification server's certificate,
   * and any metadata it returned along with it (such as onset date).
   */
  public ListenableFuture<Upload> submitKeysForCert(Upload upload) {
    if (!connectivity.hasInternet()) {
      return Futures.immediateFailedFuture(new NoInternetException());
    }
    logger.i("Submitting keys for verification certificate...");
    return diagnosisAttestor.submitKeysForCert(upload);
  }

  /**
   * Submits signed diagnosis keys and metadata in the given {@link Upload} to the diagnosis key
   * server.
   *
   * <p>Called third, after {@link #submitKeysForCert(Upload)} (Upload)}, with the {@link Upload}
   * returned by that method. Between the two calls, the caller may have added some user-supplied
   * metadata to the {@link Upload} such as onset date and travel status.
   *
   * <p>Alternatively, if there is no additional input needed from the user, the caller may
   * continue straight from {@link #submitKeysForCert(Upload)} to {@link #upload(Upload)} with no
   * user interaction between.
   */
  public ListenableFuture<Upload> upload(Upload upload) {
    if (!connectivity.hasInternet()) {
      return Futures.immediateFailedFuture(new NoInternetException());
    }
    logger.i("Uploading keys and verification certificate to the keyserver.");
    return uploader.upload(upload);
  }

  /**
   * An {@link Exception} thrown when there is no internet connectivity during phoneNumber+testDate
   * or verification code submission.
   */
  public static class NoInternetException extends Exception {}

  /**
   * An exception indicating that there was a failure to request or submit the verification code
   * to the verification server.
   */
  public static class VerificationFailureException extends UploadException {

    public VerificationFailureException(VolleyError error) {
      super(error);
    }
  }

  /**
   * An exception indicating that server failure occurred during attempt to request or submit
   * the verification code to verification server.
   */
  public static class VerificationServerFailureException extends UploadException {

    public VerificationServerFailureException(VolleyError error) {
      super(error);
    }
  }

  /**
   * An exception indicating that there was a failure to submit diagnosis keys to the key server.
   */
  public static class KeysSubmitFailureException extends UploadException {

    public KeysSubmitFailureException(VolleyError error) {
      super(error);
    }

    public KeysSubmitFailureException(UploadError err) {
      super(err);
    }
  }

  /**
   * An exception indicating that server failure occurred during attempt to submit the diagnosis
   * keys to the key server.
   */
  public static class KeysSubmitServerFailureException extends UploadException {

    public KeysSubmitServerFailureException(VolleyError error) {
      super(error);
    }
  }

  /**
   * Base class for exceptions thrown when we get error responses from either the verification
   * server or the keyserver.
   */
  abstract public static class UploadException extends Exception {

    private final UploadError uploadError;

    public UploadException(VolleyError error) {
      super(error);

      this.uploadError = UploadError.fromVolleyError(error);
    }

    public UploadException(UploadError err) {
      this.uploadError = err;
    }

    public UploadError getUploadError() {
      return uploadError;
    }

  }
}
