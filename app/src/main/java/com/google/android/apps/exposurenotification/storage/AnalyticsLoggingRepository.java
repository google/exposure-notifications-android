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

import androidx.annotation.WorkerThread;
import com.google.android.apps.exposurenotification.proto.EnxLogExtension;
import com.google.common.io.BaseEncoding;
import java.util.List;
import javax.inject.Inject;

/**
 * Abstracts database access to {@link AnalyticsLoggingDao} data source.
 */
public class AnalyticsLoggingRepository {

  private final AnalyticsLoggingDao loggingDao;

  @Inject
  AnalyticsLoggingRepository(ExposureNotificationDatabase exposureNotificationDatabase) {
    this.loggingDao = exposureNotificationDatabase.analyticsLoggingDao();
  }

  @WorkerThread
  public void eraseEventsBatch() {
      loggingDao.deleteLogEvents();
  }

  @WorkerThread
  public void eraseEventsBatchUpToIncludingEvent(AnalyticsLoggingEntity analyticsLoggingEntity) {
    loggingDao.deleteLogEventsUpToIncludingEvent(analyticsLoggingEntity.getKey());
  }

  @WorkerThread
  public void recordEvent(EnxLogExtension logProto) {
    loggingDao.insert(
        AnalyticsLoggingEntity.create(0, BaseEncoding.base64().encode(logProto.toByteArray())));
  }

  @WorkerThread
  public List<AnalyticsLoggingEntity> getEventsBatch() {
    return loggingDao.getAllLogEvents();
  }
}
