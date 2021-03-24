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

package com.google.android.apps.exposurenotification.debug;

import android.util.Log;
import androidx.hilt.lifecycle.ViewModelInject;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.google.android.apps.exposurenotification.common.SingleLiveEvent;
import com.google.android.apps.exposurenotification.nearby.ExposureNotificationClientWrapper;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.nearby.exposurenotification.ExposureNotificationStatusCodes;
import com.google.android.gms.nearby.exposurenotification.TemporaryExposureKey;
import com.google.android.gms.tasks.Tasks;
import java.util.ArrayList;
import java.util.List;

/**
 * View model for {@link KeysMatchingFragment}.
 */
public class KeysMatchingViewModel extends ViewModel {

  private static final String TAG = "ViewKeysViewModel";

  private final MutableLiveData<List<TemporaryExposureKey>> temporaryExposureKeysLiveData;

  private final MutableLiveData<InFlightResolution> inFlightResolutionLiveData
      = new MutableLiveData<>(new InFlightResolution(false));

  private final SingleLiveEvent<Void> apiDisabledLiveEvent = new SingleLiveEvent<>();
  private final SingleLiveEvent<Void> apiErrorLiveEvent = new SingleLiveEvent<>();
  private final SingleLiveEvent<ResolutionRequiredEvent> resolutionRequiredLiveEvent
      = new SingleLiveEvent<>();
  private final SingleLiveEvent<Void> waitForKeyBroadcastsEvent = new SingleLiveEvent<>();

  private final ExposureNotificationClientWrapper exposureNotificationClientWrapper;

  @ViewModelInject
  public KeysMatchingViewModel(
      ExposureNotificationClientWrapper exposureNotificationClientWrapper) {
    this.exposureNotificationClientWrapper = exposureNotificationClientWrapper;

    temporaryExposureKeysLiveData = new MutableLiveData<>(new ArrayList<>());
  }

  /**
   * An event that requests a resolution with the given {@link ApiException}.
   */
  public SingleLiveEvent<ResolutionRequiredEvent> getResolutionRequiredLiveEvent() {
    return resolutionRequiredLiveEvent;
  }

  /**
   * An event that triggers when the API is disabled.
   */
  public SingleLiveEvent<Void> getApiDisabledLiveEvent() {
    return apiDisabledLiveEvent;
  }

  /**
   * An event that triggers when there is an error in the API.
   */
  public SingleLiveEvent<Void> getApiErrorLiveEvent() {
    return apiErrorLiveEvent;
  }

  /**
   * An event that triggers when keys will be broadcast to the app.
   */
  public SingleLiveEvent<Void> getWaitForKeyBroadcastsEvent() {
    return waitForKeyBroadcastsEvent;
  }

  /**
   * The {@link LiveData} representing if there is an in-flight resolution.
   */
  public LiveData<InFlightResolution> getInFlightResolutionLiveData() {
    return inFlightResolutionLiveData;
  }

  /**
   * The {@link LiveData} representing the {@link List} of {@link TemporaryExposureKey}.
   */
  public LiveData<List<TemporaryExposureKey>> getTemporaryExposureKeysLiveData() {
    return temporaryExposureKeysLiveData;
  }

  /**
   * Requests updating the {@link TemporaryExposureKey} from GMSCore API.
   */
  public void updateTemporaryExposureKeys() {
    exposureNotificationClientWrapper
        .isEnabled()
        .continueWithTask(
            isEnabled -> {
              if (isEnabled.getResult()) {
                return exposureNotificationClientWrapper.getTemporaryExposureKeyHistory();
              } else {
                apiDisabledLiveEvent.call();
                return Tasks.forResult(new ArrayList<>());
              }
            })
        .addOnSuccessListener(
            temporaryExposureKeys -> temporaryExposureKeysLiveData.setValue(temporaryExposureKeys))
        .addOnFailureListener(
            exception -> {
              if (!(exception instanceof ApiException)) {
                Log.e(TAG, "Unknown error when attempting to start API", exception);
                apiErrorLiveEvent.call();
                return;
              }
              ApiException apiException = (ApiException) exception;
              if (apiException.getStatusCode()
                  == ExposureNotificationStatusCodes.RESOLUTION_REQUIRED) {
                if (inFlightResolutionLiveData.getValue().hasInFlightResolution()) {
                  Log.e(TAG, "Error, has in flight resolution", exception);
                } else {
                  inFlightResolutionLiveData.setValue(
                      new InFlightResolution(
                          true, ResolutionType.GET_TEMPORARY_EXPOSURE_KEY_HISTORY));
                  resolutionRequiredLiveEvent.postValue(
                      new ResolutionRequiredEvent(
                          apiException, ResolutionType.GET_TEMPORARY_EXPOSURE_KEY_HISTORY));
                }
              } else {
                Log.w(TAG, "No RESOLUTION_REQUIRED in result", apiException);
                apiErrorLiveEvent.call();
              }
            });
  }

