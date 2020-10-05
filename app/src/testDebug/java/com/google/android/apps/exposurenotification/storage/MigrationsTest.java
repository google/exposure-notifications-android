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

import static com.google.android.apps.exposurenotification.storage.ExposureNotificationDatabase.MIGRATION_35_36;
import static com.google.android.apps.exposurenotification.storage.ExposureNotificationDatabase.MIGRATION_36_37;
import static com.google.android.apps.exposurenotification.storage.ExposureNotificationDatabase.MIGRATION_37_38;

import androidx.room.Room;
import androidx.room.testing.MigrationTestHelper;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

@RunWith(AndroidJUnit4.class)
@HiltAndroidTest
@Config(application = HiltTestApplication.class)
public class MigrationsTest {

  private static final String TEST_DB = "migration-test";

  @Rule
  public MigrationTestHelper helper;

  public MigrationsTest() {
    helper = new MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),
        ExposureNotificationDatabase.class.getCanonicalName(),
        new FrameworkSQLiteOpenHelperFactory());
  }

  @Test
  public void migrateAll() throws IOException {
    // Create earliest version of the database.
    SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 35);
    db.close();

    // Open latest version of the database. Room will validate the schema
    // once all migrations execute.
    ExposureNotificationDatabase appDb = Room.databaseBuilder(
        InstrumentationRegistry.getInstrumentation().getTargetContext(),
        ExposureNotificationDatabase.class,
        TEST_DB)
        .addMigrations(ExposureNotificationDatabase.ALL_MIGRATIONS).build();
    appDb.getOpenHelper().getWritableDatabase();
    appDb.close();
  }

  @Test
  public void migrate35To36() throws IOException {
    SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 35);
    db.close();
    // MigrationTestHelper automatically verifies the schema changes.
    db = helper.runMigrationsAndValidate(TEST_DB, 36, true, MIGRATION_35_36);
  }

  @Test
  public void migrate36To37() throws IOException {
    SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 36);
    db.close();
    // MigrationTestHelper automatically verifies the schema changes.
    db = helper.runMigrationsAndValidate(TEST_DB, 37, true, MIGRATION_36_37);
  }

  @Test
  public void migrate37To38() throws IOException {
    SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 37);
    db.close();
    // MigrationTestHelper automatically verifies the schema changes.
    db = helper.runMigrationsAndValidate(TEST_DB, 38, true, MIGRATION_37_38);
  }
}