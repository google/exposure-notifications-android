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

package com.google.android.apps.exposurenotification.notify;

import androidx.hilt.lifecycle.ViewModelInject;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity;
import com.google.android.apps.exposurenotification.storage.DiagnosisRepository;
import java.util.List;

/**
 * View model for the {@link NotifyHomeFragment}.
 */
public class NotifyHomeViewModel extends ViewModel {

  private final LiveData<List<DiagnosisEntity>> getAllDiagnosisLiveData;
  private final ExposureNotificationSharedPreferences exposureNotificationSharedPreferences;

  @ViewModelInject
  public NotifyHomeViewModel(
      DiagnosisRepository diagnosisRepository,
      ExposureNotificationSharedPreferences exposureNotificationSharedPreferences) {
    getAllDiagnosisLiveData = diagnosisRepository.getAllLiveData();
    this.exposureNotificationSharedPreferences = exposureNotificationSharedPreferences;
  }

  public LiveData<List<DiagnosisEntity>> getAllDiagnosisEntityLiveData() {
    return getAllDiagnosisLiveData;
  }
}
