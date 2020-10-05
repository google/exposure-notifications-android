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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;

import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.apps.exposurenotification.common.ExecutorsModule;
import com.google.android.apps.exposurenotification.common.Qualifiers.BackgroundExecutor;
import com.google.android.apps.exposurenotification.common.Qualifiers.LightweightExecutor;
import com.google.android.apps.exposurenotification.common.Qualifiers.ScheduledExecutor;
import com.google.android.apps.exposurenotification.keyupload.ApiConstants.VerifyV1;
import com.google.android.apps.exposurenotification.keyupload.ApiConstants.VerifyV1.Error;
import com.google.android.apps.exposurenotification.keyupload.Qualifiers.UploadUri;
import com.google.android.apps.exposurenotification.keyupload.Qualifiers.VerificationCertUri;
import com.google.android.apps.exposurenotification.keyupload.Qualifiers.VerificationCodeUri;
import com.google.android.apps.exposurenotification.keyupload.UploadController.KeysSubmitFailureException;
import com.google.android.apps.exposurenotification.keyupload.UploadController.UploadException;
import com.google.android.apps.exposurenotification.keyupload.UploadController.VerificationFailureException;
import com.google.android.apps.exposurenotification.keyupload.UploadController.VerificationServerFailureException;
import com.google.android.apps.exposurenotification.network.DiagnosisKey;
import com.google.android.apps.exposurenotification.network.RealRequestQueueModule;
import com.google.android.apps.exposurenotification.network.RequestQueueWrapper;
import com.google.android.apps.exposurenotification.testsupport.ExposureNotificationRules;
import com.google.android.apps.exposurenotification.testsupport.FakeRequestQueue;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.testing.TestingExecutors;
import dagger.hilt.android.testing.BindValue;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import dagger.hilt.android.testing.UninstallModules;
import edu.emory.mathcs.backport.java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;
import org.threeten.bp.LocalDate;

@RunWith(AndroidJUnit4.class)
@HiltAndroidTest
@Config(application = HiltTestApplication.class)
@UninstallModules({RealRequestQueueModule.class, UploadUrisModule.class, ExecutorsModule.class})
public class DiagnosisAttestorTest {

  // Having uninstalled some modules above (@UninstallModules), we need to provide everything they
  // would have, even if the code under test here doesn't use them.
  @BindValue
  @VerificationCodeUri
  static final Uri CODE_URI = Uri.parse("http://sampleurls.com/verify/code");
  @BindValue
  @VerificationCertUri
  static final Uri CERT_URI = Uri.parse("http://sampleurls.com/verify/cert");
  @BindValue
  @UploadUri
  static final Uri UNUSED_URI = Uri.EMPTY;
  @BindValue
  @BackgroundExecutor
  static final ExecutorService BACKGROUND_EXEC = MoreExecutors.newDirectExecutorService();
  @BindValue
  @LightweightExecutor
  static final ExecutorService LIGHTWEIGHT_EXEC = MoreExecutors.newDirectExecutorService();
  @BindValue
  @BackgroundExecutor
  static final ListeningExecutorService BACKGROUND_LISTENING_EXEC =
      MoreExecutors.newDirectExecutorService();
  @BindValue
  @LightweightExecutor
  static final ListeningExecutorService LIGHTWEIGHT_LISTENING_EXEC =
      MoreExecutors.newDirectExecutorService();
  @BindValue
  @ScheduledExecutor
  static final ScheduledExecutorService SCHEDULED_EXEC =
      TestingExecutors.sameThreadScheduledExecutor();

  @BindValue
  RequestQueueWrapper queue = new FakeRequestQueue();

  @Rule
  public ExposureNotificationRules rules = ExposureNotificationRules.forTest(this).build();

  @Inject
  DiagnosisAttestor diagnosisAttestor;

  @Before
  public void setUp() {
    rules.hilt().inject();
  }

  @Test
  public void code_coverTrafficRequest_shouldHaveXChaffHeader() throws Exception {
    // GIVEN
    Upload input = sampleUpload("code", sampleKey(1)).toBuilder().setIsCoverTraffic(true).build();
    setupSuccessfulCodeRpc("verification-token");

    // WHEN
    diagnosisAttestor.submitCode(input);

    // THEN
    // The verification code RPC is the first of two.
    assertThat(fakeQueue().getLastRpcHeaders()).containsEntry("X-Chaff", "1");
  }

