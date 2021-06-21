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

import static android.text.format.DateUtils.DAY_IN_MILLIS;

import android.content.Context;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.format.DateUtils;
import android.text.style.URLSpan;
import android.util.Log;
import androidx.annotation.VisibleForTesting;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.riskcalculation.ExposureClassification;
import com.google.common.io.BaseEncoding;
import java.security.SecureRandom;
import java.text.DateFormat;
import java.util.Locale;
import java.util.TimeZone;
import org.threeten.bp.Duration;
import org.threeten.bp.Instant;
import org.threeten.bp.LocalDate;
import org.threeten.bp.ZoneId;
import org.threeten.bp.ZoneOffset;
import org.threeten.bp.ZonedDateTime;
import org.threeten.bp.format.DateTimeFormatter;

/**
 * Simple util class for manipulating strings.
 */
public final class StringUtils {
  private static final String TAG = "StringUtils";

  private static final SecureRandom RAND = new SecureRandom();
  private static final BaseEncoding BASE64 = BaseEncoding.base64();

  private static final DateTimeFormatter LONG_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd, HH:mm:ss z").withZone(ZoneId.of("UTC"));

  public static final String ELLIPSIS = "\u2026";

  private StringUtils() {
    // Prevent instantiation.
  }

  /**
   * Converts an epoch UTC timestamp to a formatted UTC date for a given locale.
   *
   * @param timestampMs the epoch timestamp to convert
   * @param locale the locale to represent the text in
   * @return standard date format includes month and year of the timestamp as a UTC date
   */
  public static String epochTimestampToMediumUTCDateString(long timestampMs, Locale locale) {
    DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.LONG, locale);
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    return dateFormat.format(timestampMs);
  }

  /**
   * Converts an epoch UTC timestamp to a formatted UTC date for a given locale.
   *
   * @param timestampMs the epoch timestamp to convert
   * @param locale the locale to represent the text in
   * @return the yyyy-MM-dd, HH:mm:ss z representation of the timestamp as a UTC date
   */
  public static String epochTimestampToLongUTCDateTimeString(long timestampMs, Locale locale) {
    return LONG_FORMAT.withLocale(locale).format(Instant.ofEpochMilli(timestampMs));
  }

  /**
   * Converts an epoch UTC timestamp to a default zoned date-time string that is relative up to
   * yesterday (inclusively) and standard for any time earlier.
   *
   * @param timestampMs the epoch timestamp to convert
   * @param now the current instant from the system clock
   * @param zonedNow the current date-time from the system clock in the default time-zone
   * @param context the application context
   * @return "[relative time/date], [time]" zoned representation of the timestamp
   */
  public static String epochTimestampToRelativeZonedDateTimeString(
      long timestampMs, Instant now, ZonedDateTime zonedNow, Context context) {
    long transitionResolutionMs = calculateTransitionResolutionMs(now, zonedNow);
    return DateUtils
        .getRelativeDateTimeString(context, timestampMs, DAY_IN_MILLIS, transitionResolutionMs, 0)
        .toString();
  }

  /**
   * Truncates string and appends ellipses char at the end of it if string is longer than len
   *
   * @param text string to truncate
   * @param len desired length of the string
   * @return truncated string
   */
  public static String truncateWithEllipsis(String text, int len) {
    return text.length() <= len ? text : text.substring(0, Math.max(0, len - 1)) + ELLIPSIS;
  }

  public static String randomBase64Data(int numBytes) {
    byte[] bytes = new byte[numBytes];
    RAND.nextBytes(bytes);
    return BASE64.encode(bytes);
  }

  /**
   * Returns the current application name.
   *
   * @param context Application context.
   * @return application name
   */
  public static String getApplicationName(Context context)  {
    return context.getString(R.string.app_name);
  }

  /**
   * Returns the current health authority agency name.
   *
   * @param context Application context.
   * @return health authority name.
   */
  public static String getHealthAuthorityName(Context context)  {
    return context.getString(R.string.health_authority_name);
  }

  /**
   * Generates a spannable string for the given URLSpan, text, and link text.
   *
   * @param urlSpan  the actual URL.
   * @param text     text containing the link.
   * @param linkText link text.
   * @return spannable string.
   */
  public static SpannableString generateTextWithHyperlink(URLSpan urlSpan, String text,
      String linkText) {
    SpannableString spannableString = new SpannableString(text);
    int linkStartIdx = text.indexOf(linkText);
    spannableString.setSpan(urlSpan, linkStartIdx, linkStartIdx + linkText.length(),
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    return spannableString;
  }

  /**
   * Calculates transition resolution at which to stop reporting timestamps as relative, so that
   * the only relative measurements we get are for 'Today' and 'Yesterday'. This transition
   * resolution is equal to time elapsed since yesterday's midnight (in milliseconds).
   */
  @VisibleForTesting
  static long calculateTransitionResolutionMs(Instant now, ZonedDateTime zonedNow) {
    Instant midnight = zonedNow.toLocalDate().atStartOfDay(zonedNow.getZone()).toInstant();
    Duration timeSinceYesterdayMidnight = Duration.between(midnight, now).plus(Duration.ofDays(1));
    return timeSinceYesterdayMidnight.toMillis();
  }

  /**
   * Creates the "x days" string informing the user about the date of a possible exposure.
   * Uses a ExposureClassification and the current time to calculate x.
   */
  public static String daysFromStartOfExposure(ExposureClassification exposureClassification,
      Instant nowUTC, Context context) {
    Instant exposureStartOfDayUTC = LocalDate
        .ofEpochDay(exposureClassification.getClassificationDate())
        .atStartOfDay()
        .toInstant(ZoneOffset.UTC);
    long daysFromStartOfExposure = calculateDaysFromStartOfExposure(exposureStartOfDayUTC, nowUTC);
    return context.getResources()
        .getQuantityString(R.plurals.days_from_start_of_exposure, (int) daysFromStartOfExposure,
        daysFromStartOfExposure);
  }

  /**
   * Calculates the x in "x days" to inform the user about the date of a possible exposure.
   * The calculation does not take into account timezones (timezone-less Instants).
   */
  @VisibleForTesting
  static long calculateDaysFromStartOfExposure(Instant exposureStartOfDayUTC, Instant nowUTC) {
    Duration timeSinceExposure = Duration.between(exposureStartOfDayUTC, nowUTC);
    if (timeSinceExposure.isNegative()) {
      Log.e(TAG, "Negative time since exposure!");
      return 1;
    }
    long daysSinceExposureRoundedDown = timeSinceExposure.toDays();
    return Math.max(1, daysSinceExposureRoundedDown);
  }

}
