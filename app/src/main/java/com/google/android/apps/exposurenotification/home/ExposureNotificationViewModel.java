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

package com.google.android.apps.exposurenotification.home;

import static com.google.android.apps.exposurenotification.notify.ShareDiagnosisViewModel.EMPTY_DIAGNOSIS;
import static com.google.android.apps.exposurenotification.notify.ShareDiagnosisViewModel.EN_STATES_BLOCKING_SHARING_FLOW;

import android.app.Activity;
import android.app.PendingIntent;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.hilt.lifecycle.ViewModelInject;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import com.google.android.apps.exposurenotification.common.PairLiveData;
import com.google.android.apps.exposurenotification.common.Qualifiers.LightweightExecutor;
import com.google.android.apps.exposurenotification.common.SingleLiveEvent;
import com.google.android.apps.exposurenotification.common.logging.Logger;
import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.android.apps.exposurenotification.logging.AnalyticsLogger;
import com.google.android.apps.exposurenotification.nearby.ExposureNotificationClientWrapper;
import com.google.android.apps.exposurenotification.nearby.PackageConfigurationHelper;
import com.google.android.apps.exposurenotification.proto.UiInteraction.EventType;
import com.google.android.apps.exposurenotification.riskcalculation.ExposureClassification;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity;
import com.google.android.apps.exposurenotification.storage.DiagnosisRepository;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences.NotificationInteraction;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences.OnboardingStatus;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.nearby.exposurenotification.ExposureNotificationStatus;
import com.google.android.gms.nearby.exposurenotification.ExposureNotificationStatusCodes;
import com.google.android.gms.tasks.Tasks;
import com.google.common.base.Optional;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import java.util.Set;
import java.util.concurrent.ExecutorService;

/**
 * View model for the {@link ExposureNotificationActivity} and fragments.
 */
public class ExposureNotificationViewModel extends ViewModel {

  private static final Logger logcat = Logger.getLogger("ExposureNotificationVM");

  private final MutableLiveData<ExposureNotificationState> stateLiveData;
  private final MutableLiveData<Boolean> inFlightLiveData = new MutableLiveData<>(false);
  private final MutableLiveData<Boolean> inFlightResolutionLiveData = new MutableLiveData<>(false);
  private final MutableLiveData<Boolean> enEnabledLiveData;
  private final MutableLiveData<Boolean> enEnabledLiveDataNoCache = new MutableLiveData<>(false);
  private final MutableLiveData<Boolean> isLocationEnableRequired = new MutableLiveData<>(true);
  private final MutableLiveData<Optional<Boolean>> isPackageConfigurationSmsNoticeSeenLiveData =
      new MutableLiveData<>(Optional.absent());
  private final ExposureNotificationSharedPreferences exposureNotificationSharedPreferences;
  private final PairLiveData<ExposureNotificationState, Boolean> stateWithInFlightLiveData;
  private final PairLiveData<ExposureNotificationState, ExposureClassification>
      stateWithExposureClassificationLiveData;

  private final SingleLiveEvent<Void> apiErrorLiveEvent = new SingleLiveEvent<>();
  private final SingleLiveEvent<Void> apiUnavailableLiveEvent = new SingleLiveEvent<>();
  private final SingleLiveEvent<Boolean> enStoppedLiveEvent = new SingleLiveEvent<>();
  private final SingleLiveEvent<ApiException> resolutionRequiredLiveEvent = new SingleLiveEvent<>();
  private final SingleLiveEvent<DiagnosisEntity> lastNotSharedDiagnosisLiveEvent =
      new SingleLiveEvent<>();

  private final ExposureNotificationClientWrapper exposureNotificationClientWrapper;
  private final DiagnosisRepository diagnosisRepository;
  private final AnalyticsLogger logger;
  private final PackageConfigurationHelper packageConfigurationHelper;
  private final Clock clock;
  private final ExecutorService lightweightExecutor;

  private @Nullable LiveData<Boolean> shouldShowSmsNoticeLiveData = null;
  private boolean inFlightIsEnabled = false;