  @Test
  public void cert_coverTrafficRequest_shouldHaveXChaffHeader() throws Exception {
    // GIVEN
    Upload input = sampleUpload("code", sampleKey(1)).toBuilder().setIsCoverTraffic(true).build();
    setupSuccessfulCertRpc("certificate");

    // WHEN
    diagnosisAttestor.submitKeysForCert(input);

    // THEN
    // The verification certificate RPC is the second of two.
    assertThat(fakeQueue().getLastRpcHeaders()).containsEntry("X-Chaff", "1");
  }

  @Test
  public void codeRequest_shouldHavePadding() throws Exception {
    // GIVEN
    Upload input = sampleUpload("code", sampleKey(1)).toBuilder().setIsCoverTraffic(true).build();
    setupSuccessfulCodeRpc("verification-token");

    // WHEN
    diagnosisAttestor.submitCode(input);

    // THEN
    JSONObject requestBody = fakeQueue().getLastRpcBody();
    assertThat(requestBody.getString(VerifyV1.PADDING)).isNotEmpty();
  }

  @Test
  public void certRequest_shouldHavePadding() throws Exception {
    // GIVEN
    Upload input = sampleUpload("code", sampleKey(1)).toBuilder().setIsCoverTraffic(true).build();
    setupSuccessfulCertRpc("certificate");

    // WHEN
    diagnosisAttestor.submitKeysForCert(input);

    // THEN
    JSONObject requestBody = fakeQueue().getLastRpcBody();
    assertThat(requestBody.getString(VerifyV1.PADDING)).isNotEmpty();
  }

  // TODO: lots more tests.

  @Test
  public void
      codeStatus400_unparseableRequest_throwsVerificationFailureException_withCodeExpiredError()
      throws Exception {
    doCodeErrorResponseTest(
        400,
        errorResponse(Error.UNPARSEABLE),
        VerificationFailureException.class,
        UploadError.APP_ERROR);
  }

  @Test
  public void codeStatus400_codeNotFound_throwsVerificationFailureException_withCodeInvalidError()
      throws Exception {
    doCodeErrorResponseTest(
        400,
        errorResponse(Error.CODE_NOT_FOUND),
        VerificationFailureException.class,
        UploadError.CODE_INVALID);
  }

  @Test
  public void codeStatus400_expiredCode_throwsVerificationFailureException_withCodeExpiredError()
      throws Exception {
    doCodeErrorResponseTest(
        400,
        errorResponse(Error.CODE_EXPIRED),
        VerificationFailureException.class,
        UploadError.CODE_EXPIRED);
  }

  @Test
  public void certStatus400_expiredToken_throwsVerificationFailureException_withCodeExpiredError()
      throws Exception {
    doCertErrorResponseTest(
        400,
        errorResponse(Error.TOKEN_EXPIRED),
        VerificationFailureException.class,
        UploadError.CODE_EXPIRED);
  }

  @Test
  public void certStatus400_tokenInvalid_throwsVerificationFailureException_withCodeInvalidError()
      throws Exception {
    doCertErrorResponseTest(
        400,
        errorResponse(Error.TOKEN_INVALID),
        VerificationFailureException.class,
        UploadError.CODE_INVALID);
  }

  @Test
  public void certStatus400_invalidHmac_throwsVerificationFailureException_withAppError()
      throws Exception {
    doCertErrorResponseTest(
        400,
        errorResponse(Error.HMAC_INVALID),
        VerificationFailureException.class,
        UploadError.APP_ERROR);
  }

  @Test
  public void status400_invalidTestType_throwsVerificationFailureException_withAppError()
      throws Exception {
    doCodeErrorResponseTest(
        400,
        errorResponse(Error.INVALID_TEST_TYPE),
        VerificationFailureException.class,
        UploadError.APP_ERROR);
  }

  @Test
  public void codeStatus412_unsupportedTest_throwsVerificationFailureException_withUnsupportedTest()
      throws Exception {
    doCodeErrorResponseTest(
        400,
        errorResponse(Error.UNSUPPORTED_TEST_TYPE),
        VerificationFailureException.class,
        UploadError.UNSUPPORTED_TEST_TYPE);
  }

