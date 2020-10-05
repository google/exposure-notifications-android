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
import androidx.annotation.VisibleForTesting;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import com.google.android.apps.exposurenotification.storage.Converters.HasSymptomsConverter;
import com.google.android.apps.exposurenotification.storage.Converters.LocalDateConverter;
import com.google.android.apps.exposurenotification.storage.Converters.SharedConverter;
import com.google.android.apps.exposurenotification.storage.Converters.TestResultConverter;
import com.google.android.apps.exposurenotification.storage.Converters.TravelStatusConverter;
import com.google.android.apps.exposurenotification.storage.Converters.UriConverter;
import com.google.android.apps.exposurenotification.storage.Converters.ZonedDateTimeConverter;

/**
 * Defines the sqlite database for the room persistence API.
 *
 * <p>Partners should implement a daily TTL/expiry, for on-device storage of this data, and must
 * ensure compliance with all applicable laws and requirements with respect to encryption, storage,
 * and retention polices for end user data.
 */
@Database(
    entities = {
        CountryEntity.class,
        DiagnosisEntity.class,
        DownloadServerEntity.class,
        ExposureEntity.class,
        AnalyticsLoggingEntity.class
    },
    exportSchema = true,
    version = 38  // Do not increment without migration & tests.
)
@TypeConverters({
    ZonedDateTimeConverter.class,
    LocalDateConverter.class,
    TestResultConverter.class,
    SharedConverter.class,
    HasSymptomsConverter.class,
    TravelStatusConverter.class,
    UriConverter.class
})
@VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
public abstract class ExposureNotificationDatabase extends RoomDatabase {

  private static final String DATABASE_NAME = "exposurenotification";

  static final Migration MIGRATION_35_36 = new Migration(35, 36) {
    @Override
    public void migrate(SupportSQLiteDatabase database) {
      database.execSQL(
          "ALTER TABLE DiagnosisEntity ADD COLUMN isCodeFromLink INTEGER NOT NULL DEFAULT 0");
    }
  };

  static final Migration MIGRATION_36_37 = new Migration(36, 37) {
    @Override
    public void migrate(SupportSQLiteDatabase database) {
      database.execSQL(
          "CREATE TABLE DownloadServerEntity ("
              + "indexUri TEXT NOT NULL, "
              + "mostRecentSuccessfulDownload TEXT, "
              + "PRIMARY KEY(indexUri)"
              + ")");
    }
  };

  static final Migration MIGRATION_37_38 = new Migration(37, 38) {
    @Override
    public void migrate(SupportSQLiteDatabase database) {
      database.execSQL(
          "CREATE TABLE AnalyticsLoggingEntity ("
              + "key INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
              + "eventProto TEXT NOT NULL "
              + ")");
    }
  };

  static final Migration[] ALL_MIGRATIONS = new Migration[]{MIGRATION_35_36, MIGRATION_36_37, MIGRATION_37_38};

  abstract CountryDao countryDao();

  abstract DiagnosisDao diagnosisDao();

  abstract DownloadServerDao downloadServerDao();

  abstract ExposureDao exposureDao();

  abstract AnalyticsLoggingDao analyticsLoggingDao();

  public static ExposureNotificationDatabase buildDatabase(Context context) {
    // This will create a database in:
    // /data/data/com.google.android.apps.exposurenotification/databases/ which will be only
    // accessible to the app.
    return Room.databaseBuilder(
        context.getApplicationContext(), ExposureNotificationDatabase.class, DATABASE_NAME)
        .addMigrations(ALL_MIGRATIONS)
        .fallbackToDestructiveMigrationFrom(
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24,
            25, 26, 27, 28, 29, 30, 31, 32, 33, 34) // No more destructive, must be migrations.
        .build();
  }
}
