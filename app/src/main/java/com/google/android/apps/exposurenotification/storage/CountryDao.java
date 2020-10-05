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
import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;
import org.threeten.bp.Instant;

@Dao
abstract class CountryDao {

  @Query("SELECT countryCode FROM CountryEntity WHERE lastSeenTimestampMillis >= :earliestTimestampMillis")
  abstract List<String> getRecentlySeenCountryCodes(long earliestTimestampMillis);

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract long upsert(CountryEntity entity);

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract ListenableFuture<Void> upsertAsync(CountryEntity entity);

  @Query("DELETE FROM CountryEntity WHERE lastSeenTimestampMillis < :earliestTimestampMillis")
  abstract void deleteObsoleteCountryCodes(long earliestTimestampMillis);

  @Query("DELETE FROM CountryEntity WHERE lastSeenTimestampMillis < :earliestTimestampMillis")
  abstract ListenableFuture<Void> deleteObsoleteCountryCodesAsync(long earliestTimestampMillis);

  public void markCountryCodeSeen(String countryCode, long whenSeenMillis) {
    upsert(CountryEntity.create(countryCode, whenSeenMillis));
  }

  public ListenableFuture<Void> markCountryCodeSeenAsync(String countryCode, long whenSeenMillis) {
    return upsertAsync(CountryEntity.create(countryCode, whenSeenMillis));
  }
}
