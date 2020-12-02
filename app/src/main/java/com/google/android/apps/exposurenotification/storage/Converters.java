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

import android.net.Uri;
import androidx.room.TypeConverter;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity.HasSymptoms;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity.Shared;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity.TestResult;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity.TravelStatus;
import org.threeten.bp.Instant;
import org.threeten.bp.LocalDate;
import org.threeten.bp.ZonedDateTime;
import org.threeten.bp.format.DateTimeFormatter;

/**
 * Type converters for types Room doesn't serialise by default.
 */
class Converters {

  /**
   * TypeConverters for converting to and from {@link ZonedDateTime} instances.
   */
  public static class ZonedDateTimeConverter {

    private static final DateTimeFormatter sFormatter = DateTimeFormatter.ISO_ZONED_DATE_TIME;

    @TypeConverter
    public static ZonedDateTime toOffsetDateTime(String timestamp) {
      if (timestamp != null) {
        return sFormatter.parse(timestamp, ZonedDateTime.FROM);
      } else {
        return null;
      }
    }

    @TypeConverter
    public static String fromOffsetDateTime(ZonedDateTime timestamp) {
      if (timestamp != null) {
        return timestamp.format(sFormatter);
      } else {
        return null;
      }
    }
  }

  /**
   * Type converter for {@link LocalDate}.
   */
  public static class LocalDateConverter {

    @TypeConverter
    public static String fromLocalDate(LocalDate date) {
      return date == null ? null : date.toString();
    }

    @TypeConverter
    public static LocalDate toLocalDate(String date) {
      return date == null ? null : LocalDate.parse(date);
    }
  }

  /**
   * Type converter for {@link TestResult}.
   */
  public static class TestResultConverter {

    @TypeConverter
    public static String fromTestResult(TestResult result) {
      return result == null ? null : result.name();
    }

    @TypeConverter
    public static TestResult toTestResult(String result) {
      return result == null ? null : TestResult.valueOf(result);
    }

  }

  /**
   * Type converter for {@link Shared}.
   */
  public static class SharedConverter {

    @TypeConverter
    public static String fromShared(Shared shared) {
      return shared == null ? null : shared.name();
    }

    @TypeConverter
    public static Shared toShared(String shared) {
      return shared == null ? null : Shared.valueOf(shared);
    }

  }

  /**
   * Type converter for {@link TravelStatus}.
   */
  public static class TravelStatusConverter {

    @TypeConverter
    public static String fromTravelStatus(TravelStatus travelStatus) {
      return travelStatus == null ? null : travelStatus.name();
    }

    @TypeConverter
    public static TravelStatus toTravelStatus(String travelStatus) {
      return travelStatus == null ? null : TravelStatus.valueOf(travelStatus);
    }

  }

  /**
   * Type converter for {@link HasSymptoms}.
   */
  public static class HasSymptomsConverter {

    @TypeConverter
    public static String fromHasSymptoms(HasSymptoms selection) {
      return selection == null ? null : selection.name();
    }

    @TypeConverter
    public static HasSymptoms toHasSymptoms(String selection) {
      return selection == null ? null : HasSymptoms.valueOf(selection);
    }

  }

  /**
   * Type converter for {@link Uri}.
   */
  public static class UriConverter {

    @TypeConverter
    public static String fromUri(Uri uri) {
      return uri == null ? null : uri.toString();
    }

    @TypeConverter
    public static Uri toUri(String uri) {
      return uri == null ? null : Uri.parse(uri);
    }
  }

  /**
   * Type converter for {@link Instant}.
   */
  public static class InstantConverter {

    @TypeConverter
    public static long fromInstant(Instant instant) {
      return instant == null ? 0 : instant.toEpochMilli();
    }

    @TypeConverter
    public static Instant toInstant(long epochMillis) {
      return Instant.ofEpochMilli(epochMillis);
    }
  }
}
