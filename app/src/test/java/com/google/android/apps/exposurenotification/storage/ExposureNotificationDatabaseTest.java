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
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * Basic test for {@link ExposureNotificationDatabase}. Dao's tested separately.
 */
@RunWith(RobolectricTestRunner.class)
public class ExposureNotificationDatabaseTest {

  private ExposureNotificationDatabase database;

  @Before
  public void setUp() {
    database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(), ExposureNotificationDatabase.class)
        .allowMainThreadQueries()
        .build();
  }

  @After
  public void tearDown() {
    database.close();
  }

  @Test
  public void testDatabase() {
    assertThat(database).isNotNull();
    assertThat(database.exposureDao()).isNotNull();
    assertThat(database.positiveDiagnosisDao()).isNotNull();
    assertThat(database.tokenDao()).isNotNull();
  }

}