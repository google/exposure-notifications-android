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

import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.apps.exposurenotification.storage.Converters.HasSymptomsConverter;
import com.google.android.apps.exposurenotification.storage.Converters.InstantConverter;
import com.google.android.apps.exposurenotification.storage.Converters.LocalDateConverter;
import com.google.android.apps.exposurenotification.storage.Converters.SharedConverter;
import com.google.android.apps.exposurenotification.storage.Converters.TestResultConverter;
import com.google.android.apps.exposurenotification.storage.Converters.TravelStatusConverter;
import com.google.android.apps.exposurenotification.storage.Converters.UriConverter;
import com.google.android.apps.exposurenotification.storage.Converters.ZonedDateTimeConverter;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity.HasSymptoms;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity.Shared;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity.TestResult;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity.TravelStatus;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;
import org.threeten.bp.Instant;
import org.threeten.bp.LocalDate;
import org.threeten.bp.ZoneId;
import org.threeten.bp.ZonedDateTime;

/**
 * Tests type conversions.
 */
@RunWith(AndroidJUnit4.class)
@HiltAndroidTest
@Config(application = HiltTestApplication.class)
public class TypeConvertersTest {

  private static final ZonedDateTime ZONED_DATE_TIME =
      ZonedDateTime.of(2020, 1, 3, 4, 5, 6, 7, ZoneId.of("UTC"));
  private static final String ISO_FORMATTED_ZONED_DATE_TIME =
      "2020-01-01T00:00:00-02:00";

  @Test
  public void toOffsetDateTime_null_returnNull() {
    assertThat(ZonedDateTimeConverter.toOffsetDateTime(null)).isNull();
  }

  @Test
  public void toOffsetDateTime_convertsAndBack() {
    ZonedDateTime zonedDateTime = ZonedDateTimeConverter
        .toOffsetDateTime(ISO_FORMATTED_ZONED_DATE_TIME);

    assertThat(ZonedDateTimeConverter.fromOffsetDateTime(zonedDateTime))
        .isEqualTo(ISO_FORMATTED_ZONED_DATE_TIME);
  }

  @Test
  public void fromOffsetDateTime_null_returnsNull() {
    assertThat(ZonedDateTimeConverter.fromOffsetDateTime(null)).isNull();
  }

  @Test
  public void fromOffsetDateTime_convertsAndBack() {
    String isoFormattedZonedDateTime = ZonedDateTimeConverter
        .fromOffsetDateTime(ZONED_DATE_TIME);

    assertThat(ZonedDateTimeConverter.toOffsetDateTime(isoFormattedZonedDateTime))
        .isEqualTo(ZONED_DATE_TIME);
  }

  @Test
  public void fromLocalDate_null_returnsNull() {
    assertThat(LocalDateConverter.fromLocalDate(null)).isNull();
  }

  @Test
  public void fromLocalDate_convertsToIso8601() {
    LocalDate date = LocalDate.of(2020, 1, 2);
    assertThat(LocalDateConverter.fromLocalDate(date)).isEqualTo("2020-01-02");
  }

  @Test
  public void toLocalDate_convertsFromIso8601() {
    LocalDate expected = LocalDate.of(2020, 1, 2);
    assertThat(LocalDateConverter.toLocalDate("2020-01-02")).isEqualTo(expected);
  }

  @Test
  public void fromTestResult_null_returnsNull() {
    assertThat(TestResultConverter.fromTestResult(null)).isNull();
  }

  @Test
  public void fromTestResult_convertsToEnumName() {
    assertThat(TestResultConverter.fromTestResult(TestResult.LIKELY)).isEqualTo("LIKELY");
  }

  @Test
  public void toTestResult_convertsFromEnumName() {
    assertThat(TestResultConverter.toTestResult("NEGATIVE")).isEqualTo(TestResult.NEGATIVE);
  }

  @Test
  public void fromShared_null_returnsNull() {
    assertThat(SharedConverter.fromShared(null)).isNull();
  }

  @Test
  public void fromShared_convertsToEnumName() {
    assertThat(SharedConverter.fromShared(Shared.SHARED)).isEqualTo("SHARED");
  }

  @Test
  public void toShared_convertsFromEnumName() {
    assertThat(SharedConverter.toShared("SHARED")).isEqualTo(Shared.SHARED);
  }

  @Test
  public void fromTravelStatus_null_returnsNull() {
    assertThat(TravelStatusConverter.fromTravelStatus(null)).isNull();
  }

  @Test
  public void fromTravelStatus_convertsToEnumName() {
    assertThat(TravelStatusConverter.fromTravelStatus(TravelStatus.TRAVELED)).isEqualTo("TRAVELED");
  }

  @Test
  public void toTravelStatus_convertsFromEnumName() {
    assertThat(TravelStatusConverter.toTravelStatus("TRAVELED")).isEqualTo(TravelStatus.TRAVELED);
  }

  @Test
  public void fromHasSymptoms_null_returnsNull() {
    assertThat(HasSymptomsConverter.fromHasSymptoms(null)).isNull();
  }

  @Test
  public void fromHasSymptoms_convertsToEnumName() {
    assertThat(HasSymptomsConverter.fromHasSymptoms(HasSymptoms.YES))
        .isEqualTo("YES");
  }

  @Test
  public void toHasSymptoms_convertsFromEnumName() {
    assertThat(HasSymptomsConverter.toHasSymptoms("YES"))
        .isEqualTo(HasSymptoms.YES);
  }

  @Test
  public void fromUri_null_returnsNull() {
    assertThat(UriConverter.fromUri(null)).isNull();
  }

  @Test
  public void toUri_null_returnsNull() {
    assertThat(UriConverter.toUri(null)).isNull();
  }

  @Test
  public void fromUri_convertsToUriString() {
    assertThat(UriConverter.fromUri(
        Uri.parse("https://example.com/path/file.txt?query#fragment")))
        .isEqualTo("https://example.com/path/file.txt?query#fragment");
  }

  @Test
  public void toUri_convertsFromUriString() {
    assertThat(UriConverter.toUri(
        "https://example.com/path/file.txt?query#fragment"))
        .isEqualTo(Uri.parse("https://example.com/path/file.txt?query#fragment"));
  }

  @Test
  public void fromInstant_null_returnsZero() {
    assertThat(InstantConverter.fromInstant(null)).isEqualTo(0);
  }

  @Test
  public void fromInstant_convertsToEpochMillis() {
    assertThat(
        InstantConverter.fromInstant(Instant.ofEpochMilli(1234567890L))).isEqualTo(1234567890L);
  }

  @Test
  public void toInstant_convertsFromLong() {
    assertThat(
        InstantConverter.toInstant(1234567890L)).isEqualTo(Instant.ofEpochMilli(1234567890L));
  }
}
