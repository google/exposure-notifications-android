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
import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;

/**
 * View model for the {@link ExposureEntity} table.
 */
public class ExposureViewModel extends AndroidViewModel {

  private final ExposureNotificationRepository repository;

  public ExposureViewModel(@NonNull Application application) {
    super(application);
    repository = new ExposureNotificationRepository(application);
  }

  public LiveData<List<ExposureEntity>> getAllExposureEntityLiveData() {
    return repository.getAllExposureEntityLiveData();
  }

  public ListenableFuture<Void> upsertExposureEntitiesAsync(List<ExposureEntity> entities) {
    return repository.upsertExposureEntitiesAsync(entities);
  }

  public ListenableFuture<Void> deleteAllExposureEntitiesAsync() {
    return repository.deleteAllExposureEntitiesAsync();
  }

}