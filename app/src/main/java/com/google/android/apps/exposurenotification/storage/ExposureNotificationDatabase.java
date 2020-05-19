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
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

/**
 * Defines the sqlite database for the room persistence API.
 *
 * <p>Partners should implement a daily TTL/expiry, for on-device storage of this data, and must
 * ensure compliance with all applicable laws and requirements with respect to encryption, storage,
 * and retention polices for end user data.
 */
@Database(
    entities = {
      PositiveDiagnosisEntity.class,
      ExposureEntity.class,
      TokenEntity.class
    },
    version = 22,
    exportSchema = false)
@TypeConverters({ZonedDateTimeTypeConverter.class})
abstract class ExposureNotificationDatabase extends RoomDatabase {
  private static final String DATABASE_NAME = "exposurenotification";

  @SuppressWarnings("ConstantField") // Singleton pattern.
  private static volatile ExposureNotificationDatabase INSTANCE;

  abstract PositiveDiagnosisDao positiveDiagnosisDao();
  abstract ExposureDao exposureDao();
  abstract TokenDao tokenDao();

  static synchronized ExposureNotificationDatabase getInstance(Context context) {
    if (INSTANCE == null) {
      INSTANCE = buildDatabase(context);
    }
    return INSTANCE;
  }

  private static ExposureNotificationDatabase buildDatabase(Context context) {
    // This will create a database in:
    // /data/data/com.google.android.apps.exposurenotification/databases/ which will be only
    // accessible to the app.
    return Room.databaseBuilder(
        context.getApplicationContext(), ExposureNotificationDatabase.class, DATABASE_NAME)
        .fallbackToDestructiveMigrationFrom(
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21)
        .build();
  }
}
