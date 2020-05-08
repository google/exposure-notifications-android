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

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;

/**
 * Dao for the bucket {@link TokenDao} in the exposure notification database.
 */
@Dao
public abstract class TokenDao {

  @Query("SELECT * FROM TokenEntity")
  abstract ListenableFuture<List<TokenEntity>> getAllAsync();

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract ListenableFuture<Void> upsertAsync(TokenEntity entity);

  @Query("UPDATE TokenEntity SET responded=1 WHERE token = :token")
  abstract ListenableFuture<Void> markTokenRespondedAsync(String token);

  @Query("DELETE FROM TokenEntity WHERE token = :token")
  abstract ListenableFuture<Void> deleteByTokenAsync(String token);

  @Query("DELETE FROM TokenEntity WHERE token IN (:tokens)")
  abstract ListenableFuture<Void> deleteByTokensAsync(List<String> tokens);

}
