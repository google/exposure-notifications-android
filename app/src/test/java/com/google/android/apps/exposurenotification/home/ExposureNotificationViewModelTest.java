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

import static com.google.android.apps.exposurenotification.home.ExposureNotificationViewModel.MINIMUM_FREE_STORAGE_REQUIRED_BYTES;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.location.LocationManager;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.StatFs;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.apps.exposurenotification.common.Qualifiers.BackgroundExecutor;
import com.google.android.apps.exposurenotification.common.Qualifiers.LightweightExecutor;
import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.android.apps.exposurenotification.common.time.RealTimeModule;
import com.google.android.apps.exposurenotification.home.ExposureNotificationViewModel.ExposureNotificationState;
import com.google.android.apps.exposurenotification.logging.AnalyticsLogger;
import com.google.android.apps.exposurenotification.nearby.ExposureNotificationClientWrapper;
import com.google.android.apps.exposurenotification.nearby.PackageConfigurationHelper;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.apps.exposurenotification.testsupport.ExposureNotificationRules;
import com.google.android.apps.exposurenotification.testsupport.FakeClock;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.nearby.exposurenotification.ExposureNotificationStatusCodes;
import com.google.android.gms.nearby.exposurenotification.PackageConfiguration.PackageConfigurationBuilder;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.common.util.concurrent.ListeningExecutorService;
import dagger.hilt.android.testing.BindValue;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import dagger.hilt.android.testing.UninstallModules;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowBluetoothAdapter;
import org.robolectric.shadows.ShadowStatFs;

@HiltAndroidTest
@RunWith(RobolectricTestRunner.class)
@Config(application = HiltTestApplication.class)
@UninstallModules({RealTimeModule.class})
public class ExposureNotificationViewModelTest {

  @Rule
  public ExposureNotificationRules rules = ExposureNotificationRules.forTest(this).withMocks()
      .build();

  @Inject
  LocationManager locationManager;
  @Inject
  ExposureNotificationSharedPreferences exposureNotificationSharedPreferences;
  @Inject
  PackageConfigurationHelper packageConfigurationHelper;
  @Inject
  @BackgroundExecutor
  ListeningExecutorService backgroundExecutor;
  @Inject
  @LightweightExecutor
  ExecutorService lightweightExecutor;
  @BindValue
  Clock clock = new FakeClock();


  final Context context = ApplicationProvider.getApplicationContext();
  @Mock
  private ExposureNotificationClientWrapper exposureNotificationClientWrapper;
  @Mock
  private AnalyticsLogger logger;
  private ExposureNotificationViewModel exposureNotificationViewModel;
  private StatFs statsFs;
  private static final int AVAILABLE_BLOCKS =
      (int) (MINIMUM_FREE_STORAGE_REQUIRED_BYTES / ShadowStatFs.BLOCK_SIZE) + 1;
  private Task<Boolean> taskForResultTrue = Tasks.forResult(true);
  private Task<Void> taskForResultVoid = Tasks.forResult(null);

  private ShadowBluetoothAdapter shadowBluetoothAdapter;

  @Before
  public void setup() {
    rules.hilt().inject();
    shadowBluetoothAdapter = shadowOf(BluetoothAdapter.getDefaultAdapter());
    shadowBluetoothAdapter.setEnabled(true);
    statsFs = new StatFs("/");
    setStatsFs(AVAILABLE_BLOCKS);
    exposureNotificationViewModel = new ExposureNotificationViewModel(
        exposureNotificationSharedPreferences,
        exposureNotificationClientWrapper,
        locationManager,
        statsFs,
        logger,
        packageConfigurationHelper,
        clock);
    when(exposureNotificationClientWrapper.getPackageConfiguration())
        .thenReturn(Tasks.forResult(new PackageConfigurationBuilder().build()));
  }

  @Test
  public void refreshState_clientIsNotEnabled_cacheSaysApiIsNotEnabled() {
    when(exposureNotificationClientWrapper.isEnabled()).thenReturn(Tasks.forResult(false));
    exposureNotificationSharedPreferences.setIsEnabledCache(true);

    exposureNotificationViewModel.refreshState();

    verify(exposureNotificationClientWrapper).isEnabled();
    assertThat(exposureNotificationSharedPreferences.getIsEnabledCache()).isFalse();
  }

