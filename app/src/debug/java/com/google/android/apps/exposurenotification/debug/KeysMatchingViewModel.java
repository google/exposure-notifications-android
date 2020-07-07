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

import android.app.Application;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
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
public class KeysMatchingViewModel extends AndroidViewModel {

  private static final String TAG = "ViewKeysViewModel";

  private MutableLiveData<List<TemporaryExposureKey>> temporaryExposureKeysLiveData;

  private final MutableLiveData<Boolean> inFlightResolutionLiveData = new MutableLiveData<>(false);

  private final SingleLiveEvent<Void> apiDisabledLiveEvent = new SingleLiveEvent<>();
  private final SingleLiveEvent<Void> apiErrorLiveEvent = new SingleLiveEvent<>();
  private final SingleLiveEvent<ApiException> resolutionRequiredLiveEvent = new SingleLiveEvent<>();

  public KeysMatchingViewModel(@NonNull Application application) {
    super(application);
    temporaryExposureKeysLiveData = new MutableLiveData<>(new ArrayList<>());
  }

  /**
   * An event that requests a resolution with the given {@link ApiException}.
   */
  public SingleLiveEvent<ApiException> getResolutionRequiredLiveEvent() {
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
   * The {@link LiveData} representing if there is an in-flight resolution.
   */
  public LiveData<Boolean> getInFlightResolutionLiveData() {
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
    ExposureNotificationClientWrapper clientWrapper =
        ExposureNotificationClientWrapper.get(getApplication());
    clientWrapper
        .isEnabled()
        .continueWithTask(
            isEnabled -> {
              if (isEnabled.getResult()) {
                return clientWrapper.getTemporaryExposureKeyHistory();
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
                if (inFlightResolutionLiveData.getValue()) {
                  Log.e(TAG, "Error, has in flight resolution", exception);
                } else {
                  inFlightResolutionLiveData.setValue(true);
                  resolutionRequiredLiveEvent.postValue(apiException);
                }
              } else {
                Log.w(TAG, "No RESOLUTION_REQUIRED in result", apiException);
                apiErrorLiveEvent.call();
              }
            });
  }

  /**
   * Handles {@value android.app.Activity#RESULT_OK} for a resolution. User chose to share keys.
   */
  public void startResolutionResultOk() {
    inFlightResolutionLiveData.setValue(false);
    ExposureNotificationClientWrapper.get(getApplication())
        .getTemporaryExposureKeyHistory()
        .addOnSuccessListener(temporaryExposureKeysLiveData::setValue)
        .addOnFailureListener(
            exception -> {
              Log.e(TAG, "Error handling resolution", exception);
              apiErrorLiveEvent.call();
            });
  }

  /**
   * Handles not {@value android.app.Activity#RESULT_OK} for a resolution. User chose not to share
   * keys.
   */
  public void startResolutionResultNotOk() {
    inFlightResolutionLiveData.setValue(false);
  }

}
