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

package com.google.android.apps.exposurenotification.restore;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.work.ListenableWorker.Result;
import androidx.work.WorkerParameters;
import com.google.android.apps.exposurenotification.common.NotificationHelper;
import com.google.android.apps.exposurenotification.nearby.ExposureNotificationClientWrapper;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.apps.exposurenotification.testsupport.ExposureNotificationRules;
import com.google.android.gms.nearby.exposurenotification.ExposureNotificationStatus;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.testing.TestingExecutors;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import java.util.Set;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.robolectric.annotation.Config;

/**
 * This unit test verifies the behavior of RestoreNotificationWorker.
 */
@RunWith(AndroidJUnit4.class)
@HiltAndroidTest
@Config(application = HiltTestApplication.class)
public class RestoreNotificationWorkerTest {

  private final Context context = ApplicationProvider.getApplicationContext();

  private static final Task<Set<ExposureNotificationStatus>> TASK_FOR_ACTIVATED =
      Tasks.forResult(ImmutableSet.of(ExposureNotificationStatus.ACTIVATED));
  private static final Task<Set<ExposureNotificationStatus>> TASK_FOR_EN_TURNDOWN =
      Tasks.forResult(ImmutableSet.of(ExposureNotificationStatus.EN_NOT_SUPPORT));
  private static final Task<Set<ExposureNotificationStatus>> TASK_FOR_EN_TURNDOWN_FOR_REGION =
      Tasks.forResult(ImmutableSet.of(ExposureNotificationStatus.NOT_IN_ALLOWLIST));

  @Rule
  public ExposureNotificationRules rules = ExposureNotificationRules.forTest(this)
      .withMocks()
      .build();

  @Inject
  ExposureNotificationSharedPreferences exposureNotificationSharedPreferences;
  @Inject
  NotificationHelper notificationHelper;

  @Mock
  WorkerParameters workerParameters;
  @Mock
  ExposureNotificationClientWrapper exposureNotificationClientWrapper;

  private RestoreNotificationWorker restoreNotificationWorker;

  @Before
  public void setUp() {
    rules.hilt().inject();

    // Instantiate the actual object under test
    restoreNotificationWorker = new RestoreNotificationWorker(
        context,
        workerParameters,
        notificationHelper,
        exposureNotificationSharedPreferences,
        exposureNotificationClientWrapper,
        MoreExecutors.newDirectExecutorService(),
        TestingExecutors.sameThreadScheduledExecutor());
  }

  @Test
  public void restoreNotificationWorker_enTurnedDown_restoreWorkNotDone() throws Exception {
    when(exposureNotificationClientWrapper.getStatus()).thenReturn(TASK_FOR_EN_TURNDOWN);
    // Stub the RestoreNotificationUtil APIs.
    MockedStatic<RestoreNotificationUtil> mockedRestoreNotificationUtil =
        getMockedRestoreNotificationUtil();

    Result result = restoreNotificationWorker.startWork().get();

    assertThat(result).isEqualTo(Result.success());
    verify(exposureNotificationClientWrapper).getStatus();
    verify(RestoreNotificationUtil.class, never());
    RestoreNotificationUtil.doRestoreNotificationWork(any(), any(), any());
    mockedRestoreNotificationUtil.close();
  }

  @Test
  public void restoreNotificationWorker_enTurnedDownForRegion_restoreWorkNotDone()
      throws Exception {
    when(exposureNotificationClientWrapper.getStatus()).thenReturn(TASK_FOR_EN_TURNDOWN_FOR_REGION);
    // Stub the RestoreNotificationUtil APIs.
    MockedStatic<RestoreNotificationUtil> mockedRestoreNotificationUtil =
        getMockedRestoreNotificationUtil();

    Result result = restoreNotificationWorker.startWork().get();

    assertThat(result).isEqualTo(Result.success());
    verify(exposureNotificationClientWrapper).getStatus();
    verify(RestoreNotificationUtil.class, never());
    RestoreNotificationUtil.doRestoreNotificationWork(any(), any(), any());
    mockedRestoreNotificationUtil.close();
  }

  @Test
  public void restoreNotificationWorker_enNotTurnedDown_restoreWorkDone() throws Exception {
    when(exposureNotificationClientWrapper.getStatus()).thenReturn(TASK_FOR_ACTIVATED);
    // Stub the RestoreNotificationUtil APIs.
    MockedStatic<RestoreNotificationUtil> mockedRestoreNotificationUtil =
        getMockedRestoreNotificationUtil();

    Result result = restoreNotificationWorker.startWork().get();

    assertThat(result).isEqualTo(Result.success());
    verify(exposureNotificationClientWrapper).getStatus();
    verify(RestoreNotificationUtil.class);
    RestoreNotificationUtil.doRestoreNotificationWork(any(), any(), any());
    mockedRestoreNotificationUtil.close();
  }

  private MockedStatic<RestoreNotificationUtil> getMockedRestoreNotificationUtil() {
    MockedStatic<RestoreNotificationUtil> mockedRestoreNotificationUtil =
        mockStatic(RestoreNotificationUtil.class);
    doNothing().when(RestoreNotificationUtil.class);
    RestoreNotificationUtil.doRestoreNotificationWork(any(), any(), any());
    return mockedRestoreNotificationUtil;
  }

}
