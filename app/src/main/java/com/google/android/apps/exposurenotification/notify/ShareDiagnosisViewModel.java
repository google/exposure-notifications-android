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

import android.app.PendingIntent;
import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Pair;
import android.view.View;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.appupdate.EnxAppUpdateManager;
import com.google.android.apps.exposurenotification.appupdate.EnxAppUpdateManager.AppUpdateFlowFailedToLaunchException;
import com.google.android.apps.exposurenotification.common.BuildUtils;
import com.google.android.apps.exposurenotification.common.BuildUtils.Type;
import com.google.android.apps.exposurenotification.common.Qualifiers.BackgroundExecutor;
import com.google.android.apps.exposurenotification.common.Qualifiers.LightweightExecutor;
import com.google.android.apps.exposurenotification.common.Qualifiers.ScheduledExecutor;
import com.google.android.apps.exposurenotification.common.SecureRandomUtil;
import com.google.android.apps.exposurenotification.common.SingleLiveEvent;
import com.google.android.apps.exposurenotification.common.TaskToFutureAdapter;
import com.google.android.apps.exposurenotification.common.TelephonyHelper;
import com.google.android.apps.exposurenotification.common.logging.Logger;
import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.android.apps.exposurenotification.home.ExposureNotificationViewModel.ExposureNotificationState;
import com.google.android.apps.exposurenotification.keyupload.Upload;
import com.google.android.apps.exposurenotification.keyupload.UploadController;
import com.google.android.apps.exposurenotification.keyupload.UploadController.NoInternetException;
import com.google.android.apps.exposurenotification.keyupload.UploadController.UploadException;
import com.google.android.apps.exposurenotification.keyupload.UploadError;
import com.google.android.apps.exposurenotification.keyupload.UserReportUpload;
import com.google.android.apps.exposurenotification.nearby.ExposureNotificationClientWrapper;
import com.google.android.apps.exposurenotification.network.Connectivity;
import com.google.android.apps.exposurenotification.network.DiagnosisKey;
import com.google.android.apps.exposurenotification.notify.ShareDiagnosisFlowHelper.ShareDiagnosisFlow;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity.HasSymptoms;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity.Shared;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity.TestResult;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity.TravelStatus;
import com.google.android.apps.exposurenotification.storage.DiagnosisRepository;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences.VaccinationStatus;
import com.google.android.apps.exposurenotification.storage.VerificationCodeRequestEntity;
import com.google.android.apps.exposurenotification.storage.VerificationCodeRequestRepository;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.nearby.exposurenotification.ExposureNotificationStatusCodes;
import com.google.android.gms.nearby.exposurenotification.TemporaryExposureKey;
import com.google.android.libraries.privateanalytics.PrivateAnalyticsEnabledProvider;
import com.google.auto.value.AutoValue;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.hilt.android.lifecycle.HiltViewModel;
import dagger.hilt.android.qualifiers.ApplicationContext;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import org.threeten.bp.Duration;
import org.threeten.bp.Instant;
import org.threeten.bp.LocalDate;

/**
 * View model for the Share Diagnosis flow.
 *
 * <p>Implements all aspects of sharing diagnosis and exposure keys with the keyserver, including:
 *
 * <ul>
 *   <li>Exchanging a Verification Code with the Verification Server to obtain a long(er) term token
 *   <li>Using that long term token to get the Verification Server to certify that our exposure keys
 *       are associated with a genuine diagnosis.
 *   <li>Transmitting this certification along with the exposure keys to the keyserver.
 *   <li>Storing the results of all these RPCs locally, so that the process may be resumed if
 *       abandoned by the user or failed.
 * </ul>
 */
@HiltViewModel
public class ShareDiagnosisViewModel extends ViewModel {

  private static final Logger logger = Logger.getLogger("ShareDiagnosisViewModel");

  // Current limits on a number of verification code requests per a certain period.
  private static final VerificationCodeRequestLimit DAYS_REQUEST_LIMIT =
      VerificationCodeRequestLimit.newBuilder()
          .setPeriodDuration(Duration.ofDays(30))
          .setNumOfRequests(3)
          .build();
  private static final VerificationCodeRequestLimit MINS_REQUEST_LIMIT =
      VerificationCodeRequestLimit.newBuilder()
          .setPeriodDuration(Duration.ofMinutes(30))
          .setNumOfRequests(1)
          .build();
  // Number of days in which a user can't self-report after self-reporting or reporting a confirmed
  // positive COVID-19 test result. Used to mitigate an abuse of a Self-report functionality.
  private static final int SELF_REPORT_DISABLED_NUM_OF_DAYS = 90;

  private static final Duration REQUEST_PRE_AUTH_TEKS_HISTORY_API_TIMEOUT = Duration.ofSeconds(5);

  // Keys for storing UI state data in the SavedStateHandle.
  @VisibleForTesting static final String SAVED_STATE_CODE_IS_INVALID =
      "ShareDiagnosisViewModel.SAVED_STATE_CODE_IS_INVALID";
  @VisibleForTesting static final String SAVED_STATE_CODE_IS_VERIFIED =
      "ShareDiagnosisViewModel.SAVED_STATE_CODE_IS_VERIFIED";
  @VisibleForTesting static final String SAVED_STATE_SHARE_ADVANCE_SWITCHER_CHILD =
      "ShareDiagnosisViewModel.SAVED_STATE_SHARE_ADVANCE_SWITCHER_CHILD";
  @VisibleForTesting static final String SAVED_STATE_VERIFIED_CODE =
      "ShareDiagnosisViewModel.SAVED_STATE_VERIFIED_CODE";
  @VisibleForTesting static final String SAVED_STATE_CODE_INPUT =
      "ShareDiagnosisViewModel.SAVED_STATE_CODE_INPUT";
  @VisibleForTesting static final String SAVED_STATE_CODE_INPUT_IS_ENABLED =
      "ShareDiagnosisViewModel.SAVED_STATE_CODE_INPUT_IS_ENABLED";
  @VisibleForTesting static final String SAVED_STATE_GET_CODE_PHONE_NUMBER =
      "ShareDiagnosisViewModel.SAVED_STATE_GET_CODE_PHONE_NUMBER";
  @VisibleForTesting static final String SAVED_STATE_GET_CODE_TEST_DATE =
      "ShareDiagnosisViewModel.SAVED_STATE_GET_CODE_TEST_DATE";
  static final Set<String> SAVED_STATE_GET_CODE_KEYS =
      ImmutableSet.of(SAVED_STATE_GET_CODE_TEST_DATE, SAVED_STATE_GET_CODE_PHONE_NUMBER);

  public static final Set<ExposureNotificationState> EN_STATES_BLOCKING_SHARING_FLOW =
      ImmutableSet.of(ExposureNotificationState.DISABLED,
          ExposureNotificationState.FOCUS_LOST,
          ExposureNotificationState.STORAGE_LOW,
          ExposureNotificationState.PAUSED_USER_PROFILE_NOT_SUPPORT,
          ExposureNotificationState.PAUSED_NOT_IN_ALLOWLIST,
          ExposureNotificationState.PAUSED_HW_NOT_SUPPORT,
          ExposureNotificationState.PAUSED_EN_NOT_SUPPORT);

  public static final DiagnosisEntity EMPTY_DIAGNOSIS = DiagnosisEntity.newBuilder().build();
  public static final long NO_EXISTING_ID = -1;
  private static final Duration GET_TEKS_TIMEOUT = Duration.ofSeconds(10);

  private final DiagnosisRepository diagnosisRepository;
  private final VerificationCodeRequestRepository requestRepository;
  private final UploadController uploadController;
  private final ExposureNotificationClientWrapper exposureNotificationClientWrapper;
  private final Resources resources;
  private final ExposureNotificationSharedPreferences exposureNotificationSharedPreferences;
  private final PrivateAnalyticsEnabledProvider privateAnalyticsEnabledProvider;
  private final Clock clock;
  private final TelephonyHelper telephonyHelper;
  private final SecureRandom secureRandom;
  private final Connectivity connectivity;

