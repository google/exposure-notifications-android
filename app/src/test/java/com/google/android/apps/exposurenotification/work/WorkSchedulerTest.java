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

package com.google.android.apps.exposurenotification.work;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.work.Operation;
import androidx.work.Operation.State.SUCCESS;
import androidx.work.WorkManager;
import com.google.android.apps.exposurenotification.testsupport.ExposureNotificationRules;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.annotation.Config;
import org.threeten.bp.Duration;

@RunWith(AndroidJUnit4.class)
@HiltAndroidTest
@Config(application = HiltTestApplication.class)
public class WorkSchedulerTest {

  private static final Duration TEK_PUBLISH_INTERVAL = Duration.ofHours(4);

  @Rule
  public ExposureNotificationRules rules =
      ExposureNotificationRules.forTest(this).withMocks().build();

  @Mock
  WorkManager workManager;

  private WorkScheduler workScheduler;

  @Before
  public void setUp() {
    rules.hilt().inject();
    workScheduler = new WorkScheduler(workManager, MoreExecutors.newDirectExecutorService(),
        TEK_PUBLISH_INTERVAL);
  }

  @Test
  public void schedule_callsEnqueue() {
    Operation operation = mock(Operation.class);
    when(operation.getResult()).thenReturn(
        Futures.immediateFuture(Operation.SUCCESS),
        Futures.immediateFuture(Operation.SUCCESS),
        Futures.immediateFailedFuture(new Exception()),
        Futures.immediateFuture(Operation.SUCCESS));
    when(workManager.enqueueUniquePeriodicWork(any(), any(), any())).thenReturn(operation);

    workScheduler.schedule();

    verify(workManager, times(4)).enqueueUniquePeriodicWork(any(), any(), any());
  }

}