  public void requestPreAuthorizationOfTemporaryExposureKeyHistory() {
    exposureNotificationClientWrapper
        .isEnabled()
        .continueWithTask(
            isEnabled -> {
              if (isEnabled.getResult()) {
                return
                    exposureNotificationClientWrapper
                        .requestPreAuthorizedTemporaryExposureKeyHistory();
              } else {
                apiDisabledLiveEvent.call();
                return Tasks.forResult(null);
              }
            })
        .addOnSuccessListener(
            result -> inFlightResolutionLiveData.setValue(new InFlightResolution(false)))
        .addOnFailureListener(
            exception -> {
              if (!(exception instanceof ApiException)) {
                Log.e(TAG, "Unknown error when attempting to start API", exception);
                apiErrorLiveEvent.call();
                return;
              }
              ApiException apiException = (ApiException) exception;
              if (apiException.getStatusCode()
                  == ExposureNotificationStatusCodes.RESOLUTION_REQUIRED) {
                if (inFlightResolutionLiveData.getValue().hasInFlightResolution()) {
                  Log.e(TAG, "Error, has in flight resolution", exception);
                } else {
                  inFlightResolutionLiveData.setValue(
                      new InFlightResolution(
                          true,
                          ResolutionType.PREAUTHORIZE_TEMPORARY_EXPOSURE_KEY_RELEASE));
                  resolutionRequiredLiveEvent.postValue(
                      new ResolutionRequiredEvent(
                          apiException,
                          ResolutionType.PREAUTHORIZE_TEMPORARY_EXPOSURE_KEY_RELEASE));
                }
              } else {
                Log.w(TAG, "No RESOLUTION_REQUIRED in result", apiException);
                apiErrorLiveEvent.call();
              }
            });
  }

  public void requestPreAuthorizedReleaseOfTemporaryExposureKeys() {
    exposureNotificationClientWrapper
        .isEnabled()
        .continueWithTask(
            isEnabled -> {
              if (isEnabled.getResult()) {
                return
                    exposureNotificationClientWrapper
                        .requestPreAuthorizedTemporaryExposureKeyRelease();
              } else {
                apiDisabledLiveEvent.call();
                return Tasks.forResult(null);
              }
            })
        .addOnSuccessListener(
            result -> {
              waitForKeyBroadcastsEvent.call();
              inFlightResolutionLiveData.setValue(
                  new InFlightResolution(
                      true,
                      ResolutionType.GET_PREAUTHORIZED_TEMPORARY_EXPOSURE_KEY_HISTORY));
            })
        .addOnFailureListener(
            exception -> {
              Log.e(TAG, "Unknown error when attempting to start API", exception);
              apiErrorLiveEvent.call();
            });
  }

  public void handleTemporaryExposureKeys(List<TemporaryExposureKey> temporaryExposureKeys) {
    inFlightResolutionLiveData.setValue(new InFlightResolution(false));
    temporaryExposureKeysLiveData.setValue(temporaryExposureKeys);
  }

  /**
   * Handles {@value android.app.Activity#RESULT_OK} for a resolution. User chose to share keys.
   */
  public void startResolutionResultGetHistoryOk() {
    inFlightResolutionLiveData.setValue(new InFlightResolution(false));
    exposureNotificationClientWrapper
        .getTemporaryExposureKeyHistory()
        .addOnSuccessListener(this::handleTemporaryExposureKeys)
        .addOnFailureListener(
            exception -> {
              Log.e(TAG, "Error handling resolution", exception);
              apiErrorLiveEvent.call();
            });
  }

  public void startResolutionResultPreauthorizationOk() {
    inFlightResolutionLiveData.setValue(new InFlightResolution(false));
  }

  /**
   * Handles not {@value android.app.Activity#RESULT_OK} for a resolution. User chose not to share
   * keys.
   */
  public void startResolutionResultNotOk() {
    inFlightResolutionLiveData.setValue(new InFlightResolution(false));
  }

  public enum ResolutionType {
    UNKNOWN,
    GET_TEMPORARY_EXPOSURE_KEY_HISTORY,
    PREAUTHORIZE_TEMPORARY_EXPOSURE_KEY_RELEASE,
    GET_PREAUTHORIZED_TEMPORARY_EXPOSURE_KEY_HISTORY,
  }

  public static class InFlightResolution {

    private final boolean hasInFlightResolution;
    private final ResolutionType resolutionType;

    private InFlightResolution(boolean hasInFlightResolution) {
      this(hasInFlightResolution, ResolutionType.UNKNOWN);
    }

    private InFlightResolution(
        boolean hasInFlightResolution, ResolutionType resolutionType) {
      this.hasInFlightResolution = hasInFlightResolution;
      this.resolutionType = resolutionType;
    }

    public boolean hasInFlightResolution() {
      return hasInFlightResolution;
    }

    public ResolutionType getResolutionType() {
      return resolutionType;
    }
  }

  public static class ResolutionRequiredEvent {

    private final ApiException exception;
    private final ResolutionType resolutionType;

    private ResolutionRequiredEvent(ApiException exception, ResolutionType resolutionType) {
      this.exception = exception;
      this.resolutionType = resolutionType;
    }

    public ApiException getException() {
      return exception;
    }

    public ResolutionType getResolutionType() {
      return resolutionType;
    }
  }
}
