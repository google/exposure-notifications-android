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

import android.util.Log;
import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import com.google.android.gms.nearby.exposurenotification.ExposureWindow;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;

/**
 * Dao for the bucket {@link ExposureEntity} in the exposure notification database.
 */
@Dao
abstract class ExposureDao {

  private static final String TAG = "ExposureDao";

  @Query("SELECT * FROM ExposureEntity")
  abstract List<ExposureEntity> getAll();

  @Query("SELECT * FROM ExposureEntity ORDER BY date_millis_since_epoch DESC")
  abstract ListenableFuture<List<ExposureEntity>> getAllAsync();

  @Query("SELECT * FROM ExposureEntity ORDER BY date_millis_since_epoch DESC")
  abstract LiveData<List<ExposureEntity>> getAllLiveData();

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract ListenableFuture<Void> upsertAsync(List<ExposureEntity> entities);

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract void upsert(ExposureEntity entity);

  @Query("DELETE FROM ExposureEntity")
  abstract ListenableFuture<Void> deleteAllAsync();

  /**
   * Adds missing exposures based on the current windows state.
   *
   * @param exposureWindows the {@link ExposureWindow}s
   * @return if any exposure was added
   */
  @Transaction
  public boolean refreshWithExposureWindows(List<ExposureWindow> exposureWindows) {
    // Keep track of the exposures already handled and remove them when we find matching windows.
    List<ExposureEntity> exposureEntities = getAll();
    boolean somethingAdded = false;
    for (ExposureWindow exposureWindow : exposureWindows) {
      boolean found = false;
      for (int i = 0; i < exposureEntities.size(); i++) {
        if (exposureEntities.get(i).getDateMillisSinceEpoch() == exposureWindow
            .getDateMillisSinceEpoch()) {
          exposureEntities.remove(i);
          found = true;
          break;
        }
      }
      if (!found) {
        // No existing ExposureEntity with the given date, must add an entity for this window.
        somethingAdded = true;
        upsert(ExposureEntity
            .create(exposureWindow.getDateMillisSinceEpoch(), System.currentTimeMillis()));
      }
    }
    return somethingAdded;
  }

}
