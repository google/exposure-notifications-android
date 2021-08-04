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

package com.google.android.apps.exposurenotification.keyupload;

import static com.google.android.apps.exposurenotification.keyupload.UploadCoverTrafficWorker.REPEAT_INTERVAL;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ListenableWorker.Result;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkerParameters;
import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.android.apps.exposurenotification.common.time.RealTimeModule;
import com.google.android.apps.exposurenotification.nearby.ExposureNotificationClientWrapper;
import com.google.android.apps.exposurenotification.nearby.PackageConfigurationHelper;
import com.google.android.apps.exposurenotification.storage.DbModule;
import com.google.android.apps.exposurenotification.storage.ExposureCheckRepository;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationDatabase;
import com.google.android.apps.exposurenotification.storage.VerificationCodeRequestRepository;
import com.google.android.apps.exposurenotification.testsupport.ExposureNotificationRules;
import com.google.android.apps.exposurenotification.testsupport.FakeClock;
import com.google.android.apps.exposurenotification.testsupport.InMemoryDb;
import com.google.android.apps.exposurenotification.work.WorkerStartupManager;
import com.google.android.gms.tasks.Tasks;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.testing.TestingExecutors;
import dagger.hilt.android.testing.BindValue;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import dagger.hilt.android.testing.UninstallModules;
import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.robolectric.annotation.Config;
import org.threeten.bp.LocalDate;
import org.threeten.bp.ZoneOffset;

@HiltAndroidTest
@RunWith(AndroidJUnit4.class)
@Config(application = HiltTestApplication.class)
@UninstallModules({DbModule.class, RealTimeModule.class})
public class UploadCoverTrafficWorkerTest {

  @Rule
  public ExposureNotificationRules rules =
      ExposureNotificationRules.forTest(this).withMocks().build();

  @Mock
  WorkerParameters workerParameters;
  @Mock
  SecureRandom secureRandom;
  @Mock
  ExposureNotificationClientWrapper exposureNotificationClientWrapper;
  @Mock
  WorkManager workManager;
  @Mock
  UploadController uploadController;

  @Captor
  ArgumentCaptor<String> workerNameCaptor;
  @Captor
  ArgumentCaptor<ExistingPeriodicWorkPolicy> existingPeriodicWorkPolicyCaptor;
  @Captor
  ArgumentCaptor<PeriodicWorkRequest> periodicWorkRequestCaptor;
  @Inject
  PackageConfigurationHelper packageConfigurationHelper;
  @Inject
  ExposureCheckRepository exposureCheckRepository;
  @Inject
  VerificationCodeRequestRepository verificationCodeRequestRepository;

  @BindValue
  ExposureNotificationDatabase db = InMemoryDb.create();
  @BindValue
  Clock clock = new FakeClock();

  // The SUT.
  private UploadCoverTrafficWorker worker;

  @Before
  public void setUp() {
    rules.hilt().inject();
    Context context = ApplicationProvider.getApplicationContext();
    // Randoms below probability result in execution. It's not great for the test to know the
    // internal implementation of the SUT like this. TODO: is there a better way to test?
    when(secureRandom.nextDouble())
        .thenReturn(UploadCoverTrafficWorker.EXECUTION_PROBABILITY - 0.1d,
        UploadCoverTrafficWorker.USER_REPORT_RPC_EXECUTION_PROBABILITY - 0.1d);
    // Set up all mocked controller operations to succeed by default.
    when(uploadController.requestCode(any()))
        .thenReturn(Futures.immediateFuture(UserReportUpload.newBuilder("dummy-phone-number",
            "dummy-nonce", LocalDate.from(clock.now().atZone(ZoneOffset.UTC)),
            /* tzOffsetMin= */0).build()));
    when(uploadController.submitCode(any()))
        .thenReturn(Futures.immediateFuture(Upload.newBuilder("dummy-code", "dummy-key").build()));
    when(uploadController.submitKeysForCert(any()))
        .thenReturn(Futures.immediateFuture(Upload.newBuilder("dummy-code", "dummy-key").build()));
    when(uploadController.upload(any()))
        .thenReturn(Futures.immediateFuture(Upload.newBuilder("dummy-code", "dummy-key").build()));

    worker = new UploadCoverTrafficWorker(
        context,
        workerParameters,
        uploadController,
        MoreExecutors.newDirectExecutorService(),
        MoreExecutors.newDirectExecutorService(),
        TestingExecutors.sameThreadScheduledExecutor(),
        secureRandom,
        new WorkerStartupManager(
            exposureNotificationClientWrapper,
            MoreExecutors.newDirectExecutorService(),
            TestingExecutors.sameThreadScheduledExecutor(),
            packageConfigurationHelper,
            exposureCheckRepository,
            verificationCodeRequestRepository,
            clock));
  }

  @Test
  public void randomExecution_decidesNotToExecute_shouldNotMakeRpcs() throws Exception {
    // Randoms above probability result in no execution.
    when(secureRandom.nextDouble())
        .thenReturn(UploadCoverTrafficWorker.EXECUTION_PROBABILITY + 0.1d);

    Result result = worker.startWork().get();

    verify(uploadController, never()).requestCode(any());
    verify(uploadController, never()).submitCode(any());
    verify(uploadController, never()).submitKeysForCert(any());
    verify(uploadController, never()).upload(any());
    assertThat(result).isEqualTo(Result.success());
  }