  private final MutableLiveData<Long> currentDiagnosisId = new MutableLiveData<>(NO_EXISTING_ID);

  private final MutableLiveData<Boolean> inFlightLiveData = new MutableLiveData<>(false);

  private final SingleLiveEvent<Void> deletedLiveEvent = new SingleLiveEvent<>();
  private final SingleLiveEvent<Void> appUpdateAvailableLiveEvent =
      new SingleLiveEvent<>();
  private final SingleLiveEvent<String> snackbarLiveEvent = new SingleLiveEvent<>();
  private final SingleLiveEvent<ApiException> teksReleaseResolutionRequiredLiveEvent =
      new SingleLiveEvent<>();
  private final SingleLiveEvent<ApiException> teksPreReleaseResolutionRequiredLiveEvent =
      new SingleLiveEvent<>();
  private final SingleLiveEvent<Boolean> sharedLiveEvent = new SingleLiveEvent<>();
  private final SingleLiveEvent<Boolean> revealCodeStepEvent = new SingleLiveEvent<>();
  private final SingleLiveEvent<Boolean> preAuthFlowCompletedLiveEvent = new SingleLiveEvent<>();

  private final MutableLiveData<Optional<VaccinationStatus>> vaccinationStatusForUILiveData =
      new MutableLiveData<>(Optional.absent());

  private final ExecutorService backgroundExecutor;
  private final ExecutorService lightweightExecutor;
  private final ScheduledExecutorService scheduledExecutor;

  // We use this immutable empty diagnosis livedata until we have a "real" one saved in storage,
  // just to make it nicer for observers; they don't have to check for null.
  private final LiveData<DiagnosisEntity> emptyDiagnosisLiveData =
      new MutableLiveData<>(EMPTY_DIAGNOSIS);

  private final MutableLiveData<Step> currentStepLiveData = new MutableLiveData<>();
  private final Stack<Step> backStack = new Stack<>();

  private final MutableLiveData<String> verificationErrorLiveData = new MutableLiveData<>();
  private final MutableLiveData<String> requestCodeErrorLiveData = new MutableLiveData<>();
  private final MutableLiveData<String> phoneNumberErrorMessageLiveData = new MutableLiveData<>();
  private final MutableLiveData<String> appUpdateFlowErrorLiveData = new MutableLiveData<>();
  private final EnxAppUpdateManager enxAppUpdateManager;
  private final Context context;

  private SavedStateHandle savedStateHandle;
  private ShareDiagnosisFlow shareDiagnosisFlow = ShareDiagnosisFlow.DEFAULT;
  private boolean isDeleteOpen = false;
  private boolean isCloseOpen = false;
  private boolean isCodeFromLinkUsed = false;
  private boolean resumingAndNotConfirmed = false;
  private boolean codeUiRestoredFromSavedState;

  /**
   * Via this enum, this viewmodel expresses to observers (fragments) what step in the flow the
   * current diagnosis is on.
   */
  enum Step {
    BEGIN, IS_CODE_NEEDED, GET_CODE, CODE, UPLOAD, SHARED, NOT_SHARED, VIEW, ALREADY_REPORTED,
    PRE_AUTH, VACCINATION
  }

  @Inject
  public ShareDiagnosisViewModel(
      @ApplicationContext Context context,
      SavedStateHandle savedStateHandle,
      UploadController uploadController,
      DiagnosisRepository diagnosisRepository,
      VerificationCodeRequestRepository requestRepository,
      ExposureNotificationClientWrapper exposureNotificationClientWrapper,
      ExposureNotificationSharedPreferences exposureNotificationSharedPreferences,
      PrivateAnalyticsEnabledProvider privateAnalyticsEnabledProvider,
      Clock clock,
      TelephonyHelper telephonyHelper,
      SecureRandom secureRandom,
      Connectivity connectivity,
      EnxAppUpdateManager enxAppUpdateManager,
      @BackgroundExecutor ExecutorService backgroundExecutor,
      @LightweightExecutor ExecutorService lightweightExecutor,
      @ScheduledExecutor ScheduledExecutorService scheduledExecutor) {
    this.context = context;
    this.savedStateHandle = savedStateHandle;
    this.uploadController = uploadController;
    this.diagnosisRepository = diagnosisRepository;
    this.requestRepository = requestRepository;
    this.exposureNotificationClientWrapper = exposureNotificationClientWrapper;
    this.exposureNotificationSharedPreferences = exposureNotificationSharedPreferences;
    this.privateAnalyticsEnabledProvider = privateAnalyticsEnabledProvider;
    this.clock = clock;
    this.telephonyHelper = telephonyHelper;
    this.secureRandom = secureRandom;
    this.connectivity = connectivity;
    this.enxAppUpdateManager = enxAppUpdateManager;
    this.backgroundExecutor = backgroundExecutor;
    this.lightweightExecutor = lightweightExecutor;
    this.scheduledExecutor = scheduledExecutor;
    this.resources = context.getResources();
  }

  public static Set<String> getStepNames() {
    HashSet<String> stepNames = new HashSet<>();
    for (Step step : Step.values()) {
      stepNames.add(step.name());
    }
    return stepNames;
  }

  public boolean deviceHasInternet() {
    return connectivity.hasInternet();
  }

  /**
   * A helper method to launch the resolution for a particular {@link ApiException}.
   *
   * @param apiException API Exception which might need a resolution.
   * @param resolutionActivityResultLauncher A launcher of the actual resolution.
   */
  public void startResolution(ApiException apiException,
      ActivityResultLauncher<IntentSenderRequest> resolutionActivityResultLauncher) {
    PendingIntent resolution = apiException.getStatus().getResolution();
    if (resolution != null) {
      IntentSenderRequest intentSenderRequest =
          new IntentSenderRequest.Builder(resolution).build();
      resolutionActivityResultLauncher.launch(intentSenderRequest);
    }
  }

  public void setCurrentDiagnosisId(long id) {
    currentDiagnosisId.setValue(id);
  }

  public void postCurrentDiagnosisId(long id) {
    currentDiagnosisId.postValue(id);
  }

  public LiveData<Long> getCurrentDiagnosisId() {
    return currentDiagnosisId;
  }

  @NonNull
  public LiveData<DiagnosisEntity> getCurrentDiagnosisLiveData() {
    return Transformations.distinctUntilChanged(
        Transformations.switchMap(currentDiagnosisId, id -> {
          if (id > 0) {
            return diagnosisRepository.getByIdLiveData(id);
          }
          return emptyDiagnosisLiveData;
        }));
  }

  /**
   * Tells what step of the flow we're on based on the state of the current diagnosis.
   */
  public LiveData<Step> getCurrentStepLiveData() {
    return Transformations.distinctUntilChanged(currentStepLiveData);
  }

  /**
   * Gets both the current step and the total number of steps of the shareDiagnosisFlow we're in
   * based on the state of the current diagnosis.
   * Returns a {@link Pair} (currentStep, total Number of step)
   */
  public LiveData<Pair<Integer, Integer>> getStepXofYLiveData() {
    return Transformations.map(currentStepLiveData, currentStep -> {
      int total = ShareDiagnosisFlowHelper.getTotalNumberOfStepsInDiagnosisFlow(
          getShareDiagnosisFlow());
      int currentStepNumber = ShareDiagnosisFlowHelper.getNumberForCurrentStepInDiagnosisFlow(
          getShareDiagnosisFlow(), currentStep);
      return Pair.create(currentStepNumber, total);
    });
  }

  /**
   * Tells what is the next step of the flow depending on the current step and current diagnosis.
   *
   * @param currentStep the step we want to calculate the next step for
   * @return the next step value
   */
  public LiveData<Step> getNextStepLiveData(Step currentStep) {
    return Transformations.map(
        getCurrentDiagnosisLiveData(), diagnosis ->
            ShareDiagnosisFlowHelper.getNextStep(currentStep, diagnosis, getShareDiagnosisFlow(),
                showVaccinationQuestion(resources), context));
  }

