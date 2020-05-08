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
import java.util.List;
import java.util.stream.Collectors;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.threeten.bp.Instant;
import org.threeten.bp.ZoneId;

@RunWith(RobolectricTestRunner.class)
public class ExposureNotificationDatabaseTest {

  // Sample data with which we'll populate the test database.
  private static final long TEST_TS_0 = 0L;
  private static final long CREATED_TS_0 = 1L;
  private static final long TEST_TS_1 = 1000L;
  private static final long CREATED_TS_1 = 1001L;
  private static final long TEST_TS_2 = 2000L;
  private static final long CREATED_TS_2 = 2001L;

  private ExposureNotificationDatabase database;
  private PositiveDiagnosisDao positiveDiagnosisDao;

  @Before
  public void setUp() {
    database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(), ExposureNotificationDatabase.class)
        .allowMainThreadQueries()
        .build();
    positiveDiagnosisDao = database.positiveDiagnosisDao();
  }

  @After
  public void tearDown() {
    database.close();
  }

  @Test
  public void positiveDiagnosisDao_upsert() {
    long createdTs = 196146001L;
    long testedTs = 196146000L;
    PositiveDiagnosisEntity entity = createPositiveDiagnosisEntity(createdTs, testedTs);

    positiveDiagnosisDao.upsert(entity);

    List<PositiveDiagnosisEntity> entities = positiveDiagnosisDao.getAll();
    assertThat(entities).hasSize(1);
    assertThat(entities.contains(entity));
  }

  @Test
  public void positiveDiagnosisDao_getAll() {
    populateDbWithTestData();

    List<PositiveDiagnosisEntity> entities = positiveDiagnosisDao.getAll();

    assertThat(entities).hasSize(3);
    List<Long> testTimes = entities.stream()
        .map(e -> e.getTestTimestamp().toEpochSecond() * 1000)
        .collect(Collectors.toList());
    assertThat(testTimes).containsExactly(TEST_TS_0, TEST_TS_1, TEST_TS_2);
  }

  private void populateDbWithTestData() {
    positiveDiagnosisDao
        .upsert(createPositiveDiagnosisEntity(CREATED_TS_0, TEST_TS_0));
    positiveDiagnosisDao
        .upsert(createPositiveDiagnosisEntity(CREATED_TS_1, TEST_TS_1));
    positiveDiagnosisDao
        .upsert(createPositiveDiagnosisEntity(CREATED_TS_2, TEST_TS_2));
  }

  private PositiveDiagnosisEntity createPositiveDiagnosisEntity(long createdTs, long testTs) {
    PositiveDiagnosisEntity entity = new PositiveDiagnosisEntity(
        Instant.ofEpochMilli(testTs).atZone(ZoneId.of("UTC")));
    entity.setCreatedTimestampMs(createdTs);
    return entity;
  }
}