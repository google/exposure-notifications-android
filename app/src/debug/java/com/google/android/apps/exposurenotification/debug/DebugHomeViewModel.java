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
import android.content.Intent;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import com.google.android.apps.exposurenotification.common.AppExecutors;
import com.google.android.apps.exposurenotification.common.SingleLiveEvent;
import com.google.android.apps.exposurenotification.nearby.ExposureNotificationBroadcastReceiver;
import com.google.android.apps.exposurenotification.nearby.ExposureNotificationClientWrapper;
import com.google.android.apps.exposurenotification.nearby.ProvideDiagnosisKeysWorker;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences.NetworkMode;
import com.google.android.apps.exposurenotification.storage.ExposureRepository;
import com.google.android.apps.exposurenotification.storage.TokenEntity;
import com.google.android.apps.exposurenotification.storage.TokenRepository;
import com.google.android.gms.nearby.exposurenotification.ExposureNotificationClient;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import java.util.List;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

/** View model for the {@link DebugHomeFragment}. */
public class DebugHomeViewModel extends AndroidViewModel {

  private static final String TAG = "DebugViewModel";

  private static SingleLiveEvent<String> snackbarLiveEvent = new SingleLiveEvent<>();
  private static MutableLiveData<NetworkMode> networkModeLiveData = new MutableLiveData<>();

  private final TokenRepository tokenRepository;
  private final ExposureRepository exposureRepository;
  private final ExposureNotificationSharedPreferences exposureNotificationSharedPreferences;

  public DebugHomeViewModel(@NonNull Application application) {
    super(application);
    tokenRepository = new TokenRepository(application);
    exposureRepository = new ExposureRepository(application);
    exposureNotificationSharedPreferences = new ExposureNotificationSharedPreferences(application);
  }

  public SingleLiveEvent<String> getSnackbarSingleLiveEvent() {
    return snackbarLiveEvent;
  }

  public LiveData<NetworkMode> getNetworkModeLiveData() {
    return networkModeLiveData;
  }

  public NetworkMode getNetworkMode(NetworkMode defaultMode) {
    NetworkMode networkMode = exposureNotificationSharedPreferences.getNetworkMode(defaultMode);
    networkModeLiveData.setValue(networkMode);
    return networkMode;
  }

  public void setNetworkMode(NetworkMode networkMode) {
    exposureNotificationSharedPreferences.setNetworkMode(networkMode);
    networkModeLiveData.setValue(networkMode);
  }

  /** Triggers a one off provide keys job. */
  public void provideKeys() {
    WorkManager workManager = WorkManager.getInstance(getApplication());
    workManager.enqueue(new OneTimeWorkRequest.Builder(ProvideDiagnosisKeysWorker.class).build());
  }
}
