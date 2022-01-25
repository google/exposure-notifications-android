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

package com.google.android.apps.exposurenotification.privateanalytics;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.net.Uri;
import android.os.Build.VERSION_CODES;
import androidx.hilt.work.HiltWorkerFactory;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.work.Configuration;
import androidx.work.ListenableWorker;
import androidx.work.WorkManager;
import androidx.work.WorkerParameters;
import androidx.work.impl.utils.SynchronousExecutor;
import androidx.work.testing.WorkManagerTestInitHelper;
import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.android.apps.exposurenotification.network.RealRequestQueueModule;
import com.google.android.apps.exposurenotification.network.RequestQueueWrapper;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.apps.exposurenotification.storage.StorageModule;
import com.google.android.apps.exposurenotification.testsupport.ExposureNotificationRules;
import com.google.android.libraries.privateanalytics.PrivateAnalyticsDeviceAttestation;
import com.google.android.libraries.privateanalytics.PrivateAnalyticsEnabledProvider;
import com.google.android.libraries.privateanalytics.PrivateAnalyticsEventListener;
import com.google.android.libraries.privateanalytics.PrivateAnalyticsLogger;
import com.google.android.libraries.privateanalytics.PrivateAnalyticsRemoteConfig;
import com.google.android.libraries.privateanalytics.Qualifiers.RemoteConfigUri;
import com.google.common.base.Optional;
import dagger.hilt.android.testing.BindValue;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import dagger.hilt.android.testing.UninstallModules;
import java.util.Calendar;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.robolectric.annotation.Config;
import org.threeten.bp.Duration;
import org.threeten.bp.Instant;

@RunWith(AndroidJUnit4.class)
@HiltAndroidTest
@Config(application = HiltTestApplication.class, sdk = VERSION_CODES.O)
@UninstallModules({PrivateAnalyticsModule.class, RealRequestQueueModule.class, StorageModule.class})
public class SubmitPrivateAnalyticsWorkerTest {

  @Rule
  public ExposureNotificationRules rules = ExposureNotificationRules.forTest(this).withMocks()
      .build();

  @Mock
  WorkerParameters workerParameters;
  @Inject
  HiltWorkerFactory workerFactory;
  @BindValue
  @Mock
  PrivateAnalyticsDeviceAttestation deviceAttestation;
  @BindValue
  @Mock
  PrivateAnalyticsRemoteConfig remoteConfig;
  @BindValue
  @RemoteConfigUri
  @Mock
  Uri remoteConfigUri;
  @BindValue
  @Mock
  PrivateAnalyticsEnabledProvider privateAnalyticsEnabledProvider;
  @BindValue
  Optional<PrivateAnalyticsEventListener> eventListener = Optional.absent();
  @BindValue
  @Mock
  RequestQueueWrapper requestQueueWrapper;
  @BindValue
  PrivateAnalyticsLogger.Factory loggerFactory = new FakePrivateAnalyticsLoggerFactory();
  @BindValue
  @Mock
  ExposureNotificationSharedPreferences exposureNotificationSharedPreferences;
  @Inject
  Clock clock;

  WorkManager workManager;

  @Before
  public void setUp() {
    rules.hilt().inject();

    // Initialize WorkManager for testing.
    Context context = ApplicationProvider.getApplicationContext();
    Configuration config = new Configuration.Builder()
        .setExecutor(new SynchronousExecutor())
        .build();
    WorkManagerTestInitHelper.initializeTestWorkManager(
        context, config);
    workManager = WorkManager.getInstance(context);
  }

  @Test
  public void testAnalyticsWorker_canBeInitialized() {
    ListenableWorker worker = workerFactory
        .createWorker(ApplicationProvider.getApplicationContext(),
            SubmitPrivateAnalyticsWorker.class.getName(),
            workerParameters);
    assertThat(worker).isNotNull();
  }

  @Test
  public void testAnalyticsWorker_clearsOlderFields() {
    // Set up last metrics run
    Instant lastDailyRun = clock.now().minus(Duration.ofDays(1));
    when(exposureNotificationSharedPreferences
        .getPrivateAnalyticsWorkerLastTimeForDaily()).thenReturn(lastDailyRun);
    Instant lastBiweeklyRun = clock.now().minus(Duration.ofDays(7));
    when(exposureNotificationSharedPreferences
        .getPrivateAnalyticsWorkerLastTimeForBiweekly()).thenReturn(lastBiweeklyRun);

    // Compute biweekly metrics day value for today
    Calendar calendar = Calendar.getInstance();
    int biweeklyMetricsUploadDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1;
    int biweeklyMetricsUploadWeek = calendar.get(Calendar.WEEK_OF_YEAR)  % 2;
    int biweeklyMetricsUploadDay = biweeklyMetricsUploadDayOfWeek + biweeklyMetricsUploadWeek * 7;

    // Create worker
    SubmitPrivateAnalyticsWorker worker = (SubmitPrivateAnalyticsWorker) workerFactory
        .createWorker(ApplicationProvider.getApplicationContext(),
            SubmitPrivateAnalyticsWorker.class.getName(),
            workerParameters);

    // Check on a daily-metrics-only day
    worker.biweeklyMetricsUploadDay = (biweeklyMetricsUploadDay + 1) % 14;
    worker.clearOlderPrivateAnalyticsFields();
    verify(exposureNotificationSharedPreferences)
        .clearPrivateAnalyticsDailyFieldsBefore(lastDailyRun);
    verify(exposureNotificationSharedPreferences, never())
        .clearPrivateAnalyticsBiweeklyFieldsBefore(any());

    Mockito.clearInvocations(exposureNotificationSharedPreferences);

    // Check on a biweekly-metrics day
    worker.biweeklyMetricsUploadDay = biweeklyMetricsUploadDay;
    worker.clearOlderPrivateAnalyticsFields();
    verify(exposureNotificationSharedPreferences)
        .clearPrivateAnalyticsDailyFieldsBefore(lastDailyRun);
    verify(exposureNotificationSharedPreferences)
        .clearPrivateAnalyticsBiweeklyFieldsBefore(lastBiweeklyRun);
  }
}
