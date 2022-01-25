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

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.apps.exposurenotification.testsupport.InMemoryDb;
import com.google.common.collect.ImmutableList;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

/**
 * Tests for operations in {@link ExposureDao}.
 */
@RunWith(AndroidJUnit4.class)
@HiltAndroidTest
@Config(application = HiltTestApplication.class)
public class ExposureDaoTest {

  private ExposureDao exposureDao;

  @Before
  public void setUp() {
    exposureDao = InMemoryDb.create().exposureDao();
  }

  @Test
  public void getAll_shouldReturnAllEntities() {
    List<ExposureEntity> input = ImmutableList.of(
        createExposureEntity(1234L, 4242L),
        createExposureEntity(5678L, 1000L));

    exposureDao.upsertAll(input);

    List<ExposureEntity> entities = exposureDao.getAll();

    assertThat(entities).containsExactlyElementsIn(input);
  }

  @Test
  public void deleteAll_shouldReturnNoEntities() {
    List<ExposureEntity> input = ImmutableList.of(
        createExposureEntity(3333L, 4444L),
        createExposureEntity(1111L, 2222L));
    exposureDao.upsertAll(input);
    exposureDao.deleteAll();

    List<ExposureEntity> entities = exposureDao.getAll();

    assertThat(entities).hasSize(0);
  }

  @Test
  public void clearInsertExposureEntities_shouldReplaceAllEntities() {
    ExposureEntity exposureEntity1 = createExposureEntity(1337L, 118999L);
    ExposureEntity exposureEntity2 = createExposureEntity(4242L, 88199911L);
    ExposureEntity exposureEntity3 = createExposureEntity(1337L, 97252L);

    exposureDao.upsertAll(ImmutableList.of(exposureEntity1, exposureEntity2));
    exposureDao.deleteInsertExposureEntities(ImmutableList.of(exposureEntity3));

    List<ExposureEntity> entities = exposureDao.getAll();

    assertThat(entities).containsExactly(exposureEntity3);
  }

  private ExposureEntity createExposureEntity(long daysSinceEpoch, long score) {
    return ExposureEntity.newBuilder()
        .setDateDaysSinceEpoch(daysSinceEpoch)
        .setExposureScore(score)
        .build();
  }

}
