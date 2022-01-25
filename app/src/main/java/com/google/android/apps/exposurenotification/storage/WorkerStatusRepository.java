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
import com.google.android.apps.exposurenotification.proto.WorkManagerTask.WorkerTask;
import com.google.common.base.Optional;
import com.google.common.util.concurrent.ListenableFuture;
import javax.inject.Inject;
import org.threeten.bp.Instant;

/**
 * Abstracts database access to {@link WorkerStatusDao} data source.
 */
public class WorkerStatusRepository {

  private final WorkerStatusDao workerStatusDao;

  @Inject
  WorkerStatusRepository(ExposureNotificationDatabase exposureNotificationDatabase) {
    workerStatusDao = exposureNotificationDatabase.workerStatusDao();
  }

  @WorkerThread
  public Optional<Instant> getLastRunTimestamp(WorkerTask workerTask,
      String status) {
    Long lastRunTimestamp = workerStatusDao
        .getLastRunTimestampMillis(workerTask.name() + ":" + status);
    return lastRunTimestamp != null
        ? Optional.of(Instant.ofEpochMilli(lastRunTimestamp))
        : Optional.absent();
  }

  @WorkerThread
  public void upsert(WorkerTask workerTask, String status, Instant lastRunTimestamp) {
    workerStatusDao.upsert(WorkerStatusEntity
        .create(workerTask.name() + ":" + status, lastRunTimestamp.toEpochMilli()));
  }

  @AnyThread
  public ListenableFuture<Void> deleteWorkerStatusEntities() {
    return workerStatusDao.deleteAll();
  }

}