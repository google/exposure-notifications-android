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
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.FluentFuture;
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
 * Tests for operations in {@link ExposureDao}.
 */
@RunWith(RobolectricTestRunner.class)
public class ExposureDaoTest {

  // Sample data with which we'll populate the test database.
  private static final long DATE_MILLIS_SINCE_EPOCH_1 = 1234L;
  private static final long RECEIVED_TIMESTAMP_MS_1 = 5678L;
  private static final ExposureEntity EXPOSURE_ENTITY_1 = ExposureEntity
      .create(DATE_MILLIS_SINCE_EPOCH_1, RECEIVED_TIMESTAMP_MS_1);

  private static final long DATE_MILLIS_SINCE_EPOCH_2 = 1234L;
  private static final long RECEIVED_TIMESTAMP_MS_2 = 5678L;
  private static final ExposureEntity EXPOSURE_ENTITY_2 = ExposureEntity
      .create(DATE_MILLIS_SINCE_EPOCH_2, RECEIVED_TIMESTAMP_MS_2);

  private ExposureNotificationDatabase database;
  private ExposureDao exposureDao;

  @Before
  public void setUp() {
    database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(), ExposureNotificationDatabase.class).build();
    exposureDao = database.exposureDao();
  }

  @After
  public void tearDown() {
    database.close();
  }


  @Test
  public void upsertAsync_insert()
      throws InterruptedException, ExecutionException, TimeoutException {
    ListenableFuture<List<ExposureEntity>> finalTokens =
        FluentFuture.from(exposureDao.upsertAsync(Lists.newArrayList(EXPOSURE_ENTITY_1)))
            .transformAsync(v -> exposureDao.getAllAsync(), MoreExecutors.directExecutor());

    assertEqualsStripIds(finalTokens.get(10, TimeUnit.SECONDS),
        Lists.newArrayList(EXPOSURE_ENTITY_1));
  }

  @Test
  public void upsertAsync_update()
      throws InterruptedException, ExecutionException, TimeoutException {
    ExposureEntity updatedEntity1 = ExposureEntity
        .create(DATE_MILLIS_SINCE_EPOCH_1 + 1, RECEIVED_TIMESTAMP_MS_1 + 1);
    ExposureEntity updatedEntity2 = ExposureEntity
        .create(DATE_MILLIS_SINCE_EPOCH_2 + 1, RECEIVED_TIMESTAMP_MS_2 + 1);

    ListenableFuture<List<ExposureEntity>> finalTokens =
        FluentFuture
            .from(exposureDao.upsertAsync(Lists.newArrayList(EXPOSURE_ENTITY_1, EXPOSURE_ENTITY_2)))
            .transformAsync(v -> exposureDao.getAllAsync(), MoreExecutors.directExecutor())
            .transformAsync(entities -> {
              List<ExposureEntity> updates = new ArrayList<>();
              for (ExposureEntity entity : entities) {
                entity.setDateMillisSinceEpoch(entity.getDateMillisSinceEpoch() + 1);
                entity.setReceivedTimestampMs(entity.getReceivedTimestampMs() + 1);
                updates.add(entity);
              }
              return exposureDao.upsertAsync(updates);
            }, MoreExecutors.directExecutor())
            .transformAsync(v -> exposureDao.getAllAsync(), MoreExecutors.directExecutor());

    assertEqualsStripIds(finalTokens.get(10, TimeUnit.SECONDS),
        Lists.newArrayList(updatedEntity1, updatedEntity2));
  }

  @Test
  public void deleteAllAsync()
      throws InterruptedException, ExecutionException, TimeoutException {
    ListenableFuture<List<ExposureEntity>> finalTokens =
        FluentFuture
            .from(exposureDao.upsertAsync(Lists.newArrayList(EXPOSURE_ENTITY_1, EXPOSURE_ENTITY_2)))
            .transformAsync(v -> exposureDao.deleteAllAsync(),
                MoreExecutors.directExecutor())
            .transformAsync(v -> exposureDao.getAllAsync(), MoreExecutors.directExecutor());

    assertThat(finalTokens.get(10, TimeUnit.SECONDS)).isEmpty();
  }

  private static void assertEqualsStripIds(List<ExposureEntity> exposureEntitiesA,
      List<ExposureEntity> exposureEntitiesB) {
    assertThat(stripIds(exposureEntitiesA)).containsExactlyElementsIn(stripIds(exposureEntitiesB));
  }

  private static List<ExposureEntity> stripIds(List<ExposureEntity> exposureEntities) {
    List<ExposureEntity> exposureEntitiesStripped = new ArrayList<>();
    for (ExposureEntity exposureEntity : exposureEntities) {
      exposureEntity.setId(0);
      exposureEntitiesStripped.add(exposureEntity);
    }
    return exposureEntitiesStripped;
  }

}
