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
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import com.google.common.base.Function;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;

/**
 * Dao for the {@link DiagnosisEntity} table in the exposure notification database.
 */
@Dao
abstract class DiagnosisDao {

  @Query("SELECT * FROM DiagnosisEntity")
  abstract List<DiagnosisEntity> getAll();

  @Query("SELECT * FROM DiagnosisEntity WHERE id = :id")
  abstract DiagnosisEntity getById(long id);

  @Query("SELECT * FROM DiagnosisEntity WHERE verificationCode = :verificationCode")
  abstract ListenableFuture<List<DiagnosisEntity>> getByVerificationCodeAsync(
      String verificationCode);

  @Query("SELECT * FROM DiagnosisEntity WHERE id = :id")
  abstract ListenableFuture<DiagnosisEntity> getByIdAsync(long id);

  @Query("SELECT * FROM DiagnosisEntity WHERE id = :id")
  abstract LiveData<DiagnosisEntity> getByIdLiveData(long id);

  @Query("SELECT * FROM DiagnosisEntity ORDER BY id DESC")
  abstract LiveData<List<DiagnosisEntity>> getAllLiveData();

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract ListenableFuture<Long> upsertAsync(DiagnosisEntity entity);

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract long upsert(DiagnosisEntity entity);

  @Query("DELETE FROM DiagnosisEntity WHERE id = :id")
  abstract ListenableFuture<Void> deleteById(long id);

  @Query(
      "SELECT revisionToken FROM DiagnosisEntity WHERE revisionToken IS NOT NULL ORDER BY createdTimestampMs DESC LIMIT 1")
  abstract ListenableFuture<String> getMostRecentRevisionTokenAsync();

  @Transaction
  public Long createOrMutateById(
      long id, Function<DiagnosisEntity, DiagnosisEntity> mutator) {
    // If the given ID does not exist in the DB, create a new record.
    DiagnosisEntity diagnosis = getById(id);
    diagnosis = diagnosis == null ? DiagnosisEntity.newBuilder().build() : diagnosis;
    // Apply the mutation and write to storage.
    return upsert(mutator.apply(diagnosis));
  }

}
