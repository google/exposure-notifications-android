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

package com.google.android.apps.exposurenotification.storage;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;
import org.threeten.bp.Instant;
import org.threeten.bp.ZonedDateTime;

/**
 * View model for the {@link PositiveDiagnosisEntity} table.
 */
public class PositiveDiagnosisViewModel extends AndroidViewModel {

  private final MutableLiveData<ZonedDateTime> testTimestamp = new MutableLiveData<>();

  private final ExposureNotificationRepository exposureNotificationRepository;

  public PositiveDiagnosisViewModel(Application application) {
    super(application);
    exposureNotificationRepository = new ExposureNotificationRepository(application);
  }

  @NonNull
  public LiveData<ZonedDateTime> getTestTimestamp() {
    return Transformations.distinctUntilChanged(testTimestamp);
  }

  public void onTestTimestampChanged(@NonNull ZonedDateTime timestamp) {
    if (Instant.now().isAfter(timestamp.toInstant())) {
      // If the value given is in the past we can use the value
      testTimestamp.setValue(timestamp);
    }
  }

  @NonNull
  public LiveData<List<PositiveDiagnosisEntity>> getAllLiveData() {
    return exposureNotificationRepository.getAllPositiveDiagnosisEntitiesLiveData();
  }

  @NonNull
  public ListenableFuture<Void> upsertPositiveDiagnosisEntityAsync() {
    return exposureNotificationRepository.upsertPositiveDiagnosisEntityAsync(
        PositiveDiagnosisEntity.create(testTimestamp.getValue())
    );
  }
}
