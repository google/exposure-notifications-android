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
import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;
import javax.inject.Inject;

/**
 * Abstracts database access to {@link ExposureDao} data source.
 */
public class ExposureRepository {

  private final ExposureDao exposureDao;

  @Inject
  ExposureRepository(ExposureNotificationDatabase exposureNotificationDatabase) {
    exposureDao = exposureNotificationDatabase.exposureDao();
  }

  /**
   * Query all ExposureEntities from the previous run
   * @return {@link ExposureEntity}s from the previous run
   */
  @WorkerThread
  public List<ExposureEntity> getAllExposureEntities() {
    return exposureDao.getAll();
  }

  /**
   * Wipe the ExposureEntity table and insert the ExposureEntites in this list
   * @param exposureEntities the computed from DailySummaries {@link ExposureEntity}s
   */
  @WorkerThread
  public void deleteInsertExposureEntities(List<ExposureEntity> exposureEntities) {
    exposureDao.deleteInsertExposureEntities(exposureEntities);
  }

  /**
   * Wipe the ExposureEntity table.
   */
  @AnyThread
  public ListenableFuture<Void> deleteExposureEntitiesAsync() {
    return exposureDao.deleteAllAsync();
  }

}
