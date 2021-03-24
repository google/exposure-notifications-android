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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.os.Bundle;
import android.util.Pair;
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
import com.google.android.gms.nearby.exposurenotification.ExposureNotificationStatus;
import com.google.android.gms.nearby.exposurenotification.ExposureNotificationStatusCodes;
import com.google.android.gms.nearby.exposurenotification.PackageConfiguration.PackageConfigurationBuilder;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListeningExecutorService;
import dagger.hilt.android.testing.BindValue;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import dagger.hilt.android.testing.UninstallModules;
import java.util.Set;
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

@HiltAndroidTest
@RunWith(RobolectricTestRunner.class)
@Config(application = HiltTestApplication.class)
@UninstallModules({RealTimeModule.class})
public class ExposureNotificationViewModelTest {

  @Rule
  public ExposureNotificationRules rules = ExposureNotificationRules.forTest(this).withMocks()
      .build();

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

  private static final Set<ExposureNotificationStatus> ACTIVATED_SET =
      ImmutableSet.of(ExposureNotificationStatus.ACTIVATED);
  private static final Set<ExposureNotificationStatus> INACTIVATED_SET =
      ImmutableSet.of(ExposureNotificationStatus.INACTIVATED);
  // Tasks returned by calls to the EN module APIs.
  private static final Task<Boolean> TASK_FOR_RESULT_TRUE = Tasks.forResult(true);
  private static final Task<Boolean> TASK_FOR_RESULT_FALSE = Tasks.forResult(false);
  private static final Task<Void> TASK_FOR_RESULT_VOID = Tasks.forResult(null);
  private static final Task<Set<ExposureNotificationStatus>> TASK_FOR_ACTIVATED =
      Tasks.forResult(ACTIVATED_SET);
  private static final Task<Set<ExposureNotificationStatus>> TASK_FOR_INACTIVATED =
      Tasks.forResult(INACTIVATED_SET);

  @Mock
  private ExposureNotificationClientWrapper exposureNotificationClientWrapper;
  @Mock
  private AnalyticsLogger logger;
  private ExposureNotificationViewModel exposureNotificationViewModel;

  @Before
  public void setup() {
    rules.hilt().inject();
    exposureNotificationViewModel = new ExposureNotificationViewModel(
        exposureNotificationSharedPreferences,
        exposureNotificationClientWrapper,
        logger,
        packageConfigurationHelper,
        clock);
    when(exposureNotificationClientWrapper.getPackageConfiguration())
        .thenReturn(Tasks.forResult(new PackageConfigurationBuilder().build()));
  }

  @Test
  public void refreshState_clientIsNotEnabled_cacheSaysApiIsNotEnabled() {
    when(exposureNotificationClientWrapper.isEnabled()).thenReturn(TASK_FOR_RESULT_FALSE);
    when(exposureNotificationClientWrapper.getStatus()).thenReturn(TASK_FOR_INACTIVATED);
    exposureNotificationSharedPreferences.setIsEnabledCache(true);

    exposureNotificationViewModel.refreshState();

    verify(exposureNotificationClientWrapper).isEnabled();
    verify(exposureNotificationClientWrapper).getStatus();
    assertThat(exposureNotificationSharedPreferences.getIsEnabledCache()).isFalse();
  }

  @Test
  public void refreshState_packageConfigurationAnalyticsTrue_updatesAppAnalyticsState() {
    when(exposureNotificationClientWrapper.isEnabled()).thenReturn(TASK_FOR_RESULT_FALSE);
    when(exposureNotificationClientWrapper.getStatus()).thenReturn(TASK_FOR_ACTIVATED);
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
    when(exposureNotificationClientWrapper.isEnabled()).thenReturn(TASK_FOR_RESULT_TRUE);
    when(exposureNotificationClientWrapper.getStatus()).thenReturn(TASK_FOR_ACTIVATED);
    AtomicReference<ExposureNotificationState> exposureNotificationState =
        new AtomicReference<>(ExposureNotificationState.DISABLED);
    exposureNotificationViewModel.getStateLiveData().observeForever(exposureNotificationState::set);

    exposureNotificationViewModel.refreshState();

    verify(exposureNotificationClientWrapper).isEnabled();
    verify(exposureNotificationClientWrapper).getStatus();
    assertThat(exposureNotificationState.get()).isEqualTo(ExposureNotificationState.ENABLED);
  }

