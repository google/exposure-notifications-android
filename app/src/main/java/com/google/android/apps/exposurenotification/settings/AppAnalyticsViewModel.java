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

package com.google.android.apps.exposurenotification.settings;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import dagger.hilt.android.lifecycle.HiltViewModel;
import javax.inject.Inject;

/**
 * View model for {@link AppAnalyticsFragment}.
 */
@HiltViewModel
public class AppAnalyticsViewModel extends ViewModel {

  private final ExposureNotificationSharedPreferences exposureNotificationSharedPreferences;

  @Inject
  public AppAnalyticsViewModel(
      ExposureNotificationSharedPreferences exposureNotificationSharedPreferences) {
    this.exposureNotificationSharedPreferences = exposureNotificationSharedPreferences;
  }

  public void setAppAnalyticsState(boolean isEnabled) {
    exposureNotificationSharedPreferences.setAppAnalyticsState(isEnabled);
  }

  public LiveData<Boolean> getAppAnalyticsLiveData() {
    return exposureNotificationSharedPreferences.getAppAnalyticsStateLiveData();
  }

}
