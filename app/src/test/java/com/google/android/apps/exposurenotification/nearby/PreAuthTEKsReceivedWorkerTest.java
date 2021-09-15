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

package com.google.android.apps.exposurenotification.nearby;

import static com.google.android.apps.exposurenotification.nearby.PreAuthTEKsReceivedWorker.KEYS_BYTES;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.support.test.espresso.core.internal.deps.guava.collect.ImmutableList;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.work.Data;
import androidx.work.ListenableWorker.Result;
import androidx.work.WorkerParameters;
import com.google.android.apps.exposurenotification.keyupload.Upload;
import com.google.android.apps.exposurenotification.keyupload.UploadController;
import com.google.android.apps.exposurenotification.keyupload.UploadController.NoInternetException;
import com.google.android.apps.exposurenotification.storage.DbModule;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity.Shared;
import com.google.android.apps.exposurenotification.storage.DiagnosisRepository;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationDatabase;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.apps.exposurenotification.testsupport.ExposureNotificationRules;
import com.google.android.apps.exposurenotification.testsupport.FakeClock;
import com.google.android.apps.exposurenotification.testsupport.FakeShadowResources;
import com.google.android.apps.exposurenotification.testsupport.InMemoryDb;
import com.google.android.gms.nearby.exposurenotification.ReportType;
import com.google.android.gms.nearby.exposurenotification.TemporaryExposureKey;
import com.google.android.gms.nearby.exposurenotification.TemporaryExposureKey.TemporaryExposureKeyBuilder;
import com.google.common.base.Optional;
import com.google.common.io.BaseEncoding;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.testing.TestingExecutors;
import dagger.hilt.android.testing.BindValue;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import dagger.hilt.android.testing.UninstallModules;
import java.security.SecureRandom;
import java.util.List;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.annotation.Config;

/**
 * This unit test verifies the behavior of PreAuthTEKsReceivedWorker, which is fired upon receiving
 * ACTION_PRE_AUTHORIZE_RELEASE_PHONE_UNLOCKED broadcast from the EN module.
 */
@RunWith(AndroidJUnit4.class)
@HiltAndroidTest
@Config(application = HiltTestApplication.class, shadows = {FakeShadowResources.class})
@UninstallModules({DbModule.class})
public class PreAuthTEKsReceivedWorkerTest {

  private static final BaseEncoding BASE64 = BaseEncoding.base64();

  private final Context context = ApplicationProvider.getApplicationContext();

  @Rule
  public ExposureNotificationRules rules = ExposureNotificationRules.forTest(this)
      .withMocks()
      .build();

  @BindValue
  ExposureNotificationDatabase db = InMemoryDb.create();

  @Inject
  DiagnosisRepository diagnosisRepository;
  @Inject
  ExposureNotificationSharedPreferences exposureNotificationSharedPreferences;

  @Mock
  WorkerParameters workerParameters;
  @Mock
  UploadController uploadController;
  @Mock
  SecureRandom secureRandom;

  FakeClock clock = new FakeClock();
  PreAuthTEKsReceivedWorker preAuthTEKsReceivedWorker;

  @Before
  public void setUp() {
    rules.hilt().inject();

    // Set up all mocked controller operations to succeed by default.
    when(uploadController.submitKeysForCert(any()))
        .thenReturn(Futures.immediateFuture(Upload.newBuilder("dummy-code", "dummy-key").build()));
    when(uploadController.upload(any()))
        .thenReturn(Futures.immediateFuture(Upload.newBuilder("dummy-code", "dummy-key").build()));

    // Instantiate the actual object under test
    preAuthTEKsReceivedWorker = spy(new PreAuthTEKsReceivedWorker(context, workerParameters,
        exposureNotificationSharedPreferences, diagnosisRepository, uploadController,
        MoreExecutors.newDirectExecutorService(),
        TestingExecutors.sameThreadScheduledExecutor(), secureRandom, clock));
  }

  @Test
  public void preAuthTeksReceivedWorker_emptyEncodedTEKs_doesNothing() throws Exception {
    Data inputData = new Data.Builder()
        .putByteArray(KEYS_BYTES, new byte[0])
        .build();
    when(workerParameters.getInputData()).thenReturn(inputData);

    Result result = preAuthTEKsReceivedWorker.startWork().get();

    verifyResultFailureAndNoWorkDone(result);
  }

  @Test
  public void preAuthTeksReceivedWorker_validEncodedTEKsButNoPreAuthDiagnosis_resultRetry()
      throws Exception {
    List<TemporaryExposureKey> TEKs = ImmutableList.of(key("key1"), key("key2"),
        key("key3"), key("key4"));
    Data inputData = new Data.Builder()
        .putByteArray(KEYS_BYTES, TemporaryExposureKeyHelper.keysToTEKExportBytes(TEKs))
        .build();
    when(workerParameters.getInputData()).thenReturn(inputData);

    Result result = preAuthTEKsReceivedWorker.startWork().get();
    Optional<DiagnosisEntity> diagnosisEntityOptional =
        diagnosisRepository.maybeGetLastPreAuthDiagnosisAsync().get();

    assertThat(diagnosisEntityOptional).isAbsent();
    assertThat(result).isEqualTo(Result.retry());
  }