  @Test
  public void refreshState_clientIsCanceled_stateLiveDataFromCache() {
    // First we set state to ONBOARDED
    when(exposureNotificationClientWrapper.isEnabled()).thenReturn(TASK_FOR_RESULT_TRUE);
    when(exposureNotificationClientWrapper.getStatus()).thenReturn(TASK_FOR_ACTIVATED);

    exposureNotificationViewModel.refreshState();
    assertThat(exposureNotificationViewModel.getStateLiveData().getValue())
        .isEqualTo(ExposureNotificationState.ENABLED);

    // Then we check it is returned from cache
    when(exposureNotificationClientWrapper.isEnabled()).thenReturn(Tasks.forCanceled());
    when(exposureNotificationClientWrapper.getStatus()).thenReturn(Tasks.forCanceled());

    exposureNotificationViewModel.refreshState();

    verify(exposureNotificationClientWrapper, times(2)).isEnabled();
    verify(exposureNotificationClientWrapper, times(1)).getStatus();
    assertThat(exposureNotificationViewModel.getStateLiveData().getValue())
        .isEqualTo(ExposureNotificationState.ENABLED);
  }

  @Test
  public void refreshState_clientFailed_cacheDisabled() {
    Task<Boolean> task = Tasks.forException(new Exception());
    Task<Set<ExposureNotificationStatus>> setTask = Tasks.forException(new Exception());
    when(exposureNotificationClientWrapper.isEnabled()).thenReturn(task);
    when(exposureNotificationClientWrapper.getStatus()).thenReturn(setTask);
    exposureNotificationSharedPreferences.setIsEnabledCache(true);

    exposureNotificationViewModel.refreshState();

    verify(exposureNotificationClientWrapper).isEnabled();
    verify(exposureNotificationClientWrapper).getStatus();
    assertThat(exposureNotificationSharedPreferences.getIsEnabledCache()).isFalse();
  }

  @Test
  public void refreshState_stateEnabled_notInFlight_refreshUiLiveDataUpdated() {
    // GIVEN
    AtomicReference<Pair<ExposureNotificationState, Boolean>> refreshUiData = new AtomicReference<>(
        Pair.create(ExposureNotificationState.DISABLED, /* isInFlight= */ true));
    when(exposureNotificationClientWrapper.isEnabled()).thenReturn(TASK_FOR_RESULT_TRUE);
    when(exposureNotificationClientWrapper.getStatus()).thenReturn(TASK_FOR_ACTIVATED);
    exposureNotificationViewModel.getStateWithInFlightLiveData()
        .observeForever(refreshUiData::set);

    // WHEN
    exposureNotificationViewModel.refreshState();

    // THEN
    verify(exposureNotificationClientWrapper).isEnabled();
    verify(exposureNotificationClientWrapper).getStatus();
    assertThat(refreshUiData.get().first).isEqualTo(ExposureNotificationState.ENABLED);
    assertThat(refreshUiData.get().second).isFalse();
  }

  @Test
  public void refreshState_stateDisabled_notInFlight_refreshUiLiveDataUpdated() {
    // GIVEN
    AtomicReference<Pair<ExposureNotificationState, Boolean>> refreshUiData = new AtomicReference<>(
        Pair.create(ExposureNotificationState.ENABLED, /* isInFlight= */ true));
    when(exposureNotificationClientWrapper.isEnabled()).thenReturn(TASK_FOR_RESULT_FALSE);
    when(exposureNotificationClientWrapper.getStatus()).thenReturn(TASK_FOR_INACTIVATED);
    exposureNotificationViewModel.getStateWithInFlightLiveData()
        .observeForever(refreshUiData::set);

    // WHEN
    exposureNotificationViewModel.refreshState();

    // THEN
    verify(exposureNotificationClientWrapper).isEnabled();
    verify(exposureNotificationClientWrapper).getStatus();
    assertThat(refreshUiData.get().first).isEqualTo(ExposureNotificationState.DISABLED);
    assertThat(refreshUiData.get().second).isFalse();
  }