  /**
   * Enum to denote a status (i.e. state) of the EN service. This enum allows an easier handling of
   * the EN service status on the UI as opposed to the set of {@link ExposureNotificationStatus}
   * objects, which is returned by the EN module API.
   */
  public enum ExposureNotificationState {
    DISABLED,
    ENABLED,
    PAUSED_BLE,
    PAUSED_LOCATION,
    PAUSED_LOCATION_BLE,
    STORAGE_LOW,
    PAUSED_EN_NOT_SUPPORT,
    FOCUS_LOST,
    PAUSED_HW_NOT_SUPPORT,
    PAUSED_NOT_IN_ALLOWLIST,
    PAUSED_USER_PROFILE_NOT_SUPPORT
  }

  @ViewModelInject
  public ExposureNotificationViewModel(
      ExposureNotificationSharedPreferences exposureNotificationSharedPreferences,
      ExposureNotificationClientWrapper exposureNotificationClientWrapper,
      DiagnosisRepository diagnosisRepository,
      AnalyticsLogger logger,
      PackageConfigurationHelper packageConfigurationHelper,
      Clock clock,
      @LightweightExecutor ExecutorService lightweightExecutor) {
    this.exposureNotificationSharedPreferences = exposureNotificationSharedPreferences;
    this.exposureNotificationClientWrapper = exposureNotificationClientWrapper;
    this.diagnosisRepository = diagnosisRepository;
    this.logger = logger;
    this.packageConfigurationHelper = packageConfigurationHelper;
    this.clock = clock;
    this.lightweightExecutor = lightweightExecutor;

    boolean isEnabled = exposureNotificationSharedPreferences.getIsEnabledCache();
    enEnabledLiveData = new MutableLiveData<>(isEnabled);

    ExposureNotificationState state = ExposureNotificationState
        .values()[exposureNotificationSharedPreferences.getEnStateCache()];
    stateLiveData = new MutableLiveData<>(state);

    stateWithInFlightLiveData = PairLiveData.of(getStateLiveData(), getInFlightLiveData());
    stateWithExposureClassificationLiveData = PairLiveData.of(
        getStateLiveData(),
        exposureNotificationSharedPreferences.getExposureClassificationLiveData());
  }

  /**
   * A {@link LiveData} of the {@link ExposureNotificationState} of the API.
   */
  public LiveData<ExposureNotificationState> getStateLiveData() {
    return stateLiveData;
  }

  /**
   * Returns whether there is an in flight API request. Should be used to disable buttons when
   * true.
   */
  public LiveData<Boolean> getInFlightLiveData() {
    return inFlightLiveData;
  }

  /**
   * Whether EN requires location to be enabled.
   */
  public LiveData<Boolean> getIsLocationEnableRequired() {
    return isLocationEnableRequired;
  }

  /**
   * Returns whether en_module is on/off, irrespective of its functional state. Should be used if
   * there is a EN on/off button.
   */
  public LiveData<Boolean> getEnEnabledLiveData() {
    return enEnabledLiveData;
  }

  /**
   * Returns whether en_module is on/off, irrespective of its functional state. Does not use any
   * form of caching.
   */
  public LiveData<Boolean> getEnEnabledLiveDataNoCache() {
    return enEnabledLiveDataNoCache;
  }

  /**
   * A live data that combines the in app sms notice and package configuration sms notice together.
   *
   * @return an optional of true when either is true, false otherwise or empty when still being
   * computed
   */
  private LiveData<Optional<Boolean>> hasShownSmsNoticeLiveData() {
    return Transformations.map(PairLiveData
        .of(exposureNotificationSharedPreferences.isInAppSmsNoticeSeenLiveData(),
            isPackageConfigurationSmsNoticeSeenLiveData), value -> {
      boolean smsNoticeInApp = value.first;
      Optional<Boolean> smsNoticePackageConfiguration = value.second;
      if (smsNoticeInApp) {
        return Optional.of(true);
      } else {
        return smsNoticePackageConfiguration;
      }
    });
  }

  /**
   * Whether EN is enabled and the SMS notice has not been shown in the app or play onboarding.
   */
  public LiveData<Boolean> getShouldShowSmsNoticeLiveData() {
    if (shouldShowSmsNoticeLiveData == null) {
      shouldShowSmsNoticeLiveData =
          Transformations.distinctUntilChanged(
              Transformations.map(
                  PairLiveData.of(enEnabledLiveData, hasShownSmsNoticeLiveData()),
                  combined -> {
                    boolean enEnabled = combined.first;
                    if (!enEnabled) {
                      return false; // don't show when en not enabled
                    }
                    Optional<Boolean> hasShownSmsNotice = combined.second;
                    if (hasShownSmsNotice.isPresent()) {
                      return !hasShownSmsNotice.get(); // show if not been shown
                    } else {
                      return false; // still being computed so return false for now.
                    }
                  }));
    }
    return shouldShowSmsNoticeLiveData;
  }

