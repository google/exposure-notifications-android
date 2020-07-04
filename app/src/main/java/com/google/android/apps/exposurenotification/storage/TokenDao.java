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
import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;

/**
 * Dao for the bucket {@link TokenDao} in the exposure notification database.
 */
@Dao
abstract class TokenDao {

  @Query("SELECT * FROM TokenEntity")
  abstract ListenableFuture<List<TokenEntity>> getAllAsync();

  @Query("SELECT * FROM TokenEntity ORDER BY created_timestamp_ms DESC")
  abstract LiveData<List<TokenEntity>> getAllLiveData();

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract ListenableFuture<Void> upsertAsync(TokenEntity entity);

  @Query("UPDATE TokenEntity SET responded = 1, last_updated_timestamp_ms = :timestampMs WHERE token = :token")
  abstract ListenableFuture<Void> markTokenRespondedAsync(String token, long timestampMs);

}