  /**
   * Tells what is the next step of the flow depending on the current step and current diagnosis.
   *
   * @param currentStep the step we want to calculate the next step for
   * @return {@link Optional} of the next step value
   */
  public LiveData<Optional<Step>> maybeGetNextStepLiveData(Step currentStep) {
    return Transformations.map(
        getNextStepLiveData(currentStep), step ->
            step == null ? Optional.absent() : Optional.of(step));
  }

  /**
   * Tells what is the previous step of the flow depending on the current step and current
   * diagnosis.
   */
  public LiveData<Step> getPreviousStepLiveData(Step currentStep) {
    return Transformations.map(
        getCurrentDiagnosisLiveData(), diagnosis -> ShareDiagnosisFlowHelper.getPreviousStep(
            currentStep, diagnosis, getShareDiagnosisFlow(), context));
  }

  /**
   * Notifies if the request for a verification code has failed with a particular {@link String}
   * error message.
   *
   * <p>This message will be displayed in a snackbar on the "Get a verification code" screen.
   */
  public LiveData<String> getRequestCodeErrorLiveData() {
    return requestCodeErrorLiveData;
  }

  /**
   * An event that triggers when there is an error to be displayed under a "Phone number" input
   * field on the "Get a verification code" screen.
   */
  public LiveData<String> getPhoneNumberErrorMessageLiveData() {
    return phoneNumberErrorMessageLiveData;
  }

  /**
   * Notifies if the code verification has failed with a particular {@link String} error message.
   */
  public LiveData<String> getVerificationErrorLiveData() {
    return verificationErrorLiveData;
  }

  /**
   * Notifies if the app update flow has failed to launch.
   */
  public LiveData<String> getAppUpdateFlowErrorLiveData() {
    return appUpdateFlowErrorLiveData;
  }

  /**
   * A LiveData that tracks a positive (i.e. confirmed or self-reported) diagnosis shared within
   * the last {@link ShareDiagnosisViewModel#SELF_REPORT_DISABLED_NUM_OF_DAYS} days.
   */
  public LiveData<Optional<DiagnosisEntity>> getRecentlySharedPositiveDiagnosisLiveData() {
    long minTimestampMs = clock.now().minus(Duration.ofDays(SELF_REPORT_DISABLED_NUM_OF_DAYS))
        .toEpochMilli();
    return diagnosisRepository.getPositiveDiagnosisSharedAfterThresholdLiveData(minTimestampMs);
  }

  /**
   * Invalidate verification code error if the user inputs a verified code.
   */
  public void invalidateVerificationError() {
    verificationErrorLiveData.postValue("");
  }

  /**
   * Moves to the given {@link Step} and re-initializes the back stack if step is non-null.
   */
  public void nextStepIrreversible(@Nullable Step nextStep) {
    if (nextStep == null) {
      return;
    }
    backStack.clear();
    nextStep(nextStep);
  }

  /**
   * Moves to the given {@link Step} and adds to back stack if step is non-null.
   */
  public void nextStep(@Nullable Step nextStep) {
    if (nextStep == null) {
      return;
    }
    backStack.push(nextStep);
    currentStepLiveData.postValue(nextStep);
  }

  /**
   * Moves to the given {@link Step} and updates the back stack if step is non-null.
   * <p>
   * If previous step is not corresponding to the back stack previous, the back stack is cleared.
   */
  public void previousStep(@Nullable Step previousStep) {
    if (previousStep == null) {
      return;
    }
    maybePopFromBackStack();
    if (backStack.isEmpty()) {
      // No more to go back, set new step as the stack
      backStack.push(previousStep);
    } else {
      // Is more to go back, check if top equals where we are going
      // If so, do nothing new top of stack is good.
      // If not, clear the stack and reset to the previousStep.
      if (!backStack.peek().equals(previousStep)) {
        backStack.clear();
        backStack.push(previousStep);
      }
    }
    currentStepLiveData.postValue(previousStep);
  }

  /**
   * Moves back in the back step if possible.
   *
   * @return false if the back stack is empty and so not possible.
   */
  public boolean backStepIfPossible() {
    maybePopFromBackStack();
    if (backStack.isEmpty()) {
      return false;
    }
    currentStepLiveData.postValue(backStack.peek());
    return true;
  }

  /**
   * Removes the current step (assume must be the top on the stack) from the back stack.
   */
  public void removeCurrentStepFromBackStack() {
    maybePopFromBackStack();
  }

  private void maybePopFromBackStack() {
    if (!backStack.isEmpty()) {
      backStack.pop();
    }
  }

  /**
   * Accepts the user's input that they either had or not had symptoms, or decline to answer.
   */
  public void setHasSymptoms(HasSymptoms hasSymptoms) {
    save(diagnosis -> diagnosis.toBuilder().setHasSymptoms(hasSymptoms).build());
  }

  /**
   * Accepts the user's input when their symptoms started. Implies they answered "yes" that they did
   * in fact have symptoms, so we set that date as well.
   */
  public void setSymptomOnsetDate(LocalDate onsetDate) {
    save(diagnosis -> diagnosis.toBuilder()
        .setHasSymptoms(HasSymptoms.YES)
        .setOnsetDate(onsetDate)
        .build());
  }

  public void setTravelStatus(TravelStatus travelStatus) {
    save(diagnosis -> diagnosis.toBuilder().setTravelStatus(travelStatus).build());
  }

  /**
   * Sets whether the diagnosis was shared or failed.
   */
  public ListenableFuture<Long> setIsShared(Shared isShared) {
    return save(diagnosis -> {
      sharedLiveEvent.postValue(Shared.SHARED.equals(isShared));
      return diagnosis.toBuilder().setSharedStatus(isShared).build();
    });
  }

  /**
   * Tells us which vaccination status radio button was checked to restore state.
   * Null indicated no checked button.
   */
  public LiveData<Optional<VaccinationStatus>> getVaccinationStatusForUILiveData() {
    return Transformations.distinctUntilChanged(vaccinationStatusForUILiveData);
  }

  public void setVaccinationStatusForUI(VaccinationStatus vaccinationStatus) {
    vaccinationStatusForUILiveData.postValue(Optional.of(vaccinationStatus));
  }

  private ListenableFuture<Long> save(
      Function<DiagnosisEntity, DiagnosisEntity> mutator) {
    return FluentFuture.from(getCurrentDiagnosis())
        // Apply the given mutation
        .transform(diagnosis -> diagnosisRepository.createOrMutateById(diagnosis.getId(), mutator),
            backgroundExecutor)
        .transform(diagnosisId -> {
          // Remember the diagnosis ID for further operations.
          postCurrentDiagnosisId(diagnosisId);
          return diagnosisId;
        }, lightweightExecutor);
  }

  private ListenableFuture<DiagnosisEntity> getCurrentDiagnosis() {
    return FluentFuture.from(diagnosisRepository.getByIdAsync(currentDiagnosisId.getValue()))
        .transform(diagnosis -> {
          if (diagnosis == null) {
            logger.d("No diagnosis id [" + currentDiagnosisId
                + "] exists in storage. Returning a fresh empty one.");
            return EMPTY_DIAGNOSIS;
          }
          logger.d("Got saved diagnosis for id " + currentDiagnosisId.getValue());
          return diagnosis;
        }, backgroundExecutor);
  }

  @NonNull
  public LiveData<Boolean> getInFlightLiveData() {
    return inFlightLiveData;
  }

