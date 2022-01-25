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

import android.content.Context;
import android.net.Uri;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.SecureRandomUtil;
import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.android.apps.exposurenotification.common.time.RealTimeModule;
import com.google.android.apps.exposurenotification.keyupload.ApiConstants.UploadV1;
import com.google.android.apps.exposurenotification.keyupload.ApiConstants.UploadV1.Error;
import com.google.android.apps.exposurenotification.keyupload.Qualifiers.UploadUri;
import com.google.android.apps.exposurenotification.keyupload.Qualifiers.VerificationCertUri;
import com.google.android.apps.exposurenotification.keyupload.Qualifiers.VerificationCodeUri;
import com.google.android.apps.exposurenotification.keyupload.Qualifiers.VerificationUserReportUri;
import com.google.android.apps.exposurenotification.keyupload.UploadController.KeysSubmitFailureException;
import com.google.android.apps.exposurenotification.keyupload.UploadController.KeysSubmitServerFailureException;
import com.google.android.apps.exposurenotification.keyupload.UploadController.UploadException;
import com.google.android.apps.exposurenotification.network.DiagnosisKey;
import com.google.android.apps.exposurenotification.network.RealRequestQueueModule;
import com.google.android.apps.exposurenotification.network.RequestQueueWrapper;
import com.google.android.apps.exposurenotification.testsupport.ExposureNotificationRules;
import com.google.android.apps.exposurenotification.testsupport.FakeClock;
import com.google.android.apps.exposurenotification.testsupport.FakeRequestQueue;
import com.google.common.collect.ImmutableList;
import com.google.common.io.BaseEncoding;
import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.testing.BindValue;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import dagger.hilt.android.testing.UninstallModules;
import dagger.hilt.components.SingletonComponent;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import javax.inject.Inject;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;
import org.threeten.bp.LocalDate;
import org.threeten.bp.ZoneOffset;

@RunWith(AndroidJUnit4.class)
@HiltAndroidTest
@Config(application = HiltTestApplication.class)
@UninstallModules({RealRequestQueueModule.class, RealTimeModule.class, UploadUrisModule.class})
public final class DiagnosisKeyUploaderTest {

  private static final BaseEncoding BASE64 = BaseEncoding.base64();
  private static final Uri UPLOAD_URI = Uri.parse("http://sampleurls.com/upload");

  @Rule
  public ExposureNotificationRules rules = ExposureNotificationRules.forTest(this).build();

  private final Context context = ApplicationProvider.getApplicationContext();
  @BindValue
  RequestQueueWrapper queue = new FakeRequestQueue();
  @BindValue
  Clock clock = new FakeClock();

  @Inject
  DiagnosisKeyUploader keyUploader;

  @Inject
  SecureRandom secureRandom;

  @Module
  @InstallIn(SingletonComponent.class)
  static class SampleUrisModule {

    @Provides
    @UploadUri
    public Uri provideUploadUri() {
      return UPLOAD_URI;
    }

    @Provides
    @VerificationCodeUri
    public Uri provideCodeUri() {
      return Uri.EMPTY;  // Unused in this test. Just keeping the Hilt dep graph happy.
    }

    @Provides
    @VerificationCertUri
    public Uri provideCertUri() {
      return Uri.EMPTY;  // Unused. Just keeping the Hilt dep graph happy.
    }

    @Provides
    @VerificationUserReportUri
    public Uri provideUserReportUri() {
      return Uri.EMPTY;  // Unused. Just keeping the Hilt dep graph happy.
    }
  }

  @Before
  public void setUp() {
    rules.hilt().inject();
  }

  @Test
  public void upload_emptyKeys_shouldSkipRpc() throws Exception {
    // WHEN
    keyUploader.upload(sampleUpload("code")).get();
    // THEN
    assertThat(fakeQueue().numRpcs()).isEqualTo(0);
  }

  @Test
  public void upload_emptyKeys_shouldReturnUploadArgUnchanged() throws Exception {
    // GIVEN
    Upload input = sampleUpload("code");
    // WHEN
    Upload output = keyUploader.upload(input).get();
    // THEN
    assertThat(output).isEqualTo(input);
  }

