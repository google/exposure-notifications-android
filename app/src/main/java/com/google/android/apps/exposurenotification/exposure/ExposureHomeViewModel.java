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

import androidx.hilt.lifecycle.ViewModelInject;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;
import com.google.android.apps.exposurenotification.riskcalculation.ExposureClassification;
import com.google.android.apps.exposurenotification.storage.ExposureCheckEntity;
import com.google.android.apps.exposurenotification.storage.ExposureCheckRepository;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences.BadgeStatus;
import java.util.List;

/**
 * View model for the {@link ExposureHomeFragment}.
 */
public class ExposureHomeViewModel extends ViewModel {

  // Number of exposure checks we want to display.
  private static final int NUM_CHECKS_TO_DISPLAY = 5;

  private final ExposureNotificationSharedPreferences exposureNotificationSharedPreferences;
  private final LiveData<List<ExposureCheckEntity>> getExposureChecksLiveData;
  private final ExposureCheckRepository exposureCheckRepository;

  @ViewModelInject
  public ExposureHomeViewModel(
      ExposureNotificationSharedPreferences exposureNotificationSharedPreferences,
      ExposureCheckRepository exposureCheckRepository) {
    this.exposureNotificationSharedPreferences = exposureNotificationSharedPreferences;
    this.exposureCheckRepository = exposureCheckRepository;
    getExposureChecksLiveData =
        exposureCheckRepository.getLastXExposureChecksLiveData(NUM_CHECKS_TO_DISPLAY);
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

}
