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

import static com.google.android.apps.exposurenotification.work.WorkerStartupManager.VERIFICATION_CODE_REQUEST_MAX_AGE;
import static com.google.android.apps.exposurenotification.work.WorkerStartupManager.EXPOSURE_CHECK_MAX_AGE;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.android.apps.exposurenotification.common.time.RealTimeModule;
import com.google.android.apps.exposurenotification.nearby.ExposureNotificationClientWrapper;
import com.google.android.apps.exposurenotification.nearby.PackageConfigurationHelper;
import com.google.android.apps.exposurenotification.storage.DbModule;
import com.google.android.apps.exposurenotification.storage.ExposureCheckEntity;
import com.google.android.apps.exposurenotification.storage.ExposureCheckRepository;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationDatabase;
import com.google.android.apps.exposurenotification.storage.VerificationCodeRequestEntity;
import com.google.android.apps.exposurenotification.storage.VerificationCodeRequestRepository;
import com.google.android.apps.exposurenotification.testsupport.ExposureNotificationRules;
import com.google.android.apps.exposurenotification.testsupport.FakeClock;
import com.google.android.apps.exposurenotification.testsupport.InMemoryDb;
import com.google.android.gms.nearby.exposurenotification.PackageConfiguration.PackageConfigurationBuilder;
import com.google.android.gms.tasks.Tasks;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.testing.TestingExecutors;
import dagger.hilt.android.testing.BindValue;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import dagger.hilt.android.testing.UninstallModules;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.annotation.Config;
import org.threeten.bp.Duration;
import org.threeten.bp.Instant;

@HiltAndroidTest
@RunWith(AndroidJUnit4.class)
@Config(application = HiltTestApplication.class)
@UninstallModules({DbModule.class, RealTimeModule.class})
public class WorkerStartupManagerTest {

  @Rule
  public ExposureNotificationRules rules =
      ExposureNotificationRules.forTest(this).withMocks().build();

  @Inject
  PackageConfigurationHelper packageConfigurationHelper;
  @Inject
  ExposureCheckRepository exposureCheckRepository;
  @Inject
  VerificationCodeRequestRepository verificationCodeRequestRepository;

  @Mock
  ExposureNotificationClientWrapper exposureNotificationClientWrapper;

  @BindValue
  ExposureNotificationDatabase db = InMemoryDb.create();
  @BindValue
  Clock clock = new FakeClock();

  private WorkerStartupManager workerStartupManager;

  @Before
  public void setUp() throws Exception {
    rules.hilt().inject();

    for (ExposureCheckEntity exposureCheck : getExposureCheckEntities()) {
      exposureCheckRepository.insertExposureCheck(exposureCheck);
    }

    for (VerificationCodeRequestEntity request : getVerificationCodeRequestEntities()) {
      verificationCodeRequestRepository.upsertAsync(request).get();
    }

    workerStartupManager = new WorkerStartupManager(
        exposureNotificationClientWrapper,
        MoreExecutors.newDirectExecutorService(),
        TestingExecutors.sameThreadScheduledExecutor(),
        packageConfigurationHelper,
        exposureCheckRepository,
        verificationCodeRequestRepository,
        clock
    );
  }

  @Test
  public void getIsEnabledWithStartupTasks_enNotEnabled_returnsFalseAndDeletesObsoletes()
      throws Exception {
    when(exposureNotificationClientWrapper.isEnabled()).thenReturn(Tasks.forResult(false));

    boolean isEnabledWithStartupTasks = workerStartupManager.getIsEnabledWithStartupTasks().get();

    assertThat(isEnabledWithStartupTasks).isFalse();
    assertThatObsoleteExposureChecksDeleted();
    assertThatObsoleteRequestsDeletedAndNoncesForExpiredRequestsReset();
  }

  @Test
  public void getIsEnabledWithStartupTasks_enClientThrowsException_returnsFalseAndDeletesObsoletes()
      throws Exception {
    when(exposureNotificationClientWrapper.isEnabled())
        .thenReturn(Tasks.forException(new Exception()));

    assertThrows(ExecutionException.class,
        () -> workerStartupManager.getIsEnabledWithStartupTasks().get());
    assertThatObsoleteExposureChecksDeleted();
    assertThatObsoleteRequestsDeletedAndNoncesForExpiredRequestsReset();
  }

  @Test
  public void getIsEnabledWithStartupTasks_enEnabledGetPckgConfigThrowsException_returnsTrueAndDeletesObsoletes()
      throws Exception {
    when(exposureNotificationClientWrapper.isEnabled()).thenReturn(Tasks.forResult(true));
    when(exposureNotificationClientWrapper.getPackageConfiguration())
        .thenReturn(Tasks.forException(new Exception()));

    boolean isEnabledWithStartupTasks = workerStartupManager.getIsEnabledWithStartupTasks().get();

    assertThat(isEnabledWithStartupTasks).isTrue();
    assertThatObsoleteExposureChecksDeleted();
    assertThatObsoleteRequestsDeletedAndNoncesForExpiredRequestsReset();
  }