  /**
   * Deletes a given entity
   */
  public void deleteEntity(DiagnosisEntity diagnosis) {
    Futures.addCallback(
        diagnosisRepository.deleteByIdAsync(diagnosis.getId()),
        new FutureCallback<Void>() {
          @Override
          public void onSuccess(@Nullable Void result) {
            currentDiagnosisId.postValue(NO_EXISTING_ID);
            deletedLiveEvent.postCall();
          }

          @Override
          public void onFailure(@NonNull Throwable t) {
            logger.w("Failed to delete", t);
          }
        },
        lightweightExecutor);
  }

  /**
   * Share the keys.
   */
  public ListenableFuture<?> uploadKeys() {
    inFlightLiveData.postValue(true);

    return FluentFuture.from(getRecentKeys())
        .transform(
            this::toDiagnosisKeysWithTransmissionRisk, lightweightExecutor)
        .transformAsync(this::getCertAndUploadKeys, backgroundExecutor)
        .transform(
            unused -> {
              inFlightLiveData.postValue(false);
              return null;
            },
            lightweightExecutor)
        .catching(NoInternetException.class, ex -> {
          snackbarLiveEvent.postValue(resources.getString(R.string.share_error_no_internet));
          inFlightLiveData.postValue(false);
          return null;
        }, lightweightExecutor)
        .catching(UploadException.class, ex -> {
          handleUploadError(ex.getUploadError(), snackbarLiveEvent);
          return null;
        }, lightweightExecutor)
        .catching(ApiException.class, ex -> {
          if (ex.getStatusCode() == ExposureNotificationStatusCodes.RESOLUTION_REQUIRED) {
            teksReleaseResolutionRequiredLiveEvent.postValue(ex);
            return null;
          } else {
            logger.w("No RESOLUTION_REQUIRED in result", ex);
          }
          snackbarLiveEvent.postValue(resources.getString(R.string.generic_error_message));
          inFlightLiveData.postValue(false);
          return null;
        }, lightweightExecutor)
        .catching(Exception.class, ex -> {
          snackbarLiveEvent.postValue(resources.getString(R.string.generic_error_message));
          inFlightLiveData.postValue(false);
          return null;
        }, lightweightExecutor);
  }

  /**
   * Gets recent (initially 14 days) Temporary Exposure Keys from Google Play Services.
   */
  private ListenableFuture<List<TemporaryExposureKey>> getRecentKeys() {
    logger.d("Getting current TEKs from EN API...");
    return TaskToFutureAdapter.getFutureWithTimeout(
        exposureNotificationClientWrapper.getTemporaryExposureKeyHistory(),
        GET_TEKS_TIMEOUT,
        scheduledExecutor);
  }

  /**
   * Transforms from EN API's TEK object to our network package's expression of it, applying a
   * default transmission risk. This default TR is temporary, while we determine that part of the EN
   * API's contract.
   */
  private ImmutableList<DiagnosisKey> toDiagnosisKeysWithTransmissionRisk(
      List<TemporaryExposureKey> recentKeys) {
    logger.d("Converting TEKs into DiagnosisKeys...");
    ImmutableList.Builder<DiagnosisKey> builder = new Builder<>();
    for (TemporaryExposureKey k : recentKeys) {
      builder.add(
          DiagnosisKey.newBuilder()
              .setKeyBytes(k.getKeyData())
              .setIntervalNumber(k.getRollingStartIntervalNumber())
              .setRollingPeriod(k.getRollingPeriod())
              // Accepting the default transmission risk for now, which the DiagnosisKey.Builder
              // comes with pre-set.
              .build());
    }
    return builder.build();
  }

  @AutoValue
  abstract static class EnterCodeStepReturnValue {

    static EnterCodeStepReturnValue create(boolean revealPage,
        Optional<String> verificationCodeToPrefill) {
      return new AutoValue_ShareDiagnosisViewModel_EnterCodeStepReturnValue(revealPage,
          verificationCodeToPrefill);
    }

    abstract boolean revealPage();

    abstract Optional<String> verificationCodeToPrefill();
  }

  /**
   * Checks if we are entering the Code step for the deep link flow. Call this function in
   * {@link ShareDiagnosisCodeFragment#onViewCreated(View, Bundle)}.
   *
   * @param codeFromLink code from the deep link.
   */
  public EnterCodeStepReturnValue enterCodeStep(@Nullable String codeFromLink) {
    if (Strings.isNullOrEmpty(codeFromLink)) {
      return EnterCodeStepReturnValue.create(true, Optional.absent());
    }

    // Not the first time user navigates to 'Code' step and the verification code from the link has
    // been used earlier. In this case, we deem the code was not accepted due to errors thus we
    // reveal the normal 'Code' page.
    if (isCodeFromLinkUsed()) {
      return EnterCodeStepReturnValue.create(true, Optional.absent());
    }

    setCodeFromUrlUsed();
    setCodeInputForCodeStep(codeFromLink);
    markCodeUiToBeRestoredFromSavedState(true);
    submitCode(codeFromLink, true);
    return EnterCodeStepReturnValue.create(false, Optional.of(codeFromLink));
  }

  /**
   * Requests a verification code from the verification server as part of the self-report flow.
   *
   * @param phoneNumber user-provided phone number
   * @param testDate    user-provided test date
   */
  @UiThread
  public ListenableFuture<?> requestCode(String phoneNumber, LocalDate testDate) {
    if (inFlightLiveData.getValue()) {
      return Futures.immediateVoidFuture();
    }
    inFlightLiveData.postValue(true);

    String normalizedPhoneNumber = telephonyHelper.normalizePhoneNumber(phoneNumber);
    if (!telephonyHelper.isValidPhoneNumber(phoneNumber)
        || TextUtils.isEmpty(normalizedPhoneNumber)) {
      String learnMoreText = resources.getString(R.string.learn_more);
      String errorText = resources.getString(R.string.self_report_bad_phone_number, learnMoreText);
      inFlightLiveData.postValue(false);
      phoneNumberErrorMessageLiveData.postValue(errorText);
      return Futures.immediateVoidFuture();
    }

    phoneNumberErrorMessageLiveData.postValue("");

    long tzOffsetMin = calculateTzOffsetMin();
    UserReportUpload upload = UserReportUpload
        .newBuilder(normalizedPhoneNumber, SecureRandomUtil.newNonce(secureRandom),
            testDate, tzOffsetMin)
        .build();

    return FluentFuture.from(checkIfLimitHasBeenHit(DAYS_REQUEST_LIMIT))
        .transformAsync(lastRequestTime -> {
          if (lastRequestTime != null) {
            String healthAuthorityName = resources.getString(R.string.health_authority_name);
            String errorMessage = resources.getString(
                R.string.self_report_code_requested_too_many_times, healthAuthorityName);
            return Futures.immediateFailedFuture(
                new TooManyVerificationCodeRequestsException(errorMessage));
          }
          return checkIfLimitHasBeenHit(MINS_REQUEST_LIMIT);
        }, lightweightExecutor)
        .transformAsync(lastRequestTime -> {
          if (lastRequestTime != null) {
            long minutesTillNextRequest = MINS_REQUEST_LIMIT.periodDuration()
                .minus(Duration.between(lastRequestTime, clock.now()))
                .toMinutes();
            String errorMessage = resources.getQuantityString(
                R.plurals.self_report_code_already_requested,
                (int) minutesTillNextRequest,
                minutesTillNextRequest);
            return Futures.immediateFailedFuture(
                new TooManyVerificationCodeRequestsException(errorMessage));
          }
          // No limits for sending a verification code request have been hit. Submit a request now.
          return uploadController.requestCode(upload);
        }, lightweightExecutor)
        .transformAsync(uploadResponse -> {
          UserReportUpload.Builder builder = upload.toBuilder();
          // expiresAt and expiresAtTimestampSec fields will be set for successful responses only
          // (statusCode=200).
          if (!TextUtils.isEmpty(uploadResponse.expiresAt())
              && uploadResponse.expiresAtTimestampSec() > 0) {
            builder
                .setExpiresAt(uploadResponse.expiresAt())
                .setExpiresAtTimestampSec(uploadResponse.expiresAtTimestampSec());
          }
          // And we want to capture all requests with non-error responses (200 < statusCode < 400)
          // here. Requests with error responses are handled via UploadException.
          return captureVerificationCodeRequest(builder.build());
        }, lightweightExecutor)
        .transform(
            unused -> {
              inFlightLiveData.postValue(false);
              nextStep(Step.CODE);
              return null;
            }, lightweightExecutor)
        .catching(TooManyVerificationCodeRequestsException.class, ex -> {
          inFlightLiveData.postValue(false);
          phoneNumberErrorMessageLiveData.postValue(ex.getErrorMessage());
          return null;
        }, lightweightExecutor)
        .catching(NoInternetException.class, ex -> {
          inFlightLiveData.postValue(false);
          requestCodeErrorLiveData.postValue(resources.getString(R.string.share_error_no_internet));
          return null;
        }, lightweightExecutor)
        .catchingAsync(UploadException.class, ex ->
                FluentFuture.from(captureVerificationCodeRequestAndUploadException(upload, ex))
                    .transform(unused -> null, lightweightExecutor),
            lightweightExecutor)
        .catching(Exception.class, ex -> {
          inFlightLiveData.postValue(false);
          requestCodeErrorLiveData.postValue(resources.getString(R.string.generic_error_message));
          return null;
        }, lightweightExecutor);
  }

