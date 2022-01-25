/*
 * Copyright 2021 Google LLC
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

package com.google.android.apps.exposurenotification.work;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.os.Bundle;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.apps.exposurenotification.common.CleanupHelper;
import com.google.android.apps.exposurenotification.nearby.ExposureNotificationClientWrapper;
import com.google.android.apps.exposurenotification.nearby.PackageConfigurationHelper;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.apps.exposurenotification.testsupport.ExposureNotificationRules;
import com.google.android.apps.exposurenotification.work.WorkerStartupManager.IsEnabledWithStartupTasksException;
import com.google.android.apps.exposurenotification.work.WorkerStartupManager.TurndownException;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.nearby.exposurenotification.ExposureNotificationStatus;
import com.google.android.gms.nearby.exposurenotification.PackageConfiguration;
import com.google.android.gms.nearby.exposurenotification.PackageConfiguration.PackageConfigurationBuilder;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.testing.TestingExecutors;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.annotation.Config;

@HiltAndroidTest
@RunWith(AndroidJUnit4.class)
@Config(application = HiltTestApplication.class)
public class WorkerStartupManagerTest {

  @Rule
  public ExposureNotificationRules rules =
      ExposureNotificationRules.forTest(this).withMocks().build();

  private static final Task<Set<ExposureNotificationStatus>> TASK_FOR_ACTIVATED =
      Tasks.forResult(ImmutableSet.of(ExposureNotificationStatus.ACTIVATED));
  private static final Task<Set<ExposureNotificationStatus>> TASK_FOR_INACTIVATED =
      Tasks.forResult(ImmutableSet.of(ExposureNotificationStatus.INACTIVATED));
  private static final Task<Set<ExposureNotificationStatus>> TASK_FOR_EN_TURNDOWN =
      Tasks.forResult(ImmutableSet.of(ExposureNotificationStatus.EN_NOT_SUPPORT));
  private static final Task<Set<ExposureNotificationStatus>> TASK_FOR_EN_TURNDOWN_FOR_REGION =
      Tasks.forResult(ImmutableSet.of(ExposureNotificationStatus.NOT_IN_ALLOWLIST));

  @Inject
  PackageConfigurationHelper packageConfigurationHelper;
  @Inject
  ExposureNotificationSharedPreferences exposureNotificationSharedPreferences;

  @Mock
  CleanupHelper cleanupHelper;
  @Mock
  ExposureNotificationClientWrapper exposureNotificationClientWrapper;

  private WorkerStartupManager workerStartupManager;

  @Before
  public void setUp() throws Exception {
    rules.hilt().inject();

    // Stub cleanupHelper APIs.
    doNothing().when(cleanupHelper).deleteOutdatedData();
    doNothing().when(cleanupHelper).resetOutdatedData();
    when(cleanupHelper.deleteObsoleteStorageForTurnDown())
        .thenReturn(Futures.immediateVoidFuture());
    when(cleanupHelper.cancelPendingRestoreNotificationsAndJob())
        .thenReturn(Futures.immediateVoidFuture());

    workerStartupManager = new WorkerStartupManager(
        exposureNotificationClientWrapper,
        MoreExecutors.newDirectExecutorService(),
        TestingExecutors.sameThreadScheduledExecutor(),
        packageConfigurationHelper,
        cleanupHelper
    );
  }

  @Test
  public void getIsEnabledWithStartupTasks_enNotEnabledButNotTurnedDown_returnsFalseAndDeletesOutdated()
      throws Exception {
    when(exposureNotificationClientWrapper.isEnabled()).thenReturn(Tasks.forResult(false));
    when(exposureNotificationClientWrapper.getStatus()).thenReturn(TASK_FOR_INACTIVATED);

    boolean isEnabledWithStartupTasks = workerStartupManager.getIsEnabledWithStartupTasks().get();

    assertThat(isEnabledWithStartupTasks).isFalse();
    verify(cleanupHelper).deleteOutdatedData();
    verify(cleanupHelper).resetOutdatedData();
    verify(cleanupHelper, never()).deleteObsoleteStorageForTurnDown();
  }

  @Test
  public void getIsEnabledWithStartupTasks_exception_exceptionThrownAndCleansUpOutdated() {
    when(exposureNotificationClientWrapper.isEnabled())
        .thenReturn(Tasks.forException(new Exception()));
    when(exposureNotificationClientWrapper.getStatus()).thenReturn(TASK_FOR_INACTIVATED);

    Exception thrown = assertThrows(ExecutionException.class,
        () -> workerStartupManager.getIsEnabledWithStartupTasks().get());
    assertThat(thrown.getCause()).isInstanceOf(IsEnabledWithStartupTasksException.class);
    verify(cleanupHelper).deleteOutdatedData();
    verify(cleanupHelper).resetOutdatedData();
    verify(cleanupHelper, never()).deleteObsoleteStorageForTurnDown();
  }

  @Test
  public void getIsEnabledWithStartupTasks_exceptionAndEnTurnedDownForRegion_turndownWorkTriggered() {
    when(exposureNotificationClientWrapper.isEnabled())
        .thenReturn(Tasks.forException(new Exception()));
    when(exposureNotificationClientWrapper.getStatus()).thenReturn(TASK_FOR_EN_TURNDOWN_FOR_REGION);

    Exception thrown = assertThrows(ExecutionException.class,
        () -> workerStartupManager.getIsEnabledWithStartupTasks().get());
    assertThat(thrown.getCause()).isInstanceOf(IsEnabledWithStartupTasksException.class);
    verify(cleanupHelper).deleteOutdatedData();
    verify(cleanupHelper).resetOutdatedData();
    verify(cleanupHelper).deleteObsoleteStorageForTurnDown();
    verify(cleanupHelper).cancelPendingRestoreNotificationsAndJob();
  }

  @Test
  public void getIsEnabledWithStartupTasks_exceptionAndEnTurnedDown_turndownWorkTriggered() {
    when(exposureNotificationClientWrapper.isEnabled())
        .thenReturn(Tasks.forException(new Exception()));
    when(exposureNotificationClientWrapper.getStatus()).thenReturn(TASK_FOR_EN_TURNDOWN);

    Exception thrown = assertThrows(ExecutionException.class,
        () -> workerStartupManager.getIsEnabledWithStartupTasks().get());
    assertThat(thrown.getCause()).isInstanceOf(IsEnabledWithStartupTasksException.class);
    verify(cleanupHelper).deleteOutdatedData();
    verify(cleanupHelper).resetOutdatedData();
    verify(cleanupHelper).deleteObsoleteStorageForTurnDown();
    verify(cleanupHelper).cancelPendingRestoreNotificationsAndJob();
  }

  @Test
  public void getIsEnabledWithStartupTasks_enEnabledGetPckgConfigThrowsException_returnsTrueAndCleansUpOutdated()
      throws Exception {
    when(exposureNotificationClientWrapper.isEnabled()).thenReturn(Tasks.forResult(true));
    when(exposureNotificationClientWrapper.getPackageConfiguration())
        .thenReturn(Tasks.forException(new Exception()));
    when(exposureNotificationClientWrapper.getStatus()).thenReturn(TASK_FOR_ACTIVATED);

    boolean isEnabledWithStartupTasks = workerStartupManager.getIsEnabledWithStartupTasks().get();

    assertThat(isEnabledWithStartupTasks).isTrue();
    verify(cleanupHelper).deleteOutdatedData();
    verify(cleanupHelper).resetOutdatedData();
    verify(cleanupHelper, never()).deleteObsoleteStorageForTurnDown();
  }

  @Test
  public void getIsEnabledWithStartupTasks_enEnabledGetPckgConfigSucceeds_returnsTrueAndCleansUpOutdated()
      throws Exception {
    when(exposureNotificationClientWrapper.isEnabled()).thenReturn(Tasks.forResult(true));
    when(exposureNotificationClientWrapper.getPackageConfiguration())
        .thenReturn(Tasks.forResult(new PackageConfigurationBuilder().build()));
    when(exposureNotificationClientWrapper.getStatus()).thenReturn(TASK_FOR_ACTIVATED);

    boolean isEnabledWithStartupTasks = workerStartupManager.getIsEnabledWithStartupTasks().get();

    assertThat(isEnabledWithStartupTasks).isTrue();
    verify(cleanupHelper).deleteOutdatedData();
    verify(cleanupHelper).resetOutdatedData();
    verify(cleanupHelper, never()).deleteObsoleteStorageForTurnDown();
  }

  @Test
  public void getIsEnabledWithStartupTasks_packageConfigWithSmsNoticeSeen_updatesSmsNoticeSharedPref()
      throws Exception {
    assertThat(exposureNotificationSharedPreferences.isPlaySmsNoticeSeen()).isFalse();
    when(exposureNotificationClientWrapper.isEnabled()).thenReturn(Tasks.forResult(true));
    when(exposureNotificationClientWrapper.getStatus()).thenReturn(TASK_FOR_ACTIVATED);
    Bundle bundle = new Bundle();
    bundle.putBoolean(PackageConfigurationHelper.SMS_NOTICE, true);
    PackageConfiguration packageConfiguration =
        new PackageConfigurationBuilder().setValues(bundle).build();
    when(exposureNotificationClientWrapper.getPackageConfiguration())
        .thenReturn(Tasks.forResult(packageConfiguration));

    boolean isEnabledWithStartupTasks = workerStartupManager.getIsEnabledWithStartupTasks().get();

    assertThat(isEnabledWithStartupTasks).isTrue();
    assertThat(exposureNotificationSharedPreferences.isPlaySmsNoticeSeen()).isTrue();
    verify(cleanupHelper).deleteOutdatedData();
    verify(cleanupHelper).resetOutdatedData();
    verify(cleanupHelper, never()).deleteObsoleteStorageForTurnDown();
  }

  @Test
  public void getIsEnabledWithStartupTasks_enNotEnabledButNotTurnedDown_doesNotChangeSharedPrefsAndDoesNotCleanupStorage()
      throws Exception {
    exposureNotificationSharedPreferences.setPlaySmsNoticeSeen(true);
    when(exposureNotificationClientWrapper.isEnabled()).thenReturn(Tasks.forResult(false));
    when(exposureNotificationClientWrapper.getStatus()).thenReturn(TASK_FOR_INACTIVATED);

    boolean isEnabledWithStartupTasks = workerStartupManager.getIsEnabledWithStartupTasks().get();

    assertThat(isEnabledWithStartupTasks).isFalse();
    assertThat(exposureNotificationSharedPreferences.isPlaySmsNoticeSeen()).isTrue();
    verify(cleanupHelper).deleteOutdatedData();
    verify(cleanupHelper).resetOutdatedData();
    verify(cleanupHelper, never()).deleteObsoleteStorageForTurnDown();
  }

  @Test
  public void getIsEnabledWithStartupTasks_enTurnedDown_obsoleteDataCleanedUpAndRestoreWorkDismissed()
      throws Exception {
    when(exposureNotificationClientWrapper.isEnabled()).thenReturn(Tasks.forResult(false));
    when(exposureNotificationClientWrapper.getStatus()).thenReturn(TASK_FOR_EN_TURNDOWN);

    boolean isEnabledWithStartupTasks = workerStartupManager.getIsEnabledWithStartupTasks().get();

    assertThat(isEnabledWithStartupTasks).isFalse();
    verify(exposureNotificationClientWrapper).isEnabled();
    verify(exposureNotificationClientWrapper).getStatus();
    verify(cleanupHelper).deleteOutdatedData();
    verify(cleanupHelper).resetOutdatedData();
    verify(cleanupHelper).deleteObsoleteStorageForTurnDown();
    verify(cleanupHelper).cancelPendingRestoreNotificationsAndJob();
  }

  @Test
  public void getIsEnabledWithStartupTasks_enTurnedDownForRegion_obsoleteDataCleanedUpAndRestoreWorkDismissed()
      throws Exception {
    when(exposureNotificationClientWrapper.isEnabled()).thenReturn(Tasks.forResult(false));
    when(exposureNotificationClientWrapper.getStatus()).thenReturn(TASK_FOR_EN_TURNDOWN_FOR_REGION);

    boolean isEnabledWithStartupTasks = workerStartupManager.getIsEnabledWithStartupTasks().get();

    assertThat(isEnabledWithStartupTasks).isFalse();
    verify(exposureNotificationClientWrapper).isEnabled();
    verify(exposureNotificationClientWrapper).getStatus();
    verify(cleanupHelper).deleteOutdatedData();
    verify(cleanupHelper).resetOutdatedData();
    verify(cleanupHelper).deleteObsoleteStorageForTurnDown();
    verify(cleanupHelper).cancelPendingRestoreNotificationsAndJob();
  }

  @Test
  public void getIsEnabledWithStartupTasks_enDisabledAndGetStatusThrowsException_obsoleteStorageNotCleanedUp() {
    when(exposureNotificationClientWrapper.isEnabled()).thenReturn(Tasks.forResult(false));
    when(exposureNotificationClientWrapper.getStatus())
        .thenReturn(Tasks.forException(new ApiException(Status.RESULT_INTERNAL_ERROR)));

    Exception thrown = assertThrows(ExecutionException.class,
        () -> workerStartupManager.getIsEnabledWithStartupTasks().get());
    assertThat(thrown.getCause()).isInstanceOf(IsEnabledWithStartupTasksException.class);
    assertThat(thrown.getCause().getCause()).isInstanceOf(TurndownException.class);
    verify(exposureNotificationClientWrapper).isEnabled();
    verify(exposureNotificationClientWrapper).getStatus();
    verify(cleanupHelper).deleteOutdatedData();
    verify(cleanupHelper).resetOutdatedData();
    verify(cleanupHelper, never()).deleteObsoleteStorageForTurnDown();
    verify(cleanupHelper, never()).cancelPendingRestoreNotificationsAndJob();
  }

}
