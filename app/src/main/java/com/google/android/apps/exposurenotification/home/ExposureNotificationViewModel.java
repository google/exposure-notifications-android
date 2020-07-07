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

import android.app.Application;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.google.android.apps.exposurenotification.common.AppExecutors;
import com.google.android.apps.exposurenotification.common.SingleLiveEvent;
import com.google.android.apps.exposurenotification.nearby.ExposureNotificationClientWrapper;
import com.google.android.apps.exposurenotification.nearby.ProvideDiagnosisKeysWorker;
import com.google.android.apps.exposurenotification.network.UploadCoverTrafficWorker;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.nearby.exposurenotification.ExposureNotificationStatusCodes;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

/**
 * View model for the {@link ExposureNotificationActivity} and fragments.
 */
public class ExposureNotificationViewModel extends AndroidViewModel {

  private static final String TAG = "ExposureNotificationVM";

  private final MutableLiveData<Boolean> isEnabledLiveData;
  private final MutableLiveData<Boolean> inFlightLiveData = new MutableLiveData<>(false);
  private final MutableLiveData<Boolean> inFlightResolutionLiveData = new MutableLiveData<>(false);
  private final ExposureNotificationSharedPreferences exposureNotificationSharedPreferences;

  private final SingleLiveEvent<Void> apiErrorLiveEvent = new SingleLiveEvent<>();
  private final SingleLiveEvent<ApiException> resolutionRequiredLiveEvent = new SingleLiveEvent<>();

  public ExposureNotificationViewModel(@NonNull Application application) {
    super(application);
    exposureNotificationSharedPreferences = new ExposureNotificationSharedPreferences(application);
    isEnabledLiveData = new MutableLiveData<>(
        exposureNotificationSharedPreferences.getIsEnabledCache());
  }

  /**
   * A {@link LiveData} of the isEnabled state of the API.
   */
  public LiveData<Boolean> getIsEnabledLiveData() {
    return isEnabledLiveData;
  }

  /**
   * Returns whether there is an in flight API request. Should be used to disable buttons when
   * true.
   */
  public LiveData<Boolean> getInFlightLiveData() {
    return inFlightLiveData;
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
   * Refresh isEnabled state from Exposure Notification API.
   */
  public void refreshIsEnabledState() {
    ExposureNotificationClientWrapper.get(getApplication())
        .isEnabled()
        .addOnSuccessListener(
            (isEnabled) -> {
              isEnabledLiveData.setValue(isEnabled);
              exposureNotificationSharedPreferences.setIsEnabledCache(isEnabled);
              if (isEnabled) {
                // if we're seeing it enabled then permission has been granted
                noteOnboardingCompleted();
                schedulePeriodicJobs();
              }
            });
  }

  private void schedulePeriodicJobs() {
    Futures.addCallback(AppExecutors.getBackgroundExecutor().submit(() -> {
      Log.i(TAG, "Scheduling post-enable periodic WorkManager jobs...");
      // This worker schedules some random fake key upload traffic, to help with privacy.
      UploadCoverTrafficWorker.schedule(getApplication());
      // This worker schedules daily providing of keys.
      ProvideDiagnosisKeysWorker.schedule(getApplication());
      return null;
    }), new FutureCallback<Void>() {
      @Override
      public void onSuccess(@NullableDecl Void result) {
        Log.i(TAG, "Scheduled periodic WorkManager jobs.");
      }

      @Override
      public void onFailure(Throwable t) {
        Log.e(TAG, "Failed to schedule periodic WorkManager jobs.", t);
      }
    }, AppExecutors.getLightweightExecutor());
  }

  /**
   * Calls start on the Exposure Notifications API.
   */
  public void startExposureNotifications() {
    inFlightLiveData.setValue(true);
    ExposureNotificationClientWrapper.get(getApplication())
        .start()
        .addOnSuccessListener(
            unused -> {
              refreshIsEnabledState();
              inFlightLiveData.setValue(false);
            })
        .addOnFailureListener(
            exception -> {
              if (!(exception instanceof ApiException)) {
                Log.e(TAG, "Unknown error when attempting to start API", exception);
                inFlightLiveData.setValue(false);
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
                inFlightLiveData.setValue(false);
              }
            })
        .addOnCanceledListener(() -> inFlightLiveData.setValue(false));
  }

  /**
   * Handles {@value android.app.Activity#RESULT_OK} for a resolution. User accepted opt-in.
   */
  public void startResolutionResultOk() {
    inFlightResolutionLiveData.setValue(false);
    ExposureNotificationClientWrapper.get(getApplication())
        .start()
        .addOnSuccessListener(
            unused -> {
              refreshIsEnabledState();
              inFlightLiveData.setValue(false);
            })
        .addOnFailureListener(
            exception -> {
              Log.e(TAG, "Error handling resolution ok", exception);
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
   * Calls stop on the Exposure Notifications API.
   */
  public void stopExposureNotifications() {
    inFlightLiveData.setValue(true);
    ExposureNotificationClientWrapper.get(getApplication())
        .stop()
        .addOnSuccessListener(
            unused -> {
              refreshIsEnabledState();
              inFlightLiveData.setValue(false);
            })
        .addOnFailureListener(
            exception -> {
              Log.w(TAG, "Failed to stop", exception);
              inFlightLiveData.setValue(false);
            })
        .addOnCanceledListener(() -> inFlightLiveData.setValue(false));
  }

  /**
   * Record in SharedPreferences that the user has completed the Onboarding flow.
   */
  public void noteOnboardingCompleted() {
    exposureNotificationSharedPreferences.setOnboardedState(true);
  }
}
