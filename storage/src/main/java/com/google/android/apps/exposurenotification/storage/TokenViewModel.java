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

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;

/**
 * View model for the {@link TokenEntity} table.
 */
public class TokenViewModel extends AndroidViewModel {

  private final ExposureNotificationRepository repository;

  public TokenViewModel(@NonNull Application application) {
    super(application);
    repository = new ExposureNotificationRepository(application);
  }

  public ListenableFuture<List<TokenEntity>> getAllTokenEntityAsync() {
    return repository.getAllTokenEntityAsync();
  }

  public ListenableFuture<Void> upsertTokenEntityAsync(TokenEntity tokenEntity) {
    return repository.upsertTokenEntityAsync(tokenEntity);
  }

  public ListenableFuture<Void> deleteTokenEntityAsync(String token) {
    return repository.deleteTokenEntityAsync(token);
  }

  public ListenableFuture<Void> deleteTokenEntitiesAsync(List<String> tokens) {
    return repository.deleteTokenEntitiesAsync(tokens);
  }

}