  /** Calculates offset in minutes of the user's timezone. */
  @VisibleForTesting
  protected long calculateTzOffsetMin() {
    return clock.zonedNow().getOffset().getTotalSeconds() / 60;
  }

  /**
   * Checks if a given limit for a number of verification code requests in a given period of time
   * has been hit.
   *
   * @param verificationCodeRequestLimit a given limit for a number of verification code requests
   * @return time of the most recent request if a limit has been hit or null otherwise
   */
  private ListenableFuture<Instant> checkIfLimitHasBeenHit(
      VerificationCodeRequestLimit verificationCodeRequestLimit) {
    Instant earliestThreshold = clock.now().minus(verificationCodeRequestLimit.periodDuration());
    return FluentFuture.from(requestRepository.
        getLastXRequestsNotOlderThanThresholdAsync(earliestThreshold,
            verificationCodeRequestLimit.numOfRequests()))
        .transform(requests -> {
          if (!requests.isEmpty() &&
              requests.size() >= verificationCodeRequestLimit.numOfRequests()) {
            return requests.get(0).getRequestTime();
          }
          return null;
        }, lightweightExecutor);
  }

  /**
   * Captures a request for verification code. Should be called if we get a response from the
   * verification server (either success or error).
   */
  private ListenableFuture<Long> captureVerificationCodeRequest(UserReportUpload upload) {
    VerificationCodeRequestEntity.Builder builder = VerificationCodeRequestEntity.newBuilder()
        .setRequestTime(clock.now())
        .setNonce(upload.nonceBase64());
    if (upload.expiresAtTimestampSec() > 0) {
      builder.setExpiresAtTime(Instant.ofEpochSecond(upload.expiresAtTimestampSec()));
    }
    return requestRepository.upsertAsync(builder.build());
  }

  /**
   * Captures a request for verification code and updates LiveData-s for a thrown exception.
   */
  private ListenableFuture<Void> captureVerificationCodeRequestAndUploadException(
      UserReportUpload upload, UploadException ex) {
    return FluentFuture.from(captureVerificationCodeRequest(upload))
        .transform(unused -> {
              handleUploadError(ex.getUploadError(), requestCodeErrorLiveData);
              return null;
            }, lightweightExecutor);
  }

  /**
   * Submits a short-lived verification code to the verification server in attempt to exchange it
   * for a long-lived token.
   *
   * @param code           verification code to submit
   * @param isCodeFromLink indicates if a given verification code is from an SMS deep link
   */
  @UiThread
  public ListenableFuture<?> submitCode(String code, boolean isCodeFromLink) {
    if (inFlightLiveData.getValue()) {
      return Futures.immediateVoidFuture();
    }
    inFlightLiveData.postValue(true);

    logger.d("Checking verification code locally");
    return FluentFuture.from(diagnosisRepository.getByVerificationCodeAsync(code))
        .transformAsync(diagnosisEntities -> {
          if (!diagnosisEntities.isEmpty()) {
            // Should only be 1, but to be sure just choose the first.
            return Futures.immediateFailedFuture(
                new VerificationCodeExistsException(diagnosisEntities.get(0)));
          }
          return requestRepository.getValidNoncesWithLatestExpiringFirstIfAnyAsync(clock.now());
        }, lightweightExecutor)
        .transformAsync(nonces -> {
          logger.d("Submitting verification code...");
          Upload.Builder uploadBuilder = Upload.newBuilder(
              code, SecureRandomUtil.newHmacKey(secureRandom));
          if (!nonces.isEmpty()) {
            // Nonces effectively expire every 15 minutes. We never allow two or more requests for
            // a verification code in 30-minute-intervals. Thus, we should always have at most one
            // non-expired nonce stored. TODO: add some retry logic?
            String mostRecentNonce = nonces.get(0);
            uploadBuilder.setNonceBase64(mostRecentNonce);
          }
          // Submit the verification code to the verification server:
          return uploadController.submitCode(uploadBuilder.build());
        }, lightweightExecutor)
        .transformAsync(
            verifiedUpload -> {
              // If successful, capture the long term token and some diagnosis facts into storage.
              DiagnosisEntity.Builder builder =
                  DiagnosisEntity.newBuilder().setVerificationCode(code)
                      .setIsCodeFromLink(isCodeFromLink)
                      .setSharedStatus(Shared.NOT_ATTEMPTED);
              // The long term token is required.
              builder.setLongTermToken(verifiedUpload.longTermToken());
              // Symptom onset may or may not be provided by the verification server.
              if (verifiedUpload.symptomOnset() != null) {
                builder.setIsServerOnsetDate(true);
                builder.setOnsetDate(verifiedUpload.symptomOnset())
                    .setHasSymptoms(HasSymptoms.YES);
              }
              // Test type is currently always provided by the verification server, but that seems
              // like something that could change. Let's check.
              if (verifiedUpload.testType() != null) {
                builder.setTestResult(TestResult.of(verifiedUpload.testType()));
              }
              if (isResumingAndNotConfirmed()) {
                setResumingAndNotConfirmed(false);
              }
              // Store in the preferences that a verification code has been successfully uploaded.
              exposureNotificationSharedPreferences
                  .setPrivateAnalyticsLastSubmittedCodeTime(clock.now());
              return diagnosisRepository.upsertAsync(builder.build());
            },
            backgroundExecutor)
        .transformAsync(
            newDiagnosisId -> {
              // Remember the diagnosis ID as the "current" diagnosis for the rest of the sharing
              // flow.
              logger.d("Current diagnosis stored, notifying view");
              postCurrentDiagnosisId(newDiagnosisId);
              inFlightLiveData.postValue(false);
              verificationErrorLiveData.postValue("");
              return diagnosisRepository.getByIdAsync(newDiagnosisId);
            }, lightweightExecutor)
        .transform(
            diagnosisEntity -> {
              if (ShareDiagnosisFlowHelper.isCodeStepSkippable(diagnosisEntity)) {
                skipCodeStep(diagnosisEntity);
              } else {
                revealCodeStepEvent.postValue(true);
                nextStep(ShareDiagnosisFlowHelper.getNextStep(Step.CODE, diagnosisEntity,
                    getShareDiagnosisFlow(), showVaccinationQuestion(resources), context));
              }
              return null;
            }, lightweightExecutor)
        .catching(VerificationCodeExistsException.class, ex -> {
          inFlightLiveData.postValue(false);
          postCurrentDiagnosisId(ex.getDiagnosisEntity().getId());
          if (Shared.SHARED.equals(ex.getDiagnosisEntity().getSharedStatus())) {
            verificationErrorLiveData.postValue(
                resources.getString(R.string.code_error_already_submitted));
          }
          if (ShareDiagnosisFlowHelper.isCodeStepSkippable(ex.getDiagnosisEntity())) {
            skipCodeStep(ex.getDiagnosisEntity());
          } else {
            revealCodeStepEvent.postValue(true);
          }
          return null;
        }, lightweightExecutor)
        .catching(NoInternetException.class, ex -> {
          inFlightLiveData.postValue(false);
          verificationErrorLiveData.postValue(
              resources.getString(R.string.share_error_no_internet));
          revealCodeStepEvent.postValue(true);
          return null;
        }, lightweightExecutor)
        .catching(UploadException.class, ex -> {
          revealCodeStepEvent.postValue(true);
          handleUploadError(ex.getUploadError(), verificationErrorLiveData);
          return null;
        }, lightweightExecutor)
        .catching(Exception.class, ex -> {
          logger.e("Failed to submit verification code.", ex);
          inFlightLiveData.postValue(false);
          revealCodeStepEvent.postValue(true);
          verificationErrorLiveData.postValue(resources.getString(R.string.generic_error_message));
          return null;
        }, lightweightExecutor);
  }

