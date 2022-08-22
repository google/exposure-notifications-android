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

import static com.google.android.apps.exposurenotification.notify.ShareDiagnosisViewModel.EN_STATES_BLOCKING_SHARING_FLOW;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.MoreExecutors.newDirectExecutorService;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.app.NotificationManager;
import android.content.Context;
import android.os.Bundle;
import android.util.Pair;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.apps.exposurenotification.common.BuildUtils;
import com.google.android.apps.exposurenotification.common.BuildUtils.Type;
import com.google.android.apps.exposurenotification.common.NotificationHelper;
import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.android.apps.exposurenotification.common.time.RealTimeModule;
import com.google.android.apps.exposurenotification.home.ExposureNotificationViewModel.ExposureNotificationState;
import com.google.android.apps.exposurenotification.logging.AnalyticsLogger;
import com.google.android.apps.exposurenotification.nearby.ExposureNotificationClientWrapper;
import com.google.android.apps.exposurenotification.nearby.PackageConfigurationHelper;
import com.google.android.apps.exposurenotification.riskcalculation.ExposureClassification;
import com.google.android.apps.exposurenotification.storage.DbModule;
import com.google.android.apps.exposurenotification.storage.DiagnosisRepository;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationDatabase;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.apps.exposurenotification.testsupport.ExposureNotificationRules;
import com.google.android.apps.exposurenotification.testsupport.FakeClock;
import com.google.android.apps.exposurenotification.testsupport.InMemoryDb;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.nearby.exposurenotification.ExposureNotificationStatus;
import com.google.android.gms.nearby.exposurenotification.ExposureNotificationStatusCodes;
import com.google.android.gms.nearby.exposurenotification.PackageConfiguration.PackageConfigurationBuilder;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import dagger.hilt.android.testing.BindValue;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import dagger.hilt.android.testing.UninstallModules;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.annotation.LooperMode.Mode;
import org.robolectric.shadows.ShadowNotificationManager;

@HiltAndroidTest
@RunWith(RobolectricTestRunner.class)
@Config(application = HiltTestApplication.class)
@LooperMode(Mode.LEGACY)
@UninstallModules({DbModule.class, RealTimeModule.class})
public class ExposureNotificationViewModelTest {

  @Rule
  public ExposureNotificationRules rules = ExposureNotificationRules.forTest(this).withMocks()
      .build();

  private final Context context = ApplicationProvider.getApplicationContext();
  private final ShadowNotificationManager notificationManager =
      shadowOf((NotificationManager) context
          .getSystemService(Context.NOTIFICATION_SERVICE));

  @Inject
  ExposureNotificationSharedPreferences exposureNotificationSharedPreferences;
  @Inject
  DiagnosisRepository diagnosisRepository;
  @Inject
  PackageConfigurationHelper packageConfigurationHelper;
  @Inject
  NotificationHelper notificationHelper;

  @BindValue
  Clock clock = new FakeClock();
  @BindValue
  ExposureNotificationDatabase database = InMemoryDb.create();