  @Test
  public void refreshState_packageConfigurationAnalyticsTrue_updatesAppAnalyticsState() {
    when(exposureNotificationClientWrapper.isEnabled()).thenReturn(Tasks.forResult(false));
    exposureNotificationSharedPreferences.setIsEnabledCache(true);
    Bundle values = new Bundle();
    values.putBoolean(PackageConfigurationHelper.APP_ANALYTICS_OPT_IN, true);
    when(exposureNotificationClientWrapper.getPackageConfiguration())
        .thenReturn(Tasks.forResult(new PackageConfigurationBuilder().setValues(values).build()));

    exposureNotificationViewModel.refreshState();

    assertThat(exposureNotificationSharedPreferences.isAppAnalyticsSet()).isTrue();
    assertThat(exposureNotificationSharedPreferences.getAppAnalyticsState()).isTrue();
  }

  @Test
  public void refreshState_clientIsEnabled_liveDataUpdated() {
    when(exposureNotificationClientWrapper.isEnabled()).thenReturn(taskForResultTrue);
    AtomicReference<ExposureNotificationState> exposureNotificationState =
        new AtomicReference<>(ExposureNotificationState.DISABLED);
    exposureNotificationViewModel.getStateLiveData().observeForever(exposureNotificationState::set);

    exposureNotificationViewModel.refreshState();

    verify(exposureNotificationClientWrapper).isEnabled();
    assertThat(exposureNotificationState.get()).isEqualTo(ExposureNotificationState.ENABLED);
  }

  @Test
  public void refreshState_clientIsCanceled_stateLiveDataFromCache() {
    // First we set state to ONBOARDED
    when(exposureNotificationClientWrapper.isEnabled()).thenReturn(taskForResultTrue);

    exposureNotificationViewModel.refreshState();
    assertThat(exposureNotificationViewModel.getStateLiveData().getValue())
        .isEqualTo(ExposureNotificationState.ENABLED);

    // Then we check it is returned from cache
    when(exposureNotificationClientWrapper.isEnabled()).thenReturn(Tasks.forCanceled());

    exposureNotificationViewModel.refreshState();

    verify(exposureNotificationClientWrapper, times(2)).isEnabled();
    assertThat(exposureNotificationViewModel.getStateLiveData().getValue())
        .isEqualTo(ExposureNotificationState.ENABLED);
  }

  @Test
  public void refreshState_clientFailed_cacheDisabled() {
    Task<Boolean> task = Tasks.forException(new Exception());
    when(exposureNotificationClientWrapper.isEnabled()).thenReturn(task);
    exposureNotificationSharedPreferences.setIsEnabledCache(true);

    exposureNotificationViewModel.refreshState();

    verify(exposureNotificationClientWrapper).isEnabled();
    assertThat(exposureNotificationSharedPreferences.getIsEnabledCache()).isFalse();
  }

  private ExposureNotificationState callGetStateForIsEnabled(boolean enabled) {
    Task<Boolean> task = Tasks.forResult(enabled);
    when(exposureNotificationClientWrapper.isEnabled()).thenReturn(task);
    AtomicReference<ExposureNotificationState> exposureNotificationState =
        new AtomicReference<>();
    exposureNotificationViewModel.getStateLiveData().observeForever(exposureNotificationState::set);

    exposureNotificationViewModel.refreshState();

    return exposureNotificationState.get();
  }

  @Test
  public void getStateForIsEnabled_disabled() {
    ExposureNotificationState exposureNotificationState = callGetStateForIsEnabled(false);

    assertThat(exposureNotificationState).isEqualTo(ExposureNotificationState.DISABLED);
  }

  @Test
  public void getStateForIsEnabled_bluetoothIsDisabled() {
    shadowBluetoothAdapter.setEnabled(false);

    ExposureNotificationState exposureNotificationState = callGetStateForIsEnabled(true);

    assertThat(exposureNotificationState).isEqualTo(ExposureNotificationState.PAUSED_BLE);
  }

