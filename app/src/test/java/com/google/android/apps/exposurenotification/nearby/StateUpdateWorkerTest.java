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

package com.google.android.apps.exposurenotification.nearby;

import static com.google.android.apps.exposurenotification.nearby.StateUpdatedWorker.BLE_LOC_OFF_UNTIL_NOTIFICATION_THRESHOLD;
import static com.google.android.gms.nearby.exposurenotification.ExposureNotificationStatus.BLUETOOTH_DISABLED;
import static com.google.android.gms.nearby.exposurenotification.ExposureNotificationStatus.BLUETOOTH_SUPPORT_UNKNOWN;
import static com.google.android.gms.nearby.exposurenotification.ExposureNotificationStatus.LOCATION_DISABLED;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.robolectric.Shadows.shadowOf;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.work.ListenableWorker.Result;
import androidx.work.WorkerParameters;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.NotificationHelper;
import com.google.android.apps.exposurenotification.logging.AnalyticsLogger;
import com.google.android.apps.exposurenotification.riskcalculation.DailySummaryRiskCalculator;
import com.google.android.apps.exposurenotification.riskcalculation.ExposureClassification;
import com.google.android.apps.exposurenotification.riskcalculation.RevocationDetector;
import com.google.android.apps.exposurenotification.storage.DbModule;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationDatabase;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.apps.exposurenotification.storage.ExposureRepository;
import com.google.android.apps.exposurenotification.testsupport.ExposureNotificationRules;
import com.google.android.apps.exposurenotification.testsupport.FakeClock;
import com.google.android.apps.exposurenotification.testsupport.InMemoryDb;
import com.google.android.gms.nearby.exposurenotification.DailySummariesConfig;
import com.google.android.gms.tasks.Tasks;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.testing.TestingExecutors;
import dagger.hilt.android.testing.BindValue;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import dagger.hilt.android.testing.UninstallModules;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowNotificationManager;
import org.threeten.bp.Duration;
import org.threeten.bp.Instant;

/**
 * This unit test checks behaviours of StateUpdateWorker that are not focused on specific types of
 * exposure notifications. For a exposure notification focused test, please see
 * ExposureNotificationsIntegrationTest.java.
 */
@RunWith(AndroidJUnit4.class)
@HiltAndroidTest
@Config(application = HiltTestApplication.class)
@UninstallModules({DbModule.class})
public class StateUpdateWorkerTest {

  @Rule
  public ExposureNotificationRules rules = ExposureNotificationRules.forTest(this)
      .withMocks()
      .build();

  @BindValue
  ExposureNotificationDatabase db = InMemoryDb.create();

  @Inject
  ExposureRepository exposureRepository;
  @Inject
  ExposureNotificationSharedPreferences exposureNotificationSharedPreferences;
  @Inject
  NotificationHelper notificationHelper;

  @Mock
  AnalyticsLogger analyticsLogger;
  @Mock
  WorkerParameters workerParameters;
  @Mock
  DailySummaryRiskCalculator dailySummaryRiskCalculator;
  @Mock
  RevocationDetector revocationDetector;
  @Mock
  DailySummariesConfig dailySummariesConfig;
  @Mock
  ExposureNotificationClientWrapper exposureNotificationClientWrapper;

  Context context;
  FakeClock clock = new FakeClock();
  StateUpdatedWorker stateUpdatedWorker;
  NotificationManager notificationManager;
  ShadowNotificationManager shadowNotificationManager;

  @Before
  public void setUp() {
    rules.hilt().inject();
    context = ApplicationProvider.getApplicationContext();

    // Instantiate the real notification helper and observe the system's notifications
    notificationManager = (NotificationManager) context
        .getSystemService(Context.NOTIFICATION_SERVICE);
    shadowNotificationManager = shadowOf(notificationManager);

    // Use testing versions for all the threading dependencies
    ExecutorService backgroundExecutor = MoreExecutors.newDirectExecutorService();
    ScheduledExecutorService scheduledExecutor = TestingExecutors.sameThreadScheduledExecutor();

    // Instantiate the actual object under test
    stateUpdatedWorker = spy(new StateUpdatedWorker(context, workerParameters, exposureRepository,
        exposureNotificationClientWrapper, exposureNotificationSharedPreferences,
        revocationDetector, dailySummariesConfig, dailySummaryRiskCalculator, notificationHelper,
        backgroundExecutor, scheduledExecutor, analyticsLogger, clock));
  }

