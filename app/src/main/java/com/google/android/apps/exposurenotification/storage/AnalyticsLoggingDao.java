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
import androidx.room.Query;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;

@Dao
abstract class AnalyticsLoggingDao {

  @Query("SELECT * FROM AnalyticsLoggingEntity")
  abstract List<AnalyticsLoggingEntity> getAllLogEvents();

  @Query("DELETE FROM AnalyticsLoggingEntity")
  abstract ListenableFuture<Void> deleteLogEvents();

  @Query("DELETE FROM AnalyticsLoggingEntity WHERE key <= :analyticsLoggingEntityKey")
  abstract ListenableFuture<Void> deleteLogEventsUpToIncludingEvent(long analyticsLoggingEntityKey);

  @Insert()
  abstract void insert(AnalyticsLoggingEntity entity);
}
