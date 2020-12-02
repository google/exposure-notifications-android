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

package com.google.android.apps.exposurenotification.onboarding;

import androidx.hilt.lifecycle.ViewModelInject;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import com.google.android.apps.exposurenotification.privateanalytics.PrivateAnalyticsSettingsUtil;
import com.google.android.apps.exposurenotification.privateanalytics.SubmitPrivateAnalyticsWorker;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;

/**
 * View model for onboarding related actions.
 */
public class OnboardingViewModel extends ViewModel {

  private final ExposureNotificationSharedPreferences exposureNotificationSharedPreferences;
  private final WorkManager workManager;

  @ViewModelInject
  public OnboardingViewModel(
      ExposureNotificationSharedPreferences exposureNotificationSharedPreferences,
      WorkManager workManager) {
    this.exposureNotificationSharedPreferences = exposureNotificationSharedPreferences;
    this.workManager = workManager;
  }

  public void setOnboardedState(boolean onboarded) {
    exposureNotificationSharedPreferences.setOnboardedState(onboarded);
  }

  public void setAppAnalyticsState(boolean isEnabled) {
    exposureNotificationSharedPreferences.setAppAnalyticsState(isEnabled);
  }

  public LiveData<Boolean> isPrivateAnalyticsStateSetLiveData() {
    return exposureNotificationSharedPreferences.isPrivateAnalyticsStateSetLiveData();
  }

  public LiveData<Boolean> shouldShowPrivateAnalyticsOnboardingLiveData() {
    return Transformations.map(isPrivateAnalyticsStateSetLiveData(),
        (isPrivateAnalyticsStateSet) -> !isPrivateAnalyticsStateSet && PrivateAnalyticsSettingsUtil
            .isPrivateAnalyticsSupported());
  }

  public void setPrivateAnalyticsState(boolean isEnabled) {
    exposureNotificationSharedPreferences.setPrivateAnalyticsState(isEnabled);
  }

  /**
   * Triggers a one off submit private analytics job.
   */
  public void submitPrivateAnalytics() {
    workManager.enqueue(new OneTimeWorkRequest.Builder(SubmitPrivateAnalyticsWorker.class).build());
  }
}