  private static final Set<ExposureNotificationStatus> ACTIVATED_SET =
      ImmutableSet.of(ExposureNotificationStatus.ACTIVATED);
  private static final Set<ExposureNotificationStatus> INACTIVATED_SET =
      ImmutableSet.of(ExposureNotificationStatus.INACTIVATED);
  private static final Set<ExposureNotificationStatus> EN_TURNDOWN_SET =
      ImmutableSet.of(ExposureNotificationStatus.EN_NOT_SUPPORT);
  private static final Set<ExposureNotificationStatus> EN_TURNDOWN_FOR_REGION_SET =
      ImmutableSet.of(ExposureNotificationStatus.NOT_IN_ALLOWLIST);
  /*
   * Maps from the set of ExposureNotificationStatus objects, which may actually be returned by the
   * EN module's getStatus() API, to the single ExposureNotificationState object it corresponds to.
   * These maps are primarily used to simulate scenarios with multiple edge cases returned at the
   * same time to ensure we handle them properly.
   */
  private static final Map<Set<ExposureNotificationStatus>, ExposureNotificationState>
      EN_DISABLED_STATUS_SET_TO_STATE_MAP =
      ImmutableMap.<Set<ExposureNotificationStatus>, ExposureNotificationState>builder()
          .put(ImmutableSet.of(ExposureNotificationStatus.EN_NOT_SUPPORT),
              /* state= */ExposureNotificationState.PAUSED_EN_NOT_SUPPORT)
          .put(ImmutableSet.of(ExposureNotificationStatus.NOT_IN_ALLOWLIST),
              /* state= */ExposureNotificationState.PAUSED_NOT_IN_ALLOWLIST)
          .put(ImmutableSet.of(ExposureNotificationStatus.INACTIVATED,
              ExposureNotificationStatus.HW_NOT_SUPPORT),
              /* state= */ExposureNotificationState.PAUSED_HW_NOT_SUPPORT)
          .put(ImmutableSet.of(ExposureNotificationStatus.INACTIVATED,
              ExposureNotificationStatus.USER_PROFILE_NOT_SUPPORT),
              /* state= */ExposureNotificationState.PAUSED_USER_PROFILE_NOT_SUPPORT)
          .put(ImmutableSet.of(ExposureNotificationStatus.INACTIVATED,
              ExposureNotificationStatus.USER_PROFILE_NOT_SUPPORT,
              ExposureNotificationStatus.HW_NOT_SUPPORT),
              /* state= */ExposureNotificationState.PAUSED_HW_NOT_SUPPORT)
          .put(ImmutableSet.of(ExposureNotificationStatus.INACTIVATED,
              ExposureNotificationStatus.NO_CONSENT,
              ExposureNotificationStatus.FOCUS_LOST),
              /* state= */ExposureNotificationState.DISABLED)
          .put(ImmutableSet.of(ExposureNotificationStatus.INACTIVATED,
              ExposureNotificationStatus.FOCUS_LOST),
              /* state= */ExposureNotificationState.FOCUS_LOST)
          .build();
  private static final Map<Set<ExposureNotificationStatus>, ExposureNotificationState>
      EN_DISABLED_STATUS_SET_WITH_LOW_STORAGE_TO_STATE_MAP =
      ImmutableMap.<Set<ExposureNotificationStatus>, ExposureNotificationState>builder()
          .put(ImmutableSet.of(ExposureNotificationStatus.INACTIVATED,
              ExposureNotificationStatus.USER_PROFILE_NOT_SUPPORT,
              ExposureNotificationStatus.HW_NOT_SUPPORT,
              ExposureNotificationStatus.LOW_STORAGE),
              /* state= */ExposureNotificationState.PAUSED_HW_NOT_SUPPORT)
          .put(ImmutableSet.of(ExposureNotificationStatus.INACTIVATED,
              ExposureNotificationStatus.USER_PROFILE_NOT_SUPPORT,
              ExposureNotificationStatus.LOW_STORAGE),
              /* state= */ExposureNotificationState.PAUSED_USER_PROFILE_NOT_SUPPORT)
          .put(ImmutableSet.of(ExposureNotificationStatus.INACTIVATED,
              ExposureNotificationStatus.FOCUS_LOST,
              ExposureNotificationStatus.LOW_STORAGE),
              /* state= */ExposureNotificationState.STORAGE_LOW)
          .put(ImmutableSet.of(ExposureNotificationStatus.INACTIVATED,
              ExposureNotificationStatus.NO_CONSENT,
              ExposureNotificationStatus.LOW_STORAGE),
              /* state= */ExposureNotificationState.STORAGE_LOW)
          .put(ImmutableSet.of(ExposureNotificationStatus.INACTIVATED,
              ExposureNotificationStatus.NO_CONSENT,
              ExposureNotificationStatus.FOCUS_LOST,
              ExposureNotificationStatus.LOW_STORAGE),
              /* state= */ExposureNotificationState.STORAGE_LOW)
          .build();
  /*
   * List of the set of ExposureNotificationStatus objects, which may actually be returned by the
   * EN module's getStatus() API. Used to test whether we always give priority to LOW_STORAGE
   * status if multiple statuses are returned at the same time.
   */
  private static final List<Set<ExposureNotificationStatus>>
      EN_ENABLED_STATUS_SETS_WITH_LOW_STORAGE = ImmutableList.of(
      ImmutableSet.of(ExposureNotificationStatus.INACTIVATED,
          ExposureNotificationStatus.LOW_STORAGE,
          ExposureNotificationStatus.LOCATION_DISABLED),
      ImmutableSet.of(ExposureNotificationStatus.INACTIVATED,
          ExposureNotificationStatus.LOW_STORAGE,
          ExposureNotificationStatus.BLUETOOTH_DISABLED,
          ExposureNotificationStatus.BLUETOOTH_SUPPORT_UNKNOWN),
      ImmutableSet.of(ExposureNotificationStatus.INACTIVATED,
          ExposureNotificationStatus.LOW_STORAGE,
          ExposureNotificationStatus.LOCATION_DISABLED,
          ExposureNotificationStatus.BLUETOOTH_DISABLED,
          ExposureNotificationStatus.BLUETOOTH_SUPPORT_UNKNOWN));

