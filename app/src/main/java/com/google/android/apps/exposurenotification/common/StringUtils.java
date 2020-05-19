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

package com.google.android.apps.exposurenotification.common;

import java.util.Locale;
import org.threeten.bp.Instant;
import org.threeten.bp.ZoneId;
import org.threeten.bp.format.DateTimeFormatter;

/**
 * Simple util class for manipulating strings.
 */
public final class StringUtils {

  private static final DateTimeFormatter SHORT_FORMAT =
      DateTimeFormatter.ofPattern("MMMM dd, YYYY").withZone(ZoneId.of("UTC"));

  private StringUtils() {
    // Prevent instantiation.
  }

  /**
   * Converts an epoch UTC timestamp to a formatted UCT date for a given locale.
   *
   * @param timestampMs the epoch timestamp to convert
   * @param locale the locale to represent the text in
   * @return the MMMM dd, YYYY representation of the timestamp as a UTC date
   */
  public static String epochTimestampToMediumUTCDateString(long timestampMs, Locale locale) {
    return SHORT_FORMAT.withLocale(locale).format(Instant.ofEpochMilli(timestampMs));
  }

}
