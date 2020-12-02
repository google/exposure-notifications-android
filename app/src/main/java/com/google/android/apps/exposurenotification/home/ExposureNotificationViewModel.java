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

import android.bluetooth.BluetoothAdapter;
import android.location.LocationManager;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.StatFs;
import android.util.Log;
import androidx.annotation.VisibleForTesting;
import androidx.core.location.LocationManagerCompat;
import androidx.hilt.lifecycle.ViewModelInject;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.google.android.apps.exposurenotification.common.SingleLiveEvent;
import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.android.apps.exposurenotification.logging.AnalyticsLogger;
import com.google.android.apps.exposurenotification.nearby.ExposureNotificationClientWrapper;
import com.google.android.apps.exposurenotification.nearby.PackageConfigurationHelper;
import com.google.android.apps.exposurenotification.proto.UiInteraction.EventType;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences.NotificationInteraction;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences.OnboardingStatus;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.nearby.exposurenotification.ExposureNotificationStatusCodes;

/**
 * View model for the {@link ExposureNotificationActivity} and fragments.
 */
public class ExposureNotificationViewModel extends ViewModel {

  private static final String TAG = "ExposureNotificationVM";

  @VisibleForTesting
  static final long MINIMUM_FREE_STORAGE_REQUIRED_BYTES = 1024L * 1024L * 100L;

  private final MutableLiveData<ExposureNotificationState> stateLiveData;
  private final MutableLiveData<Boolean> inFlightLiveData = new MutableLiveData<>(false);
  private final MutableLiveData<Boolean> inFlightResolutionLiveData = new MutableLiveData<>(false);
  private final MutableLiveData<Boolean> enEnabledLiveData;
  private final ExposureNotificationSharedPreferences exposureNotificationSharedPreferences;

  private final SingleLiveEvent<Void> apiErrorLiveEvent = new SingleLiveEvent<>();
  private final SingleLiveEvent<ApiException> resolutionRequiredLiveEvent = new SingleLiveEvent<>();

  private final ExposureNotificationClientWrapper exposureNotificationClientWrapper;
  private final LocationManager locationManager;
  private final StatFs filesDirStat;
  private final AnalyticsLogger logger;
  private final PackageConfigurationHelper packageConfigurationHelper;
  private final Clock clock;

  private boolean inFlightIsEnabled = false;

  public enum ExposureNotificationState {
    DISABLED,
    ENABLED,
    PAUSED_BLE,
    PAUSED_LOCATION,
    PAUSED_LOCATION_BLE,
    STORAGE_LOW
  }