  @Test
  public void preAuthTeksReceivedWorker_noInternetConnection_resultRetry() throws Exception {
    List<TemporaryExposureKey> TEKs = ImmutableList.of(key("key1"), key("key2"),
        key("key3"), key("key4"));
    Data inputData = new Data.Builder()
        .putByteArray(KEYS_BYTES, TemporaryExposureKeyHelper.keysToTEKExportBytes(TEKs))
        .build();
    when(workerParameters.getInputData()).thenReturn(inputData);
    // Throw NoInternetException when triggering UploadController.
    when(uploadController.submitKeysForCert(any()))
        .thenReturn(Futures.immediateFailedFuture(new NoInternetException()));
    // Store the pre-auth diagnosis.
    DiagnosisEntity diagnosisEntity = DiagnosisEntity.newBuilder()
        .setVerificationCode("verification-code")
        .setLongTermToken("long-term-token")
        .setRevisionToken("revisionToken")
        .setSharedStatus(Shared.NOT_ATTEMPTED)
        .setIsPreAuth(true)
        .build();
    diagnosisRepository.upsertAsync(diagnosisEntity).get();

    Result result = preAuthTEKsReceivedWorker.startWork().get();

    assertThat(result).isEqualTo(Result.retry());
  }

  @Test
  public void preAuthTeksReceivedWorker_validEncodedTEKsAndPreAuthDiagnosisPresentNoCert_submitKeysForCertAndResultSuccess()
      throws Exception {
    List<TemporaryExposureKey> TEKs = ImmutableList.of(key("key1"), key("key2"),
        key("key3"), key("key4"));
    Data inputData = new Data.Builder()
        .putByteArray(KEYS_BYTES, TemporaryExposureKeyHelper.keysToTEKExportBytes(TEKs))
        .build();
    when(workerParameters.getInputData()).thenReturn(inputData);
    // Store the pre-auth diagnosis.
    DiagnosisEntity diagnosisEntity = DiagnosisEntity.newBuilder()
        .setVerificationCode("verification-code")
        .setLongTermToken("long-term-token")
        .setRevisionToken("revisionToken")
        .setSharedStatus(Shared.NOT_ATTEMPTED)
        .setIsPreAuth(true)
        .build();
    diagnosisRepository.upsertAsync(diagnosisEntity).get();

    Result result = preAuthTEKsReceivedWorker.startWork().get();

    assertThat(result).isEqualTo(Result.success());
    verify(uploadController).submitKeysForCert(any());
    verify(uploadController).upload(any());
  }

  @Test
  public void preAuthTeksReceivedWorker_validEncodedTEKsAndPreAuthDiagnosisPresent_resultSuccess()
      throws Exception {
    List<TemporaryExposureKey> TEKs = ImmutableList.of(key("key1"), key("key2"),
        key("key3"), key("key4"));
    Data inputData = new Data.Builder()
        .putByteArray(KEYS_BYTES, TemporaryExposureKeyHelper.keysToTEKExportBytes(TEKs))
        .build();
    when(workerParameters.getInputData()).thenReturn(inputData);
    // Store the pre-auth diagnosis with a certificate.
    DiagnosisEntity diagnosisEntity = DiagnosisEntity.newBuilder()
        .setVerificationCode("verification-code")
        .setLongTermToken("long-term-token")
        .setRevisionToken("revisionToken")
        .setSharedStatus(Shared.NOT_ATTEMPTED)
        .setCertificate("cert")
        .setIsPreAuth(true)
        .build();
    diagnosisRepository.upsertAsync(diagnosisEntity).get();

    Result result = preAuthTEKsReceivedWorker.startWork().get();

    assertThat(result).isEqualTo(Result.success());
    verify(uploadController, never()).submitKeysForCert(any());
    verify(uploadController).upload(any());
  }

  private void verifyResultFailureAndNoWorkDone(Result result) {
    assertThat(result).isEqualTo(Result.failure());
    verify(uploadController, never()).submitKeysForCert(any());
    verify(uploadController, never()).upload(any());
  }

  private static TemporaryExposureKey key(String keyData) {
    return new TemporaryExposureKeyBuilder()
        .setKeyData(BASE64.decode(keyData))
        .setReportType(ReportType.CONFIRMED_TEST)
        .setRollingPeriod(144)
        .setRollingStartIntervalNumber(1)
        .setTransmissionRiskLevel(1)
        .build();
  }

}
