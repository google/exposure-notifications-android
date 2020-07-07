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

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import com.google.android.apps.exposurenotification.storage.PositiveDiagnosisEntity;
import com.google.android.apps.exposurenotification.storage.PositiveDiagnosisRepository;
import java.util.List;

/** View model for the {@link NotifyHomeFragment}. */
public class NotifyHomeViewModel extends AndroidViewModel {

  private final PositiveDiagnosisRepository positiveDiagnosisRepository;

  private final LiveData<List<PositiveDiagnosisEntity>> getAllPositiveDiagnosisLiveData;

  public NotifyHomeViewModel(@NonNull Application application) {
    super(application);
    positiveDiagnosisRepository = new PositiveDiagnosisRepository(application);
    getAllPositiveDiagnosisLiveData = positiveDiagnosisRepository.getAllLiveData();
  }

  public LiveData<List<PositiveDiagnosisEntity>> getAllPositiveDiagnosisEntityLiveData() {
    return getAllPositiveDiagnosisLiveData;
  }

}
