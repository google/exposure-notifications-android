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

import android.content.Context;
import androidx.lifecycle.LiveData;
import com.google.android.gms.nearby.exposurenotification.ExposureWindow;
import java.util.List;

/**
 * Abstracts database access to {@link ExposureDao} data source.
 */
public class ExposureRepository {

  private final ExposureDao exposureDao;
  private final LiveData<List<ExposureEntity>> getAllLiveData;

  public ExposureRepository(Context context) {
    ExposureNotificationDatabase exposureNotificationDatabase =
        ExposureNotificationDatabase.getInstance(context);
    exposureDao = exposureNotificationDatabase.exposureDao();
    getAllLiveData = exposureDao.getAllLiveData();
  }

  public LiveData<List<ExposureEntity>> getAllLiveData() {
    return getAllLiveData;
  }

  /**
   * Adds missing exposures based on the current windows state.
   *
   * @param exposureWindows the {@link ExposureWindow}s
   * @return if any exposure was added
   */
  public boolean refreshWithExposureWindows(List<ExposureWindow> exposureWindows) {
    return exposureDao.refreshWithExposureWindows(exposureWindows);
  }

}
