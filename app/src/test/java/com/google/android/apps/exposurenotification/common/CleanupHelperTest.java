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

package com.google.android.apps.exposurenotification.common;

import static com.google.android.apps.exposurenotification.common.CleanupHelper.EXPOSURE_CHECK_MAX_AGE;
import static com.google.android.apps.exposurenotification.common.CleanupHelper.VERIFICATION_CODE_REQUEST_MAX_AGE;
import static com.google.android.apps.exposurenotification.nearby.ExposureInfoCleanupWorker.EXPOSURE_INFO_CLEANUP_WORKER_TAG;
import static com.google.android.apps.exposurenotification.restore.RestoreNotificationWorker.RESTORE_NOTIFICATION_WORKER_TAG;
import static com.google.common.truth.Truth.assertThat;
import static org.robolectric.Shadows.shadowOf;

import android.app.NotificationManager;
import android.content.Context;
import android.net.Uri;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.work.Configuration;
import androidx.work.WorkInfo;
import androidx.work.WorkInfo.State;
import androidx.work.WorkManager;
import androidx.work.impl.utils.SynchronousExecutor;
import androidx.work.testing.WorkManagerTestInitHelper;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.BuildUtils.Type;
import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.android.apps.exposurenotification.common.time.RealTimeModule;
import com.google.android.apps.exposurenotification.nearby.ExposureInformationHelper;
import com.google.android.apps.exposurenotification.proto.EnxLogExtension;
import com.google.android.apps.exposurenotification.proto.WorkManagerTask.Status;
import com.google.android.apps.exposurenotification.proto.WorkManagerTask.WorkerTask;
import com.google.android.apps.exposurenotification.restore.RestoreNotificationWorker;
import com.google.android.apps.exposurenotification.riskcalculation.ExposureClassification;
import com.google.android.apps.exposurenotification.storage.AnalyticsLoggingRepository;
import com.google.android.apps.exposurenotification.storage.CountryRepository;
import com.google.android.apps.exposurenotification.storage.DbModule;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity.TestResult;
import com.google.android.apps.exposurenotification.storage.DiagnosisRepository;
import com.google.android.apps.exposurenotification.storage.DownloadServerEntity;
import com.google.android.apps.exposurenotification.storage.DownloadServerRepository;
import com.google.android.apps.exposurenotification.storage.ExposureCheckEntity;
import com.google.android.apps.exposurenotification.storage.ExposureCheckRepository;
import com.google.android.apps.exposurenotification.storage.ExposureEntity;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationDatabase;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences.NotificationInteraction;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences.VaccinationStatus;
import com.google.android.apps.exposurenotification.storage.ExposureRepository;
import com.google.android.apps.exposurenotification.storage.VerificationCodeRequestEntity;
import com.google.android.apps.exposurenotification.storage.VerificationCodeRequestRepository;
import com.google.android.apps.exposurenotification.storage.WorkerStatusRepository;
import com.google.android.apps.exposurenotification.testsupport.ExposureClassificationUtils;
import com.google.android.apps.exposurenotification.testsupport.ExposureNotificationRules;
import com.google.android.apps.exposurenotification.testsupport.FakeClock;
import com.google.android.apps.exposurenotification.testsupport.InMemoryDb;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.testing.TestingExecutors;
import dagger.hilt.android.testing.BindValue;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import dagger.hilt.android.testing.UninstallModules;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowNotificationManager;
import org.threeten.bp.Duration;
import org.threeten.bp.Instant;
import org.threeten.bp.LocalDate;
import org.threeten.bp.ZoneOffset;

