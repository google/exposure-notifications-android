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
import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;

/**
 * Abstracts access to the database layers.
 */
public class ExposureNotificationRepository {

  private final PositiveDiagnosisDao positiveDiagnosisDao;
  private final ExposureDao exposureDao;
  private final TokenDao tokenDao;

  public ExposureNotificationRepository(Context context) {
    ExposureNotificationDatabase exposureNotificationDatabase =
        ExposureNotificationDatabase.getInstance(context);
    positiveDiagnosisDao = exposureNotificationDatabase.positiveDiagnosisDao();
    exposureDao = exposureNotificationDatabase.exposureDao();
    tokenDao = exposureNotificationDatabase.tokenDao();
  }

  public LiveData<List<PositiveDiagnosisEntity>> getAllPositiveDiagnosisEntitiesLiveData() {
    return positiveDiagnosisDao.getAllLiveData();
  }

  public ListenableFuture<Void> upsertPositiveDiagnosisEntityAsync(
      PositiveDiagnosisEntity entity) {
    return positiveDiagnosisDao.upsertAsync(entity);
  }

  public LiveData<List<ExposureEntity>> getAllExposureEntityLiveData() {
    return exposureDao.getAllLiveData();
  }

  public ListenableFuture<Void> upsertExposureEntitiesAsync(List<ExposureEntity> entities) {
    return exposureDao.upsertAsync(entities);
  }

  public ListenableFuture<Void> deleteAllExposureEntitiesAsync() {
    return exposureDao.deleteAllAsync();
  }

  public ListenableFuture<List<TokenEntity>> getAllTokenEntityAsync() {
    return tokenDao.getAllAsync();
  }

  public ListenableFuture<Void> upsertTokenEntityAsync(TokenEntity entity) {
    return tokenDao.upsertAsync(entity);
  }

  public ListenableFuture<Void> markTokenEntityRespondedAsync(String token) {
    return tokenDao.markTokenRespondedAsync(token);
  }

  public ListenableFuture<Void> deleteTokenEntityAsync(String token) {
    return tokenDao.deleteByTokenAsync(token);
  }

  public ListenableFuture<Void> deleteTokenEntitiesAsync(List<String> tokens) {
    return tokenDao.deleteByTokensAsync(tokens);
  }

}