  // Tasks returned by calls to the EN module APIs.
  private static final Task<Boolean> TASK_FOR_RESULT_TRUE = Tasks.forResult(true);
  private static final Task<Boolean> TASK_FOR_RESULT_FALSE = Tasks.forResult(false);
  private static final Task<Void> TASK_FOR_RESULT_VOID = Tasks.forResult(null);
  private static final Task<Set<ExposureNotificationStatus>> TASK_FOR_ACTIVATED =
      Tasks.forResult(ACTIVATED_SET);
  private static final Task<Set<ExposureNotificationStatus>> TASK_FOR_INACTIVATED =
      Tasks.forResult(INACTIVATED_SET);
  private static final Task<Set<ExposureNotificationStatus>> TASK_FOR_EN_TURNDOWN =
      Tasks.forResult(EN_TURNDOWN_SET);
  private static final Task<Set<ExposureNotificationStatus>> TASK_FOR_EN_TURNDOWN_FOR_REGION =
      Tasks.forResult(EN_TURNDOWN_FOR_REGION_SET);
  private static final int EN_DISABLED_ORDINAL = ExposureNotificationState.DISABLED.ordinal();
  private static final int EN_ENABLED_ORDINAL = ExposureNotificationState.ENABLED.ordinal();
  private static final Task<Void> TASK_FOR_RESULT_API_EXCEPTION =
      Tasks.forException(new ApiException(Status.RESULT_INTERNAL_ERROR));

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
        diagnosisRepository,
        logger,
        packageConfigurationHelper,
        clock,
        newDirectExecutorService(), notificationHelper);
    when(exposureNotificationClientWrapper.getPackageConfiguration())
        .thenReturn(Tasks.forResult(new PackageConfigurationBuilder().build()));
  }

  @Test
  public void refreshState_clientIsNotEnabled_cacheSaysApiIsNotEnabledAndStateIsDisabled() {
    when(exposureNotificationClientWrapper.isEnabled()).thenReturn(TASK_FOR_RESULT_FALSE);
    when(exposureNotificationClientWrapper.getStatus()).thenReturn(TASK_FOR_INACTIVATED);
    exposureNotificationSharedPreferences.setIsEnabledCache(true);
    exposureNotificationSharedPreferences.setEnStateCache(EN_ENABLED_ORDINAL);

    exposureNotificationViewModel.refreshState();

    verify(exposureNotificationClientWrapper).isEnabled();
    verify(exposureNotificationClientWrapper).getStatus();
    assertThat(exposureNotificationSharedPreferences.getIsEnabledCache()).isFalse();
    assertThat(exposureNotificationSharedPreferences.getEnStateCache()).isEqualTo(
        EN_DISABLED_ORDINAL);
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
  public void refreshState_clientFailed_cacheDisabled_liveDataUpdated() {
    // GIVEN
    AtomicBoolean isEnabled = new AtomicBoolean(true);
    AtomicReference<Pair<ExposureNotificationState, Boolean>> pairLiveData = new AtomicReference<>(
        Pair.create(ExposureNotificationState.ENABLED, /* isInFlight= */ true));
    // Set cache values
    exposureNotificationSharedPreferences.setIsEnabledCache(true);
    exposureNotificationSharedPreferences.setEnStateCache(EN_ENABLED_ORDINAL);
    // Imitate failed EN API calls
    Task<Boolean> failedIsEnabledTask = Tasks.forException(new Exception());
    Task<Set<ExposureNotificationStatus>> failedGetStatusTask = Tasks.forException(new Exception());
    when(exposureNotificationClientWrapper.isEnabled()).thenReturn(failedIsEnabledTask);
    when(exposureNotificationClientWrapper.getStatus()).thenReturn(failedGetStatusTask);
    // Observe LiveData updates
    exposureNotificationViewModel.getEnEnabledLiveData().observeForever(isEnabled::set);
    exposureNotificationViewModel.getStateWithInFlightLiveData().observeForever(pairLiveData::set);

    // WHEN
    exposureNotificationViewModel.refreshState();

    // THEN
    verify(exposureNotificationClientWrapper).isEnabled();
    verify(exposureNotificationClientWrapper).getStatus();
    assertThat(exposureNotificationSharedPreferences.getIsEnabledCache()).isFalse();
    assertThat(exposureNotificationSharedPreferences.getEnStateCache()).isEqualTo(
        EN_DISABLED_ORDINAL);
    assertThat(isEnabled.get()).isFalse();
    assertThat(pairLiveData.get().first).isEqualTo(ExposureNotificationState.DISABLED);
    assertThat(pairLiveData.get().second).isFalse();
  }

  @Test
  public void refreshState_stateEnabled_notInFlight_pairLiveDataUpdated() {
    // GIVEN
    AtomicReference<Pair<ExposureNotificationState, Boolean>> pairLiveData = new AtomicReference<>(
        Pair.create(ExposureNotificationState.DISABLED, /* isInFlight= */ true));
    when(exposureNotificationClientWrapper.isEnabled()).thenReturn(TASK_FOR_RESULT_TRUE);
    when(exposureNotificationClientWrapper.getStatus()).thenReturn(TASK_FOR_ACTIVATED);
    exposureNotificationViewModel.getStateWithInFlightLiveData().observeForever(pairLiveData::set);

    // WHEN
    exposureNotificationViewModel.refreshState();

    // THEN
    verify(exposureNotificationClientWrapper).isEnabled();
    verify(exposureNotificationClientWrapper).getStatus();
    assertThat(pairLiveData.get().first).isEqualTo(ExposureNotificationState.ENABLED);
    assertThat(pairLiveData.get().second).isFalse();
  }

  @Test
  public void refreshState_stateDisabled_notInFlight_pairLiveDataUpdated() {
    // GIVEN
    AtomicReference<Pair<ExposureNotificationState, Boolean>> pairLiveData = new AtomicReference<>(
        Pair.create(ExposureNotificationState.ENABLED, /* isInFlight= */ true));
    when(exposureNotificationClientWrapper.isEnabled()).thenReturn(TASK_FOR_RESULT_FALSE);
    when(exposureNotificationClientWrapper.getStatus()).thenReturn(TASK_FOR_INACTIVATED);
    exposureNotificationViewModel.getStateWithInFlightLiveData()
        .observeForever(pairLiveData::set);

    // WHEN
    exposureNotificationViewModel.refreshState();

    // THEN
    verify(exposureNotificationClientWrapper).isEnabled();
    verify(exposureNotificationClientWrapper).getStatus();
    assertThat(pairLiveData.get().first).isEqualTo(ExposureNotificationState.DISABLED);
    assertThat(pairLiveData.get().second).isFalse();
  }

  @Test
  public void refreshState_enDisabledAndTurnedDown_liveDataUpdatedWithExpectedValues() {
    // GIVEN
    AtomicReference<Pair<ExposureNotificationState, Boolean>> pairLiveData = new AtomicReference<>(
        Pair.create(ExposureNotificationState.ENABLED, /* isInFlight= */ true));
    when(exposureNotificationClientWrapper.isEnabled()).thenReturn(TASK_FOR_RESULT_FALSE);
    when(exposureNotificationClientWrapper.getStatus()).thenReturn(TASK_FOR_EN_TURNDOWN);
    exposureNotificationViewModel.getStateWithInFlightLiveData()
        .observeForever(pairLiveData::set);

    // WHEN
    exposureNotificationViewModel.refreshState();

    // THEN
    verify(exposureNotificationClientWrapper).isEnabled();
    verify(exposureNotificationClientWrapper).getStatus();
    assertThat(pairLiveData.get().first).isEqualTo(ExposureNotificationState.PAUSED_EN_NOT_SUPPORT);
    assertThat(pairLiveData.get().second).isFalse();
  }

  @Test
  public void refreshState_enDisabledAndTurnedDownForRegion_liveDataUpdatedWithExpectedValues() {
    // GIVEN
    AtomicReference<Pair<ExposureNotificationState, Boolean>> pairLiveData = new AtomicReference<>(
        Pair.create(ExposureNotificationState.ENABLED, /* isInFlight= */ true));
    when(exposureNotificationClientWrapper.isEnabled()).thenReturn(TASK_FOR_RESULT_FALSE);
    when(exposureNotificationClientWrapper.getStatus()).thenReturn(TASK_FOR_EN_TURNDOWN_FOR_REGION);
    exposureNotificationViewModel.getStateWithInFlightLiveData()
        .observeForever(pairLiveData::set);

    // WHEN
    exposureNotificationViewModel.refreshState();

    // THEN
    verify(exposureNotificationClientWrapper).isEnabled();
    verify(exposureNotificationClientWrapper).getStatus();
    assertThat(pairLiveData.get().first)
        .isEqualTo(ExposureNotificationState.PAUSED_NOT_IN_ALLOWLIST);
    assertThat(pairLiveData.get().second).isFalse();
  }

  private ExposureNotificationState callGetStateForStatusAndIsEnabled(
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
  public void getStateForStatusAndIsEnabled_disabled() {
    ExposureNotificationState exposureNotificationState =
        callGetStateForStatusAndIsEnabled(false, INACTIVATED_SET);

    assertThat(exposureNotificationState).isEqualTo(ExposureNotificationState.DISABLED);
  }

  @Test
  public void getStateForStatusAndIsEnabled_bluetoothIsDisabled() {
    Set<ExposureNotificationStatus> bluetoothDisabledSet = ImmutableSet.of(
        ExposureNotificationStatus.INACTIVATED, ExposureNotificationStatus.BLUETOOTH_DISABLED,
        ExposureNotificationStatus.BLUETOOTH_SUPPORT_UNKNOWN);

    ExposureNotificationState exposureNotificationState =
        callGetStateForStatusAndIsEnabled(true, bluetoothDisabledSet);

    assertThat(exposureNotificationState).isEqualTo(ExposureNotificationState.PAUSED_BLE);
  }

  @Test
  public void getStateForStatusAndIsEnabled_locationIsNotEnabled() {
    Set<ExposureNotificationStatus> locationDisabledSet = ImmutableSet.of(
        ExposureNotificationStatus.INACTIVATED, ExposureNotificationStatus.LOCATION_DISABLED);

    ExposureNotificationState exposureNotificationState =
        callGetStateForStatusAndIsEnabled(true, locationDisabledSet);

    assertThat(exposureNotificationState).isEqualTo(ExposureNotificationState.PAUSED_LOCATION);
  }

  @Test
  public void getStateForStatusAndIsEnabled_bluetoothIsDisabled_locationIsDisabled() {
    Set<ExposureNotificationStatus> bluetoothAndLocationDisabledSet = ImmutableSet.of(
        ExposureNotificationStatus.INACTIVATED, ExposureNotificationStatus.BLUETOOTH_DISABLED,
        ExposureNotificationStatus.BLUETOOTH_SUPPORT_UNKNOWN,
        ExposureNotificationStatus.LOCATION_DISABLED);

    ExposureNotificationState exposureNotificationState =
        callGetStateForStatusAndIsEnabled(true, bluetoothAndLocationDisabledSet);

    assertThat(exposureNotificationState).isEqualTo(ExposureNotificationState.PAUSED_LOCATION_BLE);
  }

  @Test
  public void getStateForStatusAndIsEnabled_storageLow() {
    Set<ExposureNotificationStatus> storageLowSet = ImmutableSet.of(
        ExposureNotificationStatus.INACTIVATED, ExposureNotificationStatus.LOW_STORAGE);

    ExposureNotificationState exposureNotificationState =
        callGetStateForStatusAndIsEnabled(true, storageLowSet);

    assertThat(exposureNotificationState).isEqualTo(ExposureNotificationState.STORAGE_LOW);
  }

  @Test
  public void getStateForStatusAndIsEnabled_everythingEnabled() {
    ExposureNotificationState exposureNotificationState =
        callGetStateForStatusAndIsEnabled(true, ACTIVATED_SET);

    assertThat(exposureNotificationState).isEqualTo(ExposureNotificationState.ENABLED);
  }

  @Test
  public void getStateForStatusAndIsEnabled_enDisabled_statesReturnedAsExpected() {
    for (Set<ExposureNotificationStatus> statusSet : EN_DISABLED_STATUS_SET_TO_STATE_MAP.keySet()) {
      // WHEN
      ExposureNotificationState exposureNotificationState =
          callGetStateForStatusAndIsEnabled(false, statusSet);

      // THEN
      assertThat(exposureNotificationState).isEqualTo(
          EN_DISABLED_STATUS_SET_TO_STATE_MAP.get(statusSet));
    }
  }

  @Test
  public void getStateForStatusAndIsEnabled_enDisabled_statusSetWithLowStorage_statesReturnedAsExpected() {
    for (Set<ExposureNotificationStatus> statusSet :
        EN_DISABLED_STATUS_SET_WITH_LOW_STORAGE_TO_STATE_MAP.keySet()) {
      // WHEN
      ExposureNotificationState exposureNotificationState =
          callGetStateForStatusAndIsEnabled(false, statusSet);

      // THEN
      assertThat(exposureNotificationState).isEqualTo(
          EN_DISABLED_STATUS_SET_WITH_LOW_STORAGE_TO_STATE_MAP.get(statusSet));
    }
  }

  @Test
  public void getStateForStatusAndIsEnabled_enTurndown_pausedEnNotSupport() {
    ExposureNotificationState exposureNotificationState =
        callGetStateForStatusAndIsEnabled(false, EN_TURNDOWN_SET);

    assertThat(exposureNotificationState)
        .isEqualTo(ExposureNotificationState.PAUSED_EN_NOT_SUPPORT);
  }

  @Test
  public void getStateForStatusAndIsEnabled_enTurndownForRegion_pausedNotInAllowlist() {
    ExposureNotificationState exposureNotificationState =
        callGetStateForStatusAndIsEnabled(false, EN_TURNDOWN_FOR_REGION_SET);

    assertThat(exposureNotificationState)
        .isEqualTo(ExposureNotificationState.PAUSED_NOT_IN_ALLOWLIST);
  }

  @Test
  public void getStateForStatusAndIsEnabled_enEnabled_statusSetWithLowStorage_lowStorageStateReturned() {
    for (Set<ExposureNotificationStatus> statusSet : EN_ENABLED_STATUS_SETS_WITH_LOW_STORAGE) {
      // WHEN
      ExposureNotificationState exposureNotificationState =
          callGetStateForStatusAndIsEnabled(true, statusSet);

      // THEN
      assertThat(exposureNotificationState).isEqualTo(ExposureNotificationState.STORAGE_LOW);
    }
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
    AtomicReference<Pair<ExposureNotificationState, Boolean>> pairLiveData = new AtomicReference<>(
        Pair.create(ExposureNotificationState.DISABLED, /* isInFlight= */ true));
    exposureNotificationViewModel.getStateLiveData().observeForever(exposureNotificationState::set);
    exposureNotificationViewModel.getInFlightLiveData().observeForever(inFlight::set);
    exposureNotificationViewModel.getStateWithInFlightLiveData()
        .observeForever(pairLiveData::set);

    exposureNotificationViewModel.startExposureNotifications();

    verify(exposureNotificationClientWrapper).start();
    assertThat(inFlight.get()).isFalse();
    assertThat(exposureNotificationState.get()).isEqualTo(ExposureNotificationState.ENABLED);
    assertThat(pairLiveData.get().first).isEqualTo(ExposureNotificationState.ENABLED);
    assertThat(pairLiveData.get().second).isFalse();
  }

  @Test
  public void startExposureNotifications_liveDataUpdatedOnFailed_isNotApiException() {
    Task<Void> task = Tasks.forException(new Exception());
    when(exposureNotificationClientWrapper.start()).thenReturn(task);
    AtomicBoolean apiErrorObserved = new AtomicBoolean(false);
    AtomicBoolean apiUnavailableObserved = new AtomicBoolean(false);
    AtomicBoolean inFlightObserved = new AtomicBoolean(true);
    exposureNotificationViewModel.getInFlightLiveData().observeForever(inFlightObserved::set);
    exposureNotificationViewModel.getApiErrorLiveEvent()
        .observeForever(unused -> apiErrorObserved.set(true));
    exposureNotificationViewModel.getApiUnavailableLiveEvent()
        .observeForever(unused -> apiUnavailableObserved.set(true));

    exposureNotificationViewModel.startExposureNotifications();

    verify(exposureNotificationClientWrapper).start();
    assertThat(apiErrorObserved.get()).isTrue();
    assertThat(apiUnavailableObserved.get()).isFalse();
    assertThat(inFlightObserved.get()).isFalse();
  }

  @Test
  public void startExposureNotifications_liveDataUpdatedOnFailed_isResolutionRequiredApiException() {
    Task<Void> task = Tasks.forException(
        new ApiException(new Status(ExposureNotificationStatusCodes.RESOLUTION_REQUIRED)));
    when(exposureNotificationClientWrapper.start()).thenReturn(task);
    AtomicBoolean apiErrorObserved = new AtomicBoolean(false);
    AtomicBoolean apiUnavailableObserved = new AtomicBoolean(false);
    AtomicReference<Status> resolutionRequiredObserved =
        new AtomicReference<>(Status.RESULT_SUCCESS);
    AtomicBoolean inFlightObserved = new AtomicBoolean();
    exposureNotificationViewModel.getResolutionRequiredLiveEvent()
        .observeForever(ex -> resolutionRequiredObserved.set(ex.getStatus()));
    exposureNotificationViewModel.getApiUnavailableLiveEvent()
        .observeForever(unused -> apiUnavailableObserved.set(true));
    exposureNotificationViewModel.getInFlightLiveData().observeForever(inFlightObserved::set);

    exposureNotificationViewModel.startExposureNotifications();

    verify(exposureNotificationClientWrapper).start();
    assertThat(resolutionRequiredObserved.get().getStatusCode())
        .isEqualTo(ExposureNotificationStatusCodes.RESOLUTION_REQUIRED);
    assertThat(apiErrorObserved.get()).isFalse();
    assertThat(apiUnavailableObserved.get()).isFalse();
    assertThat(inFlightObserved.get()).isTrue();
  }

  @Test
  public void startExposureNotifications_liveDataUpdatedOnFailed_isUnavailableApiException() {
    Task<Void> taskDisabled = Tasks.forException(
        new ApiException(new Status(new ConnectionResult(ConnectionResult.SERVICE_DISABLED), "")));
    Task<Void> taskInvalid = Tasks.forException(
        new ApiException(new Status(new ConnectionResult(ConnectionResult.SERVICE_INVALID), "")));
    Task<Void> taskMissing = Tasks.forException(
        new ApiException(new Status(new ConnectionResult(ConnectionResult.SERVICE_MISSING), "")));
    Task<Void> taskServiceVersionUpdateRequired = Tasks.forException(
        new ApiException(
            new Status(new ConnectionResult(ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED),
                "")));
    when(exposureNotificationClientWrapper.start())
        .thenReturn(taskDisabled, taskInvalid, taskMissing, taskServiceVersionUpdateRequired);
    AtomicBoolean apiErrorObserved = new AtomicBoolean(false);
    AtomicInteger apiUnavailableObserved = new AtomicInteger(0);
    AtomicBoolean resolutionRequiredObserved = new AtomicBoolean(false);
    AtomicBoolean inFlightObserved = new AtomicBoolean();
    exposureNotificationViewModel.getResolutionRequiredLiveEvent()
        .observeForever(ex -> resolutionRequiredObserved.set(true));
    exposureNotificationViewModel.getApiErrorLiveEvent()
        .observeForever(unused -> apiErrorObserved.set(true));
    exposureNotificationViewModel.getApiUnavailableLiveEvent()
        .observeForever(unused -> apiUnavailableObserved.incrementAndGet());
    exposureNotificationViewModel.getInFlightLiveData().observeForever(inFlightObserved::set);

    exposureNotificationViewModel.startExposureNotifications();
    exposureNotificationViewModel.startExposureNotifications();
    exposureNotificationViewModel.startExposureNotifications();
    exposureNotificationViewModel.startExposureNotifications();

    verify(exposureNotificationClientWrapper, times(4)).start();
    assertThat(resolutionRequiredObserved.get()).isFalse();
    assertThat(apiErrorObserved.get()).isFalse();
    assertThat(apiUnavailableObserved.get()).isEqualTo(4);
    assertThat(inFlightObserved.get()).isFalse();
  }

  @Test
  public void startResolutionResultOk_onFailed() {
    Task<Void> task = Tasks.forException(new Exception());
    when(exposureNotificationClientWrapper.start()).thenReturn(task);
    AtomicBoolean atomicBoolean = new AtomicBoolean(false);
    exposureNotificationViewModel.getApiErrorLiveEvent()
        .observeForever(unused -> atomicBoolean.set(true));
    AtomicBoolean inFlight = new AtomicBoolean();
    exposureNotificationViewModel.getInFlightLiveData().observeForever(inFlight::set);

    exposureNotificationViewModel.startResolutionResultOk();

    verify(exposureNotificationClientWrapper).start();
    assertThat(atomicBoolean.get()).isTrue();
    assertThat(inFlight.get()).isFalse();
  }

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
    AtomicReference<Pair<ExposureNotificationState, Boolean>> pairLiveData = new AtomicReference<>(
        Pair.create(ExposureNotificationState.DISABLED, /* isInFlight= */ true));
    exposureNotificationViewModel.getStateLiveData().observeForever(exposureNotificationState::set);
    exposureNotificationViewModel.getInFlightLiveData().observeForever(inFlight::set);
    exposureNotificationViewModel.getStateWithInFlightLiveData()
        .observeForever(pairLiveData::set);

    exposureNotificationViewModel.startResolutionResultOk();

    assertThat(inFlight.get()).isFalse();
    assertThat(exposureNotificationState.get()).isEqualTo(ExposureNotificationState.ENABLED);
    assertThat(pairLiveData.get().first).isEqualTo(ExposureNotificationState.ENABLED);
    assertThat(pairLiveData.get().second).isFalse();
  }

  @Test
  public void startResolutionResultNotOk_liveDataUpdated() {
    AtomicBoolean atomicBoolean = new AtomicBoolean(true);
    exposureNotificationViewModel.getInFlightLiveData()
        .observeForever(atomicBoolean::set);

    exposureNotificationViewModel.startResolutionResultNotOk();

    assertThat(atomicBoolean.get()).isFalse();
  }

  @Test
  public void stopExposureNotifications_onSuccess_enStoppedIsTrueAndStateRefreshed() {
    // GIVEN
    // Configure required mock behavior for all methods that should be called.
    when(exposureNotificationClientWrapper.stop()).thenReturn(TASK_FOR_RESULT_VOID);
    when(exposureNotificationClientWrapper.isEnabled()).thenReturn(TASK_FOR_RESULT_TRUE);
    when(exposureNotificationClientWrapper.getStatus()).thenReturn(TASK_FOR_INACTIVATED);
    // Ensure the enStoppedLiveEvent sends expected updates.
    AtomicBoolean enStopped = new AtomicBoolean(false);
    exposureNotificationViewModel.getEnStoppedLiveEvent().observeForever(enStopped::set);

    // WHEN
    exposureNotificationViewModel.stopExposureNotifications();

    // THEN
    verify(exposureNotificationClientWrapper).stop();
    assertThat(enStopped.get()).isTrue();
    // isEnabled() and getStatus() APIs get called since we call
    // {@link ExposureNotificationViewModel#refreshState()} if stop() succeeds.
    verify(exposureNotificationClientWrapper).isEnabled();
    verify(exposureNotificationClientWrapper).getStatus();
  }

  @Test
  public void stopExposureNotifications_onFailed_enStoppedIsFalseAndStateNotRefreshed() {
    // GIVEN
    // Configure required mock behavior for all methods that should be called.
    Task<Void> task = Tasks.forException(new Exception());
    when(exposureNotificationClientWrapper.stop()).thenReturn(task);
    // Ensure the enStoppedLiveEvent sends expected updates.
    AtomicBoolean enStopped = new AtomicBoolean(true);
    exposureNotificationViewModel.getEnStoppedLiveEvent().observeForever(enStopped::set);

    // WHEN
    exposureNotificationViewModel.stopExposureNotifications();

    // THEN
    verify(exposureNotificationClientWrapper).stop();
    assertThat(enStopped.get()).isFalse();
    // Verify no more EN APIs have been called
    verifyNoMoreInteractions(exposureNotificationClientWrapper);
  }

  @Test
  public void isStateBlockingSharingFlow_blockingStates_returnsTrue() {
    for (ExposureNotificationState state : EN_STATES_BLOCKING_SHARING_FLOW) {
      assertThat(exposureNotificationViewModel.isStateBlockingSharingFlow(state)).isTrue();
    }
  }

  @Test
  public void isStateBlockingSharingFlow_nonBlockingStates_returnsFalse() {
    // GIVEN
    Set<ExposureNotificationState> enStatesNotBlockingSharingFlow =
        EnumSet.allOf(ExposureNotificationState.class);
    enStatesNotBlockingSharingFlow.removeAll(EN_STATES_BLOCKING_SHARING_FLOW);

    // THEN
    for (ExposureNotificationState state : enStatesNotBlockingSharingFlow) {
      assertThat(exposureNotificationViewModel.isStateBlockingSharingFlow(state)).isFalse();
    }
  }

  @Test
  public void isPossibleExposurePresent_noExposure_returnsFalse() {
    // GIVEN
    ExposureClassification noExposure = ExposureClassification.createNoExposureClassification();

    // WHEN
    exposureNotificationSharedPreferences.setExposureClassification(noExposure);

    // THEN
    assertThat(exposureNotificationViewModel.isPossibleExposurePresent()).isFalse();
  }

  @Test
  public void isPossibleExposurePresent_noExposureButRevokedExposure_returnsTrue() {
    // GIVEN
    ExposureClassification noExposure = ExposureClassification.createNoExposureClassification();

    // WHEN
    exposureNotificationSharedPreferences.setExposureClassification(noExposure);
    exposureNotificationSharedPreferences.setIsExposureClassificationRevoked(true);

    // THEN
    assertThat(exposureNotificationViewModel.isPossibleExposurePresent()).isTrue();
  }

  @Test
  public void isPossibleExposurePresent_exposure_returnsTrue() {
    // GIVEN
    ExposureClassification newExposure = ExposureClassification.create(
        /* classificationIndex= */1, /* classificationName= */"", clock.now().toEpochMilli());

    // WHEN
    exposureNotificationSharedPreferences.setExposureClassification(newExposure);

    // THEN
    assertThat(exposureNotificationViewModel.isPossibleExposurePresent()).isTrue();
  }

  @Test
  public void getShouldShowSmsNoticeLiveData_enDisabled_returnsFalseForV2TrueForV3() {
    when(exposureNotificationClientWrapper.isEnabled()).thenReturn(TASK_FOR_RESULT_FALSE);
    when(exposureNotificationClientWrapper.getStatus()).thenReturn(TASK_FOR_ACTIVATED);
    when(exposureNotificationClientWrapper.getPackageConfiguration())
        .thenReturn(Tasks.forResult(new PackageConfigurationBuilder().build()));
    AtomicReference<Boolean> shouldShowSmsNotice = new AtomicReference<>();
    exposureNotificationViewModel.getShouldShowSmsNoticeLiveData()
        .observeForever(shouldShowSmsNotice::set);

    exposureNotificationViewModel.refreshState();

    verify(exposureNotificationClientWrapper).isEnabled();
    verify(exposureNotificationClientWrapper).getPackageConfiguration();

    if (BuildUtils.getType() == Type.V2) {
      assertThat(shouldShowSmsNotice.get()).isEqualTo(false);
    } else {
      assertThat(shouldShowSmsNotice.get()).isEqualTo(true);
    }
  }

  @Test
  public void getShouldShowSmsNoticeLiveData_shownInApp_returnsFalse() {
    when(exposureNotificationClientWrapper.isEnabled()).thenReturn(TASK_FOR_RESULT_TRUE);
    when(exposureNotificationClientWrapper.getStatus()).thenReturn(TASK_FOR_ACTIVATED);
    exposureNotificationSharedPreferences.markInAppSmsNoticeSeen();
    when(exposureNotificationClientWrapper.getPackageConfiguration())
        .thenReturn(Tasks.forResult(new PackageConfigurationBuilder().build()));
    AtomicReference<Boolean> shouldShowSmsNotice = new AtomicReference<>();
    exposureNotificationViewModel.getShouldShowSmsNoticeLiveData()
        .observeForever(shouldShowSmsNotice::set);

    exposureNotificationViewModel.refreshState();

    verify(exposureNotificationClientWrapper).isEnabled();
    verify(exposureNotificationClientWrapper).getPackageConfiguration();
    assertThat(shouldShowSmsNotice.get()).isEqualTo(false);
  }

  @Test
  public void getShouldShowSmsNoticeLiveData_shownPackageConfiguration_returnsFalse() {
    when(exposureNotificationClientWrapper.isEnabled()).thenReturn(TASK_FOR_RESULT_TRUE);
    when(exposureNotificationClientWrapper.getStatus()).thenReturn(TASK_FOR_ACTIVATED);
    Bundle bundle = new Bundle();
    bundle.putBoolean(PackageConfigurationHelper.SMS_NOTICE, true);
    when(exposureNotificationClientWrapper.getPackageConfiguration())
        .thenReturn(Tasks.forResult(new PackageConfigurationBuilder().setValues(bundle).build()));
    AtomicReference<Boolean> shouldShowSmsNotice = new AtomicReference<>();
    exposureNotificationViewModel.getShouldShowSmsNoticeLiveData()
        .observeForever(shouldShowSmsNotice::set);

    exposureNotificationViewModel.refreshState();

    verify(exposureNotificationClientWrapper).isEnabled();
    verify(exposureNotificationClientWrapper).getPackageConfiguration();
    assertThat(shouldShowSmsNotice.get()).isEqualTo(false);
  }

  @Test
  public void getShouldShowSmsNoticeLiveData_packageConfigurationStillComputing_returnsFalse() {
    when(exposureNotificationClientWrapper.isEnabled()).thenReturn(TASK_FOR_RESULT_TRUE);
    when(exposureNotificationClientWrapper.getStatus()).thenReturn(TASK_FOR_ACTIVATED);
    when(exposureNotificationClientWrapper.getPackageConfiguration())
        .thenReturn(Tasks.forException(new Exception()));
    AtomicReference<Boolean> shouldShowSmsNotice = new AtomicReference<>();
    exposureNotificationViewModel.getShouldShowSmsNoticeLiveData()
        .observeForever(shouldShowSmsNotice::set);

    exposureNotificationViewModel.refreshState();

    verify(exposureNotificationClientWrapper).isEnabled();
    verify(exposureNotificationClientWrapper).getPackageConfiguration();
    assertThat(shouldShowSmsNotice.get()).isEqualTo(false);
  }

  @Test
  public void getShouldShowSmsNoticeLiveData_enEnabledAndNotShown_returnsTrue() {
    when(exposureNotificationClientWrapper.isEnabled()).thenReturn(TASK_FOR_RESULT_TRUE);
    when(exposureNotificationClientWrapper.getStatus()).thenReturn(TASK_FOR_ACTIVATED);
    when(exposureNotificationClientWrapper.getPackageConfiguration())
        .thenReturn(Tasks.forResult(new PackageConfigurationBuilder().build()));
    AtomicReference<Boolean> shouldShowSmsNotice = new AtomicReference<>();
    exposureNotificationViewModel.getShouldShowSmsNoticeLiveData()
        .observeForever(shouldShowSmsNotice::set);

    exposureNotificationViewModel.refreshState();

    verify(exposureNotificationClientWrapper).isEnabled();
    verify(exposureNotificationClientWrapper).getPackageConfiguration();
    assertThat(shouldShowSmsNotice.get()).isEqualTo(true);
  }

  @Test
  public void refreshNotificationsEnabledState_notificationDisabled_areNotificationsEnabledLiveDataUpdated() {
    // GIVEN
    notificationManager.setNotificationsEnabled(false);
    AtomicReference<Boolean> areNotificationEnabled = new AtomicReference<>();
    exposureNotificationViewModel.getAreNotificationsEnabledLiveData()
        .observeForever(b -> areNotificationEnabled.set(b.isPresent() && b.get()));

    // WHEN
    exposureNotificationViewModel.refreshNotificationsEnabledState(context);

    // THEN
    assertThat(areNotificationEnabled.get()).isFalse();
  }

  @Test
  public void refreshNotificationsEnabledState_notificationEnabled_areNotificationsEnabledLiveDataUpdated() {
    // GIVEN
    notificationManager.setNotificationsEnabled(true);

    AtomicReference<Boolean> areNotificationEnabled = new AtomicReference<>();
    exposureNotificationViewModel.getAreNotificationsEnabledLiveData()
        .observeForever(b -> areNotificationEnabled.set(b.isPresent() && b.get()));

    // WHEN
    exposureNotificationViewModel.refreshNotificationsEnabledState(context);

    // THEN
    assertThat(areNotificationEnabled.get()).isTrue();
  }

}