  @Test
  public void randomExecution_decidesNotToExecuteUserReportRpcButExecutesRest_shouldNotMakeUserReportRpc()
      throws Exception {
    // Randoms below probability result in execution and above probability result in no execution.
    // First, secureRandom.nextDouble() is called to determine if we execute the worker at all.
    // Then, it is called to determine if we execute the RPC call to /user-report API.
    when(secureRandom.nextDouble())
        .thenReturn(UploadCoverTrafficWorker.EXECUTION_PROBABILITY - 0.1d,
            UploadCoverTrafficWorker.USER_REPORT_RPC_EXECUTION_PROBABILITY + 0.1d);
    when(exposureNotificationClientWrapper.isEnabled()).thenReturn(Tasks.forResult(true));

    Result result = worker.startWork().get();

    verify(uploadController, never()).requestCode(any());
    verify(uploadController).submitCode(any());
    verify(uploadController).submitKeysForCert(any());
    verify(uploadController).upload(any());
    assertThat(result).isEqualTo(Result.success());
  }

  @Test
  public void isApiEnabledCheckFails_shouldAbortExecutionAndReturnFailure() throws Exception {
    when(exposureNotificationClientWrapper.isEnabled())
        .thenReturn(Tasks.forException(new Exception()));

    Result result = worker.startWork().get();

    verify(uploadController, never()).requestCode(any());
    verify(uploadController, never()).submitCode(any());
    verify(uploadController, never()).submitKeysForCert(any());
    verify(uploadController, never()).upload(any());
    assertThat(result).isEqualTo(Result.failure());
  }

  @Test
  public void isEnabledFalse_shouldAbortExecutionAndReturnSuccess() throws Exception {
    when(exposureNotificationClientWrapper.isEnabled()).thenReturn(Tasks.forResult(false));

    Result result = worker.startWork().get();

    verify(uploadController, never()).requestCode(any());
    verify(uploadController, never()).submitCode(any());
    verify(uploadController, never()).submitKeysForCert(any());
    verify(uploadController, never()).upload(any());
    assertThat(result).isEqualTo(Result.success());
  }

  @Test
  public void submitCodeRequest_shouldIncludeIsCoverTrafficAndFieldsRequiredByServer()
      throws Exception {
    when(exposureNotificationClientWrapper.isEnabled()).thenReturn(Tasks.forResult(true));
    ArgumentCaptor<UserReportUpload> captor = ArgumentCaptor.forClass(UserReportUpload.class);

    worker.startWork().get();

    verify(uploadController).requestCode(captor.capture());
    UserReportUpload upload = captor.getValue();
    assertThat(upload.isCoverTraffic()).isTrue();
    assertThat(upload.nonceBase64()).isNotEmpty();
    assertThat(upload.phoneNumber()).isNotEmpty();
    assertThat(upload.testDate()).isNotNull();
    assertThat(upload.tzOffsetMin()).isEqualTo(0L);
  }

  @Test
  public void submitVerifyCodeRequest_shouldIncludeIsCoverTrafficAndVerificationCode()
      throws Exception {
    when(exposureNotificationClientWrapper.isEnabled()).thenReturn(Tasks.forResult(true));
    ArgumentCaptor<Upload> captor = ArgumentCaptor.forClass(Upload.class);

    worker.startWork().get();

    verify(uploadController).submitCode(captor.capture());
    Upload upload = captor.getValue();
    assertThat(upload.isCoverTraffic()).isTrue();
    assertThat(upload.verificationCode()).isNotEmpty();
  }

  @Test
  public void submitKeysForCertRequest_shouldIncludeIsCoverTrafficKeysAndLongTermToken()
      throws Exception {
    when(exposureNotificationClientWrapper.isEnabled()).thenReturn(Tasks.forResult(true));
    ArgumentCaptor<Upload> captor = ArgumentCaptor.forClass(Upload.class);

    worker.startWork().get();

    verify(uploadController).submitKeysForCert(captor.capture());
    Upload upload = captor.getValue();
    assertThat(upload.isCoverTraffic()).isTrue();
    assertThat(upload.keys()).isNotEmpty();
    assertThat(upload.longTermToken()).isNotEmpty();
  }

  @Test
  public void uploadKeysRequest_shouldIncludeIsCoverTraffic_andFieldsRequiredByServer()
      throws Exception {
    when(exposureNotificationClientWrapper.isEnabled()).thenReturn(Tasks.forResult(true));
    ArgumentCaptor<Upload> captor = ArgumentCaptor.forClass(Upload.class);

    worker.startWork().get();

    verify(uploadController).upload(captor.capture());
    Upload upload = captor.getValue();
    assertThat(upload.isCoverTraffic()).isTrue();
    assertThat(upload.keys()).isNotEmpty();
    assertThat(upload.regions()).isNotEmpty();
    assertThat(upload.hmacKeyBase64()).isNotEmpty();
    assertThat(upload.certificate()).isNotEmpty();
    assertThat(upload.symptomOnset()).isNotNull();
  }

  @Test
  public void schedule_verifyParameters() {
    UploadCoverTrafficWorker.schedule(workManager);

    verify(workManager)
        .enqueueUniquePeriodicWork(workerNameCaptor.capture(),
            existingPeriodicWorkPolicyCaptor.capture(),
            periodicWorkRequestCaptor.capture());
    assertThat(workerNameCaptor.getValue()).isEqualTo(UploadCoverTrafficWorker.WORKER_NAME);
    assertThat(existingPeriodicWorkPolicyCaptor.getValue())
        .isEqualTo(ExistingPeriodicWorkPolicy.KEEP);
    PeriodicWorkRequest periodicWorkRequest = periodicWorkRequestCaptor.getValue();
    assertThat(periodicWorkRequest.getWorkSpec().intervalDuration)
        .isEqualTo(TimeUnit.HOURS.toMillis(REPEAT_INTERVAL));
  }
}