  void skipCodeStep(DiagnosisEntity diagnosisEntity) {
    removeCurrentStepFromBackStack();
    nextStep(ShareDiagnosisFlowHelper.getNextStep(Step.CODE, diagnosisEntity,
        getShareDiagnosisFlow(), showVaccinationQuestion(resources), context));
  }

  /**
   * An {@link Exception} for when the verification code already exists in the database.
   */
  private static class VerificationCodeExistsException extends Exception {

    private final DiagnosisEntity diagnosisEntity;

    public VerificationCodeExistsException(DiagnosisEntity diagnosisEntity) {
      this.diagnosisEntity = diagnosisEntity;
    }

    public DiagnosisEntity getDiagnosisEntity() {
      return diagnosisEntity;
    }
  }

  /**
   * Submits TEKs and our diagnosis for sharing to other EN participating devices.
   *
   * <p>This involves these steps:
   * <ol>
   *   <li>Grab some info from the current diagnosis in local storage.
   *   <li>Submit our TEKs to the verification server to get them signed (obtain a cert).
   *   <li>Submit the TEKs, the cert, and some other metadata to the keyserver
   * </ol>
   *
   * <p>In order to support resumption of past partially-successful diagnosis sharing flows, this
   * series of operations tries to look at the state of the stored diagnosis and do the right thing.
   *
   * @return a {@link ListenableFuture} of type {@link Boolean} of successfully submitted state
   */
  private ListenableFuture<?> getCertAndUploadKeys(ImmutableList<DiagnosisKey> diagnosisKeys) {
    logger.d("Certifying and uploading keys...");
    return FluentFuture.from(getCurrentDiagnosis())
        .transform(
            // Construct an Upload from some diagnosis fields.
            diagnosis ->
                Upload.newBuilder(diagnosisKeys, diagnosis.getVerificationCode(),
                        SecureRandomUtil.newHmacKey(secureRandom))
                    .setLongTermToken(diagnosis.getLongTermToken())
                    .setSymptomOnset(diagnosis.getOnsetDate())
                    .setCertificate(diagnosis.getCertificate())
                    .setHasTraveled(TravelStatus.TRAVELED.equals(diagnosis.getTravelStatus()))
                    .setTestType(diagnosis.getTestResult().toApiType())
                    .build(),
            lightweightExecutor)
        .transformAsync(
            upload -> {
              logger.d("Submitting keys to verification server for certificate...");
              // We normally do not have a certificate yet, but in some cases like resuming a past
              // failed upload, we have one already. Get one if we need one.
              if (Strings.isNullOrEmpty(upload.certificate())) {
                return uploadController.submitKeysForCert(upload);
              }
              return Futures.immediateFuture(upload);
            },
            backgroundExecutor)
        .transformAsync(
            this::addRevisionTokenToUpload, lightweightExecutor)
        .transformAsync(
            upload -> {
              logger.d("Uploading keys and cert to keyserver...");
              // Finally, the verification server having certified our diagnosis, upload our keys.
              return uploadController.upload(upload);
            },
            backgroundExecutor)
        .transform(
            upload -> {
              // Successfully submitted
              logger.d("Upload success: " + upload);
              save(
                  diagnosis -> diagnosis.toBuilder()
                      .setCertificate(upload.certificate())
                      .setRevisionToken(upload.revisionToken())
                      .setSharedStatus(Shared.SHARED)
                      .build());
              sharedLiveEvent.postValue(true);
              // Store in the preferences that keys have been successfully uploaded and the
              // associated report type.
              exposureNotificationSharedPreferences
                  .setPrivateAnalyticsLastSubmittedKeysTime(clock.now());

              TestResult testResult = null;
              try {
                testResult = TestResult.of(upload.testType());
              } catch (IllegalArgumentException | NullPointerException e) {
                // Do nothing: testResult is already null, which is the right behavior
              }
              exposureNotificationSharedPreferences.setPrivateAnalyticsLastReportType(testResult);

              return Shared.SHARED;
            },
            lightweightExecutor)
        .catching(
            ApiException.class,
            e -> {
              // Not successfully submitted
              logger.e("Upload fail: ", e);
              setIsShared(Shared.NOT_SHARED);
              return Shared.NOT_SHARED;
            },
            lightweightExecutor);
  }

  private ListenableFuture<Upload> addRevisionTokenToUpload(Upload upload) {
    return FluentFuture.from(diagnosisRepository.getMostRecentRevisionTokenAsync())
        .transform(
            revisionToken -> upload.toBuilder().setRevisionToken(revisionToken).build(),
            backgroundExecutor);
  }

  /**
   * Handles upload errors we get when talking to the verification server.
   *
   * @param uploadError {@link UploadError} associated with the error returned from the server.
   * @param errorLiveData LiveData object to update with an error message.
   */
  private void handleUploadError(UploadError uploadError, MutableLiveData<String> errorLiveData) {
    if (uploadError.equals(UploadError.UNAUTHORIZED_CLIENT) && BuildUtils.getType() == Type.V2) {
      checkForAppUpdateAvailability(uploadError, errorLiveData);
    } else {
      inFlightLiveData.postValue(false);
      errorLiveData.postValue(uploadError.getErrorMessage(
          resources, ShareDiagnosisFlow.SELF_REPORT.equals(getShareDiagnosisFlow())));
    }
  }

  /**
   * If we get the 401 Unauthorized Client error from the verification server, this might be an
   * indicator that the current API key, which is used to talk to the server, has been revoked. In
   * this case, we might have an app update available, which is set up to use a new valid API key to
   * continue talking to the server.
   *
   * <p>This method checks whether there is an app update available and if so, informs the upload
   * flow about this to trigger the app update flow. Otherwise, it displays a corresponding error
   * message.
   *
   * <p>This method should only be called upon receiving 401 Unauthorized Client error from the
   * server (i.e. in case of {@link UploadError#UNAUTHORIZED_CLIENT} and for {@link Type#V2} apps.
   *
   * @param uploadError {@link UploadError} associated with the error returned from the server.
   * @param errorLiveData LiveData object to update with an error message.
   */
  private void checkForAppUpdateAvailability(
      UploadError uploadError, MutableLiveData<String> errorLiveData) {
    enxAppUpdateManager
        .getAppUpdateInfo()
        .addOnSuccessListener(
            appUpdateInfo -> {
              inFlightLiveData.postValue(false);
              if (enxAppUpdateManager.isImmediateAppUpdateAvailable(appUpdateInfo)) {
                appUpdateAvailableLiveEvent.postCall();
              } else {
                errorLiveData.postValue(uploadError.getErrorMessage(
                    resources, ShareDiagnosisFlow.SELF_REPORT.equals(getShareDiagnosisFlow())));
              }
            })
        .addOnFailureListener(
            lightweightExecutor,
            ex -> {
              inFlightLiveData.postValue(false);
              errorLiveData.postValue(uploadError.getErrorMessage(
                  resources, ShareDiagnosisFlow.SELF_REPORT.equals(getShareDiagnosisFlow())));
            });
  }

