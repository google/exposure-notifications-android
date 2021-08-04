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

import androidx.annotation.AnyThread;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;
import com.google.android.apps.exposurenotification.common.Qualifiers.BackgroundExecutor;
import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity.Shared;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity.TestResult;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;
import java.util.concurrent.ExecutorService;
import javax.inject.Inject;

/**
 * Abstracts database access to {@link DiagnosisEntity} data and TEK revision tokens.
 */
public class DiagnosisRepository {

  private final static List<Shared> NOT_SHARED_STATUSES = ImmutableList.of(
      Shared.NOT_SHARED, Shared.NOT_ATTEMPTED);
  private final static List<Shared> SHARED_STATUSES = ImmutableList.of(Shared.SHARED);
  private final static List<TestResult> POSITIVE_TEST_RESULTS = ImmutableList.of(
      TestResult.CONFIRMED, TestResult.USER_REPORT);

  private final DiagnosisDao diagnosisDao;
  private final ExecutorService backgroundExecutor;
  private final Clock clock;

  @Inject
  DiagnosisRepository(
      ExposureNotificationDatabase exposureNotificationDatabase,
      @BackgroundExecutor ExecutorService backgroundExecutor,
      Clock clock) {
    diagnosisDao = exposureNotificationDatabase.diagnosisDao();
    this.backgroundExecutor = backgroundExecutor;
    this.clock = clock;
  }

  @AnyThread
  public LiveData<List<DiagnosisEntity>> getAllLiveData() {
    return diagnosisDao.getAllLiveData();
  }

  @AnyThread
  public ListenableFuture<DiagnosisEntity> getByIdAsync(long id) {
    return diagnosisDao.getByIdAsync(id);
  }

  @AnyThread
  public ListenableFuture<DiagnosisEntity> maybeGetLastNotSharedDiagnosisAsync() {
    return diagnosisDao.maybeGetLastDiagnosisWithSharedStatusInStatusesAsync(NOT_SHARED_STATUSES);
  }

  @AnyThread
  public LiveData<Optional<DiagnosisEntity>> getPositiveDiagnosisSharedAfterThresholdLiveData(
      long minTimestampMs) {
    return diagnosisDao.getDiagnosisWithTestResultAndStatusLastUpdatedAfterThresholdLiveData(
        POSITIVE_TEST_RESULTS, SHARED_STATUSES, minTimestampMs);
  }

  @AnyThread
  public ListenableFuture<List<DiagnosisEntity>> getByVerificationCodeAsync(
      String verificationCode) {
    return diagnosisDao.getByVerificationCodeAsync(verificationCode);
  }

  @AnyThread
  public ListenableFuture<String> getMostRecentRevisionTokenAsync() {
    return diagnosisDao.getMostRecentRevisionTokenAsync();
  }

  @AnyThread
  public LiveData<DiagnosisEntity> getByIdLiveData(long id) {
    // TODO: cache this locally.
    return diagnosisDao.getByIdLiveData(id);
  }

  @AnyThread
  public ListenableFuture<Long> upsertAsync(DiagnosisEntity entity) {
    return Futures.submit(
        () -> diagnosisDao.upsert(updateLastUpdatedTimestampMs(maybeSetCreationTime(entity))),
        backgroundExecutor);
  }

  @AnyThread
  public ListenableFuture<Void> deleteByIdAsync(long id) {
    return diagnosisDao.deleteById(id);
  }

  @WorkerThread
  public Long createOrMutateById(long id, Function<DiagnosisEntity, DiagnosisEntity> mutator) {
    Function<DiagnosisEntity, DiagnosisEntity> mutateAndMaybeSetCreationTime =
        entity -> updateLastUpdatedTimestampMs(maybeSetCreationTime(mutator.apply(entity)));
    return diagnosisDao.createOrMutateById(id, mutateAndMaybeSetCreationTime);
  }

  private DiagnosisEntity maybeSetCreationTime(DiagnosisEntity entity) {
    if (entity.getCreatedTimestampMs() < 1) {
      entity = entity.toBuilder().setCreatedTimestampMs(clock.now().toEpochMilli()).build();
    }
    return entity;
  }

  private DiagnosisEntity updateLastUpdatedTimestampMs(DiagnosisEntity entity) {
      return entity.toBuilder().setLastUpdatedTimestampMs(clock.now().toEpochMilli()).build();
  }

}

