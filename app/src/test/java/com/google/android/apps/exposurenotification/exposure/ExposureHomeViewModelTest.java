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

package com.google.android.apps.exposurenotification.exposure;

import static com.google.android.apps.exposurenotification.restore.RestoreNotificationWorker.RESTORE_NOTIFICATION_WORKER_TAG;
import static com.google.common.truth.Truth.assertThat;
import static org.robolectric.Shadows.shadowOf;

import android.app.NotificationManager;
import android.content.Context;
import androidx.lifecycle.LiveData;
import androidx.test.core.app.ApplicationProvider;
import androidx.work.Configuration;
import androidx.work.WorkInfo;
import androidx.work.WorkInfo.State;
import androidx.work.WorkManager;
import androidx.work.impl.utils.SynchronousExecutor;
import androidx.work.testing.WorkManagerTestInitHelper;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.BuildUtils;
import com.google.android.apps.exposurenotification.common.BuildUtils.Type;
import com.google.android.apps.exposurenotification.common.NotificationHelper;
import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.android.apps.exposurenotification.common.time.RealTimeModule;
import com.google.android.apps.exposurenotification.nearby.ExposureInformationHelper;
import com.google.android.apps.exposurenotification.restore.RestoreNotificationWorker;
import com.google.android.apps.exposurenotification.riskcalculation.ExposureClassification;
import com.google.android.apps.exposurenotification.storage.DbModule;
import com.google.android.apps.exposurenotification.storage.ExposureCheckEntity;
import com.google.android.apps.exposurenotification.storage.ExposureCheckRepository;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationDatabase;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences.BadgeStatus;
import com.google.android.apps.exposurenotification.testsupport.ExposureClassificationUtils;
import com.google.android.apps.exposurenotification.testsupport.ExposureNotificationRules;
import com.google.android.apps.exposurenotification.testsupport.FakeClock;
import com.google.android.apps.exposurenotification.testsupport.InMemoryDb;
import com.google.common.util.concurrent.testing.TestingExecutors;
import dagger.hilt.android.testing.BindValue;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import dagger.hilt.android.testing.UninstallModules;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowNotificationManager;
import org.threeten.bp.Instant;
import org.threeten.bp.LocalDate;
import org.threeten.bp.OffsetDateTime;
import org.threeten.bp.ZoneOffset;

@HiltAndroidTest
@RunWith(RobolectricTestRunner.class)
@Config(application = HiltTestApplication.class)
@UninstallModules({DbModule.class, RealTimeModule.class})
public class ExposureHomeViewModelTest {

  @Rule
  public ExposureNotificationRules rules = ExposureNotificationRules.forTest(this).build();

  private final Context context = ApplicationProvider.getApplicationContext();
  private final ShadowNotificationManager notificationManager =
      shadowOf((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE));

  @Inject
  ExposureNotificationSharedPreferences exposureNotificationSharedPreferences;

  @Inject
  NotificationHelper notificationHelper;

  @Inject
  ExposureCheckRepository exposureCheckRepository;

  @Inject
  ExposureInformationHelper exposureInformationHelper;

  @BindValue
  Clock clock = new FakeClock();

  @BindValue
  ExposureNotificationDatabase database = InMemoryDb.create();

  private ExposureHomeViewModel exposureHomeViewModel;
  private WorkManager workManager;

  @Before
  public void setup() {
    rules.hilt().inject();

    // Initialize WorkManager for testing.
    Configuration config = new Configuration.Builder()
        .setExecutor(new SynchronousExecutor())
        .build();
    WorkManagerTestInitHelper.initializeTestWorkManager(
        context, config);
    workManager = WorkManager.getInstance(ApplicationProvider.getApplicationContext());

    exposureHomeViewModel = new ExposureHomeViewModel(
        exposureNotificationSharedPreferences, exposureInformationHelper, exposureCheckRepository,
        notificationHelper, clock, workManager, TestingExecutors.sameThreadScheduledExecutor()
    );
  }

  @Test
  public void getExposureClassificationLiveData_deliversExposureClassificationToObservers() {
    ExposureClassification exposureClassification = ExposureClassification
        .create(0, "exposureClassification", 0L);
    AtomicReference<ExposureClassification> exposureClassificationAtomicReference =
        new AtomicReference<>();

    LiveData<ExposureClassification> liveData = exposureHomeViewModel
        .getExposureClassificationLiveData();
    liveData.observeForever(exposureClassificationAtomicReference::set);
    exposureNotificationSharedPreferences.setExposureClassification(exposureClassification);

    assertThat(exposureClassificationAtomicReference.get().getClassificationName())
        .isEqualTo("exposureClassification");
  }