  @Test
  public void upload_singleKey_requestShouldIncludeKeyData_asJsonArrayOfJsonObjects()
      throws Exception {
    // GIVEN
    DiagnosisKey key = sampleKey(1);
    Upload input = sampleUpload("code", key);
    setupSuccessfulRpc("revision-token");

    // WHEN
    keyUploader.upload(input).get();

    // THEN
    JSONObject requestBody = fakeQueue().getLastRpcBody();
    JSONArray keysJson = requestBody.getJSONArray(UploadV1.KEYS);
    assertThat(keysJson).isNotNull();
    assertThat(keysJson.get(0)).isInstanceOf(JSONObject.class);
    JSONObject keyJson = (JSONObject) keysJson.get(0);
    assertThat(keyJson.getString(UploadV1.KEY)).isEqualTo(BASE64.encode(key.getKeyBytes()));
    assertThat(keyJson.getInt(UploadV1.ROLLING_START_NUM)).isEqualTo(key.getIntervalNumber());
    assertThat(keyJson.getInt(UploadV1.ROLLING_PERIOD)).isEqualTo(key.getRollingPeriod());
    assertThat(keyJson.getInt(UploadV1.TRANSMISSION_RISK)).isEqualTo(key.getTransmissionRisk());
  }

  @Test
  public void upload_multipleKeys_requestShouldIncludeAllKeysInRequest() throws Exception {
    // GIVEN
    DiagnosisKey key1 = sampleKey(1);
    DiagnosisKey key2 = sampleKey(2);
    DiagnosisKey key3 = sampleKey(3);
    Upload input = sampleUpload("code", key1, key2, key3);
    setupSuccessfulRpc("revision-token");

    // WHEN
    keyUploader.upload(input).get();

    // THEN
    JSONObject requestBody = fakeQueue().getLastRpcBody();
    JSONArray keysJson = requestBody.getJSONArray(UploadV1.KEYS);
    assertThat(keysJson).isNotNull();
    assertThat(keysJson.length()).isEqualTo(3);
  }

  @Test
  public void upload_requestShouldIncludeSubmissionMetadata() throws Exception {
    // GIVEN
    Upload input = sampleUpload("code", sampleKey(1));
    setupSuccessfulRpc("revision-token");

    // WHEN
    keyUploader.upload(input).get();

    // THEN
    JSONObject requestBody = fakeQueue().getLastRpcBody();
    int expectedOnsetIntervalNum = DiagnosisKey.instantToInterval(
        input.symptomOnset().atStartOfDay(ZoneOffset.UTC).toInstant());
    assertThat(requestBody.getBoolean(UploadV1.TRAVELER)).isEqualTo(true);
    assertThat(requestBody.getString(UploadV1.APP_PACKAGE))
        .isEqualTo(context.getString(R.string.enx_healthAuthorityID));
    assertThat(requestBody.getString(UploadV1.HMAC_KEY)).isEqualTo(input.hmacKeyBase64());
    assertThat(requestBody.getInt(UploadV1.ONSET)).isEqualTo(expectedOnsetIntervalNum);
    assertThat(requestBody.getString(UploadV1.VERIFICATION_CERT)).isEqualTo(input.certificate());
    assertThat(requestBody.getString(UploadV1.TRAVELER)).isEqualTo("true");
    assertThat(requestBody.getString(UploadV1.PADDING)).isNotEmpty();
  }

  @Test
  public void upload_noOnsetDateInInput_requestShouldNotIncludeOnsetDate() throws Exception {
    // GIVEN
    Upload input = sampleUpload("code", sampleKey(1)).toBuilder().setSymptomOnset(null).build();
    setupSuccessfulRpc("revision-token");

    // WHEN
    keyUploader.upload(input).get();

    // THEN
    JSONObject requestBody = fakeQueue().getLastRpcBody();
    assertThat(requestBody.has(UploadV1.ONSET)).isFalse();
  }

  @Test
  public void upload_whenSuccessful_shouldCaptureRevisionTokenFromServer() throws Exception {
    // GIVEN
    DiagnosisKey key = sampleKey(1);
    Upload input = sampleUpload("code", key);
    setupSuccessfulRpc("revision-token");

    // WHEN
    Upload result = keyUploader.upload(input).get();

    // THEN
    assertThat(result.revisionToken()).isEqualTo("revision-token");
  }

  @Test
  public void coverTrafficRequest_shouldHaveXChaffHeader() throws Exception {
    // GIVEN
    Upload input = sampleUpload("code", sampleKey(1)).toBuilder().setIsCoverTraffic(true).build();
    setupSuccessfulRpc("revision-token");

    // WHEN
    keyUploader.upload(input).get();

    // THEN
    Map<String, String> headers = fakeQueue().getLastRpcHeaders();
    assertThat(headers).containsEntry("X-Chaff", "1");
  }