  @ViewModelInject
  public ExposureNotificationViewModel(
      ExposureNotificationSharedPreferences exposureNotificationSharedPreferences,
      ExposureNotificationClientWrapper exposureNotificationClientWrapper,
      LocationManager locationManager,
      StatFs statFs,
      AnalyticsLogger logger,
      PackageConfigurationHelper packageConfigurationHelper,
      Clock clock) {
    this.exposureNotificationSharedPreferences = exposureNotificationSharedPreferences;
    this.exposureNotificationClientWrapper = exposureNotificationClientWrapper;
    this.locationManager = locationManager;
    this.filesDirStat = statFs;
    this.logger = logger;
    this.packageConfigurationHelper = packageConfigurationHelper;
    this.clock = clock;

    boolean isEnabled = exposureNotificationSharedPreferences.getIsEnabledCache();
    enEnabledLiveData = new MutableLiveData<>(isEnabled);
    stateLiveData = new MutableLiveData<>(getStateForIsEnabled(isEnabled));
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
   * Returns whether en_module is on/off, irrespective of its functional state. Should be used if
   * there is a EN on/off button.
   */
  public LiveData<Boolean> getEnEnabledLiveData() {
    return enEnabledLiveData;
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

  public OnboardingStatus getOnboardedState() {
    return exposureNotificationSharedPreferences.getOnboardedState();
  }

  /**
   * Refresh isEnabled state and getExposureWindows from Exposure Notification API.
   */
  public void refreshState() {
    maybeRefreshIsEnabled();
    maybeRefreshAnalytics();
  }

  private synchronized void maybeRefreshIsEnabled() {
    if (inFlightIsEnabled) {
      return;
    }
    inFlightIsEnabled = true;
    exposureNotificationClientWrapper.isEnabled()
        .addOnSuccessListener(
            (isEnabled) -> {
              stateLiveData.setValue(getStateForIsEnabled(isEnabled));
              exposureNotificationSharedPreferences.setIsEnabledCache(isEnabled);
              inFlightIsEnabled = false;
            })
        .addOnCanceledListener(() -> {
          Log.i(TAG, "Call isEnabled is canceled");
          inFlightIsEnabled = false;
        })
        .addOnFailureListener((t) -> {
          inFlightIsEnabled = false;
          stateLiveData.setValue(getStateForIsEnabled(false));
          exposureNotificationSharedPreferences.setIsEnabledCache(false);
        });
  }

  private synchronized void maybeRefreshAnalytics() {
    exposureNotificationClientWrapper.getPackageConfiguration()
        .addOnSuccessListener(
            packageConfigurationHelper::maybeUpdateAnalyticsState)
        .addOnCanceledListener(() -> Log.i(TAG, "Call getPackageConfiguration is canceled"))
        .addOnFailureListener((t) -> Log.e(TAG, "Error calling getPackageConfiguration", t));
  }

  private ExposureNotificationState getStateForIsEnabled(boolean isEnabled) {
    enEnabledLiveData.setValue(isEnabled);
    if (!isEnabled) {
      /*
       * Show low-storage errors before telling the user that EN is disabled,
       * as EN can only be (re)enabled with enough storage space available.
       */
      if (freeStorageSpaceRequired()) {
        return ExposureNotificationState.STORAGE_LOW;
      }

      return ExposureNotificationState.DISABLED;
    }

    /*
     * Go though the possible combinations of location/ble enabled states and set the
     * ExposureNotificationState accordingly
     */
    boolean isLocationEnableRequired = isLocationEnableRequired();
    boolean isBluetoothEnableRequired = isBluetoothEnableRequired();

    if (isBluetoothEnableRequired && isLocationEnableRequired) {
      return ExposureNotificationState.PAUSED_LOCATION_BLE;
    }

    if (isBluetoothEnableRequired) {
      return ExposureNotificationState.PAUSED_BLE;
    }

    if (isLocationEnableRequired) {
      return ExposureNotificationState.PAUSED_LOCATION;
    }

    /*
     * If everything else works, finally make sure that there is enough storage space
     */
    if (freeStorageSpaceRequired()) {
      return ExposureNotificationState.STORAGE_LOW;
    }

    return ExposureNotificationState.ENABLED;
  }

  /**
   * Check if the user needs more free storage space
   */
  private boolean freeStorageSpaceRequired() {
    /*
     * DiagnosisKeyDownloader works with the App's private files dir, so check available space
     * there
     */
    long freeStorage = filesDirStat.getAvailableBytes();
    return (freeStorage <= MINIMUM_FREE_STORAGE_REQUIRED_BYTES);
  }

  /**
   * Check if the user needs to enable Bluetooth
   */
  private boolean isBluetoothEnableRequired() {
    BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    return (mBluetoothAdapter != null && !mBluetoothAdapter.isEnabled());
  }

  /**
   * When it comes to Location and BLE, there are the following conditions: - Location on is only
   * necessary to use bluetooth for Android M+. - Starting with Android S, there may be support for
   * locationless BLE scanning => We only go into an error state if these conditions require us to
   * have location on, but it is not activated on device.
   */
  private boolean isLocationEnableRequired() {
    return (!exposureNotificationClientWrapper.deviceSupportsLocationlessScanning()
        && VERSION.SDK_INT >= VERSION_CODES.M
        && locationManager != null && !LocationManagerCompat.isLocationEnabled(locationManager));
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
              stateLiveData.setValue(getStateForIsEnabled(true));
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
    exposureNotificationClientWrapper
        .start()
        .addOnSuccessListener(
            unused -> {
              stateLiveData.setValue(getStateForIsEnabled(true));
              inFlightLiveData.setValue(false);
              refreshState();
            })
        .addOnFailureListener(
            exception -> {
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
    exposureNotificationClientWrapper
        .stop()
        .addOnSuccessListener(
            unused -> {
              refreshState();
              inFlightLiveData.setValue(false);
            })
        .addOnFailureListener(
            exception -> inFlightLiveData.setValue(false))
        .addOnCanceledListener(() -> inFlightLiveData.setValue(false));
  }

  public void updateLastExposureNotificationLastClickedTime() {
    exposureNotificationSharedPreferences.setExposureNotificationLastInteraction(clock.now(), NotificationInteraction.CLICKED);
  }

  public void logUiInteraction(EventType event) {
    logger.logUiInteraction(event);
  }
}
