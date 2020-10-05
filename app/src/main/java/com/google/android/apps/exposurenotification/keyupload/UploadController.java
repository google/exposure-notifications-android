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

import android.util.Log;
import com.android.volley.VolleyError;
import com.google.common.util.concurrent.ListenableFuture;
import javax.inject.Inject;

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

  private final DiagnosisAttestor diagnosisAttestor;
  private final DiagnosisKeyUploader uploader;

  @Inject
  public UploadController(
      DiagnosisAttestor diagnosisAttestor,
      DiagnosisKeyUploader uploader) {
    this.diagnosisAttestor = diagnosisAttestor;
    this.uploader = uploader;
  }

  /**
   * Exchanges a short-lived verification code for a long-lived token.
   *
   * <p>Called first, prior to {@link #submitKeysForCert(Upload)}.
   */
  public ListenableFuture<Upload> submitCode(Upload upload) {
    Log.i(TAG, "Submitting code...");
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
    Log.i(TAG, "Submitting keys for verification certificate...");
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
    Log.i(TAG, "Uploading keys and verification certificate to the keyserver.");
    return uploader.upload(upload);
  }

  /**
   * An exception indicating that there was a failure to submit the verification code to the
   * verification server.
   */
  public static class VerificationFailureException extends UploadException {

    public VerificationFailureException(VolleyError error) {
      super(error);
    }
  }

  /**
   * An exception indicating that server failure occurred during attempt to submit the verification
   * code to verification server.
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
  abstract static class UploadException extends Exception {

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