  /**
   * Triggers the in-app update flow.
   *
   * @param activityResultLauncher A launcher for a previously-prepared call to start the process of
   *                               executing an {@link ActivityResultContract}.
   */
  public ListenableFuture<Void> triggerAppUpdateFlow(
      ActivityResultLauncher<IntentSenderRequest> activityResultLauncher) {
    return FluentFuture
        .from(enxAppUpdateManager.triggerImmediateAppUpdateFlow(activityResultLauncher))
        .transformAsync(
            appUpdateFlowLaunched -> {
              if (!appUpdateFlowLaunched) {
                // App update flow didn't launch. Nothing much we can do except asking user to try
                // again later.
                appUpdateFlowErrorLiveData.postValue(
                    context.getResources().getString(R.string.try_again_later_error_message));
              }
              return Futures.immediateVoidFuture();
            }, lightweightExecutor)
        .catchingAsync(AppUpdateFlowFailedToLaunchException.class, ex -> {
          appUpdateFlowErrorLiveData.postValue(
              context.getResources().getString(R.string.try_again_later_error_message));
          return Futures.immediateVoidFuture();
        }, lightweightExecutor);
  }

  /**
   * Checks whether there is currently in-app update in progress and if so, triggers the in-app
   * update flow.
   *
   * @param activityResultLauncher A launcher for a previously-prepared call to start the process of
   *                               executing an {@link ActivityResultContract}.
   */
  public ListenableFuture<Void> maybeTriggerAppUpdateFlowIfUpdateInProgress(
      ActivityResultLauncher<IntentSenderRequest> activityResultLauncher) {
    return enxAppUpdateManager.getAppUpdateInfoFuture()
        .transformAsync(
            appUpdateInfo -> {
              if (enxAppUpdateManager.isAppUpdateInProgress(appUpdateInfo)) {
                return enxAppUpdateManager
                    .triggerImmediateAppUpdateFlow(appUpdateInfo, activityResultLauncher);
              }
              // No app update in progress. Return true as we don't need to display an error
              // message.
              return Futures.immediateFuture(true);
            }, lightweightExecutor)
        .transformAsync(
            appUpdateFlowLaunched -> {
              if (!appUpdateFlowLaunched) {
                // Failed to start update flow. Nothing much we can do except asking user to try
                // again later.
                appUpdateFlowErrorLiveData.postValue(
                    context.getResources().getString(R.string.try_again_later_error_message));
              }
              return Futures.immediateVoidFuture();
            }, lightweightExecutor)
        .catchingAsync(AppUpdateFlowFailedToLaunchException.class, ex -> {
          appUpdateFlowErrorLiveData.postValue(
              context.getResources().getString(R.string.try_again_later_error_message));
          return Futures.immediateVoidFuture();
        }, lightweightExecutor);
  }

  /**
   * Requests user authorization to get {@link TemporaryExposureKey}s in the background and stores
   * the user decision.
   *
   * <p>If approved, the app will be able to get the TEKs in the background once in the next 5 days.
   */
  public ListenableFuture<?> preAuthorizeTeksRelease() {
    inFlightLiveData.postValue(true);
    return FluentFuture.from(requestPreAuthorizedTemporaryExposureKeyHistory())
        .transform(
            unused -> {
              preAuthFlowCompletedLiveEvent.postValue(true);
              inFlightLiveData.postValue(false);
              return null;
            }, lightweightExecutor)
        .catching(ApiException.class, ex -> {
          if (ex.getStatusCode() == ExposureNotificationStatusCodes.RESOLUTION_REQUIRED) {
            teksPreReleaseResolutionRequiredLiveEvent.postValue(ex);
            return null;
          }
          snackbarLiveEvent.postValue(resources.getString(R.string.generic_error_message));
          inFlightLiveData.postValue(false);
          return null;
        }, lightweightExecutor)
        .catching(Exception.class, ex -> {
          snackbarLiveEvent.postValue(resources.getString(R.string.generic_error_message));
          inFlightLiveData.postValue(false);
          return null;
        }, lightweightExecutor);
  }

  /**
   * Marks the flow to pre-authorize TEKs release as completed in case the user opts out of
   * releasing TEKs in the background.
   */
  public void skipPreAuthorizedTEKsRelease() {
    preAuthFlowCompletedLiveEvent.postValue(true);
  }

  /**
   * Requests user authorization to release {@link TemporaryExposureKey}s for the self-report flow
   * in the background.
   */
  private ListenableFuture<Void> requestPreAuthorizedTemporaryExposureKeyHistory() {
    return TaskToFutureAdapter.getFutureWithTimeout(
        exposureNotificationClientWrapper
            .requestPreAuthorizedTemporaryExposureKeyHistoryForSelfReport(),
        REQUEST_PRE_AUTH_TEKS_HISTORY_API_TIMEOUT,
        scheduledExecutor);
  }

  /**
   * A {@link SingleLiveEvent} to trigger a snackbar.
   */
  public SingleLiveEvent<String> getSnackbarSingleLiveEvent() {
    return snackbarLiveEvent;
  }

  /**
   * A {@link SingleLiveEvent} that signifies a successful deletion.
   */
  public SingleLiveEvent<Void> getDeletedSingleLiveEvent() {
    return deletedLiveEvent;
  }

  /**
   * A {@link SingleLiveEvent} that signifies availability of the app update.
   */
  public SingleLiveEvent<Void> getAppUpdateAvailableLiveEvent() {
    return appUpdateAvailableLiveEvent;
  }

  /**
   * A {@link SingleLiveEvent} that signifies a completion of the pre-auth flow (which might be
   * completed with either user opting in or opting out of pre-releasing TEKs).
   */
  public SingleLiveEvent<Boolean> getPreAuthFlowCompletedLiveEvent() {
    return preAuthFlowCompletedLiveEvent;
  }

  /**
   * A {@link SingleLiveEvent} that returns {@link ApiException} to help with starting the
   * resolution for releasing TEKs.
   */
  public SingleLiveEvent<ApiException> getTeksReleaseResolutionRequiredLiveEvent() {
    return teksReleaseResolutionRequiredLiveEvent;
  }

  /**
   * A {@link SingleLiveEvent} that returns {@link ApiException} to help with starting the
   * resolution for consenting to pre-release TEKs.
   */
  public SingleLiveEvent<ApiException> getTeksPreReleaseResolutionRequiredLiveEvent() {
    return teksPreReleaseResolutionRequiredLiveEvent;
  }

  /**
   * A {@link SingleLiveEvent} that returns {@link Boolean} to control whether the 'Code' step view
   * should be revealed.
   */
  public SingleLiveEvent<Boolean> getRevealCodeStepEvent() {
    return revealCodeStepEvent;
  }

  /**
   * A {@link SingleLiveEvent} that returns {@link Boolean} to show whether the data was shared.
   */
  public SingleLiveEvent<Boolean> getSharedLiveEvent() {
    return sharedLiveEvent;
  }

  public void setCloseOpen(boolean isCloseOpen) {
    this.isCloseOpen = isCloseOpen;
  }

  public boolean isCloseOpen() {
    return isCloseOpen;
  }

  public void setDeleteOpen(boolean isDeleteOpen) {
    this.isDeleteOpen = isDeleteOpen;
  }

  public boolean isDeleteOpen() {
    return isDeleteOpen;
  }

  public void setCodeFromUrlUsed() {
    isCodeFromLinkUsed = true;
  }

  public boolean isCodeFromLinkUsed() {
    return isCodeFromLinkUsed;
  }

  public boolean isTravelStatusStepSkippable() {
    return ShareDiagnosisFlowHelper.isTravelStatusStepSkippable(context);
  }