  @Test
  public void
      codeStatus400_codeUserUnauthorised_throwsVerificationFailureException_withCodeInvalid()
      throws Exception {
    doCodeErrorResponseTest(
        400,
        errorResponse(Error.CODE_USER_UNAUTHORIZED),
        VerificationFailureException.class,
        UploadError.CODE_INVALID);
  }

  @Test
  public void codeStatus400_emptyErrorCode_throwsVerificationFailureException_withAppError()
      throws Exception {
    doCodeErrorResponseTest(
        400,
        "", // No error code in response.
        VerificationFailureException.class,
        UploadError.APP_ERROR);
  }

  @Test
  public void certStatus400_emptyErrorCode_throwsVerificationFailureException_withAppError()
      throws Exception {
    doCertErrorResponseTest(
        400,
        "", // No error code in response.
        VerificationFailureException.class,
        UploadError.APP_ERROR);
  }

  @Test
  public void codeStatus401_anyErrorCode_throwsVerificationFailureException_withAppError()
      throws Exception {
    doCodeErrorResponseTest(
        401,
        "", // Error code in response ignored.
        VerificationFailureException.class,
        UploadError.APP_ERROR);
  }

  @Test
  public void certStatus401_anyErrorCode_throwsVerificationFailureException_withAppError()
      throws Exception {
    doCertErrorResponseTest(
        401,
        "", // Error code in response ignored.
        VerificationFailureException.class,
        UploadError.APP_ERROR);
  }

  @Test
  public void codeStatus404_anyErrorCode_throwsVerificationFailureException_withAppError()
      throws Exception {
    doCodeErrorResponseTest(
        404,
        "", // Error code in response ignored.
        VerificationFailureException.class,
        UploadError.APP_ERROR);
  }

  @Test
  public void certStatus404_anyErrorCode_throwsVerificationFailureException_withAppError()
      throws Exception {
    doCertErrorResponseTest(
        404,
        "", // Error code in response ignored.
        VerificationFailureException.class,
        UploadError.APP_ERROR);
  }

  @Test
  public void codeStatus405_anyErrorCode_throwsVerificationFailureException_withAppError()
      throws Exception {
    doCodeErrorResponseTest(
        405,
        "", // Error code in response ignored.
        VerificationFailureException.class,
        UploadError.APP_ERROR);
  }

  @Test
  public void certStatus405_anyErrorCode_throwsVerificationFailureException_withAppError()
      throws Exception {
    doCertErrorResponseTest(
        405,
        "", // Error code in response ignored.
        VerificationFailureException.class,
        UploadError.APP_ERROR);
  }

  @Test
  public void codeStatus429_anyErrorCode_throwsVerificationFailureException_withRateLimitedError()
      throws Exception {
    doCodeErrorResponseTest(
        429,
        "", // Error code in response ignored.
        VerificationFailureException.class,
        UploadError.RATE_LIMITED);
  }

  @Test
  public void certStatus429_anyErrorCode_throwsVerificationFailureException_withRateLimitedError()
      throws Exception {
    doCertErrorResponseTest(
        429,
        "", // Error code in response ignored.
        VerificationFailureException.class,
        UploadError.RATE_LIMITED);
  }

  @Test
  public void codeStatus500_internalError_throwsVerificationServerFailureException_withServerError()
      throws Exception {
    doCodeErrorResponseTest(
        500,
        errorResponse(Error.INTERNAL),
        VerificationServerFailureException.class,
        UploadError.SERVER_ERROR);
  }

  @Test
  public void certStatus500_internalError_throwsVerificationServerFailureException_withServerError()
      throws Exception {
    doCertErrorResponseTest(
        500,
        errorResponse(Error.INTERNAL),
        VerificationServerFailureException.class,
        UploadError.SERVER_ERROR);
  }

  @Test
  public void codeStatus500_noErrorCode_throwsVerificationServerFailureException_withServerError()
      throws Exception {
    doCodeErrorResponseTest(
        500,
        "", // No error code in response.
        VerificationServerFailureException.class,
        UploadError.SERVER_ERROR);
  }

  @Test
  public void certStatus500_noErrorCode_throwsVerificationServerFailureException_withServerError()
      throws Exception {
    doCertErrorResponseTest(
        500,
        "", // No error code in response.
        VerificationServerFailureException.class,
        UploadError.SERVER_ERROR);
  }

