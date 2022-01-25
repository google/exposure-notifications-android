/*
 * Copyright 2021 Google LLC
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
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;
import org.threeten.bp.Instant;

/**
 * Dao for the bucket {@link VerificationCodeRequestEntity} in the exposure notification database.
 */
@Dao
public abstract class VerificationCodeRequestDao {

  @WorkerThread
  @Query("SELECT * FROM VerificationCodeRequestEntity")
  abstract List<VerificationCodeRequestEntity> getAll();

  @WorkerThread
  @Query("SELECT nonce FROM VerificationCodeRequestEntity WHERE nonce <> ''")
  abstract List<String> getAllNonces();

  @AnyThread
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract ListenableFuture<Long> upsert(VerificationCodeRequestEntity entity);

  @WorkerThread
  @Query("UPDATE VerificationCodeRequestEntity SET expiresAtTime = :expiresAtTime WHERE id = :id")
  abstract void setExpiresAtTime(long id, Instant expiresAtTime);

  @WorkerThread
  @Query("DELETE FROM VerificationCodeRequestEntity WHERE requestTime < :earliestThreshold")
  abstract void deleteOlderThanThreshold(Instant earliestThreshold);

  @WorkerThread
  @Query("UPDATE VerificationCodeRequestEntity SET nonce = '' WHERE expiresAtTime <= :currentTime")
  abstract void resetNonceForExpiredRequests(Instant currentTime);

  @AnyThread
  @Query("SELECT nonce FROM VerificationCodeRequestEntity WHERE expiresAtTime > :currentTime"
      + " ORDER BY expiresAtTime DESC")
  abstract ListenableFuture<List<String>> getAllValidNoncesWithLatestExpiringFirstAsync(
      Instant currentTime);

  @AnyThread
  @Query("SELECT * FROM VerificationCodeRequestEntity WHERE requestTime >= :earliestThreshold"
      + " ORDER BY requestTime DESC LIMIT :numOfRequestsToRetrieve")
  abstract ListenableFuture<List<VerificationCodeRequestEntity>> getLastXRequestsNotOlderThanThresholdAsync(
      Instant earliestThreshold, int numOfRequestsToRetrieve);

  @AnyThread
  @Query("DELETE FROM VerificationCodeRequestEntity")
  abstract ListenableFuture<Void> deleteAll();

}