  @Test
  public void getIsEnabledWithStartupTasks_enEnabledGetPckgConfigSucceeds_returnsTrueAndDeletesObsoletes()
      throws Exception {
    when(exposureNotificationClientWrapper.isEnabled()).thenReturn(Tasks.forResult(true));
    when(exposureNotificationClientWrapper.getPackageConfiguration())
        .thenReturn(Tasks.forResult(new PackageConfigurationBuilder().build()));

    boolean isEnabledWithStartupTasks = workerStartupManager.getIsEnabledWithStartupTasks().get();

    assertThat(isEnabledWithStartupTasks).isTrue();
    assertThatObsoleteExposureChecksDeleted();
    assertThatObsoleteRequestsDeletedAndNoncesForExpiredRequestsReset();
  }

  private void assertThatObsoleteExposureChecksDeleted() {
    List<ExposureCheckEntity> exposureChecks = getExposureCheckEntities();
    // We expect only those exposure checks, which are not obsolete i.e. captured later than
    // {@code WorkerStartupManager.TWO_WEEKS} ago.
    List<ExposureCheckEntity> expectedExposureChecks =
        exposureChecks.subList(1, exposureChecks.size());

    List<ExposureCheckEntity> storedExposureChecks = new ArrayList<>();
    exposureCheckRepository
        .getLastXExposureChecksLiveData(exposureChecks.size())
        .observeForever(storedExposureChecks::addAll);

    assertThat(storedExposureChecks).containsExactlyElementsIn(expectedExposureChecks);
  }

  private void assertThatObsoleteRequestsDeletedAndNoncesForExpiredRequestsReset()
      throws Exception {
    List<VerificationCodeRequestEntity> requests = getVerificationCodeRequestEntities();
    // We expect only those requests, which are not obsolete i.e. captured later than
    // {@code WorkerStartupManager.THIRTY_DAYS} ago.
    List<VerificationCodeRequestEntity> expectedRequests;
    // Reset nonces for expired verification code requests before adding them to a list of expected
    // requests. An expired verification code request is a request with expiresAtTime in the future.
    expectedRequests = requests.stream()
        .filter(request -> request.getNonce().equals("dummy-nonce-to-reset"))
        .map(request -> request.toBuilder().setNonce("").build())
        .collect(Collectors.toList());
    expectedRequests.add(requests.get(requests.size() - 1));

    List<VerificationCodeRequestEntity> storedRequests = verificationCodeRequestRepository
        .getLastXRequestsNotOlderThanThresholdAsync(Instant.ofEpochMilli(-1L), requests.size())
        .get();

    assertThat(storedRequests).containsExactlyElementsIn(expectedRequests);
  }

  private List<ExposureCheckEntity> getExposureCheckEntities() {
    Instant earliestThreshold= clock.now().minus(EXPOSURE_CHECK_MAX_AGE);
    return ImmutableList.of(
        // Obsolete exposure check
        ExposureCheckEntity.create(earliestThreshold.minus(Duration.ofDays(1))),
        // Non-obsolete exposure checks
        ExposureCheckEntity.create(earliestThreshold),
        ExposureCheckEntity.create(earliestThreshold.plus(Duration.ofDays(1))));
  }

  private List<VerificationCodeRequestEntity> getVerificationCodeRequestEntities() {
    Instant earliestThreshold = clock.now().minus(VERIFICATION_CODE_REQUEST_MAX_AGE);
    return ImmutableList.of(
        // Obsolete verification code request
        VerificationCodeRequestEntity.newBuilder()
            .setRequestTime(earliestThreshold.minus(Duration.ofDays(1)))
            .setExpiresAtTime(earliestThreshold.minus(Duration.ofDays(1)))
            .setNonce("dummy-nonce-0")
            .build(),
        // Non-obsolete verification code requests
        VerificationCodeRequestEntity.newBuilder()
            .setRequestTime(earliestThreshold)
            .setExpiresAtTime(earliestThreshold)
            .setNonce("dummy-nonce-to-reset")
            .build(),
        VerificationCodeRequestEntity.newBuilder()
            .setRequestTime(earliestThreshold.plus(Duration.ofDays(1)))
            .setExpiresAtTime(earliestThreshold.plus(Duration.ofDays(1)))
            .setNonce("dummy-nonce-to-reset")
            .build(),
        VerificationCodeRequestEntity.newBuilder()
            .setRequestTime(clock.now())
            .setExpiresAtTime(clock.now().plus(Duration.ofMinutes(15)))
            .setNonce("dummy-nonce-2")
            .build());
  }

}
