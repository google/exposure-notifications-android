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
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import com.google.android.apps.exposurenotification.common.AppExecutors;
import com.google.android.apps.exposurenotification.common.SingleLiveEvent;
import com.google.android.apps.exposurenotification.debug.VerificationCodeCreator.VerificationCode;
import com.google.android.apps.exposurenotification.nearby.ProvideDiagnosisKeysWorker;
import com.google.android.apps.exposurenotification.network.RequestQueueSingleton;
import com.google.android.apps.exposurenotification.network.RequestQueueWrapper;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences.NetworkMode;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import java.util.List;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

/**
 * View model for the {@link DebugHomeFragment}.
 */
public class DebugHomeViewModel extends AndroidViewModel {

  private static final String TAG = "DebugViewModel";

  private static SingleLiveEvent<String> snackbarLiveEvent = new SingleLiveEvent<>();
  private static MutableLiveData<NetworkMode> keySharingNetworkModeLiveData =
      new MutableLiveData<>(NetworkMode.DISABLED);
  private static MutableLiveData<NetworkMode> verificationNetworkModeLiveData =
      new MutableLiveData<>(NetworkMode.DISABLED);
  private final LiveData<List<WorkInfo>> provideDiagnosisKeysWorkLiveData;
  private static MutableLiveData<VerificationCode> verificationCodeLiveData =
      new MutableLiveData<>();

  private final ExposureNotificationSharedPreferences exposureNotificationSharedPreferences;
  private final VerificationCodeCreator codeCreator;

  public DebugHomeViewModel(@NonNull Application application) {
    super(application);
    exposureNotificationSharedPreferences = new ExposureNotificationSharedPreferences(application);
    provideDiagnosisKeysWorkLiveData =
        WorkManager.getInstance(application)
            .getWorkInfosForUniqueWorkLiveData(ProvideDiagnosisKeysWorker.WORKER_NAME);
    codeCreator = new VerificationCodeCreator(
        application,
        exposureNotificationSharedPreferences,
        RequestQueueWrapper.wrapping(RequestQueueSingleton.get(application)));
  }

  public LiveData<List<WorkInfo>> getProvideDiagnosisKeysWorkLiveData() {
    return provideDiagnosisKeysWorkLiveData;
  }

  public SingleLiveEvent<String> getSnackbarSingleLiveEvent() {
    return snackbarLiveEvent;
  }

  public LiveData<NetworkMode> getKeySharingNetworkModeLiveData() {
    return keySharingNetworkModeLiveData;
  }

  public NetworkMode getKeySharingNetworkMode(NetworkMode defaultMode) {
    NetworkMode networkMode =
        exposureNotificationSharedPreferences.getKeySharingNetworkMode(defaultMode);
    keySharingNetworkModeLiveData.setValue(networkMode);
    return networkMode;
  }

  public void setKeySharingNetworkMode(NetworkMode networkMode) {
    exposureNotificationSharedPreferences.setKeySharingNetworkMode(networkMode);
    keySharingNetworkModeLiveData.setValue(networkMode);
  }

  public LiveData<NetworkMode> getVerificationNetworkModeLiveData() {
    return verificationNetworkModeLiveData;
  }

  public NetworkMode getVerificationNetworkMode(NetworkMode defaultMode) {
    NetworkMode networkMode =
        exposureNotificationSharedPreferences.getVerificationNetworkMode(defaultMode);
    verificationNetworkModeLiveData.setValue(networkMode);
    return networkMode;
  }

  public void setVerificationNetworkMode(NetworkMode networkMode) {
    exposureNotificationSharedPreferences.setVerificationNetworkMode(networkMode);
    verificationNetworkModeLiveData.setValue(networkMode);
  }

  public LiveData<VerificationCode> getVerificationCodeLiveData() {
    return verificationCodeLiveData;
  }

  public void createVerificationCode() {
    Futures.addCallback(codeCreator.create(), new FutureCallback<VerificationCode>() {
      @Override
      public void onSuccess(@NullableDecl VerificationCode result) {
        verificationCodeLiveData.postValue(result);
      }

      @Override
      public void onFailure(Throwable t) {
        Log.e(TAG, "Failed to create a verification code: " + t.getMessage(), t);
        verificationCodeLiveData.postValue(VerificationCode.EMPTY);
      }
    }, AppExecutors.getLightweightExecutor());
  }

  /**
   * Triggers a one off provide keys job.
   */
  public void provideKeys() {
    WorkManager workManager = WorkManager.getInstance(getApplication());
    workManager.enqueue(new OneTimeWorkRequest.Builder(ProvideDiagnosisKeysWorker.class).build());
  }
}
