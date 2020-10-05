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

import com.google.android.apps.exposurenotification.common.Qualifiers.BackgroundExecutor;
import com.google.android.apps.exposurenotification.proto.EnxLogExtension;
import com.google.common.io.BaseEncoding;
import java.util.List;
import java.util.concurrent.ExecutorService;
import javax.inject.Inject;

/**
 * Abstracts database access to {@link AnalyticsLoggingDao} data source.
 */
public class AnalyticsLoggingRepository {

  private final AnalyticsLoggingDao loggingDao;
  private final ExecutorService backgroundExecutor;

  @Inject
  AnalyticsLoggingRepository(ExposureNotificationDatabase exposureNotificationDatabase,
      @BackgroundExecutor ExecutorService backgroundExecutor) {
    this.loggingDao = exposureNotificationDatabase.analyticsLoggingDao();
    this.backgroundExecutor = backgroundExecutor;
  }

  public List<AnalyticsLoggingEntity> getAndEraseEventsBatch() {
    synchronized (AnalyticsLoggingRepository.class) {
      List<AnalyticsLoggingEntity> batch = loggingDao.getAllLogEvents();
      loggingDao.deleteLogEvents();
      return batch;
    }
  }

  public void eraseEventsBatch() {
      loggingDao.deleteLogEvents();
  }

  public void recordEvent(EnxLogExtension logProto) {
    backgroundExecutor.execute(() -> {
      loggingDao.insert(
          AnalyticsLoggingEntity.create(0, BaseEncoding.base64().encode(logProto.toByteArray())));
    });
  }
}
