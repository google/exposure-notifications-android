/*
 * Copyright 2022 Google LLC
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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.net.Uri;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.work.Configuration;
import androidx.work.ListenableWorker.Result;
import androidx.work.WorkManager;
import androidx.work.WorkerParameters;
import androidx.work.impl.utils.SynchronousExecutor;
import androidx.work.testing.WorkManagerTestInitHelper;
import com.google.android.apps.exposurenotification.common.CleanupHelper;
import com.google.android.apps.exposurenotification.keydownload.DiagnosisKeyDownloader;
import com.google.android.apps.exposurenotification.keydownload.DownloadUriPair;
import com.google.android.apps.exposurenotification.keydownload.DownloadUrisModule;
import com.google.android.apps.exposurenotification.keydownload.KeyFile;
import com.google.android.apps.exposurenotification.keydownload.Qualifiers.HomeDownloadUriPair;
import com.google.android.apps.exposurenotification.keydownload.Qualifiers.TravellerDownloadUriPairs;
import com.google.android.apps.exposurenotification.logging.AnalyticsLogger;
import com.google.android.apps.exposurenotification.network.RealRequestQueueModule;
import com.google.android.apps.exposurenotification.network.RequestQueueWrapper;
import com.google.android.apps.exposurenotification.proto.WorkManagerTask.WorkerTask;
import com.google.android.apps.exposurenotification.riskcalculation.DiagnosisKeyDataMappingHelper;
import com.google.android.apps.exposurenotification.storage.DbModule;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationDatabase;
import com.google.android.apps.exposurenotification.testsupport.ExposureNotificationRules;
import com.google.android.apps.exposurenotification.testsupport.FakeRequestQueue;
import com.google.android.apps.exposurenotification.testsupport.InMemoryDb;
import com.google.android.apps.exposurenotification.work.WorkerStartupManager;
import com.google.android.gms.nearby.exposurenotification.DiagnosisKeysDataMapping;
import com.google.android.gms.nearby.exposurenotification.ExposureNotificationStatus;
import com.google.android.gms.nearby.exposurenotification.PackageConfiguration.PackageConfigurationBuilder;
import com.google.android.gms.nearby.exposurenotification.ReportType;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.testing.TestingExecutors;
import dagger.hilt.android.testing.BindValue;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import dagger.hilt.android.testing.UninstallModules;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.annotation.Config;

/**
 * This unit test verifies the behavior of ProvideDiagnosisKeysWorker, which is a regularly
 * executed worker, responsible for providing keys to the EN client.
 */
@RunWith(AndroidJUnit4.class)
@HiltAndroidTest
@Config(application = HiltTestApplication.class)
@UninstallModules({
    DbModule.class,
    RealRequestQueueModule.class,
    DownloadUrisModule.class
})
public class ProvideDiagnosisKeysWorkerTest {

  @Rule
  public ExposureNotificationRules rules = ExposureNotificationRules.forTest(this)
      .withMocks()
      .build();

  private static final Task<Set<ExposureNotificationStatus>> TASK_FOR_ACTIVATED =
      Tasks.forResult(ImmutableSet.of(ExposureNotificationStatus.ACTIVATED));
  private static final Task<Set<ExposureNotificationStatus>> TASK_FOR_INACTIVATED =
      Tasks.forResult(ImmutableSet.of(ExposureNotificationStatus.INACTIVATED));
  private static final Task<Void> TASK_FOR_RESULT_VOID = Tasks.forResult(null);
  private static final DiagnosisKeysDataMapping SAMPLE_DIAGNOSIS_KEYS_DATA_MAPPING =
      DiagnosisKeyDataMappingHelper
          .createDiagnosisKeysDataMapping("0x0000000000000000", ReportType.CONFIRMED_TEST);
  private static final ImmutableList<KeyFile> SAMPLE_KEY_FILES = ImmutableList.of(
      KeyFile.create(newDownloadUriPair("CA").indexUri(), Uri.parse("example.com"), true),
      KeyFile.create(newDownloadUriPair("MA").indexUri(), Uri.parse("example.com"), true)
  );

  @BindValue
  @HomeDownloadUriPair
  static final DownloadUriPair HOME_URIS = newDownloadUriPair("US");
  @BindValue
  @TravellerDownloadUriPairs
  static final Map<String, List<DownloadUriPair>> TRAVEL_URIS =
      ImmutableMap.of(
          "MX", ImmutableList.of(newDownloadUriPair("MX")),
          "CA", ImmutableList.of(newDownloadUriPair("CA")));
  @BindValue
  ExposureNotificationDatabase db = InMemoryDb.create();
  @BindValue
  RequestQueueWrapper queue = new FakeRequestQueue();

