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

package com.google.android.apps.exposurenotification.notify;

import static com.google.android.apps.exposurenotification.notify.ShareDiagnosisViewModel.SAVED_STATE_CODE_INPUT;
import static com.google.android.apps.exposurenotification.notify.ShareDiagnosisViewModel.SAVED_STATE_CODE_INPUT_IS_ENABLED;
import static com.google.android.apps.exposurenotification.notify.ShareDiagnosisViewModel.SAVED_STATE_CODE_IS_INVALID;
import static com.google.android.apps.exposurenotification.notify.ShareDiagnosisViewModel.SAVED_STATE_CODE_IS_VERIFIED;
import static com.google.android.apps.exposurenotification.notify.ShareDiagnosisViewModel.SAVED_STATE_SHARE_ADVANCE_SWITCHER_CHILD;
import static com.google.android.apps.exposurenotification.notify.ShareDiagnosisViewModel.SAVED_STATE_VERIFIED_CODE;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.test.espresso.core.internal.deps.guava.collect.ImmutableList;
import androidx.annotation.Nullable;
import androidx.lifecycle.SavedStateHandle;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.IntentUtil;
import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.android.apps.exposurenotification.common.time.RealTimeModule;
import com.google.android.apps.exposurenotification.keyupload.ApiConstants.UploadV1;
import com.google.android.apps.exposurenotification.keyupload.ApiConstants.VerifyV1;
import com.google.android.apps.exposurenotification.keyupload.Qualifiers.UploadUri;
import com.google.android.apps.exposurenotification.keyupload.Qualifiers.VerificationCertUri;
import com.google.android.apps.exposurenotification.keyupload.Qualifiers.VerificationCodeUri;
import com.google.android.apps.exposurenotification.keyupload.UploadController;
import com.google.android.apps.exposurenotification.keyupload.UploadUrisModule;
import com.google.android.apps.exposurenotification.nearby.ExposureNotificationClientWrapper;
import com.google.android.apps.exposurenotification.nearby.ExposureNotificationsClientModule;
import com.google.android.apps.exposurenotification.network.Connectivity;
import com.google.android.apps.exposurenotification.network.RealRequestQueueModule;
import com.google.android.apps.exposurenotification.network.RequestQueueWrapper;
import com.google.android.apps.exposurenotification.notify.ShareDiagnosisViewModel.EnterCodeStepReturnValue;
import com.google.android.apps.exposurenotification.notify.ShareDiagnosisViewModel.Step;
import com.google.android.apps.exposurenotification.storage.DbModule;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity.HasSymptoms;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity.Shared;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity.TestResult;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity.TravelStatus;
import com.google.android.apps.exposurenotification.storage.DiagnosisRepository;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationDatabase;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences.VaccinationStatus;
import com.google.android.apps.exposurenotification.testsupport.ExposureNotificationRules;
import com.google.android.apps.exposurenotification.testsupport.FakeClock;
import com.google.android.apps.exposurenotification.testsupport.FakeRequestQueue;
import com.google.android.apps.exposurenotification.testsupport.FakeShadowResources;
import com.google.android.apps.exposurenotification.testsupport.InMemoryDb;
import com.google.android.gms.nearby.exposurenotification.ReportType;
import com.google.android.gms.nearby.exposurenotification.TemporaryExposureKey;
import com.google.android.gms.nearby.exposurenotification.TemporaryExposureKey.TemporaryExposureKeyBuilder;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.libraries.privateanalytics.PrivateAnalyticsEnabledProvider;
import com.google.common.base.Optional;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.testing.TestingExecutors;
import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.testing.BindValue;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import dagger.hilt.android.testing.UninstallModules;
import dagger.hilt.components.SingletonComponent;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.threeten.bp.Instant;
import org.threeten.bp.LocalDate;

@HiltAndroidTest
@RunWith(RobolectricTestRunner.class)
@Config(application = HiltTestApplication.class, shadows = {FakeShadowResources.class})
@UninstallModules({DbModule.class, RealRequestQueueModule.class, UploadUrisModule.class,
    ExposureNotificationsClientModule.class, RealTimeModule.class})
