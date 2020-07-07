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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.threeten.bp.ZoneId;
import org.threeten.bp.ZonedDateTime;

/**
 * Tests {@link ZonedDateTime} type conversions.
 */
@RunWith(RobolectricTestRunner.class)
public class ZonedDateTimeTypeConverterTest {

  private static final ZonedDateTime ZONED_DATE_TIME =
      ZonedDateTime.of(2020, 1, 3, 4, 5, 6, 7, ZoneId.of("UTC"));
  private static final String ISO_FORMATTED_ZONED_DATE_TIME =
      "2020-01-01T00:00:00-02:00";

  @Test
  public void toOffsetDateTime_null_returnNull() {
    assertThat(ZonedDateTimeTypeConverter.toOffsetDateTime(null)).isNull();
  }

  @Test
  public void toOffsetDateTime_convertsAndBack() {
    ZonedDateTime zonedDateTime = ZonedDateTimeTypeConverter
        .toOffsetDateTime(ISO_FORMATTED_ZONED_DATE_TIME);

    assertThat(ZonedDateTimeTypeConverter.fromOffsetDateTime(zonedDateTime))
        .isEqualTo(ISO_FORMATTED_ZONED_DATE_TIME);
  }

  @Test
  public void fromOffsetDateTime_null_returnsNull() {
    assertThat(ZonedDateTimeTypeConverter.fromOffsetDateTime(null)).isNull();
  }

  @Test
  public void fromOffsetDateTime_convertsAndBack() {
    String isoFormattedZonedDateTime = ZonedDateTimeTypeConverter
        .fromOffsetDateTime(ZONED_DATE_TIME);

    assertThat(ZonedDateTimeTypeConverter.toOffsetDateTime(isoFormattedZonedDateTime))
        .isEqualTo(ZONED_DATE_TIME);
  }

}
