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
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.location.LocationManager;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.StatFs;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.core.location.LocationManagerCompat;
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

  private static final long MINIMUM_FREE_STORAGE_REQUIRED_BYTES = 1024L * 1024L * 100L;

  private final MutableLiveData<ExposureNotificationState> stateLiveData;
  private final MutableLiveData<Boolean> inFlightLiveData = new MutableLiveData<>(false);
  private final MutableLiveData<Boolean> inFlightResolutionLiveData = new MutableLiveData<>(false);
  private final ExposureNotificationSharedPreferences exposureNotificationSharedPreferences;

  private final SingleLiveEvent<Void> apiErrorLiveEvent = new SingleLiveEvent<>();
  private final SingleLiveEvent<ApiException> resolutionRequiredLiveEvent = new SingleLiveEvent<>();

  private final ExposureNotificationClientWrapper wrapper;

  private boolean inFlightIsEnabled = false;

  public enum ExposureNotificationState {
    DISABLED,
    ENABLED,
    PAUSED_BLE,
    PAUSED_LOCATION,
    STORAGE_LOW
  }

  public ExposureNotificationViewModel(@NonNull Application application) {
    super(application);
    exposureNotificationSharedPreferences = new ExposureNotificationSharedPreferences(application);
    wrapper = ExposureNotificationClientWrapper.get(getApplication());
    stateLiveData = new MutableLiveData<>(
        getStateForIsEnabled(exposureNotificationSharedPreferences.getIsEnabledCache()));
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
   * Refresh isEnabled state and getExposureWindows from Exposure Notification API.
   */
  public void refreshState() {
    maybeRefreshIsEnabled(wrapper);
  }

  private synchronized void maybeRefreshIsEnabled(ExposureNotificationClientWrapper wrapper) {
    if (inFlightIsEnabled) {
      return;
    }
    inFlightIsEnabled = true;
    wrapper.isEnabled()
        .addOnSuccessListener(
            (isEnabled) -> {
              stateLiveData.setValue(getStateForIsEnabled(isEnabled));
              exposureNotificationSharedPreferences.setIsEnabledCache(isEnabled);
              if (isEnabled) {
                // if we're seeing it enabled then permission has been granted
                noteOnboardingCompleted();
                schedulePeriodicJobs();
              }
              inFlightIsEnabled = false;
            })
        .addOnCanceledListener(() -> inFlightIsEnabled = false)
        .addOnFailureListener((t) -> {
          Log.e(TAG, "Failed to call isEnabled", t);
          inFlightIsEnabled = false;
          stateLiveData.setValue(getStateForIsEnabled(false));
          exposureNotificationSharedPreferences.setIsEnabledCache(false);
        });
  }

  private ExposureNotificationState getStateForIsEnabled(boolean isEnabled) {
    if (!isEnabled) {
      return ExposureNotificationState.DISABLED;
    }

    BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    if (mBluetoothAdapter != null && !mBluetoothAdapter.isEnabled()) {
      return ExposureNotificationState.PAUSED_BLE;
    }

    if (isLocationEnableRequired(getApplication())) {
      return ExposureNotificationState.PAUSED_LOCATION;
    }

    /* DiagnosisKeyDownloader works with the App's private files dir, so check available space
     * there */
    StatFs filesDirStat = new StatFs(getApplication().getFilesDir().toString());
    long freeStorage = filesDirStat.getAvailableBytes();
    if (freeStorage <= MINIMUM_FREE_STORAGE_REQUIRED_BYTES) {
      return ExposureNotificationState.STORAGE_LOW;
    }

    return ExposureNotificationState.ENABLED;
  }

  /**
   * When it comes to Location and BLE, there are the following conditions:
   * - Location on is only necessary to use bluetooth for Android M+.
   * - Starting with Android S, there may be support for locationless BLE scanning
   * => We only go into an error state if these conditions require us to have location on, but
   *    it is not activated on device.
   */
  private boolean isLocationEnableRequired(Context context) {
    LocationManager locationManager =
        (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);

    return (!wrapper.deviceSupportsLocationlessScanning()
        && VERSION.SDK_INT >= VERSION_CODES.M
        && locationManager != null && !LocationManagerCompat.isLocationEnabled(locationManager));
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
      public void onFailure(@NonNull Throwable t) {
        Log.e(TAG, "Failed to schedule periodic WorkManager jobs.", t);
      }
    }, AppExecutors.getLightweightExecutor());
  }

  /**
   * Calls start on the Exposure Notifications API.
   */
  public void startExposureNotifications() {
    inFlightLiveData.setValue(true);
    wrapper
        .start()
        .addOnSuccessListener(
            unused -> {
              stateLiveData.setValue(getStateForIsEnabled(true));
              inFlightLiveData.setValue(false);
              refreshState();
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
    wrapper
        .start()
        .addOnSuccessListener(
            unused -> {
              stateLiveData.setValue(getStateForIsEnabled(true));
              inFlightLiveData.setValue(false);
              refreshState();
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
    wrapper
        .stop()
        .addOnSuccessListener(
            unused -> {
              refreshState();
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
