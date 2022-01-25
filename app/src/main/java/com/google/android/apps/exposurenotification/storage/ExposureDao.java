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
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;

/**
 * Dao for the bucket {@link ExposureEntity} in the exposure notification database.
 */
@Dao
abstract class ExposureDao {

  @WorkerThread
  @Query("SELECT * FROM ExposureEntity")
  abstract List<ExposureEntity> getAll();

  @WorkerThread
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract void upsertAll(List<ExposureEntity> entities);

  @WorkerThread
  @Query("DELETE FROM ExposureEntity")
  abstract void deleteAll();

  @AnyThread
  @Query("DELETE FROM ExposureEntity")
  abstract ListenableFuture<Void> deleteAllAsync();

  /**
   * Wipe the ExposureEntity table and insert the ExposureEntites in this list.
   *
   * @param exposureEntities the computed from DailySummaries {@link ExposureEntity}s
   */
  @WorkerThread
  @Transaction
  public void deleteInsertExposureEntities(List<ExposureEntity> exposureEntities) {
    deleteAll();
    upsertAll(exposureEntities);
  }

}