  public void setResumingAndNotConfirmed(boolean resumingAndNotConfirmed) {
    this.resumingAndNotConfirmed = resumingAndNotConfirmed;
  }

  public boolean isResumingAndNotConfirmed() {
    return resumingAndNotConfirmed;
  }

  /** Set the type of the current sharing flow. */
  public void setShareDiagnosisFlow(ShareDiagnosisFlow shareDiagnosisFlow) {
    if (ShareDiagnosisFlow.SELF_REPORT.equals(shareDiagnosisFlow)) {
      // Sharing flow may have been populated with the last non-shared diagnosis or SavedStateHandle
      // data for the Step.CODE already as part of the "Resume diagnosis automatically flow". We
      // won't need that data for the self-report flow, so reset it.
      setCurrentDiagnosisId(ShareDiagnosisViewModel.NO_EXISTING_ID);
      resetSavedStateHandleForCodeStep();
    }
    this.shareDiagnosisFlow = shareDiagnosisFlow;
  }

  /** Return the type of the current sharing flow. */
  public ShareDiagnosisFlow getShareDiagnosisFlow() {
    return shareDiagnosisFlow;
  }

  /**
   * Sets a boolean flag to indicate if the UI for the Code step in the sharing flow should be
   * marked as restored from {@link SavedStateHandle} upon the next render.
   *
   * @param codeUiRestoredFromSavedState whether the Code step UI should be marked as restored from
   *                                     saved state upon the next render
   */
  public void markCodeUiToBeRestoredFromSavedState(boolean codeUiRestoredFromSavedState) {
    this.codeUiRestoredFromSavedState = codeUiRestoredFromSavedState;
  }

  /**
   * Checks whether the UI for the Code step in the sharing flow has been restored from
   * {@link SavedStateHandle} (which is the case if the UI has been restored e.g. upon device
   * configuration changes such as rotations).
   *
   * @return a boolean indicating if the UI for the Code step has been restored from the saved state
   */
  public boolean isCodeUiToBeRestoredFromSavedState() {
    return codeUiRestoredFromSavedState;
  }

  /** Resets SavedStateHandle values for {@link Step#CODE}. */
  public void resetSavedStateHandleForCodeStep() {
    Set<String> keysToReset = savedStateHandle.keys();
    keysToReset.removeAll(SAVED_STATE_GET_CODE_KEYS);
    for (String key : keysToReset) {
      savedStateHandle.remove(key);
    }
  }

  /*
   * All the methods below are to store or retrieve the different bits of UI state for the CODE and
   * GET_CODE steps in the sharing flow with the help of SavedStateHandle.
   */
  public void setCodeIsInvalidForCodeStep(boolean isCodeInvalid) {
    savedStateHandle.set(SAVED_STATE_CODE_IS_INVALID, isCodeInvalid);
  }

  public LiveData<Boolean> isCodeInvalidForCodeStepLiveData() {
    return savedStateHandle.getLiveData(SAVED_STATE_CODE_IS_INVALID, false);
  }

  public void setCodeIsVerifiedForCodeStep(boolean isCodeVerified) {
    savedStateHandle.set(SAVED_STATE_CODE_IS_VERIFIED, isCodeVerified);
  }

  public LiveData<Boolean> isCodeVerifiedForCodeStepLiveData() {
    return savedStateHandle.getLiveData(SAVED_STATE_CODE_IS_VERIFIED, false);
  }

  public void setSwitcherChildForCodeStep(int displayedChild) {
    savedStateHandle.set(SAVED_STATE_SHARE_ADVANCE_SWITCHER_CHILD, displayedChild);
  }

  public LiveData<Integer> getSwitcherChildForCodeStepLiveData() {
    return savedStateHandle.getLiveData(SAVED_STATE_SHARE_ADVANCE_SWITCHER_CHILD, -1);
  }

  public void setCodeInputEnabledForCodeStep(boolean codeInputForCodeStepEnabled) {
    savedStateHandle.set(SAVED_STATE_CODE_INPUT_IS_ENABLED, codeInputForCodeStepEnabled);
  }

  public LiveData<Boolean> isCodeInputEnabledForCodeStepLiveData() {
    return savedStateHandle.getLiveData(SAVED_STATE_CODE_INPUT_IS_ENABLED, true);
  }

  public void setCodeInputForCodeStep(String codeInput) {
    savedStateHandle.set(SAVED_STATE_CODE_INPUT, codeInput);
  }

  public LiveData<String> getCodeInputForCodeStepLiveData() {
    return savedStateHandle.getLiveData(SAVED_STATE_CODE_INPUT, null);
  }

  public void setVerifiedCodeForCodeStep(@Nullable String verifiedCode) {
    savedStateHandle.set(SAVED_STATE_VERIFIED_CODE, verifiedCode);
  }

  public LiveData<String> getVerifiedCodeForCodeStepLiveData() {
    return savedStateHandle.getLiveData(SAVED_STATE_VERIFIED_CODE, null);
  }

  public void setPhoneNumberForGetCodeStep(@Nullable String phoneNumber) {
    savedStateHandle.set(SAVED_STATE_GET_CODE_PHONE_NUMBER, phoneNumber);
  }

  public LiveData<String> getPhoneNumberForGetCodeStepLiveData() {
    return savedStateHandle.getLiveData(SAVED_STATE_GET_CODE_PHONE_NUMBER, null);
  }

  public void setTestDateForGetCodeStep(@Nullable String testDate) {
    savedStateHandle.set(SAVED_STATE_GET_CODE_TEST_DATE, testDate);
  }

  public LiveData<String> getTestDateForGetCodeStepLiveData() {
    return savedStateHandle.getLiveData(SAVED_STATE_GET_CODE_TEST_DATE, null);
  }

  public void setLastVaccinationResponse(VaccinationStatus vaccinationStatus) {
    exposureNotificationSharedPreferences
        .setLastVaccinationResponse(clock.now(), vaccinationStatus);
  }

  /**
   * Check whether we show the vaccination question screen.
   * We only want to show it, if ALL of the following conditions are true:
   * - ENPA is supported by the App
   * - The user has enabled ENPA
   * - Vaccination question is enabled by the HealthAuthority (empty string indicates it being
   * disabled)
   */
  public boolean showVaccinationQuestion(Resources resources) {
    return privateAnalyticsEnabledProvider.isSupportedByApp()
        && privateAnalyticsEnabledProvider.isEnabledForUser()
        && !TextUtils.isEmpty(resources.getString(R.string.share_vaccination_detail));
  }

  /**
   * An {@link Exception} thrown when a number of requests for a verification code made within
   * a particular time frame (e.g. 30 minutes or 30 days) has exceeded a certain limit.
   */
  public static class TooManyVerificationCodeRequestsException extends Exception {

    private final String errorMessage;

    public TooManyVerificationCodeRequestsException(String errorMessage) {
      this.errorMessage = errorMessage;
    }

    public String getErrorMessage() {
      return errorMessage;
    }
  }

  /**
   * Used to represent a limit on number of requests for a verification code sent per a particular
   * period of time (e.g. 2 limits per 2 weeks).
   *
   * <p>Request limits are in place to mitigate an abuse of a Self-report functionality.
   */
  @AutoValue
  abstract static class VerificationCodeRequestLimit {

    abstract Duration periodDuration();

    abstract Integer numOfRequests();

    static VerificationCodeRequestLimit.Builder newBuilder() {
      return new AutoValue_ShareDiagnosisViewModel_VerificationCodeRequestLimit.Builder();
    }

    @AutoValue.Builder
    abstract static class Builder {

      abstract VerificationCodeRequestLimit.Builder setPeriodDuration(Duration numOfTimeUnits);

      abstract VerificationCodeRequestLimit.Builder setNumOfRequests(Integer numOfRequests);

      abstract VerificationCodeRequestLimit build();
    }
  }

}