  @Test
  @Config(minSdk = VERSION_CODES.M)
  public void getStateForIsEnabled_locationIsNotEnabled() {
    when(exposureNotificationClientWrapper.deviceSupportsLocationlessScanning())
        .thenReturn(false);
    shadowOf(locationManager).setLocationEnabled(false);
    shadowOf(locationManager).setProviderEnabled(LocationManager.NETWORK_PROVIDER, false);
    shadowOf(locationManager).setProviderEnabled(LocationManager.GPS_PROVIDER, false);

    ExposureNotificationState exposureNotificationState = callGetStateForIsEnabled(true);

    assertThat(exposureNotificationState).isEqualTo(ExposureNotificationState.PAUSED_LOCATION);
  }

  @Test
  public void getStateForIsEnabled_storageLow() {
    when(exposureNotificationClientWrapper.deviceSupportsLocationlessScanning())
        .thenReturn(false);
    setStatsFs(0);

    ExposureNotificationState exposureNotificationState = callGetStateForIsEnabled(true);

    assertThat(exposureNotificationState).isEqualTo(ExposureNotificationState.STORAGE_LOW);
  }

  @Test
  public void getStateForIsEnabled_everythingEnabled() {
    when(exposureNotificationClientWrapper.deviceSupportsLocationlessScanning())
        .thenReturn(false);
    setStatsFs(AVAILABLE_BLOCKS);

    ExposureNotificationState exposureNotificationState = callGetStateForIsEnabled(true);

    assertThat(exposureNotificationState).isEqualTo(ExposureNotificationState.ENABLED);
  }

  @Test
  public void startExposureNotifications_onSuccess_stateEnabled() {
    when(exposureNotificationClientWrapper.start()).thenReturn(taskForResultVoid);
    when(exposureNotificationClientWrapper.isEnabled()).thenReturn(taskForResultTrue);
    when(exposureNotificationClientWrapper.deviceSupportsLocationlessScanning())
        .thenReturn(false);
    setStatsFs(AVAILABLE_BLOCKS);
    AtomicReference<ExposureNotificationState> exposureNotificationState =
        new AtomicReference<>(ExposureNotificationState.DISABLED);
    exposureNotificationViewModel.getStateLiveData().observeForever(exposureNotificationState::set);

    exposureNotificationViewModel.startExposureNotifications();

    verify(exposureNotificationClientWrapper).start();
    assertThat(exposureNotificationState.get())
        .isEqualTo(ExposureNotificationState.ENABLED);
  }

  @Test
  public void startExposureNotifications_onSuccess_liveDataUpdated() {
    when(exposureNotificationClientWrapper.start()).thenReturn(taskForResultVoid);
    when(exposureNotificationClientWrapper.isEnabled()).thenReturn(taskForResultTrue);
    AtomicReference<ExposureNotificationState> exposureNotificationState =
        new AtomicReference<>(ExposureNotificationState.DISABLED);
    AtomicReference<Boolean> inFlight =
        new AtomicReference<>(true);
    exposureNotificationViewModel.getStateLiveData().observeForever(exposureNotificationState::set);
    exposureNotificationViewModel.getInFlightLiveData().observeForever(inFlight::set);

    exposureNotificationViewModel.startExposureNotifications();

    verify(exposureNotificationClientWrapper).start();
    assertThat(inFlight.get()).isFalse();
    assertThat(exposureNotificationState.get()).isEqualTo(ExposureNotificationState.ENABLED);
  }

  @Test
  public void startExposureNotifications_liveDataUpdatedOnFailed_isNotApiException() {
    Task<Void> task = Tasks.forException(new Exception());
    when(exposureNotificationClientWrapper.start()).thenReturn(task);
    AtomicBoolean apiErrorObserved = new AtomicBoolean(false);
    AtomicBoolean inFlight = new AtomicBoolean(true);
    exposureNotificationViewModel.getInFlightLiveData().observeForever(inFlight::set);
    exposureNotificationViewModel.getApiErrorLiveEvent()
        .observeForever(unused -> apiErrorObserved.set(true));

    exposureNotificationViewModel.startExposureNotifications();

    verify(exposureNotificationClientWrapper).start();
    assertThat(apiErrorObserved.get()).isTrue();
    assertThat(inFlight.get()).isFalse();
  }

