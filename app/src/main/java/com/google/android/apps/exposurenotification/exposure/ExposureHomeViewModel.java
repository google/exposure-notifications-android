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

import android.content.Context;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;
import androidx.work.WorkManager;
import com.google.android.apps.exposurenotification.common.NotificationHelper;
import com.google.android.apps.exposurenotification.common.Qualifiers.BackgroundExecutor;
import com.google.android.apps.exposurenotification.common.StringUtils;
import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.android.apps.exposurenotification.nearby.ExposureInformationHelper;
import com.google.android.apps.exposurenotification.restore.RestoreNotificationWorker;
import com.google.android.apps.exposurenotification.riskcalculation.ExposureClassification;
import com.google.android.apps.exposurenotification.storage.ExposureCheckEntity;
import com.google.android.apps.exposurenotification.storage.ExposureCheckRepository;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences.BadgeStatus;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.hilt.android.lifecycle.HiltViewModel;
import java.util.List;
import java.util.concurrent.ExecutorService;
import javax.inject.Inject;
import org.threeten.bp.ZoneId;

/**
 * View model that updates on the exposures and the exposure checks.
 */
@HiltViewModel
public class ExposureHomeViewModel extends ViewModel {

  // Number of exposure checks we want to display.
  private static final int NUM_CHECKS_TO_DISPLAY = 5;

  private final ExposureNotificationSharedPreferences exposureNotificationSharedPreferences;
  private final ExposureInformationHelper exposureInformationHelper;
  private final NotificationHelper notificationHelper;
  private final LiveData<List<ExposureCheckEntity>> getExposureChecksLiveData;
  private final Clock clock;
  private final WorkManager workManager;
  private final ExecutorService backgroundExecutor;

  @Inject
  public ExposureHomeViewModel(
      ExposureNotificationSharedPreferences exposureNotificationSharedPreferences,
      ExposureInformationHelper exposureInformationHelper,
      ExposureCheckRepository exposureCheckRepository,
      NotificationHelper notificationHelper,
      Clock clock,
      WorkManager workManager,
      @BackgroundExecutor ExecutorService backgroundExecutor) {
    this.exposureNotificationSharedPreferences = exposureNotificationSharedPreferences;
    this.exposureInformationHelper = exposureInformationHelper;
    this.notificationHelper = notificationHelper;
    getExposureChecksLiveData =
        exposureCheckRepository.getLastXExposureChecksLiveData(NUM_CHECKS_TO_DISPLAY);
    this.clock = clock;
    this.workManager = workManager;
    this.backgroundExecutor = backgroundExecutor;
  }

  public LiveData<ExposureClassification> getExposureClassificationLiveData() {
    return exposureNotificationSharedPreferences.getExposureClassificationLiveData();
  }

  public ExposureClassification getExposureClassification() {
    return exposureNotificationSharedPreferences.getExposureClassification();
  }

  public Boolean getIsExposureClassificationRevoked() {
    return exposureNotificationSharedPreferences.getIsExposureClassificationRevoked();
  }

  public LiveData<BadgeStatus> getIsExposureClassificationNewLiveData() {
    return exposureNotificationSharedPreferences.getIsExposureClassificationNewLiveData();
  }

  public void tryTransitionExposureClassificationNew(BadgeStatus from, BadgeStatus to) {
    if (exposureNotificationSharedPreferences.getIsExposureClassificationNew()
      == from) {
      exposureNotificationSharedPreferences.setIsExposureClassificationNewAsync(to);
    }
  }

  public LiveData<BadgeStatus> getIsExposureClassificationDateNewLiveData() {
    return exposureNotificationSharedPreferences.getIsExposureClassificationDateNewLiveData();
  }

  public void tryTransitionExposureClassificationDateNew(BadgeStatus from, BadgeStatus to) {
    if (exposureNotificationSharedPreferences.getIsExposureClassificationDateNew()
        == from) {
      exposureNotificationSharedPreferences.setIsExposureClassificationDateNewAsync(to);
    }
  }

  /**
   * A {@link LiveData} to track the list of exposure checks to display.
   */
  public LiveData<List<ExposureCheckEntity>> getExposureChecksLiveData() {
    return getExposureChecksLiveData;
  }

  /**
   * Cancels any pending job to notify users to reactivate exposure notification and dismisses
   * restore notification if currently showing.
   */
  public ListenableFuture<Void> dismissReactivateENAppNotificationAndPendingJob(Context context) {

    return FluentFuture.from(
        RestoreNotificationWorker.cancelRestoreNotificationWorkIfExisting(workManager).getResult())
        .transformAsync(unused -> {
          notificationHelper.dismissReActivateENNotificationIfShowing(context);
          return Futures.immediateVoidFuture();
        }, backgroundExecutor);
  }

  public String getExposureDateRangeString(ExposureClassification exposureClassification,
      Context context, ZoneId timezone) {
    return StringUtils.exposureDateRange(exposureClassification, context, timezone);
  }

  public boolean isActiveExposurePresent() {
    return exposureInformationHelper.isActiveExposurePresent();
  }

}
