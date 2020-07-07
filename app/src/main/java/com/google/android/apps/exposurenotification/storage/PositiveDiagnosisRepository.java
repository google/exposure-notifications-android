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

import android.content.Context;
import androidx.lifecycle.LiveData;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;

/**
 * Abstracts database access to {@link PositiveDiagnosisDao} data source.
 */
public class PositiveDiagnosisRepository {

  private final PositiveDiagnosisDao positiveDiagnosisDao;
  private final LiveData<List<PositiveDiagnosisEntity>> getAllLiveData;

  public PositiveDiagnosisRepository(Context context) {
    ExposureNotificationDatabase exposureNotificationDatabase =
        ExposureNotificationDatabase.getInstance(context);
    positiveDiagnosisDao = exposureNotificationDatabase.positiveDiagnosisDao();
    getAllLiveData = positiveDiagnosisDao.getAllLiveData();
  }

  public LiveData<List<PositiveDiagnosisEntity>> getAllLiveData() {
    return getAllLiveData;
  }

  public LiveData<PositiveDiagnosisEntity> getByIdLiveData(long id) {
    // TODO: cache this locally.
    return positiveDiagnosisDao.getById(id);
  }

  public ListenableFuture<Void> upsertAsync(
      PositiveDiagnosisEntity entity) {
    return positiveDiagnosisDao.upsertAsync(entity);
  }

  public ListenableFuture<Void> deleteByIdAsync(long id) {
    return positiveDiagnosisDao.deleteById(id);
  }

  public ListenableFuture<Void> markSharedForIdAsync(long id,
      boolean shared) {
    return positiveDiagnosisDao.markSharedForId(id, shared);
  }

}
