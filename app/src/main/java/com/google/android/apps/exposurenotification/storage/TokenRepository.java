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
import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;

/**
 * Abstracts database access to {@link TokenDao} data source.
 */
public class TokenRepository {

  private final TokenDao tokenDao;

  public TokenRepository(Context context) {
    ExposureNotificationDatabase exposureNotificationDatabase =
        ExposureNotificationDatabase.getInstance(context);
    tokenDao = exposureNotificationDatabase.tokenDao();
  }

  public ListenableFuture<List<TokenEntity>> getAllAsync() {
    return tokenDao.getAllAsync();
  }

  public ListenableFuture<Void> upsertAsync(TokenEntity entity) {
    return tokenDao.upsertAsync(entity);
  }

  public ListenableFuture<Void> deleteByTokensAsync(String... tokens) {
    return tokenDao.deleteByTokensAsync(tokens);
  }

}