  /**
   * Returns {@link PairLiveData} to observe changes both in the {@link ExposureNotificationState}
   * and 'in-flight' status of the API request.
   */
  public PairLiveData<ExposureNotificationState, Boolean> getStateWithInFlightLiveData() {
    return stateWithInFlightLiveData;
  }

  /**
   * Returns {@link PairLiveData} to observe changes both in {@link ExposureNotificationState} and
   * {@link ExposureClassification}.
   */
  public PairLiveData<ExposureNotificationState, ExposureClassification>
  getStateWithExposureClassificationLiveData() {
    return stateWithExposureClassificationLiveData;
  }

  /**
   * An event that requests a resolution with the given {@link ApiException}.
   */
  public SingleLiveEvent<ApiException> getResolutionRequiredLiveEvent() {
    return resolutionRequiredLiveEvent;
  }

  /**
   * An event that triggers when there is an error in the API.
   */
  public SingleLiveEvent<Void> getApiErrorLiveEvent() {
    return apiErrorLiveEvent;
  }

  /**
   * An event that triggers when there is no API available, e.g. disabled / missing GMSCore.
   */
  public SingleLiveEvent<Void> getApiUnavailableLiveEvent() {
    return apiUnavailableLiveEvent;
  }

  public OnboardingStatus getOnboardedState() {
    return exposureNotificationSharedPreferences.getOnboardedState();
  }

  /**
   * Refresh isEnabled state, EN service status, and getExposureWindows from Exposure Notification
   * API.
   */
  public void refreshState() {
    maybeRefreshIsEnabled();
    maybeRefreshPackageConfig();
  }

  private synchronized void maybeRefreshIsEnabled() {
    if (inFlightIsEnabled) {
      return;
    }
    inFlightIsEnabled = true;
    exposureNotificationClientWrapper.isEnabled()
        .addOnSuccessListener(
            isEnabled -> {
              maybeRefreshStatus(isEnabled);
              enEnabledLiveData.setValue(isEnabled);
              enEnabledLiveDataNoCache.setValue(isEnabled);
              exposureNotificationSharedPreferences.setIsEnabledCache(isEnabled);
              inFlightIsEnabled = false;
            })
        .addOnCanceledListener(() -> {
          logcat.i("Call isEnabled is canceled");
          inFlightIsEnabled = false;
        })
        .addOnFailureListener(t -> {
          maybeRefreshStatus(false);
          enEnabledLiveData.setValue(false);
          enEnabledLiveDataNoCache.setValue(false);
          exposureNotificationSharedPreferences.setIsEnabledCache(false);
          inFlightIsEnabled = false;
        });
  }

  private synchronized void maybeRefreshStatus(boolean isEnabled) {
    inFlightLiveData.setValue(true);
    exposureNotificationClientWrapper.getStatus()
        .addOnSuccessListener(status -> {
          if (status != null) {
            if (status.contains(ExposureNotificationStatus.LOCATION_DISABLED)) {
              isLocationEnableRequired.setValue(true);
            } else {
              isLocationEnableRequired.setValue(false);
            }
          }
          ExposureNotificationState state = getStateForStatusAndIsEnabled(status, isEnabled);
          stateLiveData.setValue(state);
          exposureNotificationSharedPreferences.setEnStateCache(state.ordinal());
          inFlightLiveData.setValue(false);
        })
        .addOnCanceledListener(() -> {
          logcat.i("Call getStatus is canceled");
          inFlightLiveData.setValue(false);
        })
        .addOnFailureListener(t -> {
          logcat.e("Error calling getStatus", t);
          stateLiveData.setValue(ExposureNotificationState.DISABLED);
          exposureNotificationSharedPreferences.setEnStateCache(
              ExposureNotificationState.DISABLED.ordinal());
          inFlightLiveData.setValue(false);
        });
  }

  private synchronized void maybeRefreshPackageConfig() {
    exposureNotificationClientWrapper.getPackageConfiguration()
        .addOnSuccessListener(packageConfiguration -> {
          packageConfigurationHelper.maybeUpdateAnalyticsState(packageConfiguration);
          isPackageConfigurationSmsNoticeSeenLiveData.postValue(
              Optional.of(
                  PackageConfigurationHelper.getSmsNoticeFromPackageConfiguration(
                      packageConfiguration)));
        })
        .addOnCanceledListener(() -> logcat.i("Call getPackageConfiguration is canceled"))
        .addOnFailureListener(t -> logcat.e("Error calling getPackageConfiguration", t));
  }

