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
import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;
import javax.inject.Inject;
import org.threeten.bp.Instant;

/**
 * Abstracts database access to {@link CountryDao} data source.
 */
public class CountryRepository {

  private final CountryDao countryDao;
  private final Clock clock;

  @Inject
  CountryRepository(ExposureNotificationDatabase exposureNotificationDatabase, Clock clock) {
    countryDao = exposureNotificationDatabase.countryDao();
    this.clock = clock;
  }

  @WorkerThread
  public List<String> getRecentlySeenCountryCodes(Instant earliestTimestamp) {
    return countryDao.getRecentlySeenCountryCodes(earliestTimestamp.toEpochMilli());
  }

  @WorkerThread
  public void deleteObsoleteCountryCodes(Instant earliestTimestamp) {
    countryDao.deleteObsoleteCountryCodes(earliestTimestamp.toEpochMilli());
  }

  @AnyThread
  public ListenableFuture<Void> deleteObsoleteCountryCodesAsync(Instant earliestTimestamp) {
    return countryDao.deleteObsoleteCountryCodesAsync(earliestTimestamp.toEpochMilli());
  }

  @AnyThread
  public ListenableFuture<Void> deleteCountryEntitiesAsync() {
    return countryDao.deleteAll();
  }

  @WorkerThread
  public void markCountrySeen(String countryCode) {
    countryDao.markCountryCodeSeen(countryCode, clock.currentTimeMillis());
  }

  @AnyThread
  public ListenableFuture<Void> markCountrySeenAsync(String countryCode) {
    return countryDao.markCountryCodeSeenAsync(countryCode, clock.currentTimeMillis());
  }
}