  @Test
  public void codeRequest_otherHttpStatus_throwsVerificationFailureException_withUnknown()
      throws Exception {
    int nonExistentHttpStatus = 499;
    doCodeErrorResponseTest(
        nonExistentHttpStatus,
        errorResponse("any-error"),
        VerificationFailureException.class,
        UploadError.UNKNOWN);
  }

  @Test
  public void certRequest_otherHttpStatus_throwsVerificationFailureException_withUnknown()
      throws Exception {
    int nonExistentHttpStatus = 499;
    doCertErrorResponseTest(
        nonExistentHttpStatus,
        errorResponse("any-error"),
        VerificationFailureException.class,
        UploadError.UNKNOWN);
  }

  private void doCodeErrorResponseTest(
      int httpStatus, String responseBody, Class<? extends UploadException> thrownException,
      UploadError expectedErrorCode) {
    // GIVEN
    Upload anyInput = sampleUpload("code", sampleKey(1));
    fakeQueue().addResponse(CODE_URI.toString(), httpStatus, responseBody);

    // WHEN
    ThrowingRunnable execute = () -> diagnosisAttestor.submitCode(anyInput).get();

    // Then
    Exception thrown = assertThrows(ExecutionException.class, execute);
    // The cause it what we're really interested in.
    assertThat(thrown.getCause()).isInstanceOf(thrownException);
    // The UploadError is also important
    assertThat(errorCodeFrom(thrown)).isEqualTo(expectedErrorCode);
  }

  private void doCertErrorResponseTest(
      int httpStatus, String responseBody, Class<? extends UploadException> thrownException,
      UploadError expectedErrorCode) throws Exception {
    // GIVEN
    Upload anyInput = sampleUpload("code", sampleKey(1));
    fakeQueue().addResponse(CERT_URI.toString(), httpStatus, responseBody);

    // WHEN
    ThrowingRunnable execute = () -> diagnosisAttestor.submitKeysForCert(anyInput).get();

    // Then
    Exception thrown = assertThrows(ExecutionException.class, execute);
    // The cause it what we're really interested in.
    assertThat(thrown.getCause()).isInstanceOf(thrownException);
    // The UploadError is also important
    assertThat(errorCodeFrom(thrown)).isEqualTo(expectedErrorCode);
  }

  private static UploadError errorCodeFrom(Throwable thrown) {
    if (thrown.getCause() instanceof UploadException) {
      return ((UploadException) thrown.getCause()).getUploadError();
    }
    fail("Unexpected exception cause: " + thrown.getCause().getClass().getSimpleName());
    return null; // Can't get here, keeping the compiler happy.
  }

  /**
   * Constructs error response body in the JSON format defined by the keyserver. See:
   * https://github.com/google/exposure-notifications-verification-server/blob/main/pkg/api/api.go
   */
  private static String errorResponse(String errorCode) throws JSONException {
    return new JSONObject().put(VerifyV1.ERR_CODE, errorCode).toString();
  }

  private DiagnosisKey sampleKey(int i) {
    return DiagnosisKey.newBuilder()
        .setKeyBytes(("KEY-" + i).getBytes())
        .setIntervalNumber(42)
        .build();
  }

  private Upload sampleUpload(String verificationCode, DiagnosisKey... keys) {
    return Upload.newBuilder(verificationCode)
        .setKeys(Arrays.asList(keys))
        .setRegions(ImmutableList.of("US", "GB"))
        .setSymptomOnset(LocalDate.of(2020, 1, 2))
        .setHasTraveled(true)
        .build();
  }

  private void setupSuccessfulCodeRpc(String revisionToken) throws Exception {
    fakeQueue().addResponse(CODE_URI.toString(), 200, successfulCodeResponse(revisionToken));
  }

  private String successfulCodeResponse(String token) throws JSONException {
    return new JSONObject().put(VerifyV1.VERIFICATION_TOKEN, token).toString();
  }

  private void setupSuccessfulCertRpc(String cert) throws Exception {
    fakeQueue().addResponse(CERT_URI.toString(), 200, successfulCertResponse(cert));
  }

  private String successfulCertResponse(String cert) throws JSONException {
    return new JSONObject().put(VerifyV1.CERT, cert).toString();
  }

  /**
   * Just some syntactical sugar to encapsulate the ugly cast.
   */
  private FakeRequestQueue fakeQueue() {
    return (FakeRequestQueue) queue;
  }

}