  /**
   * Returns a {@link ExposureNotificationState}, which is a 'mapping' for the given set of {@link
   * ExposureNotificationStatus} objects.
   * <p>
   * We do the mapping as ExposureNotificationState object is easier to handle on the UI when
   * compared to a set of ExposureNotificationStatus objects. The given set is retrieved by calling
   * the EN module's {@code getStatus()} API. This set always contains at least one element.
   *
   * @param statusSet a set of ExposureNotificationStatus objects denoting the status of the EN
   *                  API.
   * @param isEnabled a boolean indicating whether contact tracing is running for the current
   *                  requesting app.
   * @return The enum state that denotes the status of the EN API.
   */
  private ExposureNotificationState getStateForStatusAndIsEnabled(
      Set<ExposureNotificationStatus> statusSet, boolean isEnabled) {
    if (!isEnabled) {
      /*
       * The EN is not enabled for the current app. In this case we have 'terminal' states for which
       * EN cannot be re-enabled and 'non-terminal' states for which it can. Display terminal states
       * first and give the highest priority to LOW_STORAGE among non-terminal ones.
       */
      if (statusSet.contains(ExposureNotificationStatus.EN_NOT_SUPPORT)) {
        return ExposureNotificationState.PAUSED_EN_NOT_SUPPORT;
      } else if (statusSet.contains(ExposureNotificationStatus.HW_NOT_SUPPORT)) {
        return ExposureNotificationState.PAUSED_HW_NOT_SUPPORT;
      } else if (statusSet.contains(ExposureNotificationStatus.USER_PROFILE_NOT_SUPPORT)) {
        return ExposureNotificationState.PAUSED_USER_PROFILE_NOT_SUPPORT;
      } else if (statusSet.contains(ExposureNotificationStatus.NOT_IN_ALLOWLIST)) {
        return ExposureNotificationState.PAUSED_NOT_IN_ALLOWLIST;
      } else if (statusSet.contains(ExposureNotificationStatus.LOW_STORAGE)) {
        return ExposureNotificationState.STORAGE_LOW;
      } else if (statusSet.contains(ExposureNotificationStatus.NO_CONSENT)) {
        return ExposureNotificationState.DISABLED;
      } else if (statusSet.contains(ExposureNotificationStatus.FOCUS_LOST)) {
        return ExposureNotificationState.FOCUS_LOST;
      }
      // We should not get here but in case for some reason we do, return the DISABLED state.
      return ExposureNotificationState.DISABLED;
    }

    if (statusSet.contains(ExposureNotificationStatus.ACTIVATED)) {
      // The EN is enabled and operational.
      return ExposureNotificationState.ENABLED;
    } else if (statusSet.contains(ExposureNotificationStatus.INACTIVATED)) {
      // The EN is enabled but non-operational.
      if (statusSet.contains(ExposureNotificationStatus.LOW_STORAGE)) {
        return ExposureNotificationState.STORAGE_LOW;
      } else if (statusSet.contains(ExposureNotificationStatus.LOCATION_DISABLED)
          && (statusSet.contains(ExposureNotificationStatus.BLUETOOTH_DISABLED)
          || statusSet.contains(ExposureNotificationStatus.BLUETOOTH_SUPPORT_UNKNOWN))) {
        return ExposureNotificationState.PAUSED_LOCATION_BLE;
      } else if (statusSet.contains(ExposureNotificationStatus.BLUETOOTH_DISABLED)
          || statusSet.contains(ExposureNotificationStatus.BLUETOOTH_SUPPORT_UNKNOWN)) {
        return ExposureNotificationState.PAUSED_BLE;
      } else if (statusSet.contains(ExposureNotificationStatus.LOCATION_DISABLED)) {
        return ExposureNotificationState.PAUSED_LOCATION;
      }
    }

    // We should not get here but in case for some reason we do, return the DISABLED state.
    return ExposureNotificationState.DISABLED;
  }