  /**
   * Test that verifies that maybeShowEdgeCaseNotification is called if there is no exposure
   */
  @Test
  public void stateUpdateWorker_noExposureNotification_callsMaybeShowEdgeCaseNotification()
      throws Exception {
    // GIVEN no exposure
    doReturn(Tasks.forResult(null))
        .when(exposureNotificationClientWrapper).getDailySummaries(any());
    doReturn(Tasks.forResult(null))
        .when(exposureNotificationClientWrapper).getStatus();
    doReturn(false)
        .when(stateUpdatedWorker).retrievePreviousExposuresAndCheckForExposureUpdate(any(), any());
    doNothing()
        .when(stateUpdatedWorker).maybeShowEdgeCaseNotification(any());

    // WHEN starting stateUpdateWorker
    Result result = stateUpdatedWorker.startWork().get();

    // THEN maybeShowEdgeCaseNotification is invoked, and the task completes successfully
    verify(stateUpdatedWorker, times(1)).maybeShowEdgeCaseNotification(any());
    assertThat(result).isEqualTo(Result.success());
  }

  /**
   * Test that verifies that maybeShowEdgeCaseNotification is not called if there an exposure
   */
  @Test
  public void stateUpdateWorker_exposureNotification_doesNotCallMaybeShowEdgeCaseNotification()
      throws Exception {
    // GIVEN exposure
    doReturn(Tasks.forResult(null))
        .when(exposureNotificationClientWrapper).getDailySummaries(any());
    doReturn(Tasks.forResult(null))
        .when(exposureNotificationClientWrapper).getStatus();
    doReturn(true)
        .when(stateUpdatedWorker).retrievePreviousExposuresAndCheckForExposureUpdate(any(), any());

    // WHEN starting stateUpdateWorker
    Result result = stateUpdatedWorker.startWork().get();

    // THEN maybeShowEdgeCaseNotification is not invoked, and the task completes successfully
    verify(stateUpdatedWorker, never()).maybeShowEdgeCaseNotification(any());
    assertThat(result).isEqualTo(Result.success());
  }

  /**
   * No notification is send if this is called twice in 23 hours
   */
  @Test
  public void maybeShowEdgeCaseNotification_calledTwiceIn23h_noNotification()
      throws Exception {
    clock.set(Instant.now());
    stateUpdatedWorker.maybeShowEdgeCaseNotification(ImmutableSet.of(BLUETOOTH_DISABLED));
    clock.advanceBy(BLE_LOC_OFF_UNTIL_NOTIFICATION_THRESHOLD.minusHours(1));
    stateUpdatedWorker.maybeShowEdgeCaseNotification(ImmutableSet.of(BLUETOOTH_DISABLED));

    assertNoNotificationTriggered();
  }

  /**
   * No notification is send if this is called twice in 24 hours and Ble and location both on
   */
  @Test
  public void maybeShowEdgeCaseNotification_calledTwiceIn24hBleLocOn_noNotification()
      throws Exception {
    clock.set(Instant.now());
    stateUpdatedWorker.maybeShowEdgeCaseNotification(ImmutableSet.of());
    clock.advanceBy(BLE_LOC_OFF_UNTIL_NOTIFICATION_THRESHOLD);
    stateUpdatedWorker.maybeShowEdgeCaseNotification(ImmutableSet.of());

    assertNoNotificationTriggered();
  }

  /**
   * Ble notification is triggered if this is called twice in 24 hours and ble is off
   */
  @Test
  public void maybeShowEdgeCaseNotification_calledTwiceIn24hBleOff_bleNotification()
      throws Exception {
    clock.set(Instant.now());
    stateUpdatedWorker.maybeShowEdgeCaseNotification(ImmutableSet.of(BLUETOOTH_DISABLED));
    clock.advanceBy(BLE_LOC_OFF_UNTIL_NOTIFICATION_THRESHOLD);
    stateUpdatedWorker.maybeShowEdgeCaseNotification(ImmutableSet.of(BLUETOOTH_DISABLED));

    assertNotificationTriggered(R.string.updated_permission_disabled_notification_title,
        R.string.updated_bluetooth_state_notification);
  }