  private ExposureNotificationState callGetStateForIsEnabledAndStatusSet(
      boolean enabled, Set<ExposureNotificationStatus> statusSet) {
    Task<Boolean> task = Tasks.forResult(enabled);
    Task<Set<ExposureNotificationStatus>> setTask = Tasks.forResult(statusSet);
    when(exposureNotificationClientWrapper.isEnabled()).thenReturn(task);
    when(exposureNotificationClientWrapper.getStatus()).thenReturn(setTask);
    AtomicReference<ExposureNotificationState> exposureNotificationState =
        new AtomicReference<>();
    exposureNotificationViewModel.getStateLiveData().observeForever(exposureNotificationState::set);

    exposureNotificationViewModel.refreshState();

    return exposureNotificationState.get();
  }

  @Test
  public void getStateForIsEnabled_disabled() {
    ExposureNotificationState exposureNotificationState =
        callGetStateForIsEnabledAndStatusSet(false, INACTIVATED_SET);

    assertThat(exposureNotificationState).isEqualTo(ExposureNotificationState.DISABLED);
  }

  @Test
  public void getStateForIsEnabled_bluetoothIsDisabled() {
    Set<ExposureNotificationStatus> bluetoothDisabledSet = ImmutableSet.of(
        ExposureNotificationStatus.INACTIVATED, ExposureNotificationStatus.BLUETOOTH_DISABLED,
        ExposureNotificationStatus.BLUETOOTH_SUPPORT_UNKNOWN);

    ExposureNotificationState exposureNotificationState =
        callGetStateForIsEnabledAndStatusSet(true, bluetoothDisabledSet);

    assertThat(exposureNotificationState).isEqualTo(ExposureNotificationState.PAUSED_BLE);
  }

  @Test
  public void getStateForIsEnabled_locationIsNotEnabled() {
    Set<ExposureNotificationStatus> locationDisabledSet = ImmutableSet.of(
        ExposureNotificationStatus.INACTIVATED, ExposureNotificationStatus.LOCATION_DISABLED);

    ExposureNotificationState exposureNotificationState =
        callGetStateForIsEnabledAndStatusSet(true, locationDisabledSet);

    assertThat(exposureNotificationState).isEqualTo(ExposureNotificationState.PAUSED_LOCATION);
  }

  @Test
  public void getStateForIsEnabled_bluetoothIsDisabled_locationIsDisabled() {
    Set<ExposureNotificationStatus> bluetoothAndLocationDisabledSet = ImmutableSet.of(
        ExposureNotificationStatus.INACTIVATED, ExposureNotificationStatus.BLUETOOTH_DISABLED,
        ExposureNotificationStatus.BLUETOOTH_SUPPORT_UNKNOWN,
        ExposureNotificationStatus.LOCATION_DISABLED);

    ExposureNotificationState exposureNotificationState =
        callGetStateForIsEnabledAndStatusSet(true, bluetoothAndLocationDisabledSet);

    assertThat(exposureNotificationState).isEqualTo(ExposureNotificationState.PAUSED_LOCATION_BLE);
  }

  @Test
  public void getStateForIsEnabled_storageLow() {
    Set<ExposureNotificationStatus> storageLowSet = ImmutableSet.of(
        ExposureNotificationStatus.INACTIVATED, ExposureNotificationStatus.LOW_STORAGE);

    ExposureNotificationState exposureNotificationState =
        callGetStateForIsEnabledAndStatusSet(true, storageLowSet);

    assertThat(exposureNotificationState).isEqualTo(ExposureNotificationState.STORAGE_LOW);
  }

  @Test
  public void getStateForIsEnabled_everythingEnabled() {
    ExposureNotificationState exposureNotificationState =
        callGetStateForIsEnabledAndStatusSet(true, ACTIVATED_SET);

    assertThat(exposureNotificationState).isEqualTo(ExposureNotificationState.ENABLED);
  }