/**
 * Unit test for the EnTurndownHelper.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4.class)
@Config(application = HiltTestApplication.class)
@UninstallModules({DbModule.class, RealTimeModule.class})
public class CleanupHelperTest {

  private final Context context = ApplicationProvider.getApplicationContext();

  @Rule
  public ExposureNotificationRules rules =
      ExposureNotificationRules.forTest(this).withMocks().build();

  private final Uri index = Uri.parse("example.com/index");
  private final Uri file = Uri.parse("example.com/file");
  private final ShadowNotificationManager notificationManager =
      shadowOf((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE));

  @Inject
  ExposureNotificationSharedPreferences exposureNotificationSharedPreferences;
  @Inject
  ExposureInformationHelper exposureInformationHelper;
  @Inject
  AnalyticsLoggingRepository analyticsLoggingRepository;
  @Inject
  CountryRepository countryRepository;
  @Inject
  DiagnosisRepository diagnosisRepository;
  @Inject
  DownloadServerRepository downloadServerRepository;
  @Inject
  ExposureRepository exposureRepository;
  @Inject
  ExposureCheckRepository exposureCheckRepository;
  @Inject
  VerificationCodeRequestRepository verificationCodeRequestRepository;
  @Inject
  WorkerStatusRepository workerStatusRepository;
  @Inject
  NotificationHelper notificationHelper;

  @BindValue
  ExposureNotificationDatabase db = InMemoryDb.create();
  @BindValue
  Clock clock = new FakeClock();

  private Instant earliestExposureCheckThreshold;
  private Instant earliestVerificationCodeRequestThreshold;
  private WorkManager workManager;
  private CleanupHelper cleanupHelper;

  @Before
  public void setUp() throws Exception {
    rules.hilt().inject();

    earliestExposureCheckThreshold = clock.now().minus(EXPOSURE_CHECK_MAX_AGE);
    earliestVerificationCodeRequestThreshold = clock.now().minus(VERIFICATION_CODE_REQUEST_MAX_AGE);

    // Initialize WorkManager for testing.
    Configuration config = new Configuration.Builder()
        .setExecutor(new SynchronousExecutor())
        .build();
    WorkManagerTestInitHelper.initializeTestWorkManager(
        context, config);
    workManager = WorkManager.getInstance(ApplicationProvider.getApplicationContext());

    cleanupHelper = new CleanupHelper(
        context,
        exposureNotificationSharedPreferences,
        analyticsLoggingRepository,
        countryRepository,
        diagnosisRepository,
        downloadServerRepository,
        exposureRepository,
        exposureCheckRepository,
        verificationCodeRequestRepository,
        workerStatusRepository,
        exposureInformationHelper,
        workManager,
        notificationHelper,
        clock,
        TestingExecutors.sameThreadScheduledExecutor());
  }

  @After
  public void tearDown() {
    db.close();
  }

  @Test
  public void deleteOutdatedData_noOutdatedData_noDataGotDeleted() throws Exception {
    List<ExposureCheckEntity> exposureChecks = getNonOutdatedExposureChecks();
    List<ExposureCheckEntity> expectedExposureChecks = exposureChecks;
    List<VerificationCodeRequestEntity> requests = getNonOutdatedVerificationCodeRequests();
    List<VerificationCodeRequestEntity> expectedRequests = requests;
    // Insert the data.
    insertExposureChecks(exposureChecks);
    insertCodeRequests(requests);

    cleanupHelper.deleteOutdatedData();
    // Get the data retained after deletion.
    List<ExposureCheckEntity> storedExposureChecks = new ArrayList<>();
    exposureCheckRepository.getLastXExposureChecksLiveData(expectedExposureChecks.size())
        .observeForever(storedExposureChecks::addAll);
    List<VerificationCodeRequestEntity> storedRequests = verificationCodeRequestRepository
        .getLastXRequestsNotOlderThanThresholdAsync(
            Instant.ofEpochMilli(-1L), expectedRequests.size())
        .get();

    assertThat(storedExposureChecks).containsExactlyElementsIn(expectedExposureChecks);
    assertThat(storedRequests).containsExactlyElementsIn(expectedRequests);
  }

  @Test
  public void deleteOutdatedData_outdatedExposureChecksPresent_outdatedExposureChecksDeleted()
      throws Exception {
    List<VerificationCodeRequestEntity> requests = getNonOutdatedVerificationCodeRequests();
    List<VerificationCodeRequestEntity> expectedRequests = requests;
    // We expect only those exposure checks, which are not outdated i.e. captured later than
    // {@code WorkerStartupManager.TWO_WEEKS} ago.
    List<ExposureCheckEntity> exposureChecks = getExposureChecks();
    List<ExposureCheckEntity> expectedExposureChecks =
        exposureChecks.subList(1, exposureChecks.size());
    // Insert the data.
    insertExposureChecks(exposureChecks);
    insertCodeRequests(requests);

    cleanupHelper.deleteOutdatedData();
    // Get the data retained after deletion.
    List<ExposureCheckEntity> storedExposureChecks = new ArrayList<>();
    exposureCheckRepository.getLastXExposureChecksLiveData(expectedExposureChecks.size())
        .observeForever(storedExposureChecks::addAll);
    List<VerificationCodeRequestEntity> storedRequests = verificationCodeRequestRepository
        .getLastXRequestsNotOlderThanThresholdAsync(
            Instant.ofEpochMilli(-1L), expectedRequests.size())
        .get();

    assertThat(storedExposureChecks).containsExactlyElementsIn(expectedExposureChecks);
    assertThat(storedRequests).containsExactlyElementsIn(expectedRequests);
  }

  @Test
  public void deleteOutdatedData_outdatedRequestsPresent_outdatedRequestsDeleted()
      throws Exception {
    List<ExposureCheckEntity> exposureChecks = getNonOutdatedExposureChecks();
    List<ExposureCheckEntity> expectedExposureChecks = exposureChecks;
    // We expect only those requests, which are not outdated i.e. captured later than
    // {@code WorkerStartupManager.THIRTY_DAYS} ago.
    List<VerificationCodeRequestEntity> requests = getVerificationCodeRequests();
    List<VerificationCodeRequestEntity> expectedRequests = requests.subList(1, requests.size());
    // Insert the data.
    insertExposureChecks(exposureChecks);
    insertCodeRequests(requests);

    cleanupHelper.deleteOutdatedData();
    // Get the data retained after deletion.
    List<ExposureCheckEntity> storedExposureChecks = new ArrayList<>();
    exposureCheckRepository.getLastXExposureChecksLiveData(expectedExposureChecks.size())
        .observeForever(storedExposureChecks::addAll);
    List<VerificationCodeRequestEntity> storedRequests = verificationCodeRequestRepository
        .getLastXRequestsNotOlderThanThresholdAsync(
            Instant.ofEpochMilli(-1L), expectedRequests.size())
        .get();

    assertThat(storedExposureChecks).containsExactlyElementsIn(expectedExposureChecks);
    assertThat(storedRequests).containsExactlyElementsIn(expectedRequests);
  }

  @Test
  public void deleteOutdatedData_bothOutdatedExposureChecksAndRequestsPresent_outdatedDataDeleted()
      throws Exception {
    // We expect only those exposure checks, which are not outdated i.e. captured later than
    // {@code WorkerStartupManager.TWO_WEEKS} ago.
    List<ExposureCheckEntity> exposureChecks = getExposureChecks();
    List<ExposureCheckEntity> expectedExposureChecks =
        exposureChecks.subList(1, exposureChecks.size());
    // We expect only those requests, which are not outdated i.e. captured later than
    // {@code WorkerStartupManager.THIRTY_DAYS} ago.
    List<VerificationCodeRequestEntity> requests = getVerificationCodeRequests();
    List<VerificationCodeRequestEntity> expectedRequests = requests.subList(1, requests.size());
    // Insert the data.
    insertExposureChecks(exposureChecks);
    insertCodeRequests(requests);

    cleanupHelper.deleteOutdatedData();
    // Get the data retained after deletion.
    List<ExposureCheckEntity> storedExposureChecks = new ArrayList<>();
    exposureCheckRepository.getLastXExposureChecksLiveData(expectedExposureChecks.size())
        .observeForever(storedExposureChecks::addAll);
    List<VerificationCodeRequestEntity> storedRequests = verificationCodeRequestRepository
        .getLastXRequestsNotOlderThanThresholdAsync(
            Instant.ofEpochMilli(-1L), expectedRequests.size())
        .get();

    assertThat(storedExposureChecks).containsExactlyElementsIn(expectedExposureChecks);
    assertThat(storedRequests).containsExactlyElementsIn(expectedRequests);
  }

  @Test
  public void deleteOutdatedData_activeExposure_exposureInfoNotDeleted() {
    exposureNotificationSharedPreferences.setExposureClassification(
        ExposureClassificationUtils.getActiveExposure());

    assertThat(exposureInformationHelper.isActiveExposurePresent()).isTrue();
    cleanupHelper.deleteOutdatedData();

    assertThat(exposureInformationHelper.isActiveExposurePresent()).isTrue();
  }

  @Test
  public void deleteOutdatedData_outdatedExposure_exposureInfoDeleted() {
    exposureNotificationSharedPreferences.setExposureClassification(
        ExposureClassificationUtils.getOutdatedExposure());

    assertThat(exposureInformationHelper.isOutdatedExposurePresent()).isTrue();
    cleanupHelper.deleteOutdatedData();

    assertThat(exposureInformationHelper.isOutdatedExposurePresent()).isFalse();
  }

  @Test
  public void resetOutdatedData_outdatedNoncesPresent_outdatedNoncesReset()
      throws Exception {
    // We expect only those requests, which are not outdated i.e. captured later than
    // {@code WorkerStartupManager.THIRTY_DAYS} ago.
    List<VerificationCodeRequestEntity> requests = getVerificationCodeRequests();
    List<VerificationCodeRequestEntity> expectedRequests;
    // Reset nonces for expired verification code requests before adding them to a list of expected
    // requests. An expired verification code request is a request with expiresAtTime in the future.
    expectedRequests = requests.stream()
        .filter(request -> request.getNonce().equals("dummy-nonce-to-reset"))
        .map(request -> request.toBuilder().setNonce("").build())
        .collect(Collectors.toList());
    expectedRequests.add(requests.get(requests.size() - 1));
    // Insert the data.
    insertCodeRequests(requests);

    cleanupHelper.resetOutdatedData();
    // Get the data retained after deletion.
    List<VerificationCodeRequestEntity> storedRequests = verificationCodeRequestRepository
        .getLastXRequestsNotOlderThanThresholdAsync(
            Instant.ofEpochMilli(-1L), expectedRequests.size())
        .get();

    assertThat(storedRequests).containsExactlyElementsIn(expectedRequests);
  }

  @Test
  public void deleteObsoleteStorageForTurnDown_noActiveExposure_obsoleteStorageDeleted()
      throws Exception {
    // Insert the data.
    createNonObsoleteStorage();
    createObsoleteStorage();
    createAnalyticsData();

    List<DiagnosisEntity> observer = new ArrayList<>();
    diagnosisRepository.getAllLiveData().observeForever(observer::addAll);
    cleanupHelper.deleteObsoleteStorageForTurnDown().get();
    List<WorkInfo> exposureCleanupWorkInfos =
        workManager.getWorkInfosByTag(EXPOSURE_INFO_CLEANUP_WORKER_TAG).get();

    assertNonObsoleteStoragePresent(observer);
    assertObsoleteStorageDeleted();
    assertAnalyticsDataDeleted();
    assertThat(exposureCleanupWorkInfos).isEmpty();
  }

  @Test
  public void deleteObsoleteStorageForTurnDown_activeExposure_obsoleteStorageDeletedAndExposureCleanupEnqueued()
      throws Exception {
    // Insert the data.
    exposureNotificationSharedPreferences.setExposureClassification(
        ExposureClassificationUtils.getActiveExposure());
    createNonObsoleteStorage();
    createObsoleteStorage();
    createAnalyticsData();

    List<DiagnosisEntity> observer = new ArrayList<>();
    diagnosisRepository.getAllLiveData().observeForever(observer::addAll);
    cleanupHelper.deleteObsoleteStorageForTurnDown().get();
    List<WorkInfo> exposureCleanupWorkInfos =
        workManager.getWorkInfosByTag(EXPOSURE_INFO_CLEANUP_WORKER_TAG).get();

    assertNonObsoleteStoragePresent(observer);
    assertObsoleteStorageDeleted();
    assertAnalyticsDataDeleted();
    assertThat(exposureCleanupWorkInfos).hasSize(1);
  }

  @Test
  public void cancelPendingRestoreNotificationsAndJob_pendingNotificationForV2_pendingNotificationCanceled()
      throws Exception {
    if (BuildUtils.getType() == Type.V3) {
      return;
    }
    // Show restore notification
    notificationHelper.showReActivateENAppNotification(context,
        R.string.reactivate_exposure_notification_app_subject,
        R.string.reactivate_exposure_notification_app_body);
    // Assert that notification is displayed.
    assertThat(notificationManager.getAllNotifications()).hasSize(1);

    cleanupHelper.cancelPendingRestoreNotificationsAndJob().get();

    assertThat(notificationManager.getAllNotifications()).isEmpty();
  }

  @Test
  public void cancelPendingRestoreNotificationsAndJob_pendingJobForV2_pendingJobCanceled()
      throws Exception {
    if (BuildUtils.getType() == Type.V3) {
      return;
    }
    // Enqueue restore notification work.
    RestoreNotificationWorker.scheduleWork(workManager);
    // Assert that work has been enqueued.
    List<WorkInfo> workInfos = workManager.getWorkInfosByTag(RESTORE_NOTIFICATION_WORKER_TAG).get();
    assertThat(workInfos).hasSize(1);
    assertThat(workInfos.get(0).getState()).isEqualTo(State.ENQUEUED);

    cleanupHelper.cancelPendingRestoreNotificationsAndJob().get();
    workInfos = workManager.getWorkInfosByTag(RESTORE_NOTIFICATION_WORKER_TAG).get();

    assertThat(workInfos).hasSize(1);
    assertThat(workInfos.get(0).getState()).isEqualTo(State.CANCELLED);
  }

  @Test
  public void cancelPendingRestoreNotificationsAndJob_pendingNotificationAndJobForV2_bothPendingNotificationAndJobCanceled()
      throws Exception {
    if (BuildUtils.getType() == Type.V3) {
      return;
    }
    // Enqueue restore notification work.
    RestoreNotificationWorker.scheduleWork(workManager);
    // Show restore notification
    notificationHelper.showReActivateENAppNotification(context,
        R.string.reactivate_exposure_notification_app_subject,
        R.string.reactivate_exposure_notification_app_body);
    // Assert that work has been enqueued and notification is displayed.
    List<WorkInfo> workInfos = workManager.getWorkInfosByTag(RESTORE_NOTIFICATION_WORKER_TAG).get();
    assertThat(workInfos).hasSize(1);
    assertThat(workInfos.get(0).getState()).isEqualTo(State.ENQUEUED);
    assertThat(notificationManager.getAllNotifications()).hasSize(1);

    cleanupHelper.cancelPendingRestoreNotificationsAndJob().get();
    workInfos = workManager.getWorkInfosByTag(RESTORE_NOTIFICATION_WORKER_TAG).get();

    assertThat(workInfos).hasSize(1);
    assertThat(workInfos.get(0).getState()).isEqualTo(State.CANCELLED);
    assertThat(notificationManager.getAllNotifications()).isEmpty();
  }

  /*
   * Helper methods for asserting that we clean up only that data, which needs to be cleaned up.
   */
  private void assertNonObsoleteStoragePresent(List<DiagnosisEntity> observer) {
    // Assert that no test results are deleted or modified.
    assertThat(observer).hasSize(2);
    List<String> verificationCodes = observer.stream()
        .map(DiagnosisEntity::getVerificationCode)
        .collect(Collectors.toList());
    assertThat(verificationCodes).containsExactly("code1", "code2");
  }

  private void assertObsoleteStorageDeleted() throws Exception {
    // Assert no analytics logs are stored anymore.
    assertThat(analyticsLoggingRepository.getEventsBatch()).isEmpty();
    // Assert no country entities are stored anymore.
    assertThat(countryRepository.getRecentlySeenCountryCodes(Instant.ofEpochMilli(0))).isEmpty();
    // Assert no revision tokens are stored anymore.
    assertThat(diagnosisRepository.getMostRecentRevisionTokenAsync().get()).isNull();
    // Assert no download server entities are stored anymore.
    assertThat(downloadServerRepository.getMostRecentSuccessfulDownload(index)).isNull();
    // Assert no exposure entities are stored anymore.
    assertThat(exposureRepository.getAllExposureEntities()).isEmpty();
    // Assert no exposure checks are stored anymore.
    assertThat(exposureCheckRepository.getAllExposureChecks()).isEmpty();
    // Assert no requests for a verification code are stored anymore.
    assertThat(verificationCodeRequestRepository.getAll()).isEmpty();
    // Assert that no worker status entities are stored anymore.
    assertThat(workerStatusRepository.getLastRunTimestamp(WorkerTask.TASK_PROVIDE_DIAGNOSIS_KEYS,
        Status.STATUS_STARTED.toString())).isEqualTo(Optional.absent());
  }

  private void assertAnalyticsDataDeleted() {
    // Assert that private analytics data is deleted.
    assertThat(exposureNotificationSharedPreferences.getPrivateAnalyticsLastSubmittedCodeTime())
        .isEqualTo(Instant.EPOCH);
    assertThat(exposureNotificationSharedPreferences.getPrivateAnalyticsLastSubmittedKeysTime())
        .isEqualTo(Instant.EPOCH);
    assertThat(exposureNotificationSharedPreferences.getExposureNotificationLastShownTime())
        .isEqualTo(Instant.EPOCH);
    assertThat(
        exposureNotificationSharedPreferences.getExposureNotificationLastShownClassification())
        .isEqualTo(0);
    assertThat(exposureNotificationSharedPreferences.getExposureNotificationLastInteractionTime())
        .isEqualTo(Instant.EPOCH);
    assertThat(exposureNotificationSharedPreferences.getExposureNotificationLastInteractionType())
        .isEqualTo(NotificationInteraction.UNKNOWN);
    assertThat(
        exposureNotificationSharedPreferences.getExposureNotificationLastShownClassification())
        .isEqualTo(0);
    assertThat(exposureNotificationSharedPreferences.getPrivateAnalyticsLastExposureTime())
        .isEqualTo(Instant.EPOCH);
    assertThat(exposureNotificationSharedPreferences.getLastVaccinationStatusResponseTime())
        .isEqualTo(Instant.EPOCH);
    assertThat(exposureNotificationSharedPreferences.getLastVaccinationStatus())
        .isEqualTo(VaccinationStatus.UNKNOWN);
    assertThat(exposureNotificationSharedPreferences.getPrivateAnalyticsLastReportType()).isNull();

    // And assert that the last analytics logging timestamp is also cleaned.
    assertThat(exposureNotificationSharedPreferences.maybeGetAnalyticsLoggingLastTimestamp())
        .isAbsent();
  }

  /*
   * Helper methods for getting data, which will be stored to test the
   * {@link ObsoleteStorageCleanupHelper} APIs.
   */
  private List<ExposureCheckEntity> getNonOutdatedExposureChecks() {
    return ImmutableList.of(
        ExposureCheckEntity.create(earliestExposureCheckThreshold),
        ExposureCheckEntity.create(earliestExposureCheckThreshold.plus(Duration.ofDays(1))));
  }

  private List<ExposureCheckEntity> getExposureChecks() {
    return ImmutableList.of(
        // Obsolete exposure check
        ExposureCheckEntity.create(earliestExposureCheckThreshold.minus(Duration.ofDays(1))),
        // Non-obsolete exposure checks
        ExposureCheckEntity.create(earliestExposureCheckThreshold),
        ExposureCheckEntity.create(earliestExposureCheckThreshold.plus(Duration.ofDays(1))));
  }

  private List<VerificationCodeRequestEntity> getNonOutdatedVerificationCodeRequests() {
    return ImmutableList.of(
        // Non-outdated verification code requests
        VerificationCodeRequestEntity.newBuilder()
            .setRequestTime(clock.now().minus(Duration.ofMinutes(10)))
            .setExpiresAtTime(clock.now().plus(Duration.ofMinutes(5)))
            .setNonce("dummy-nonce-0")
            .build(),
        VerificationCodeRequestEntity.newBuilder()
            .setRequestTime(clock.now().minus(Duration.ofMinutes(5)))
            .setExpiresAtTime(clock.now().plus(Duration.ofMinutes(10)))
            .setNonce("dummy-nonce-1")
            .build());
  }

  private List<VerificationCodeRequestEntity> getVerificationCodeRequests() {
    return ImmutableList.of(
        // Outdated verification code request
        VerificationCodeRequestEntity.newBuilder()
            .setRequestTime(earliestVerificationCodeRequestThreshold.minus(Duration.ofDays(1)))
            .setExpiresAtTime(earliestVerificationCodeRequestThreshold.minus(Duration.ofDays(1)))
            .setNonce("dummy-nonce-0")
            .build(),
        // Non-outdated verification code requests
        VerificationCodeRequestEntity.newBuilder()
            .setRequestTime(earliestVerificationCodeRequestThreshold)
            .setExpiresAtTime(earliestVerificationCodeRequestThreshold)
            .setNonce("dummy-nonce-to-reset") // Expired request, nonce needs to be reset.
            .build(),
        VerificationCodeRequestEntity.newBuilder()
            .setRequestTime(earliestVerificationCodeRequestThreshold.plus(Duration.ofDays(1)))
            .setExpiresAtTime(earliestVerificationCodeRequestThreshold.plus(Duration.ofDays(1)))
            .setNonce("dummy-nonce-to-reset") // Expired request, nonce needs to be reset.
            .build(),
        VerificationCodeRequestEntity.newBuilder()
            .setRequestTime(clock.now())
            .setExpiresAtTime(clock.now().plus(Duration.ofMinutes(15)))
            .setNonce("dummy-nonce-2")
            .build());
  }

  /*
   * Helper methods for storing data, which is used to test the {@link ObsoleteStorageCleanupHelper}
   * APIs.
   */
  private void insertExposureChecks(List<ExposureCheckEntity> exposureChecks) {
    for (ExposureCheckEntity exposureCheck : exposureChecks) {
      exposureCheckRepository.insertExposureCheck(exposureCheck);
    }
  }

  private void insertCodeRequests(List<VerificationCodeRequestEntity> requests) throws Exception {
    for (VerificationCodeRequestEntity request : requests) {
      verificationCodeRequestRepository.upsertAsync(request).get();
    }
  }

  private void createNonObsoleteStorage() throws Exception {
    // Store some test results.
    DiagnosisEntity diagnosis1 = DiagnosisEntity.newBuilder()
        .setVerificationCode("code1")
        .setCreatedTimestampMs(10L)
        .setRevisionToken("revisionToken1")
        .build();
    diagnosisRepository.upsertAsync(diagnosis1).get();
    DiagnosisEntity diagnosis2 = DiagnosisEntity.newBuilder()
        .setVerificationCode("code2")
        .setCreatedTimestampMs(42L)
        .setRevisionToken("revisionToken2")
        .build();
    diagnosisRepository.upsertAsync(diagnosis2).get();
  }

  private void createObsoleteStorage() throws Exception {
    // Store some non-outdated exposure checks.
    for (ExposureCheckEntity exposureCheck : getNonOutdatedExposureChecks()) {
      exposureCheckRepository.insertExposureCheck(exposureCheck);
    }
    // Store some analytics logs.
    analyticsLoggingRepository.recordEvent(
        EnxLogExtension.newBuilder().getDefaultInstanceForType());
    analyticsLoggingRepository.recordEvent(
        EnxLogExtension.newBuilder().getDefaultInstanceForType());
    // Store some country entities.
    countryRepository.markCountrySeen("GB");
    // Store some download server entities.
    downloadServerRepository.upsert(DownloadServerEntity.create(index, file));
    // Store some exposure entities.
    List<ExposureEntity> exposureEntities = ImmutableList.of(
        ExposureEntity.create(LocalDate.now(ZoneOffset.UTC).toEpochDay(), 10.0),
        ExposureEntity.create(LocalDate.now(ZoneOffset.UTC).toEpochDay(), 20.0));
    exposureRepository.deleteInsertExposureEntities(exposureEntities);
    // Store some requests for a verification code.
    verificationCodeRequestRepository.upsertAsync(VerificationCodeRequestEntity.newBuilder()
        .setRequestTime(clock.now())
        .setExpiresAtTime(clock.now())
        .setNonce("nonce-0")
        .build()).get();
    verificationCodeRequestRepository.upsertAsync(VerificationCodeRequestEntity.newBuilder()
        .setRequestTime(clock.now().plus(Duration.ofHours(1)))
        .setExpiresAtTime(clock.now().plus(Duration.ofHours(1)))
        .setNonce("nonce-1")
        .build()).get();
    // And finally store some worker status entities.
    workerStatusRepository.upsert(WorkerTask.TASK_PROVIDE_DIAGNOSIS_KEYS,
        Status.STATUS_STARTED.toString(), clock.now());
  }

  private void createAnalyticsData() {
    exposureNotificationSharedPreferences.setPrivateAnalyticsState(true);
    Instant time = Instant.ofEpochMilli(123456789L);
    int classificationIndex = 1;
    long exposureDay = (time.getEpochSecond() / TimeUnit.DAYS.toSeconds(1)) - 10;
    ExposureClassification exposureClassification = ExposureClassification
        .create(classificationIndex, "", exposureDay);
    exposureNotificationSharedPreferences.setPrivateAnalyticsWorkerLastTimeForDaily(time);
    exposureNotificationSharedPreferences.setPrivateAnalyticsWorkerLastTimeForBiweekly(time);
    exposureNotificationSharedPreferences.setPrivateAnalyticsLastSubmittedCodeTime(time);
    exposureNotificationSharedPreferences.setPrivateAnalyticsLastSubmittedKeysTime(time);
    exposureNotificationSharedPreferences
        .setExposureNotificationLastShownClassification(time, exposureClassification);
    exposureNotificationSharedPreferences
        .setExposureNotificationLastInteraction(time, NotificationInteraction.CLICKED, 2);
    exposureNotificationSharedPreferences
        .setLastVaccinationResponse(time, VaccinationStatus.VACCINATED);
    exposureNotificationSharedPreferences.setPrivateAnalyticsLastReportType(TestResult.CONFIRMED);
  }

}