  /**
   * Ble notification is triggered if this is called twice in 24 hours and ble is unsupported
   */
  @Test
  public void maybeShowEdgeCaseNotification_calledTwiceIn24hBleUnsupported_bleNotification()
      throws Exception {
    clock.set(Instant.now());
    stateUpdatedWorker.maybeShowEdgeCaseNotification(ImmutableSet.of(BLUETOOTH_SUPPORT_UNKNOWN));
    clock.advanceBy(BLE_LOC_OFF_UNTIL_NOTIFICATION_THRESHOLD);
    stateUpdatedWorker.maybeShowEdgeCaseNotification(ImmutableSet.of(BLUETOOTH_SUPPORT_UNKNOWN));

    assertNotificationTriggered(R.string.updated_permission_disabled_notification_title,
        R.string.updated_bluetooth_state_notification);
  }

  /**
   * Location notification is triggered if this is called twice in 24 hours and location is off
   */
  @Test
  public void maybeShowEdgeCaseNotification_calledTwiceIn24hLocOff_locNotification()
      throws Exception {
    clock.set(Instant.now());
    stateUpdatedWorker.maybeShowEdgeCaseNotification(ImmutableSet.of(LOCATION_DISABLED));
    clock.advanceBy(BLE_LOC_OFF_UNTIL_NOTIFICATION_THRESHOLD);
    stateUpdatedWorker.maybeShowEdgeCaseNotification(ImmutableSet.of(LOCATION_DISABLED));

    assertNotificationTriggered(R.string.updated_permission_disabled_notification_title,
        R.string.updated_location_state_notification);
  }

  /**
   * Ble and location notification is triggered if this is called twice in 24 hours and ble/loc off
   */
  @Test
  public void maybeShowEdgeCaseNotification_calledTwiceIn24hBleLocOff_bleLocNotification()
      throws Exception {
    clock.set(Instant.now());
    stateUpdatedWorker.maybeShowEdgeCaseNotification(
        ImmutableSet.of(BLUETOOTH_DISABLED, LOCATION_DISABLED));
    clock.advanceBy(BLE_LOC_OFF_UNTIL_NOTIFICATION_THRESHOLD);
    stateUpdatedWorker.maybeShowEdgeCaseNotification(
        ImmutableSet.of(BLUETOOTH_DISABLED, LOCATION_DISABLED));

    assertNotificationTriggered(R.string.updated_permission_disabled_notification_title,
        R.string.updated_bluetooth_location_state_notification);
  }

  /**
   * No notification is triggered if there is an edge case, but it is resolved after within 24 hours
   */
  @Test
  public void maybeShowEdgeCaseNotification_resolvedWithin24Hours_noNotification()
      throws Exception {
    clock.set(Instant.now());
    stateUpdatedWorker.maybeShowEdgeCaseNotification(ImmutableSet.of(BLUETOOTH_DISABLED));
    clock.advanceBy(BLE_LOC_OFF_UNTIL_NOTIFICATION_THRESHOLD.dividedBy(2));
    stateUpdatedWorker.maybeShowEdgeCaseNotification(ImmutableSet.of());
    clock.advanceBy(BLE_LOC_OFF_UNTIL_NOTIFICATION_THRESHOLD.dividedBy(2));
    stateUpdatedWorker.maybeShowEdgeCaseNotification(ImmutableSet.of(BLUETOOTH_DISABLED));

    assertNoNotificationTriggered();
  }

  /**
   * A notification is triggered if there is an edge case, it is resolved, but another edge case
   * happens and sticks around 24h
   */
  @Test
  public void maybeShowEdgeCaseNotification_resolvedWithin24HoursNewCaseFor24h_showNotification()
      throws Exception {
    clock.set(Instant.now());
    stateUpdatedWorker.maybeShowEdgeCaseNotification(ImmutableSet.of(BLUETOOTH_DISABLED));
    clock.advanceBy(BLE_LOC_OFF_UNTIL_NOTIFICATION_THRESHOLD.dividedBy(2));
    stateUpdatedWorker.maybeShowEdgeCaseNotification(ImmutableSet.of());
    clock.advanceBy(BLE_LOC_OFF_UNTIL_NOTIFICATION_THRESHOLD.dividedBy(2));
    stateUpdatedWorker.maybeShowEdgeCaseNotification(ImmutableSet.of(BLUETOOTH_DISABLED));
    clock.advanceBy(BLE_LOC_OFF_UNTIL_NOTIFICATION_THRESHOLD);
    stateUpdatedWorker.maybeShowEdgeCaseNotification(ImmutableSet.of(BLUETOOTH_DISABLED));

    assertNotificationTriggered(R.string.updated_permission_disabled_notification_title,
        R.string.updated_bluetooth_state_notification);
  }

