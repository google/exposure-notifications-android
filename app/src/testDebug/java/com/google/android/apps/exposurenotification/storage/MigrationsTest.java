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
import static com.google.android.apps.exposurenotification.storage.ExposureNotificationDatabase.MIGRATION_38_39;
import static com.google.android.apps.exposurenotification.storage.ExposureNotificationDatabase.MIGRATION_39_40;
import static com.google.android.apps.exposurenotification.storage.ExposureNotificationDatabase.MIGRATION_40_41;
import static com.google.android.apps.exposurenotification.storage.ExposureNotificationDatabase.MIGRATION_41_42;
import static com.google.android.apps.exposurenotification.storage.ExposureNotificationDatabase.MIGRATION_42_43;
import static com.google.common.truth.Truth.assertThat;

import android.database.Cursor;
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
    helper = new MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
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
    ExposureNotificationDatabase appDb = createAppDatabase();
    appDb.getOpenHelper().getWritableDatabase();
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

  @Test
  public void migrate38To39() throws IOException {
    SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 38);
    db.close();
    // MigrationTestHelper automatically verifies the schema changes.
    db = helper.runMigrationsAndValidate(TEST_DB, 39, true, MIGRATION_38_39);
  }

  @Test
  public void migrate38To39_shouldBackFillExistingRevisionTokens() throws Exception {
    // GIVEN
    // Set up a version 38 database with two diagnoses.
    SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 38);
    db.execSQL("INSERT INTO DiagnosisEntity"
        + " (id, createdTimestampMs, revisionToken, isServerOnsetDate, hasSymptoms, isCodeFromLink)"
        + " VALUES (1, 100, 'revision-token-1', 0, 'NO', 0)");
    db.execSQL("INSERT INTO DiagnosisEntity"
        + " (id, createdTimestampMs, revisionToken, isServerOnsetDate, hasSymptoms, isCodeFromLink)"
        + " VALUES (2, 200, 'revision-token-2', 0, 'NO', 0)");

    // WHEN
    // Now upgrade the database
    helper.runMigrationsAndValidate(TEST_DB, 39, true, MIGRATION_38_39);

    // THEN
    // The most recent revision token should be available in the revision token table.
    try (Cursor c = db.query(
        "SELECT revisionToken FROM RevisionTokenEntity"
            + " WHERE revisionToken IS NOT NULL"
            + " ORDER BY createdTimestampMs DESC LIMIT 1")) {
      assertThat(c.moveToNext()).isTrue();
      assertThat(c.getString(0)).isEqualTo("revision-token-2");
    }
  }

  @Test
  public void migrate38To39_backfillShouldSkipDiagnosesWithNullRevisionTokens() throws Exception {
    // GIVEN
    // Set up a version 38 database with two diagnoses, one of which has a NULL revisionToken.
    SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 38);
    db.execSQL("INSERT INTO DiagnosisEntity"
        + " (id, createdTimestampMs, revisionToken, isServerOnsetDate, hasSymptoms, isCodeFromLink)"
        + " VALUES (1, 100, 'revision-token-1', 0, 'NO', 0)");
    db.execSQL("INSERT INTO DiagnosisEntity"
        + " (id, createdTimestampMs, revisionToken, isServerOnsetDate, hasSymptoms, isCodeFromLink)"
        + " VALUES (2, 200, NULL, 0, 'NO', 0)");

    // WHEN
    // Now upgrade the database
    helper.runMigrationsAndValidate(TEST_DB, 39, true, MIGRATION_38_39);

    // THEN
    // The non-null revision token should be available in the revision token table.
    try (Cursor c = db.query(
        "SELECT revisionToken FROM RevisionTokenEntity"
            + " WHERE revisionToken IS NOT NULL"
            + " ORDER BY createdTimestampMs DESC LIMIT 1")) {
      assertThat(c.moveToNext()).isTrue();
      assertThat(c.getString(0)).isEqualTo("revision-token-1");
    }
  }

  @Test
  public void migrate39To40() throws IOException {
    SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 39);
    db.close();
    // MigrationTestHelper automatically verifies the schema changes.
    helper.runMigrationsAndValidate(TEST_DB, 40, true, MIGRATION_39_40);
  }

  @Test
  public void migrate40to41() throws IOException {
    SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 40);
    db.close();
    // MigrationTestHelper automatically verifies the schema changes.
    helper.runMigrationsAndValidate(TEST_DB, 41, true, MIGRATION_40_41);
  }

  @Test
  public void migrate41to42() throws IOException {
    SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 41);
    db.close();
    // MigrationTestHelper automatically verifies the schema changes.
    helper.runMigrationsAndValidate(TEST_DB, 42, true, MIGRATION_41_42);
  }

  @Test
  public void migrate41To42_backfillShouldDuplicateCreatedTimestampMs() throws Exception {
    // GIVEN
    // Set up a version 41 database with two diagnoses (with default/arbitrary createdTimestampMs).
    SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 41);
    db.execSQL("INSERT INTO DiagnosisEntity"
        + " (id, createdTimestampMs, isServerOnsetDate, isCodeFromLink)"
        + " VALUES (1, 0, 0, 0)");
    db.execSQL("INSERT INTO DiagnosisEntity"
        + " (id, createdTimestampMs, isServerOnsetDate, isCodeFromLink)"
        + " VALUES (2, 1616687307341, 0, 0)");

    // WHEN
    // Now upgrade the database
    helper.runMigrationsAndValidate(TEST_DB, 42, true, MIGRATION_41_42);

    // THEN
    // There should not be any rows where createdTimestampMs is not equal to lastUpdatedTimestampMs
    try (Cursor c = db.query(
        "SELECT count(*) FROM DiagnosisEntity"
            + " WHERE createdTimestampMs != lastUpdatedTimestampMs")) {
      assertThat(c.moveToNext()).isTrue();
      assertThat(c.getInt(0)).isEqualTo(0);
    }
  }

  @Test
  public void migrate42to43() throws IOException {
    SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 42);
    db.close();
    // MigrationTestHelper automatically verifies the schema changes.
    helper.runMigrationsAndValidate(TEST_DB, 43, true, MIGRATION_42_43);
  }

  private ExposureNotificationDatabase createAppDatabase() {
    ExposureNotificationDatabase db = Room.databaseBuilder(
        InstrumentationRegistry.getInstrumentation().getTargetContext(),
        ExposureNotificationDatabase.class,
        TEST_DB)
        .addMigrations(ExposureNotificationDatabase.ALL_MIGRATIONS)
        .build();
    helper.closeWhenFinished(db);
    return db;
  }
}