  @Test
  public void coverTrafficRequest_shouldTolerateGarbageResponse() throws Exception {
    // GIVEN
    Upload input = sampleUpload("code", sampleKey(1)).toBuilder().setIsCoverTraffic(true).build();
    fakeQueue().addResponse(UPLOAD_URI.toString(), 200, "Response not parsable as JSON.");

    // WHEN
    // If nothing is thrown here we're happy.
    keyUploader.upload(input).get();
  }

  @Test
  public void status400_noErrorCode_throwsKeysSubmitFailureException_withAppErrorCode() {
    String responseWithoutErrorCode = "{}";
    doErrorResponseTest(
        400,
        responseWithoutErrorCode,
        KeysSubmitFailureException.class,
        UploadError.APP_ERROR);
  }

  @Test
  public void status400_unknownApp_throwsKeysSubmitFailureException_withServerError()
      throws Exception {
    doErrorResponseTest(
        400,
        errorResponse(UploadV1.Error.UNKNOWN_APP),
        KeysSubmitFailureException.class,
        UploadError.SERVER_ERROR);
  }

  @Test
  public void status400_haConfigFail_throwsKeysSubmitFailureException_withServerError()
      throws Exception {
    doErrorResponseTest(
        400,
        errorResponse(Error.HA_CONFIG_LOAD_FAIL),
        KeysSubmitFailureException.class,
        UploadError.SERVER_ERROR);
  }

  @Test
  public void status400_haRegionConfigFail_throwsKeysSubmitFailureException_withServerError()
      throws Exception {
    doErrorResponseTest(
        400,
        errorResponse(Error.HA_REGION_CONFIG),
        KeysSubmitFailureException.class,
        UploadError.SERVER_ERROR);
  }

  @Test
  public void status400_certInvalid_throwsKeysSubmitFailureException_withServerError()
      throws Exception {
    doErrorResponseTest(
        400,
        errorResponse(Error.CERT_INVALID),
        KeysSubmitFailureException.class,
        UploadError.SERVER_ERROR);
  }

  @Test
  public void status400_badRequest_throwsKeysSubmitFailureException_withServerError()
      throws Exception {
    doErrorResponseTest(
        400,
        errorResponse(Error.BAD_REQUEST),
        KeysSubmitFailureException.class,
        UploadError.APP_ERROR);
  }

  @Test
  public void status400_missingRevisionToken_throwsKeysSubmitFailureException_withAppError()
      throws Exception {
    doErrorResponseTest(
        400,
        errorResponse(Error.MISSING_REVISION_TOKEN),
        KeysSubmitFailureException.class,
        UploadError.APP_ERROR);
  }

  @Test
  public void status400_invalidRevisionToken_throwsKeysSubmitFailureException_withAppError()
      throws Exception {
    doErrorResponseTest(
        400,
        errorResponse(Error.INVALID_REVISION_TOKEN),
        KeysSubmitFailureException.class,
        UploadError.APP_ERROR);
  }

  @Test
  public void status400_keyAlreadyRevised_throwsKeysSubmitFailureException_withAppError()
      throws Exception {
    doErrorResponseTest(
        400,
        errorResponse(Error.KEY_ALREADY_REVISED),
        KeysSubmitFailureException.class,
        UploadError.APP_ERROR);
  }

  @Test
  public void status400_invalidRevisionTransition_throwsKeysSubmitFailureException_withAppError()
      throws Exception {
    doErrorResponseTest(
        400,
        errorResponse(Error.INVALID_REVISION_TRANSITION),
        KeysSubmitFailureException.class,
        UploadError.APP_ERROR);
  }

  @Test
  public void status401_throwsKeysSubmitFailureException_withUnauthClient()
      throws Exception {
    doErrorResponseTest(
        401,
        errorResponse("any-error"),
        KeysSubmitFailureException.class,
        UploadError.UNAUTHORIZED_CLIENT);
  }

  @Test
  public void status403_throwsKeysSubmitFailureException_withAppError()
      throws Exception {
    doErrorResponseTest(
        403,
        errorResponse("any-error"),
        KeysSubmitFailureException.class,
        UploadError.APP_ERROR);
  }