  @Inject
  DiagnosisKeysDataMapping diagnosisKeysDataMapping;
  @Inject
  PackageConfigurationHelper packageConfigurationHelper;

  @Mock
  AnalyticsLogger analyticsLogger;
  @Mock
  WorkerParameters workerParameters;
  @Mock
  ExposureNotificationClientWrapper exposureNotificationClientWrapper;
  @Mock
  CleanupHelper cleanupHelper;
  @Mock
  DiagnosisKeyDownloader diagnosisKeyDownloader;
  @Mock
  DiagnosisKeyFileSubmitter diagnosisKeyFileSubmitter;

  WorkManager workManager;
  ProvideDiagnosisKeysWorker provideDiagnosisKeysWorker;

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

    // Set up default mocked controller operations.
    when(exposureNotificationClientWrapper.isEnabled()).thenReturn(Tasks.forResult(true));
    when(exposureNotificationClientWrapper.getStatus()).thenReturn(TASK_FOR_ACTIVATED);
    when(exposureNotificationClientWrapper.getPackageConfiguration())
        .thenReturn(Tasks.forResult(new PackageConfigurationBuilder().build()));
    when(exposureNotificationClientWrapper.getDiagnosisKeysDataMapping())
        .thenReturn(Tasks.forResult(SAMPLE_DIAGNOSIS_KEYS_DATA_MAPPING));
    when(exposureNotificationClientWrapper.setDiagnosisKeysDataMapping(any()))
        .thenReturn(TASK_FOR_RESULT_VOID);
    when(diagnosisKeyDownloader.download()).thenReturn(Futures.immediateFuture(SAMPLE_KEY_FILES));
    when(diagnosisKeyFileSubmitter.submitFiles(any())).thenReturn(Futures.immediateFuture(null));

