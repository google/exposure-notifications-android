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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.os.Build.VERSION_CODES;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.work.Operation;
import androidx.work.WorkManager;
import com.google.android.apps.exposurenotification.BuildConfig;
import com.google.android.apps.exposurenotification.common.ExecutorsModule;
import com.google.android.apps.exposurenotification.common.Qualifiers.BackgroundExecutor;
import com.google.android.apps.exposurenotification.common.Qualifiers.LightweightExecutor;
import com.google.android.apps.exposurenotification.common.Qualifiers.ScheduledExecutor;
import com.google.android.apps.exposurenotification.privateanalytics.PrivateAnalyticsDeviceAttestation;
import com.google.android.apps.exposurenotification.privateanalytics.PrivateAnalyticsFirebaseModule;
import com.google.android.apps.exposurenotification.privateanalytics.PrivateAnalyticsRemoteConfig;
import com.google.android.apps.exposurenotification.privateanalytics.RemoteConfigs;
import com.google.android.apps.exposurenotification.testsupport.ExposureNotificationRules;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.testing.TestingExecutors;
import com.google.firebase.FirebaseApp;
import com.google.firebase.firestore.FirebaseFirestore;
import dagger.hilt.android.testing.BindValue;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import dagger.hilt.android.testing.UninstallModules;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
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
@UninstallModules({
    ExecutorsModule.class,
    PrivateAnalyticsFirebaseModule.class
})
public class WorkSchedulerTest {

  private static final Duration TEK_PUBLISH_INTERVAL = Duration.ofHours(4);

  @Rule
  public ExposureNotificationRules rules =
      ExposureNotificationRules.forTest(this).withMocks().build();

  @Mock
  WorkManager workManager;

  @BindValue
  @Mock
  PrivateAnalyticsRemoteConfig privateAnalyticsRemoteConfig;

  @BindValue
  @Mock
  FirebaseApp firebaseApp;

  @BindValue
  @Mock
  FirebaseFirestore firebaseFirestore;

  private WorkScheduler workScheduler;

  // Having uninstalled some modules above (@UninstallModules), we need to provide everything they
  // would have, even if the code under test here doesn't use them.
  @BindValue
  @BackgroundExecutor
  static final ExecutorService BACKGROUND_EXEC = MoreExecutors.newDirectExecutorService();
  @BindValue
  @LightweightExecutor
  static final ExecutorService LIGHTWEIGHT_EXEC = MoreExecutors.newDirectExecutorService();
  @BindValue
  @BackgroundExecutor
  static final ListeningExecutorService BACKGROUND_LISTENING_EXEC =
      MoreExecutors.newDirectExecutorService();
  @BindValue
  @LightweightExecutor
  static final ListeningExecutorService LIGHTWEIGHT_LISTENING_EXEC =
      MoreExecutors.newDirectExecutorService();
  @BindValue
  @ScheduledExecutor
  static final ScheduledExecutorService SCHEDULED_EXEC =
      TestingExecutors.sameThreadScheduledExecutor();

  @Before
  public void setUp() {
    FirebaseApp.initializeApp(ApplicationProvider.getApplicationContext());
    rules.hilt().inject();
    workScheduler = new WorkScheduler(workManager, MoreExecutors.newDirectExecutorService(),
        TEK_PUBLISH_INTERVAL, privateAnalyticsRemoteConfig);
    when(privateAnalyticsRemoteConfig.fetchUpdatedConfigs()).thenReturn(Futures.immediateFuture(
        RemoteConfigs.newBuilder().build()));
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

    int expectedWork = BuildConfig.PRIVATE_ANALYTICS_SUPPORTED &&
        PrivateAnalyticsDeviceAttestation.isDeviceAttestationAvailable() ? 5 : 4;
    verify(workManager, times(expectedWork)).enqueueUniquePeriodicWork(any(), any(), any());
  }

  @Test
  @Config(maxSdk = VERSION_CODES.M)
  public void schedule_callsEnqueue_EnpaNotScheduledSdk23() {
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