  @Test
  public void getExposureClassification_returnsExposureClassificationThatWasSet() {
    ExposureClassification exposureClassification = ExposureClassification
        .create(0, "exposureClassification", 0L);
    exposureNotificationSharedPreferences.setExposureClassification(exposureClassification);

    ExposureClassification exposureClassification1 = exposureHomeViewModel
        .getExposureClassification();

    assertThat(exposureClassification1.getClassificationName())
        .isEqualTo("exposureClassification");
  }

  @Test
  public void getIsExposureClassificationRevoked_returnsExposureClassificationThatWasSet() {
    assertThat(exposureHomeViewModel.getIsExposureClassificationRevoked()).isFalse();
    exposureNotificationSharedPreferences.setIsExposureClassificationRevoked(true);
    assertThat(exposureHomeViewModel.getIsExposureClassificationRevoked()).isTrue();
  }

  @Test
  public void getIsExposureClassificationNewLiveData_deliversBadgeStatusToObservers() {
    BadgeStatus badgeStatus = BadgeStatus.DISMISSED;
    AtomicReference<BadgeStatus> badgeStatusAtomicReference = new AtomicReference<>();

    LiveData<BadgeStatus> liveData = exposureHomeViewModel
        .getIsExposureClassificationNewLiveData();
    liveData.observeForever(badgeStatusAtomicReference::set);
    exposureNotificationSharedPreferences.setIsExposureClassificationNewAsync(badgeStatus);

    assertThat(badgeStatusAtomicReference.get())
        .isEqualTo(BadgeStatus.DISMISSED);
  }

  @Test
  public void getIsExposureClassificationDateNewLiveData_deliversBadgeStatusToObservers() {
    BadgeStatus badgeStatus = BadgeStatus.DISMISSED;
    AtomicReference<BadgeStatus> badgeStatusAtomicReference = new AtomicReference<>();

    LiveData<BadgeStatus> liveData = exposureHomeViewModel
        .getIsExposureClassificationDateNewLiveData();
    liveData.observeForever(badgeStatusAtomicReference::set);
    exposureNotificationSharedPreferences.setIsExposureClassificationDateNewAsync(badgeStatus);

    assertThat(badgeStatusAtomicReference.get())
        .isEqualTo(BadgeStatus.DISMISSED);
  }

  @Test
  public void tryTransitionExposureClassificationNew_valueIsSet() {
    BadgeStatus from = BadgeStatus.SEEN;
    BadgeStatus to = BadgeStatus.DISMISSED;
    exposureNotificationSharedPreferences.setIsExposureClassificationNewAsync(BadgeStatus.SEEN);

    exposureHomeViewModel.tryTransitionExposureClassificationNew(from, to);

    assertThat(exposureNotificationSharedPreferences.getIsExposureClassificationNew())
        .isEqualTo(BadgeStatus.DISMISSED);
  }

  @Test
  public void tryTransitionExposureClassificationNew_valueNotSet() {
    BadgeStatus from = BadgeStatus.SEEN;
    BadgeStatus to = BadgeStatus.DISMISSED;
    exposureNotificationSharedPreferences.setIsExposureClassificationNewAsync(BadgeStatus.NEW);

    exposureHomeViewModel.tryTransitionExposureClassificationNew(from, to);

    assertThat(exposureNotificationSharedPreferences.getIsExposureClassificationNew())
        .isEqualTo(BadgeStatus.NEW);
  }

  @Test
  public void tryTransitionExposureClassificationDateNew_valueIsSet() {
    BadgeStatus from = BadgeStatus.SEEN;
    BadgeStatus to = BadgeStatus.DISMISSED;
    exposureNotificationSharedPreferences.setIsExposureClassificationDateNewAsync(BadgeStatus.SEEN);

    exposureHomeViewModel.tryTransitionExposureClassificationDateNew(from, to);

    assertThat(exposureNotificationSharedPreferences.getIsExposureClassificationDateNew())
        .isEqualTo(BadgeStatus.DISMISSED);
  }