  @Test
  public void status404_throwsKeysSubmitFailureException_withAppError()
      throws Exception {
    doErrorResponseTest(
        404,
        errorResponse("any-error"),
        KeysSubmitFailureException.class,
        UploadError.APP_ERROR);
  }

  @Test
  public void status405_throwsKeysSubmitFailureException_withAppError()
      throws Exception {
    doErrorResponseTest(
        405,
        errorResponse("any-error"),
        KeysSubmitFailureException.class,
        UploadError.APP_ERROR);
  }

  @Test
  public void status500_internalError_throwsKeysSubmitServerFailureException_withServerError()
      throws Exception {
    doErrorResponseTest(
        500,
        errorResponse(Error.INTERNAL_ERROR),
        KeysSubmitServerFailureException.class,
        UploadError.SERVER_ERROR);
  }

  @Test
  public void status500_anyErrorCode_throwsKeysSubmitServerFailureException_withServerError()
      throws Exception {
    doErrorResponseTest(
        500,
        errorResponse("any-error"),
        KeysSubmitServerFailureException.class,
        UploadError.SERVER_ERROR);
  }

  @Test
  public void otherHttpStatus_throwsKeysSubmitFailureException_withUnknown()
      throws Exception {
    int nonExistentHttpStatus = 499;
    doErrorResponseTest(nonExistentHttpStatus, errorResponse("any-error"),
        KeysSubmitFailureException.class, UploadError.UNKNOWN);
  }

  @Test
  public void failToCaptureRevisionToken_throwsKeysSubmitFailureException_withServerErrorCode() {
    // GIVEN
    Upload input = sampleUpload("code", sampleKey(1));
    String validJsonWithoutRevisionToken = "{}";
    fakeQueue().addResponse(UPLOAD_URI.toString(), 200, validJsonWithoutRevisionToken);

    // THEN
    Exception thrown = assertThrows(
        // Failure comes wrapped in an ExecutionException.
        ExecutionException.class, () -> keyUploader.upload(input).get());
    // The cause it what we're really interested in.
    assertThat(thrown.getCause()).isInstanceOf(KeysSubmitFailureException.class);
    // The UploadError is also important
    assertThat(errorCodeFrom(thrown)).isEqualTo(UploadError.SERVER_ERROR);
  }

  private void doErrorResponseTest(
      int httpStatus,
      String responseBody,
      Class<? extends UploadException> thrownException,
      UploadError expectedErrorCode) {
    // GIVEN
    Upload anyInput = sampleUpload("code", sampleKey(1));
    fakeQueue().addResponse(UPLOAD_URI.toString(), httpStatus, responseBody);

    // WHEN
    ThrowingRunnable execute = () -> keyUploader.upload(anyInput).get();

    // Then
    Exception thrown = assertThrows(ExecutionException.class, execute);
    // The cause it what we're really interested in.
    assertThat(thrown.getCause()).isInstanceOf(thrownException);
    // The UploadError is also important
    assertThat(errorCodeFrom(thrown)).isEqualTo(expectedErrorCode);
  }

  private DiagnosisKey sampleKey(int i) {
    return DiagnosisKey.newBuilder()
        .setKeyBytes(("KEY-" + i).getBytes())
        .setIntervalNumber(42)
        .build();
  }

  private Upload sampleUpload(String verificationCode, DiagnosisKey... keys) {
    return Upload.newBuilder(verificationCode, SecureRandomUtil.newHmacKey(secureRandom))
        .setKeys(Arrays.asList(keys))
        .setRegions(ImmutableList.of("US", "GB"))
        .setHmacKeyBase64("hmackey")
        .setCertificate("cert")
        .setSymptomOnset(LocalDate.of(2020, 1, 2))
        .setHasTraveled(true)
        .build();
  }

  private void setupSuccessfulRpc(String revisionToken) throws Exception {
    fakeQueue().addResponse(UPLOAD_URI.toString(), 200, successResponse(revisionToken));
  }

  private String successResponse(String revisionToken) throws JSONException {
    return new JSONObject().put("revisionToken", revisionToken).toString();
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
   * https://github.com/google/exposure-notifications-server/blob/main/pkg/api/v1/exposure_types.go
   */
  private static String errorResponse(String errorCode) throws JSONException {
    return new JSONObject().put(UploadV1.ERR_CODE, errorCode).toString();
  }

  /**
   * Just some syntactical sugar to encapsulate the ugly cast.
   */
  private FakeRequestQueue fakeQueue() {
    return (FakeRequestQueue) queue;
  }
}