  @Test
  public void startExposureNotifications_onSuccess_stateEnabled() {
    when(exposureNotificationClientWrapper.start()).thenReturn(TASK_FOR_RESULT_VOID);
    when(exposureNotificationClientWrapper.isEnabled()).thenReturn(TASK_FOR_RESULT_TRUE);
    when(exposureNotificationClientWrapper.getStatus()).thenReturn(TASK_FOR_ACTIVATED);
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
    when(exposureNotificationClientWrapper.start()).thenReturn(TASK_FOR_RESULT_VOID);
    when(exposureNotificationClientWrapper.isEnabled()).thenReturn(TASK_FOR_RESULT_TRUE);
    when(exposureNotificationClientWrapper.getStatus()).thenReturn(TASK_FOR_ACTIVATED);
    AtomicReference<ExposureNotificationState> exposureNotificationState =
        new AtomicReference<>(ExposureNotificationState.DISABLED);
    AtomicReference<Boolean> inFlight =
        new AtomicReference<>(true);
    AtomicReference<Pair<ExposureNotificationState, Boolean>> refreshUiData = new AtomicReference<>(
        Pair.create(ExposureNotificationState.DISABLED, /* isInFlight= */ true));
    exposureNotificationViewModel.getStateLiveData().observeForever(exposureNotificationState::set);
    exposureNotificationViewModel.getInFlightLiveData().observeForever(inFlight::set);
    exposureNotificationViewModel.getStateWithInFlightLiveData()
        .observeForever(refreshUiData::set);

    exposureNotificationViewModel.startExposureNotifications();

    verify(exposureNotificationClientWrapper).start();
    assertThat(inFlight.get()).isFalse();
    assertThat(exposureNotificationState.get()).isEqualTo(ExposureNotificationState.ENABLED);
    assertThat(refreshUiData.get().first).isEqualTo(ExposureNotificationState.ENABLED);
    assertThat(refreshUiData.get().second).isFalse();
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
    when(exposureNotificationClientWrapper.start()).thenReturn(TASK_FOR_RESULT_VOID);
    when(exposureNotificationClientWrapper.isEnabled()).thenReturn(TASK_FOR_RESULT_TRUE);
    when(exposureNotificationClientWrapper.getStatus()).thenReturn(TASK_FOR_ACTIVATED);

    exposureNotificationViewModel.startResolutionResultOk();

    verify(exposureNotificationClientWrapper).start();
    assertThat(exposureNotificationViewModel.getStateLiveData().getValue())
        .isEqualTo(ExposureNotificationState.ENABLED);
  }

  @Test
  public void startResolutionResultOk_onSuccess_liveDataUpdated() {
    when(exposureNotificationClientWrapper.start()).thenReturn(TASK_FOR_RESULT_VOID);
    when(exposureNotificationClientWrapper.isEnabled()).thenReturn(TASK_FOR_RESULT_TRUE);
    when(exposureNotificationClientWrapper.getStatus()).thenReturn(TASK_FOR_ACTIVATED);
    AtomicBoolean inFlight = new AtomicBoolean(true);
    AtomicReference<ExposureNotificationState> exposureNotificationState =
        new AtomicReference<>(ExposureNotificationState.DISABLED);
    AtomicReference<Pair<ExposureNotificationState, Boolean>> refreshUiData = new AtomicReference<>(
        Pair.create(ExposureNotificationState.DISABLED, /* isInFlight= */ true));
    exposureNotificationViewModel.getStateLiveData().observeForever(exposureNotificationState::set);
    exposureNotificationViewModel.getInFlightLiveData().observeForever(inFlight::set);
    exposureNotificationViewModel.getStateWithInFlightLiveData()
        .observeForever(refreshUiData::set);

    exposureNotificationViewModel.startResolutionResultOk();

    assertThat(inFlight.get()).isFalse();
    assertThat(exposureNotificationState.get()).isEqualTo(ExposureNotificationState.ENABLED);
    assertThat(refreshUiData.get().first).isEqualTo(ExposureNotificationState.ENABLED);
    assertThat(refreshUiData.get().second).isFalse();
  }

  @Test
  public void startResolutionResultNotOk_liveDataUpdated() {
    AtomicBoolean atomicBoolean = new AtomicBoolean(true);
    exposureNotificationViewModel.getInFlightLiveData()
        .observeForever(atomicBoolean::set);

    exposureNotificationViewModel.startResolutionResultNotOk();

    assertThat(atomicBoolean.get()).isFalse();
  }
}