  @Test
  public void tryTransitionExposureClassificationDateNew_valueNotSet() {
    BadgeStatus from = BadgeStatus.SEEN;
    BadgeStatus to = BadgeStatus.DISMISSED;
    exposureNotificationSharedPreferences.setIsExposureClassificationDateNewAsync(BadgeStatus.NEW);

    exposureHomeViewModel.tryTransitionExposureClassificationDateNew(from, to);

    assertThat(exposureNotificationSharedPreferences.getIsExposureClassificationDateNew())
        .isEqualTo(BadgeStatus.NEW);
  }

  @Test
  public void exposureCheckRepository_insertChecks_deliversChecksToObserver() {
    // GIVEN
    List<ExposureCheckEntity> exposureChecks = new ArrayList<>();
    exposureCheckRepository
        .insertExposureCheck(ExposureCheckEntity.create(Instant.ofEpochMilli(12345L)));
    exposureCheckRepository
        .insertExposureCheck(ExposureCheckEntity.create(Instant.ofEpochMilli(23456L)));

    // WHEN
    exposureHomeViewModel.getExposureChecksLiveData().observeForever(exposureChecks::addAll);

    // THEN
    assertThat(exposureChecks).hasSize(2);
    assertThat(exposureChecks.get(0).getCheckTime()).isEqualTo(Instant.ofEpochMilli(23456L));
  }

  @Test
  public void getDaysFromStartOfExposureString_exposure2DaysAgo_returns2DaysAgo() {
    // GIVEN
    ExposureClassification exposureClassification = ExposureClassification
        .create(0,ExposureClassification.NO_EXPOSURE_CLASSIFICATION_NAME,
            LocalDate.of(2021,2,6).toEpochDay());
    ((FakeClock) clock).set(Instant.from(OffsetDateTime
        .of(2021, 2 ,8, 18,0,0,0, ZoneOffset.UTC)));

    // WHEN
    String result = exposureHomeViewModel.getDaysFromStartOfExposureString(exposureClassification,
        ApplicationProvider.getApplicationContext());

    // THEN
    assertThat(result).isEqualTo("2 days ago");
  }

  @Test
  public void isActiveExposurePresent_noExposure_returnsFalse() {
    assertThat(exposureHomeViewModel.isActiveExposurePresent()).isFalse();
  }

  @Test
  public void isActiveExposurePresent_outdatedExposure_returnsFalse() {
    exposureNotificationSharedPreferences.setExposureClassification(
        ExposureClassificationUtils.getOutdatedExposure());

    assertThat(exposureHomeViewModel.isActiveExposurePresent()).isFalse();
  }

  @Test
  public void isActiveExposurePresent_activeExposure_returnsTrue() {
    exposureNotificationSharedPreferences.setExposureClassification(
        ExposureClassificationUtils.getActiveExposure());

    assertThat(exposureHomeViewModel.isActiveExposurePresent()).isTrue();
  }

  @Test
  public void dismissReactivateENAppNotificationAndPendingJob_V2pendingNotificationOnly_notificationCancelled()
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

    exposureHomeViewModel.dismissReactivateENAppNotificationAndPendingJob(context).get();

    assertThat(notificationManager.getAllNotifications()).isEmpty();
  }

  @Test
  public void dismissReactivateENAppNotificationAndPendingJob_V2pendingJobOnly_jobCanceled()
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

    exposureHomeViewModel.dismissReactivateENAppNotificationAndPendingJob(context).get();
    workInfos = workManager.getWorkInfosByTag(RESTORE_NOTIFICATION_WORKER_TAG).get();

    assertThat(workInfos).hasSize(1);
    assertThat(workInfos.get(0).getState()).isEqualTo(State.CANCELLED);
  }

  @Test
  public void dismissReactivateENAppNotificationAndPendingJob_V2pendingNotificationAndJob_bothCanceled()
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

    exposureHomeViewModel.dismissReactivateENAppNotificationAndPendingJob(context).get();
    workInfos = workManager.getWorkInfosByTag(RESTORE_NOTIFICATION_WORKER_TAG).get();

    assertThat(workInfos).hasSize(1);
    assertThat(workInfos.get(0).getState()).isEqualTo(State.CANCELLED);
    assertThat(notificationManager.getAllNotifications()).isEmpty();
  }

}