  /**
   * Helper methods to make it easy for activities and fragments to add opt-in resolution handling.
   * <p>
   * Instead of starting resolution handling with apiException.startResolutionForResult and
   * listening for results in an Activity with onActivityResult, these methods create an AndroidX
   * ActivityResultLauncher and attach it to a fragment/activity via registerForActivityResult.
   */
  public void registerResolutionForActivityResult(AppCompatActivity activity) {
    ActivityResultLauncher<IntentSenderRequest> resolutionActivityResultLauncher =
        activity.registerForActivityResult(
            new StartIntentSenderForResult(), this::onResolutionComplete);

    getResolutionRequiredLiveEvent()
        .observe(
            activity,
            apiException -> startResolution(apiException, resolutionActivityResultLauncher));
  }

  public void registerResolutionForActivityResult(Fragment fragment) {
    ActivityResultLauncher<IntentSenderRequest> resolutionActivityResultLauncher =
        fragment.registerForActivityResult(
            new StartIntentSenderForResult(), this::onResolutionComplete);

    getResolutionRequiredLiveEvent()
        .observe(
            fragment,
            apiException -> startResolution(apiException, resolutionActivityResultLauncher));
  }

  public void onResolutionComplete(ActivityResult activityResult) {
    logcat.d("onResolutionComplete");
    if (activityResult.getResultCode() == Activity.RESULT_OK) {
      startResolutionResultOk();
    } else {
      startResolutionResultNotOk();
    }
  }

  private void startResolution(ApiException apiException,
      ActivityResultLauncher<IntentSenderRequest> resolutionActivityResultLauncher) {
    logcat.d("startResolutionForResult");
    PendingIntent resolution = apiException.getStatus().getResolution();
    if (resolution != null) {
      IntentSenderRequest intentSenderRequest =
          new IntentSenderRequest.Builder(resolution).build();
      resolutionActivityResultLauncher.launch(intentSenderRequest);
    }
  }

  /**
   * Calls start on the Exposure Notifications API.
   */
  public void startExposureNotifications() {
    inFlightLiveData.setValue(true);
    exposureNotificationClientWrapper
        .start()
        .addOnSuccessListener(
            unused -> {
              maybeRefreshStatus(true);
              enEnabledLiveData.setValue(true);
              enEnabledLiveDataNoCache.setValue(true);
              inFlightLiveData.setValue(false);
              refreshState();
            })
        .addOnFailureListener(
            exception -> {
              if (!(exception instanceof ApiException)) {
                inFlightLiveData.setValue(false);
                apiErrorLiveEvent.call();
                return;
              }
              ApiException apiException = (ApiException) exception;
              if (apiException.getStatusCode()
                  == ExposureNotificationStatusCodes.RESOLUTION_REQUIRED) {
                if (inFlightResolutionLiveData.getValue()) {
                  logcat.e("Error, has in flight resolution", exception);
                } else {
                  inFlightResolutionLiveData.setValue(true);
                  resolutionRequiredLiveEvent.postValue(apiException);
                }
              } else {
                logcat.w("No RESOLUTION_REQUIRED in result", apiException);
                int connectionResult = ConnectionResult.UNKNOWN;
                if (apiException.getStatus() != null
                    && apiException.getStatus().getConnectionResult() != null) {
                  connectionResult = apiException.getStatus().getConnectionResult().getErrorCode();
                }
                switch (connectionResult) {
                  case ConnectionResult.SERVICE_DISABLED:
                  case ConnectionResult.SERVICE_INVALID:
                  case ConnectionResult.SERVICE_MISSING:
                  case ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED:
                    apiUnavailableLiveEvent.call();
                    inFlightLiveData.setValue(false);
                    break;
                  default:
                    apiErrorLiveEvent.call();
                    inFlightLiveData.setValue(false);
                    break;
                }
              }
            })
        .addOnCanceledListener(() -> inFlightLiveData.setValue(false));
  }

  /**
   * Handles {@value android.app.Activity#RESULT_OK} for a resolution. User accepted opt-in.
   */
  public void startResolutionResultOk() {
    inFlightResolutionLiveData.setValue(false);
    exposureNotificationClientWrapper
        .start()
        .addOnSuccessListener(
            unused -> {
              maybeRefreshStatus(true);
              enEnabledLiveData.setValue(true);
              enEnabledLiveDataNoCache.setValue(true);
              inFlightLiveData.setValue(false);
              refreshState();
            })
        .addOnFailureListener(
            exception -> {
              // We don't need to handle GMSCore unavailable here because if we get to resolution
              // RESULT_OK this means the API must be available. Instead send generic error event.
              apiErrorLiveEvent.call();
              inFlightLiveData.setValue(false);
            })
        .addOnCanceledListener(() -> inFlightLiveData.setValue(false));
  }