public class ShareDiagnosisViewModelTest {

  private static final Uri UPLOAD_URI = Uri.parse("http://sampleurls.com/upload");
  private static final Uri CODE_URI = Uri.parse("http://sampleurls.com/code");
  private static final Uri CERT_URI = Uri.parse("http://sampleurls.com/cert");
  private static final LocalDate ONSET_DATE = LocalDate.parse("2020-04-01");

  @Rule
  public ExposureNotificationRules rules =
      ExposureNotificationRules.forTest(this).withMocks().build();

  private final Context context = ApplicationProvider.getApplicationContext();
  @Inject
  UploadController uploadController;
  @Inject
  DiagnosisRepository diagnosisRepository;
  @BindValue
  @Mock
  ExposureNotificationClientWrapper exposureNotificationClient;
  @BindValue
  ExposureNotificationDatabase database = InMemoryDb.create();
  @BindValue
  RequestQueueWrapper queue = new FakeRequestQueue();
  @BindValue
  @Mock
  Connectivity connectivity;
  @Inject
  ExposureNotificationSharedPreferences exposureNotificationSharedPreferences;
  @Mock
  PrivateAnalyticsEnabledProvider privateAnalyticsEnabledProvider;
  @BindValue
  Clock clock = new FakeClock();

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
      return CODE_URI;
    }

    @Provides
    @VerificationCertUri
    public Uri provideCertUri() {
      return CERT_URI;
    }
  }

  private ShareDiagnosisViewModel viewModel;

  @Before
  public void setup() {
    rules.hilt().inject();
    viewModel = new ShareDiagnosisViewModel(
        context,
        new SavedStateHandle(),
        uploadController,
        diagnosisRepository,
        exposureNotificationClient,
        exposureNotificationSharedPreferences,
        privateAnalyticsEnabledProvider,
        clock,
        connectivity,
        MoreExecutors.newDirectExecutorService(),
        MoreExecutors.newDirectExecutorService(),
        TestingExecutors.sameThreadScheduledExecutor());
    when(connectivity.hasInternet()).thenReturn(true);
  }

  @Test
  public void flow_startsOnStep_null() {
    AtomicReference<Step> observedStep = observeFlowStep();
    assertThat(observedStep.get()).isNull();
  }

  @Test
  public void savedStateHandle_withValuesSet_stateRestored_uiNotMarkedAsRestored() {
    // GIVEN
    SavedStateHandle savedStateHandle = new SavedStateHandle();
    savedStateHandle.set(SAVED_STATE_CODE_IS_INVALID, false);
    savedStateHandle.set(SAVED_STATE_CODE_IS_VERIFIED, true);
    savedStateHandle.set(SAVED_STATE_SHARE_ADVANCE_SWITCHER_CHILD, 0);
    savedStateHandle.set(SAVED_STATE_CODE_INPUT_IS_ENABLED, false);
    savedStateHandle.set(SAVED_STATE_CODE_INPUT, "code-input");
    savedStateHandle.set(SAVED_STATE_VERIFIED_CODE, "verified-code");

    // WHEN
    viewModel = new ShareDiagnosisViewModel(
        context,
        savedStateHandle,
        uploadController,
        diagnosisRepository,
        exposureNotificationClient,
        exposureNotificationSharedPreferences,
        privateAnalyticsEnabledProvider,
        clock,
        connectivity,
        MoreExecutors.newDirectExecutorService(),
        MoreExecutors.newDirectExecutorService(),
        TestingExecutors.sameThreadScheduledExecutor());
    when(connectivity.hasInternet()).thenReturn(true);

    // THEN
    assertThat(viewModel.isCodeInvalidForCodeStepLiveData().getValue()).isFalse();
    assertThat(viewModel.isCodeVerifiedForCodeStepLiveData().getValue()).isTrue();
    assertThat(viewModel.getSwitcherChildForCodeStepLiveData().getValue()).isEqualTo(0);
    assertThat(viewModel.isCodeInputEnabledForCodeStepLiveData().getValue()).isFalse();
    assertThat(viewModel.getCodeInputForCodeStepLiveData().getValue())
        .isEqualTo("code-input");
    assertThat(viewModel.getVerifiedCodeForCodeStepLiveData().getValue())
        .isEqualTo("verified-code");
    assertThat(viewModel.isUiToBeRestoredFromSavedState()).isFalse();
  }

  @Test
  public void savedStateHandle_setValues_markUiToBeRestored_stateUpdated_uiMarkedAsRestored() {
    // GIVEN
    AtomicBoolean isCodeInvalid = new AtomicBoolean();
    AtomicBoolean isCodeVerified = new AtomicBoolean();
    AtomicInteger displayedChild = new AtomicInteger();
    AtomicBoolean isCodeInputEnabledForCodeStep = new AtomicBoolean(true);
    AtomicReference<String> codeInput = new AtomicReference<>();
    AtomicReference<String> verifiedCode = new AtomicReference<>();
    viewModel.isCodeInvalidForCodeStepLiveData().observeForever(isCodeInvalid::set);
    viewModel.isCodeVerifiedForCodeStepLiveData().observeForever(isCodeVerified::set);
    viewModel.getSwitcherChildForCodeStepLiveData().observeForever(displayedChild::set);
    viewModel.getCodeInputForCodeStepLiveData().observeForever(codeInput::set);
    viewModel.getVerifiedCodeForCodeStepLiveData().observeForever(verifiedCode::set);
    viewModel.isCodeInputEnabledForCodeStepLiveData()
        .observeForever(isCodeInputEnabledForCodeStep::set);

    // WHEN
    viewModel.setCodeIsInvalidForCodeStep(true);
    viewModel.setCodeIsVerifiedForCodeStep(true);
    viewModel.setSwitcherChildForCodeStep(1);
    viewModel.setCodeInputForCodeStep("code-input");
    viewModel.setVerifiedCodeForCodeStep("verified-code");
    viewModel.setCodeInputEnabledForCodeStep(false);
    viewModel.markUiToBeRestoredFromSavedState(true);

    // THEN
    assertThat(isCodeInvalid.get()).isTrue();
    assertThat(isCodeVerified.get()).isTrue();
    assertThat(displayedChild.get()).isEqualTo(1);
    assertThat(isCodeInputEnabledForCodeStep.get()).isFalse();
    assertThat(codeInput.get()).isEqualTo("code-input");
    assertThat(verifiedCode.get()).isEqualTo("verified-code");
    assertThat(viewModel.isUiToBeRestoredFromSavedState()).isTrue();
  }

  @Test
  public void submitCode_shouldSaveCodeInCurrentDiagnosisAndAdvanceStep() throws Exception {
    // Later we'll need the diagnosis ID to read it from the DB, so observe and capture it.
    AtomicLong observedDiagnosisId = observeDiagnosisId();
    AtomicReference<Step> observedStep = observeFlowStep();
    queue().addResponse(CODE_URI.toString(), 200, codeResponse(/* onsetDate= */ null));

    viewModel.submitCode("code", false).get();

    assertThat(diagnosisRepository.getByIdAsync(observedDiagnosisId.get()).get()
        .getVerificationCode())
        .isEqualTo("code");
    assertThat(observedStep.get()).isEqualTo(Step.ONSET);
  }

  @Test
  public void submitCode_shouldSetIsCodeFromLink() throws Exception {
    // Later we'll need the diagnosis ID to read it from the DB, so observe and capture it.
    AtomicLong observedDiagnosisId = observeDiagnosisId();
    queue().addResponse(CODE_URI.toString(), 200, codeResponse(/* onsetDate= */ null));

    viewModel.submitCode("code", true).get();

    assertThat(diagnosisRepository.getByIdAsync(observedDiagnosisId.get()).get()
        .getIsCodeFromLink())
        .isTrue();
  }

  @Test
  public void submitCode_shouldStoreDiagnosisAndSetNotAttemptedSharedStatus() throws Exception {
    // GIVEN
    // Later we'll need the diagnosis ID to read it from the DB, so observe and capture it.
    AtomicLong observedDiagnosisId = observeDiagnosisId();
    queue().addResponse(CODE_URI.toString(), 200, codeResponse(/* onsetDate= */ null));

    // WHEN
    viewModel.submitCode("code", false).get();
    DiagnosisEntity lastNonSharedDiagnosis = diagnosisRepository
        .maybeGetLastNotSharedDiagnosisAsync().get();

    // THEN
    long testDiagnosisId = observeDiagnosisId().get();
    assertThat(lastNonSharedDiagnosis).isNotNull();
    assertThat(testDiagnosisId).isEqualTo(lastNonSharedDiagnosis.getId());
    assertThat(diagnosisRepository.getByIdAsync(testDiagnosisId).get().getSharedStatus())
        .isEqualTo(Shared.NOT_ATTEMPTED);
  }

  @Test
  public void submitCode_shouldSkipCodeStep() throws Exception {
    AtomicBoolean observedRevealCodeStepEvent = observeRevealCodeStepEvent();
    queue().addResponse(CODE_URI.toString(), 200, codeResponse(/* onsetDate= */ null));

    viewModel.nextStep(Step.CODE);
    viewModel.submitCode("code", true).get();

    assertThat(observedRevealCodeStepEvent.get()).isFalse();
    assertThat(observeFlowStep().get()).isEqualTo(Step.ONSET);
  }

  @Test
  public void submitCode_shouldNotSkipCodeStepIfVerificationFailed() throws Exception {
    AtomicBoolean observedRevealCodeStepEvent = observeRevealCodeStepEvent();
    queue().addResponse(CODE_URI.toString(), 400, codeResponse(/* onsetDate= */ null));

    viewModel.nextStep(Step.CODE);
    viewModel.submitCode("code", true).get();

    assertThat(observedRevealCodeStepEvent.get()).isTrue();
    assertThat(observeFlowStep().get()).isEqualTo(Step.CODE);
  }

  @Test
  public void submitCode_withOnsetDate_shouldSaveOnsetDateInCurrentDiagnosis() throws Exception {
    // Later we'll need the diagnosis ID to read it from the DB, so observe and capture it.
    AtomicLong observedDiagnosisId = observeDiagnosisId();
    queue().addResponse(CODE_URI.toString(), 200, codeResponse(ONSET_DATE));

    viewModel.submitCode("code", false).get();

    assertThat(diagnosisRepository.getByIdAsync(observedDiagnosisId.get()).get()
        .getOnsetDate())
        .isEqualTo(ONSET_DATE);
  }

  @Test
  public void submitCode_codeExists_shouldResumeCurrent() throws Exception {
    String existingCode = "existingCode";
    long existingId = diagnosisRepository
        .upsertAsync(DiagnosisEntity.newBuilder().setVerificationCode(existingCode).build()).get();
    AtomicLong observedDiagnosisId = observeDiagnosisId();

    viewModel.submitCode(existingCode, false).get();

    assertThat(observedDiagnosisId.get()).isEqualTo(existingId);
  }

  @Test
  public void submitCode_noConnectivity_shouldCancel() throws Exception {
    when(connectivity.hasInternet()).thenReturn(false);

    String code = "code";
    viewModel.submitCode(code, false).get();
    assertThat(queue().numRpcs()).isEqualTo(0);
  }

  @Test
  public void submitCode_resumeFlowAutomatically_shouldReset() throws Exception {
    // GIVEN
    queue().addResponse(CODE_URI.toString(), 200, codeResponse(/* onsetDate= */ null));
    viewModel.setResumingAndNotConfirmed(true);

    // WHEN
    viewModel.submitCode("code", true).get();

    // THEN
    assertThat(viewModel.isResumingAndNotConfirmed()).isFalse();
  }

  @Test
  public void setNoSymptoms_shouldBeSaved_andOnsetDateShouldBeUnset() throws Exception {
    // GIVEN
    // Start with a diagnosis that has its verification code (a precondition to onset date entry).
    AtomicLong observedDiagnosisId = observeDiagnosisId();
    queue().addResponse(CODE_URI.toString(), 200, codeResponse(/* onsetDate= */null));
    viewModel.submitCode("code", false).get();

    // WHEN
    viewModel.setHasSymptoms(HasSymptoms.NO);

    // THEN
    DiagnosisEntity diagnosis = diagnosisRepository.getByIdAsync(observedDiagnosisId.get()).get();
    assertThat(diagnosis.getHasSymptoms()).isEqualTo(HasSymptoms.NO);
    assertThat(diagnosis.getOnsetDate()).isNull();
  }

  @Test
  public void declineToAnswerSymptoms_shouldBeSaved_andOnsetDateShouldBeUnset() throws Exception {
    // GIVEN
    // Start with a diagnosis that has its verification code (a precondition to onset date entry).
    AtomicLong observedDiagnosisId = observeDiagnosisId();
    queue().addResponse(CODE_URI.toString(), 200, codeResponse(/* onsetDate= */null));
    viewModel.submitCode("code", false).get();

    // WHEN
    viewModel.setHasSymptoms(HasSymptoms.WITHHELD);

    // THEN
    DiagnosisEntity diagnosis = diagnosisRepository.getByIdAsync(observedDiagnosisId.get()).get();
    assertThat(diagnosis.getHasSymptoms()).isEqualTo(HasSymptoms.WITHHELD);
    assertThat(diagnosis.getOnsetDate()).isNull();
  }

  @Test
  public void setSymptomOnsetDate_shouldSaveDate_andHasSymptoms() throws Exception {
    // GIVEN
    // Start with a diagnosis that has its verification code (a precondition to onset date entry).
    AtomicLong observedDiagnosisId = observeDiagnosisId();
    queue().addResponse(CODE_URI.toString(), 200, codeResponse(/* onsetDate= */null));
    viewModel.submitCode("code", false).get();

    // WHEN
    LocalDate onset = LocalDate.of(2020, 4, 1);
    viewModel.setSymptomOnsetDate(onset);

    // THEN
    DiagnosisEntity diagnosis = diagnosisRepository.getByIdAsync(observedDiagnosisId.get()).get();
    assertThat(diagnosis.getOnsetDate()).isEqualTo(onset);
    assertThat(diagnosis.getHasSymptoms()).isEqualTo(HasSymptoms.YES);
  }

  @Test
  public void enterCodeStep_shouldNotRevealStepIfCodeIsFromLink() {
    Optional<String> codeFromDeepLink = IntentUtil
        .maybeGetCodeFromDeepLinkUri(Uri.parse("ens://v?c=123"));
    EnterCodeStepReturnValue returnValue = viewModel.enterCodeStep(codeFromDeepLink.get());

    assertThat(returnValue.revealPage()).isFalse();
    assertThat(returnValue.verificationCodeToPrefill()).hasValue("123");
  }

  @Test
  public void enterCodeStep_shouldRevealStepIfCodeIsNotFromLink() {
    EnterCodeStepReturnValue returnValue = viewModel.enterCodeStep(null);

    assertThat(returnValue.revealPage()).isTrue();
    assertThat(returnValue.verificationCodeToPrefill()).isAbsent();
  }

  @Test
  public void enterCodeStep_shouldRevealStepIfCodeIsNull() {
    EnterCodeStepReturnValue returnValue = viewModel.enterCodeStep(null);

    assertThat(returnValue.revealPage()).isTrue();
    assertThat(returnValue.verificationCodeToPrefill()).isAbsent();
  }

  @Test
  public void nextStep_afterOnsetStepAndTravelQuestionEnabled_shouldBeTravelStatusStep()
      throws Exception {
    // GIVEN
    // Enable travel step by providing a non-empty value for the share_travel_detail string,
    // which is an optional health authority config field
    FakeShadowResources resources = (FakeShadowResources) shadowOf(context.getResources());
    resources.addFakeResource(R.string.share_travel_detail, "Sample travel question");
    // Imitate the submission of code with symptom onset date
    viewModel.nextStep(Step.CODE);
    viewModel.submitCode("code", false).get();
    viewModel.nextStep(Step.ONSET);
    viewModel.setSymptomOnsetDate(LocalDate.parse("2020-04-01"));

    // WHEN
    Step nextStep = observeNextStep(Step.ONSET).get();
    viewModel.nextStep(nextStep);

    // THEN
    // As Travel step is enabled, the next step after an Onset step should be a Travel Status step.
    assertThat(observeFlowStep().get()).isEqualTo(Step.TRAVEL_STATUS);
  }

  @Test
  public void nextStep_afterOnsetStepAndTravelQuestionDisabled_shouldBeReviewStep()
      throws Exception {
    // GIVEN
    // Disable travel step by providing an empty value for the share_travel_detail string,
    // which is an optional health authority config field
    FakeShadowResources resources = (FakeShadowResources) shadowOf(context.getResources());
    resources.addFakeResource(R.string.share_travel_detail, "");
    // Imitate the submission of code with symptom onset date
    viewModel.nextStep(Step.CODE);
    viewModel.submitCode("code", false).get();
    viewModel.nextStep(Step.ONSET);
    viewModel.setSymptomOnsetDate(LocalDate.parse("2020-04-01"));

    // WHEN
    Step nextStep = observeNextStep(Step.ONSET).get();
    viewModel.nextStep(nextStep);

    // THEN
    // As Travel step is disabled, the next step after an Onset step should be a Review step.
    assertThat(observeFlowStep().get()).isEqualTo(Step.REVIEW);
  }

  @Test
  public void previousStep_beforeReviewStepAndTravelQuestionEnabled_shouldBeTravelStatusStep()
      throws Exception {
    // GIVEN
    // Enable travel step by providing a non-empty value for the share_travel_detail string,
    // which is an optional health authority config field
    FakeShadowResources resources = (FakeShadowResources) shadowOf(context.getResources());
    resources.addFakeResource(R.string.share_travel_detail, "Sample travel question");
    // Imitate the submission of code with symptom onset date
    AtomicLong observedDiagnosisId = observeDiagnosisId();
    viewModel.nextStep(Step.CODE);
    viewModel.submitCode("code", false).get();
    viewModel.nextStep(Step.ONSET);
    viewModel.setSymptomOnsetDate(LocalDate.parse("2020-04-01"));
    viewModel.nextStep(Step.REVIEW);

    // WHEN
    Step previousStep = observePreviousStep(Step.REVIEW).get();
    viewModel.previousStep(previousStep);

    // THEN
    // As Travel step is enabled, the previous step before a Review step should be a Travel Status
    // step.
    assertThat(observeFlowStep().get()).isEqualTo(Step.TRAVEL_STATUS);
  }

  @Test
  public void previousStep_beforeReviewStepAndTravelQuestionDisabled_shouldBeOnsetStep()
      throws Exception {
    // GIVEN
    // Disable travel step by providing an empty value for the share_travel_detail string,
    // which is an optional health authority config field
    FakeShadowResources resources = (FakeShadowResources) shadowOf(context.getResources());
    resources.addFakeResource(R.string.share_travel_detail, "");
    // Imitate the submission of code with symptom onset date
    AtomicLong observedDiagnosisId = observeDiagnosisId();
    viewModel.nextStep(Step.CODE);
    viewModel.submitCode("code", false).get();
    viewModel.nextStep(Step.ONSET);
    viewModel.setSymptomOnsetDate(LocalDate.parse("2021-04-01"));
    viewModel.nextStep(Step.REVIEW);

    // WHEN
    Step previousStep = observePreviousStep(Step.REVIEW).get();
    viewModel.previousStep(previousStep);

    // THEN
    // As Travel step is disabled, the previous step before a Review step should be an Onset step.
    assertThat(observeFlowStep().get()).isEqualTo(Step.ONSET);
  }

  // TODO: Lots more narrow tests of specific steps in the flow and specific expectations.

  /**
   * Different from the other tests in this file, this tests one long end to end flow along the
   * "happy path".
   */
  @Test
  public void executeAllSteps_withValidInputs_shouldSaveRelevantData()
      throws Exception {
    // GIVEN
    // We'll also need the diagnosis ID to read it from the DB, so observe and capture it.
    AtomicLong observedDiagnosisId = observeDiagnosisId();
    // We'll need successful responses set up in the fake servers for all three RPCs
    queue().addResponse(
        CODE_URI.toString(),
        200,
        codeResponse("token", /* testType= */ "confirmed", /* onsetDate= */ null));
    queue().addResponse(CERT_URI.toString(), 200, certResponse("cert-data"));
    queue().addResponse(
        UPLOAD_URI.toString(), 200, uploadResponse("revision-token", /* numExposures= */ 3));
    // Also we'll need some keys from GMSCore.
    Task<List<TemporaryExposureKey>> keys = Tasks.forResult(ImmutableList.of(key("key1")));
    when(exposureNotificationClient.getTemporaryExposureKeyHistory()).thenReturn(keys);

    // WHEN
    // Starts with the verification code step
    viewModel.submitCode("code", false).get();
    // Next is the onset date step
    viewModel.setSymptomOnsetDate(LocalDate.parse("2020-04-01"));
    // Next is the travel status step
    viewModel.setTravelStatus(TravelStatus.TRAVELED);
    // Then the review step, and we're done.
    viewModel.uploadKeys().get();

    // THEN
    // And we should have stored numerous artifacts of the successful steps:
    DiagnosisEntity diagnosis = diagnosisRepository.getByIdAsync(observedDiagnosisId.get()).get();
    assertThat(diagnosis).isNotNull();
    assertThat(diagnosis.getVerificationCode()).isEqualTo("code");
    assertThat(diagnosis.getLongTermToken()).isEqualTo("token");
    assertThat(diagnosis.getOnsetDate()).isEqualTo(LocalDate.parse("2020-04-01"));
    assertThat(diagnosis.getCertificate()).isEqualTo("cert-data");
    assertThat(diagnosis.getRevisionToken()).isEqualTo("revision-token");
    assertThat(diagnosis.getTestResult()).isEqualTo(TestResult.CONFIRMED);
    assertThat(diagnosis.getSharedStatus()).isEqualTo(Shared.SHARED);
    assertThat(diagnosis.getTravelStatus()).isEqualTo(TravelStatus.TRAVELED);
  }

  @Test
  public void setLastVaccinationResponse_setState_setsStateAndTimestamp() {
    Instant timestamp = Instant.now();
    ((FakeClock) clock).set(timestamp);
    exposureNotificationSharedPreferences.setPrivateAnalyticsState(true);

    viewModel.setLastVaccinationResponse(VaccinationStatus.VACCINATED);

    assertThat(exposureNotificationSharedPreferences.getLastVaccinationStatus())
        .isEqualTo(VaccinationStatus.VACCINATED);
    assertThat(exposureNotificationSharedPreferences.getLastVaccinationStatusResponseTime())
        .isEqualTo(timestamp);
  }

  @Test
  public void showVaccinationQuestion_enpaDisabled_returnsFalse() {
    when(privateAnalyticsEnabledProvider.isEnabledForUser()).thenReturn(false);
    when(privateAnalyticsEnabledProvider.isSupportedByApp()).thenReturn(true);
    FakeShadowResources resources = (FakeShadowResources) shadowOf(context.getResources());
    resources.addFakeResource(R.string.share_vaccination_detail, "non-empty");

    boolean result = viewModel.showVaccinationQuestion(context.getResources());

    assertThat(result).isFalse();
  }

  @Test
  public void showVaccinationQuestion_enpaUnsupported_returnsFalse() {
    when(privateAnalyticsEnabledProvider.isEnabledForUser()).thenReturn(true);
    when(privateAnalyticsEnabledProvider.isSupportedByApp()).thenReturn(false);
    FakeShadowResources resources = (FakeShadowResources) shadowOf(context.getResources());
    resources.addFakeResource(R.string.share_vaccination_detail, "non-empty");

    boolean result = viewModel.showVaccinationQuestion(context.getResources());

    assertThat(result).isFalse();
  }

  @Test
  public void showVaccinationQuestion_vaccinationQuestionDisabled_returnsFalse() {
    when(privateAnalyticsEnabledProvider.isEnabledForUser()).thenReturn(true);
    when(privateAnalyticsEnabledProvider.isSupportedByApp()).thenReturn(true);
    FakeShadowResources resources = (FakeShadowResources) shadowOf(context.getResources());
    resources.addFakeResource(R.string.share_vaccination_detail, "");

    boolean result = viewModel.showVaccinationQuestion(context.getResources());

    assertThat(result).isFalse();
  }

  @Test
  public void showVaccinationQuestion_enpaSupportedEnabledVaccinationEnabled_returnsTrue() {
    when(privateAnalyticsEnabledProvider.isEnabledForUser()).thenReturn(true);
    when(privateAnalyticsEnabledProvider.isSupportedByApp()).thenReturn(true);
    FakeShadowResources resources = (FakeShadowResources) shadowOf(context.getResources());
    resources.addFakeResource(R.string.share_vaccination_detail, "non-empty");

    boolean result = viewModel.showVaccinationQuestion(context.getResources());

    assertThat(result).isTrue();
  }

  private static String codeResponse(@Nullable LocalDate onsetDate) throws Exception {
    return codeResponse("token", "confirmed", onsetDate);
  }

  private static String codeResponse(String token, String testType, @Nullable LocalDate onsetDate)
      throws Exception {
    JSONObject response = new JSONObject()
        .put(VerifyV1.VERIFICATION_TOKEN, token)
        .put(VerifyV1.TEST_TYPE, testType);
    if (onsetDate != null) {
      response.put(VerifyV1.ONSET_DATE, onsetDate.toString());
    }
    return response.toString();
  }

  private static String certResponse(String cert) throws Exception {
    return new JSONObject().put(VerifyV1.CERT, cert).toString();
  }

  private static String uploadResponse(String revisionToken, int numExposures) throws Exception {
    return new JSONObject()
        .put(UploadV1.REVISION_TOKEN, revisionToken)
        .put(UploadV1.NUM_INSERTED_EXPOSURES, numExposures)
        .put(UploadV1.PADDING, "padding")
        .toString();
  }

  private static TemporaryExposureKey key(String keyData) {
    return new TemporaryExposureKeyBuilder()
        .setKeyData(keyData.getBytes())
        .setReportType(ReportType.CONFIRMED_TEST)
        .setRollingPeriod(144)
        .setRollingStartIntervalNumber(1)
        .setTransmissionRiskLevel(2)
        .setTransmissionRiskLevel(3)
        .build();
  }

  private AtomicLong observeDiagnosisId() {
    AtomicLong observedDiagnosisId = new AtomicLong();
    viewModel.getCurrentDiagnosisId().observeForever(observedDiagnosisId::set);
    return observedDiagnosisId;
  }

  private AtomicReference<Step> observeFlowStep() {
    AtomicReference<Step> observedStep = new AtomicReference<>();
    viewModel.getCurrentStepLiveData().observeForever(observedStep::set);
    return observedStep;
  }

  private AtomicBoolean observeRevealCodeStepEvent() {
    AtomicBoolean observedRevealCodeStepEvent = new AtomicBoolean();
    viewModel.getRevealCodeStepEvent().observeForever(observedRevealCodeStepEvent::set);
    return observedRevealCodeStepEvent;
  }

  private AtomicReference<Step> observeNextStep(Step currentStep) {
    AtomicReference<Step> observedNextStep = new AtomicReference<>();
    viewModel.getNextStepLiveData(currentStep).observeForever(observedNextStep::set);
    return observedNextStep;
  }

  private AtomicReference<Step> observePreviousStep(Step previousStep) {
    AtomicReference<Step> observedPreviousStep = new AtomicReference<>();
    viewModel.getPreviousStepLiveData(previousStep).observeForever(observedPreviousStep::set);
    return observedPreviousStep;
  }

  private FakeRequestQueue queue() {
    return (FakeRequestQueue) queue;
  }
}