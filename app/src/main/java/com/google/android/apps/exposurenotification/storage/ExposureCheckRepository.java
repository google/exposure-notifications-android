/*
 * Copyright 2021 Google LLC
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

import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;
import java.util.List;
import javax.inject.Inject;
import org.threeten.bp.Instant;

/**
 * Abstracts database access to {@link ExposureCheckDao} data source.
 */
public class ExposureCheckRepository {

  private final ExposureCheckDao exposureCheckDao;

  @Inject
  ExposureCheckRepository(ExposureNotificationDatabase exposureNotificationDatabase) {
    exposureCheckDao = exposureNotificationDatabase.exposureCheckDao();
  }

  /**
   * Store a given ExposureCheckEntity in the database to capture the timestamp of
   * an exposure check.
   */
  @WorkerThread
  public void insertExposureCheck(ExposureCheckEntity entity) {
    exposureCheckDao.insert(entity);
  }

  /**
   * Delete obsolete ExposureCheckEntities (i.e. those captured earlier than a given
   * threshold, if any).
   * @param earliestThreshold delete all the {@link ExposureCheckEntity}s captured earlier than this
   *                          threshold value
   */
  @WorkerThread
  public void deleteObsoleteChecksIfAny(Instant earliestThreshold) {
    exposureCheckDao.deleteOlderThanThreshold(earliestThreshold);
  }

  /**
   * Retrieve the specified number of the most recently captured ExposureCheckEntities to be
   * displayed on the UI.
   * @param numOfChecksToRetrieve number of the most recently captured checks to retrieve
   * @return the most recently captured {@link ExposureCheckEntity}s
   */
  @WorkerThread
  public LiveData<List<ExposureCheckEntity>> getLastXExposureChecksLiveData(
      int numOfChecksToRetrieve) {
    return exposureCheckDao.getLastXChecksLiveData(numOfChecksToRetrieve);
  }

  /**
   * Retrieve all the captured ExposureCheckEntities currently stored in the database.
   * @return all {@link ExposureCheckEntity}s in the database
   */
  @VisibleForTesting
  @WorkerThread
  List<ExposureCheckEntity> getAllExposureChecks() {
    return exposureCheckDao.getAll();
  }

}