  /**
   * Handles not {@value android.app.Activity#RESULT_OK} for a resolution. User rejected opt-in.
   */
  public void startResolutionResultNotOk() {
    inFlightResolutionLiveData.setValue(false);
    inFlightLiveData.setValue(false);
  }

  /**
   * Calls stop on the Exposure Notifications API but only if Exposure Notifications is enabled.
   */
  public void stopExposureNotifications() {
    inFlightLiveData.setValue(true);
    exposureNotificationClientWrapper
        .isEnabled()
        .continueWithTask(
            isEnabled -> {
              if (isEnabled.getResult()) {
                return exposureNotificationClientWrapper.stop();
              } else {
                // Even if En is disabled, call stop to correctly gather STOP metrics.
                // This call will fail with an exception, so we ignore the result.
                return exposureNotificationClientWrapper.stop()
                    .continueWithTask(stop -> Tasks.forResult(null));
              }
            })
        .addOnSuccessListener(
            result -> {
              refreshState();
              inFlightLiveData.setValue(false);
              enStoppedLiveEvent.postValue(true);
            })
        .addOnFailureListener(exception -> {
          inFlightLiveData.setValue(false);
          enStoppedLiveEvent.postValue(false);
        })
        .addOnCanceledListener(() -> {
          inFlightLiveData.setValue(false);
          enStoppedLiveEvent.postValue(false);
        });
  }

  public void updateLastExposureNotificationLastClickedTime() {
    // We assume it clicked on the last notification received.
    int classificationIndex = exposureNotificationSharedPreferences
        .getExposureNotificationLastShownClassification();
    exposureNotificationSharedPreferences
        .setExposureNotificationLastInteraction(clock.now(), NotificationInteraction.CLICKED,
            classificationIndex);
  }

  public void logUiInteraction(EventType event) {
    logger.logUiInteraction(event);
  }

  public SingleLiveEvent<Boolean> getEnStoppedLiveEvent() {
    return enStoppedLiveEvent;
  }

  public SingleLiveEvent<DiagnosisEntity> getLastNotSharedDiagnosisLiveEvent() {
    return lastNotSharedDiagnosisLiveEvent;
  }

  /**
   * Attempts at retrieving last non-shared diagnosis.
   * <p>
   * If there is no last non-shared diagnosis available, updates the LiveData for that last
   * non-shared diagnosis with an empty diagnosis object.
   */
  public void getLastNotSharedDiagnosisIfAny() {
    Futures.addCallback(
        diagnosisRepository.maybeGetLastNotSharedDiagnosisAsync(),
        new FutureCallback<DiagnosisEntity>() {
          @Override
          public void onSuccess(DiagnosisEntity diagnosis) {
            lastNotSharedDiagnosisLiveEvent.postValue(
                diagnosis == null ? EMPTY_DIAGNOSIS : diagnosis);
          }

          @Override
          public void onFailure(@NonNull Throwable t) {
            logcat.w("Failed to retrieve last not shared diagnosis", t);
          }
        },
        lightweightExecutor);
  }

  /**
   * Checks if the given state blocks user from starting or resuming a "Share diagnosis" flow.
   *
   * @param state state of the EN.
   * @return if the given state blocks the sharing flow
   */
  public boolean isStateBlockingSharingFlow(ExposureNotificationState state) {
    return EN_STATES_BLOCKING_SHARING_FLOW.contains(state);
  }

  /**
   * Checks if a user had a possible exposure (or an exposure revocation) in the last 14 days.
   *
   * @return true if a user had an exposure (or revocation) and false otherwise
   */
  public boolean isPossibleExposurePresent() {
    ExposureClassification exposureClassification =
        exposureNotificationSharedPreferences.getExposureClassification();

    return exposureClassification.getClassificationIndex()
        != ExposureClassification.NO_EXPOSURE_CLASSIFICATION_INDEX
        || exposureNotificationSharedPreferences.getIsExposureClassificationRevoked();
  }

  public void markInAppSmsInterceptNoticeSeenAsync() {
    exposureNotificationSharedPreferences.markInAppSmsNoticeSeenAsync();
  }

}