    // Instantiate the actual object under test
    provideDiagnosisKeysWorker = spy(new ProvideDiagnosisKeysWorker(context, workerParameters,
        diagnosisKeyDownloader, exposureNotificationClientWrapper,
        diagnosisKeyFileSubmitter, diagnosisKeysDataMapping,
        MoreExecutors.newDirectExecutorService(),
        TestingExecutors.sameThreadScheduledExecutor(),
        new WorkerStartupManager(
            exposureNotificationClientWrapper,
            MoreExecutors.newDirectExecutorService(),
            TestingExecutors.sameThreadScheduledExecutor(),
            packageConfigurationHelper,
            cleanupHelper
        ), analyticsLogger));
  }

  @Test
  public void startWork_isEnabledCallThrowsException_doesNothingAndReturnsFailure()
      throws Exception {
    when(exposureNotificationClientWrapper.isEnabled())
        .thenReturn(Tasks.forException(new Exception()));

    Result result = provideDiagnosisKeysWorker.startWork().get();

    assertThat(result).isEqualTo(Result.failure());
    // Ensure no extra calls to EN client have been made.
    verify(exposureNotificationClientWrapper).isEnabled();
    verify(exposureNotificationClientWrapper).getStatus();
    verifyNoMoreInteractions(exposureNotificationClientWrapper);
    // Ensure we don't download or submit the keys.
    verify(diagnosisKeyDownloader, never()).download();
    verify(diagnosisKeyFileSubmitter, never()).submitFiles(any());
    // Ensure we log that worker has failed but nothing else.
    verify(analyticsLogger)
        .logWorkManagerTaskFailure(eq(WorkerTask.TASK_PROVIDE_DIAGNOSIS_KEYS), any());
    verifyNoMoreInteractions(analyticsLogger);
  }

  @Test
  public void startWork_enDisabled_doesNothingAndReturnsSuccess() throws Exception {
    when(exposureNotificationClientWrapper.isEnabled()).thenReturn(Tasks.forResult(false));
    when(exposureNotificationClientWrapper.getStatus()).thenReturn(TASK_FOR_INACTIVATED);

    Result result = provideDiagnosisKeysWorker.startWork().get();

    assertThat(result).isEqualTo(Result.success());
    // Ensure no extra calls to EN client have been made.
    verify(exposureNotificationClientWrapper).isEnabled();
    verify(exposureNotificationClientWrapper).getStatus();
    verifyNoMoreInteractions(exposureNotificationClientWrapper);
    // Ensure we don't download or submit the keys.
    verify(diagnosisKeyDownloader, never()).download();
    verify(diagnosisKeyFileSubmitter, never()).submitFiles(any());
    // Ensure we log that worker has started and got abandoned but nothing else.
    verify(analyticsLogger).logWorkManagerTaskStarted(eq(WorkerTask.TASK_PROVIDE_DIAGNOSIS_KEYS));
    verify(analyticsLogger).logWorkManagerTaskAbandoned(eq(WorkerTask.TASK_PROVIDE_DIAGNOSIS_KEYS));
    verifyNoMoreInteractions(analyticsLogger);
  }

  @Test
  public void startWork_getDiagnosisKeyDataMappingThrowsException_doesWorkAndReturnsSuccess()
      throws Exception {
    when(exposureNotificationClientWrapper.getDiagnosisKeysDataMapping())
        .thenReturn(Tasks.forException(new Exception()));

    Result result = provideDiagnosisKeysWorker.startWork().get();

    assertThat(result).isEqualTo(Result.success());
    verify(exposureNotificationClientWrapper).isEnabled();
    verify(exposureNotificationClientWrapper).getDiagnosisKeysDataMapping();
    // We should never call setDiagnosisKeysDataMapping() if getDiagnosisKeysDataMapping threw
    // an exception.
    verify(exposureNotificationClientWrapper, never()).setDiagnosisKeysDataMapping(any());
    // Ensure we still download and submit the keys.
    verify(diagnosisKeyDownloader).download();
    verify(diagnosisKeyFileSubmitter).submitFiles(any());
    // Ensure we log that worker has started and succeeded but nothing else.
    verify(analyticsLogger).logWorkManagerTaskStarted(eq(WorkerTask.TASK_PROVIDE_DIAGNOSIS_KEYS));
    verify(analyticsLogger).logWorkManagerTaskSuccess(eq(WorkerTask.TASK_PROVIDE_DIAGNOSIS_KEYS));
    verifyNoMoreInteractions(analyticsLogger);
  }

  @Test
  public void startWork_setDiagnosisKeyDataMappingThrowsException_doesWorkAndReturnsSuccess()
      throws Exception {
    when(exposureNotificationClientWrapper.setDiagnosisKeysDataMapping(any()))
        .thenReturn(Tasks.forException(new Exception()));

    Result result = provideDiagnosisKeysWorker.startWork().get();

    assertThat(result).isEqualTo(Result.success());
    verify(exposureNotificationClientWrapper).isEnabled();
    verify(exposureNotificationClientWrapper).getDiagnosisKeysDataMapping();
    verify(exposureNotificationClientWrapper).setDiagnosisKeysDataMapping(any());
    // Ensure we still download and submit the keys.
    verify(diagnosisKeyDownloader).download();
    verify(diagnosisKeyFileSubmitter).submitFiles(any());
    // Ensure we log that worker has started and succeeded but nothing else.
    verify(analyticsLogger).logWorkManagerTaskStarted(eq(WorkerTask.TASK_PROVIDE_DIAGNOSIS_KEYS));
    verify(analyticsLogger).logWorkManagerTaskSuccess(eq(WorkerTask.TASK_PROVIDE_DIAGNOSIS_KEYS));
    verifyNoMoreInteractions(analyticsLogger);
  }

  @Test
  public void startWork_enEnabled_doesWorkAndReturnsSuccess() throws Exception {
    Result result = provideDiagnosisKeysWorker.startWork().get();

    assertThat(result).isEqualTo(Result.success());
    verify(exposureNotificationClientWrapper).isEnabled();
    verify(exposureNotificationClientWrapper).getDiagnosisKeysDataMapping();
    verify(exposureNotificationClientWrapper).setDiagnosisKeysDataMapping(any());
    // Ensure we download and submit the keys.
    verify(diagnosisKeyDownloader).download();
    verify(diagnosisKeyFileSubmitter).submitFiles(any());
    // Ensure we log that worker has started and succeeded but nothing else.
    verify(analyticsLogger).logWorkManagerTaskStarted(eq(WorkerTask.TASK_PROVIDE_DIAGNOSIS_KEYS));
    verify(analyticsLogger).logWorkManagerTaskSuccess(eq(WorkerTask.TASK_PROVIDE_DIAGNOSIS_KEYS));
    verifyNoMoreInteractions(analyticsLogger);
  }

  private static DownloadUriPair newDownloadUriPair(String country) {
    int nextInt = new AtomicInteger(1).incrementAndGet();
    return DownloadUriPair.create(
        "http://example.com/" + country + "/" + nextInt + "/index.txt",
        "http://example.com/" + country + "/" + nextInt + "/files/");
  }

}
