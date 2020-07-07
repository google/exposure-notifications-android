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

import static com.google.common.truth.Truth.assertThat;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * Tests for operations in {@link TokenDao}.
 */
@RunWith(RobolectricTestRunner.class)
public class TokenDaoTest {

  // Sample data with which we'll populate the test database.
  private static final String TOKEN_1 = "TOKEN_1";
  private static final boolean RESPONDED_1 = false;
  private static final TokenEntity TOKEN_ENTITY_1 = TokenEntity.create(TOKEN_1, RESPONDED_1);

  private static final String TOKEN_2 = "TOKEN_2";
  private static final boolean RESPONDED_2 = true;
  private static final TokenEntity TOKEN_ENTITY_2 = TokenEntity.create(TOKEN_2, RESPONDED_2);

  private static final String TOKEN_3 = "TOKEN_3";
  private static final boolean RESPONDED_3 = false;
  private static final TokenEntity TOKEN_ENTITY_3 = TokenEntity.create(TOKEN_3, RESPONDED_3);

  private ExposureNotificationDatabase database;
  private TokenDao tokenDao;

  @Before
  public void setUp() {
    database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(), ExposureNotificationDatabase.class).build();
    tokenDao = database.tokenDao();
  }

  @After
  public void tearDown() {
    database.close();
  }

  @Test
  public void upsertAsync_insert()
      throws InterruptedException, ExecutionException, TimeoutException {
    ListenableFuture<List<TokenEntity>> finalTokens =
        FluentFuture.from(populate(TOKEN_ENTITY_1))
            .transformAsync(v -> tokenDao.getAllAsync(), MoreExecutors.directExecutor());

    assertThat(finalTokens.get(10, TimeUnit.SECONDS)).containsExactly(TOKEN_ENTITY_1);
  }

  @Test
  public void upsertAsync_update()
      throws InterruptedException, ExecutionException, TimeoutException {
    TokenEntity updatedToken = TokenEntity.create(TOKEN_1, !RESPONDED_1);
    ListenableFuture<List<TokenEntity>> finalTokens =
        FluentFuture.from(populate(TOKEN_ENTITY_1))
            .transformAsync(v -> tokenDao.upsertAsync(updatedToken),
                MoreExecutors.directExecutor())
            .transformAsync(v -> tokenDao.getAllAsync(), MoreExecutors.directExecutor());

    assertThat(finalTokens.get(10, TimeUnit.SECONDS)).containsExactly(updatedToken);
  }

  @Test
  public void markTokenRespondedAsync_updates()
      throws InterruptedException, ExecutionException, TimeoutException {
    TokenEntity updatedToken = TokenEntity.create(TOKEN_1, true);
    updatedToken.setCreatedTimestampMs(TOKEN_ENTITY_1.getCreatedTimestampMs());
    updatedToken.setLastUpdatedTimestampMs(123L);
    ListenableFuture<List<TokenEntity>> finalTokens =
        FluentFuture.from(populate(TOKEN_ENTITY_1))
            .transformAsync(v -> tokenDao.markTokenRespondedAsync(TOKEN_1, 123L),
                MoreExecutors.directExecutor())
            .transformAsync(v -> tokenDao.getAllAsync(), MoreExecutors.directExecutor());

    assertThat(finalTokens.get(10, TimeUnit.SECONDS)).containsExactly(updatedToken);
  }

  @Test
  public void getAllAsync() throws InterruptedException, ExecutionException, TimeoutException {
    ListenableFuture<List<TokenEntity>> finalTokens =
        FluentFuture.from(populate(TOKEN_ENTITY_1, TOKEN_ENTITY_2, TOKEN_ENTITY_3))
            .transformAsync(v -> tokenDao.getAllAsync(), MoreExecutors.directExecutor());

    assertThat(finalTokens.get(10, TimeUnit.SECONDS))
        .containsExactly(TOKEN_ENTITY_1, TOKEN_ENTITY_2, TOKEN_ENTITY_3);
  }

  private ListenableFuture<List<Void>> populate(TokenEntity... tokenEntities) {
    List<ListenableFuture<Void>> upserts = new ArrayList<>();
    for (TokenEntity tokenEntity : tokenEntities) {
      upserts.add(tokenDao.upsertAsync(tokenEntity));
    }
    return Futures.allAsList(upserts);
  }

}
