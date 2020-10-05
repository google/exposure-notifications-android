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

import androidx.lifecycle.LiveData;
import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.common.base.Function;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;
import javax.inject.Inject;

/**
 * Abstracts database access to {@link DiagnosisDao} data source.
 */
public class DiagnosisRepository {

  private final DiagnosisDao diagnosisDao;
  private final Clock clock;

  @Inject
  DiagnosisRepository(
      ExposureNotificationDatabase exposureNotificationDatabase,
      Clock clock) {
    diagnosisDao = exposureNotificationDatabase.diagnosisDao();
    this.clock = clock;
  }

  public LiveData<List<DiagnosisEntity>> getAllLiveData() {
    return diagnosisDao.getAllLiveData();
  }

  public ListenableFuture<DiagnosisEntity> getById(long id) {
    return diagnosisDao.getByIdAsync(id);
  }

  public ListenableFuture<List<DiagnosisEntity>> getByVerificationCodeAsync(
      String verificationCode) {
    return diagnosisDao.getByVerificationCodeAsync(verificationCode);
  }

  public ListenableFuture<String> getMostRecentRevisionTokenAsync() {
    return diagnosisDao.getMostRecentRevisionTokenAsync();
  }

  public LiveData<DiagnosisEntity> getByIdLiveData(long id) {
    // TODO: cache this locally.
    return diagnosisDao.getByIdLiveData(id);
  }

  public ListenableFuture<Long> upsertAsync(DiagnosisEntity entity) {
    if (entity.getCreatedTimestampMs() < 1) {
      entity = entity.toBuilder().setCreatedTimestampMs(clock.now().toEpochMilli()).build();
    }
    return diagnosisDao.upsertAsync(entity);
  }

  public ListenableFuture<Void> deleteByIdAsync(long id) {
    return diagnosisDao.deleteById(id);
  }

  public Long createOrMutateById(long id, Function<DiagnosisEntity, DiagnosisEntity> mutator) {
    return diagnosisDao.createOrMutateById(id, mutator);
  }
}