  /**
   * There would usually be a notification, but there was an exposure within the last 14 days,
   * so we do not trigger a notification
   */
  @Test
  public void maybeShowEdgeCaseNotification_exposureInLast14days_noNotification()
      throws Exception {
    exposureNotificationSharedPreferences.setExposureClassification(
        ExposureClassification.create(1, "Classification 1", 42));
    clock.set(Instant.now());
    stateUpdatedWorker.maybeShowEdgeCaseNotification(ImmutableSet.of(BLUETOOTH_DISABLED));
    clock.advanceBy(BLE_LOC_OFF_UNTIL_NOTIFICATION_THRESHOLD);
    stateUpdatedWorker.maybeShowEdgeCaseNotification(ImmutableSet.of(BLUETOOTH_DISABLED));

    assertNoNotificationTriggered();
  }

  /**
   * There would usually be a notification, but there was a revocation within the last 14 days,
   * so we do not trigger a notification
   */
  @Test
  public void maybeShowEdgeCaseNotification_revocationInLast14days_noNotification()
      throws Exception {
    clock.set(Instant.now());
    exposureNotificationSharedPreferences.setIsExposureClassificationRevoked(true);
    stateUpdatedWorker.maybeShowEdgeCaseNotification(ImmutableSet.of(BLUETOOTH_DISABLED));
    clock.advanceBy(BLE_LOC_OFF_UNTIL_NOTIFICATION_THRESHOLD);
    stateUpdatedWorker.maybeShowEdgeCaseNotification(ImmutableSet.of(BLUETOOTH_DISABLED));

    assertNoNotificationTriggered();
  }

  /**
   * We should only show a notification once per user. This test verifies that this is the case.
   */
  @Test
  public void maybeShowEdgeCaseNotification_notifiedAnotherEdgeCaseAfterAWeek_onlyOneNotification()
      throws Exception {
    // GIVEN Triggered a first notification and dismissed it
    clock.set(Instant.now());
    stateUpdatedWorker.maybeShowEdgeCaseNotification(
        ImmutableSet.of(BLUETOOTH_DISABLED, LOCATION_DISABLED));
    clock.advanceBy(BLE_LOC_OFF_UNTIL_NOTIFICATION_THRESHOLD);
    stateUpdatedWorker.maybeShowEdgeCaseNotification(
        ImmutableSet.of(BLUETOOTH_DISABLED, LOCATION_DISABLED));
    assertNotificationTriggered(R.string.updated_permission_disabled_notification_title,
        R.string.updated_bluetooth_location_state_notification);
    notificationManager.cancelAll();

    // WHEN One week later, the same scenario is repeated
    clock.advanceBy(Duration.ofDays(7));
    stateUpdatedWorker.maybeShowEdgeCaseNotification(
        ImmutableSet.of(BLUETOOTH_DISABLED, LOCATION_DISABLED));
    clock.advanceBy(BLE_LOC_OFF_UNTIL_NOTIFICATION_THRESHOLD);
    stateUpdatedWorker.maybeShowEdgeCaseNotification(
        ImmutableSet.of(BLUETOOTH_DISABLED, LOCATION_DISABLED));

    // THEN No second notification should be shown
    assertNoNotificationTriggered();
  }

  /**
   * Helper to verify that the notification was fired
   */
  private void assertNotificationTriggered(int titleStringResource, int descStringResource) {
    if (shadowNotificationManager.size() == 0) {
      fail("Expected: " + context.getString(titleStringResource) + ", but got no notification");
    }
    Notification notification = shadowNotificationManager.getNotification(0);
    assertThat(shadowOf(notification).getContentTitle())
        .isEqualTo(context.getString(titleStringResource));
    assertThat(shadowOf(notification).getContentText())
        .isEqualTo(context.getString(descStringResource));
  }

  private void assertNoNotificationTriggered() {
    if (shadowNotificationManager.size() != 0) {
      Notification notification = shadowNotificationManager.getNotification(0);
      fail("Expected no notification, but got " + shadowOf(notification).getContentTitle());
    }
  }
}