  @Test
  public void startExposureNotifications_liveDataUpdatedOnFailed_isApiException() {
    Task<Void> task = Tasks.forException(
        new ApiException(new Status(ExposureNotificationStatusCodes.RESOLUTION_REQUIRED)));
    when(exposureNotificationClientWrapper.start()).thenReturn(task);
    AtomicReference<Status> apiErrorObserved =
        new AtomicReference<>(Status.RESULT_SUCCESS);
    exposureNotificationViewModel.getResolutionRequiredLiveEvent()
        .observeForever(ex -> apiErrorObserved.set(ex.getStatus()));

    exposureNotificationViewModel.startExposureNotifications();

    verify(exposureNotificationClientWrapper).start();
    assertThat(apiErrorObserved.get().getStatusCode())
        .isEqualTo(ExposureNotificationStatusCodes.RESOLUTION_REQUIRED);
  }

  @Test
  public void startResolutionResultOk_onFailed() {
    Task<Void> task = Tasks.forException(new Exception());
    when(exposureNotificationClientWrapper.start()).thenReturn(task);
    AtomicBoolean atomicBoolean = new AtomicBoolean(false);
    exposureNotificationViewModel.getApiErrorLiveEvent()
        .observeForever(unused -> atomicBoolean.set(true));
    AtomicBoolean inFlight = new AtomicBoolean(true);
    exposureNotificationViewModel.getInFlightLiveData().observeForever(inFlight::set);

    exposureNotificationViewModel.startResolutionResultOk();

    verify(exposureNotificationClientWrapper).start();
    assertThat(atomicBoolean.get()).isTrue();
    assertThat(inFlight.get()).isFalse();
  }

  // TODO
  @Test
  public void startResolutionResultOk_onSuccess_enabled() {
    when(exposureNotificationClientWrapper.start()).thenReturn(taskForResultVoid);
    when(exposureNotificationClientWrapper.isEnabled()).thenReturn(taskForResultTrue);
    when(exposureNotificationClientWrapper.deviceSupportsLocationlessScanning())
        .thenReturn(false);
    statsFs.restat("/");
    setStatsFs(AVAILABLE_BLOCKS);

    exposureNotificationViewModel.startResolutionResultOk();

    verify(exposureNotificationClientWrapper).start();
    assertThat(exposureNotificationViewModel.getStateLiveData().getValue())
        .isEqualTo(ExposureNotificationState.ENABLED);
  }

  @Test
  public void startResolutionResultOk_onSuccess_liveDataUpdated() {
    when(exposureNotificationClientWrapper.start()).thenReturn(taskForResultVoid);
    when(exposureNotificationClientWrapper.isEnabled()).thenReturn(taskForResultTrue);
    AtomicBoolean inFlight = new AtomicBoolean(true);
    exposureNotificationViewModel.getInFlightLiveData().observeForever(inFlight::set);
    AtomicReference<ExposureNotificationState> exposureNotificationState =
        new AtomicReference<>(ExposureNotificationState.DISABLED);
    exposureNotificationViewModel.getStateLiveData().observeForever(exposureNotificationState::set);

    exposureNotificationViewModel.startResolutionResultOk();

    assertThat(inFlight.get()).isFalse();
    assertThat(exposureNotificationState.get()).isEqualTo(ExposureNotificationState.ENABLED);
  }

  @Test
  public void startResolutionResultNotOk_liveDataUpdated() {
    AtomicBoolean atomicBoolean = new AtomicBoolean(true);
    exposureNotificationViewModel.getInFlightLiveData()
        .observeForever(atomicBoolean::set);

    exposureNotificationViewModel.startResolutionResultNotOk();

    assertThat(atomicBoolean.get()).isFalse();
  }

  private void setStatsFs(int size) {
    ShadowStatFs.registerStats("/", size, size, size);
    statsFs.restat("/");
  }
}