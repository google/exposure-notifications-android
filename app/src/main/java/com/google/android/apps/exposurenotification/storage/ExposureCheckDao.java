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
import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;
import org.threeten.bp.Instant;

/**
 * Dao for the bucket {@link ExposureCheckEntity} in the exposure notification database.
 */
@Dao
public abstract class ExposureCheckDao {

  @WorkerThread
  @Query("SELECT * FROM ExposureCheckEntity")
  abstract List<ExposureCheckEntity> getAll();

  @WorkerThread
  @Query("SELECT * FROM ExposureCheckEntity ORDER BY checkTime DESC LIMIT :numOfChecksToRetrieve")
  abstract LiveData<List<ExposureCheckEntity>> getLastXChecksLiveData(int numOfChecksToRetrieve);

  @WorkerThread
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract void insert(ExposureCheckEntity entity);

  @WorkerThread
  @Query("DELETE FROM ExposureCheckEntity WHERE checkTime < :earliestThreshold")
  abstract void deleteOlderThanThreshold(Instant earliestThreshold);

  @AnyThread
  @Query("DELETE FROM ExposureCheckEntity")
  abstract ListenableFuture<Void> deleteAll();

}
