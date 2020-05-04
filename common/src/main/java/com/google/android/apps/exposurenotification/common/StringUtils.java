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

import android.content.Intent;
import android.net.Uri;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.View;
import android.widget.TextView;
import java.util.Locale;
import org.threeten.bp.Instant;
import org.threeten.bp.ZoneId;
import org.threeten.bp.format.DateTimeFormatter;

/**
 * Simple util class for manipulating strings.
 */
public final class StringUtils {

  private static final DateTimeFormatter LONG_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss_SSS zzz").withZone(ZoneId.of("UTC"));
  private static final DateTimeFormatter SHORT_FORMAT =
      DateTimeFormatter.ofPattern("MMMM dd, YYYY").withZone(ZoneId.of("UTC"));

  private StringUtils() {
    // Prevent instantiation.
  }

  public static String timestampMsToString(long timestampMs) {
    return LONG_FORMAT.format(Instant.ofEpochMilli(timestampMs));
  }

  public static String timestampMsToMediumString(long timestampMs, Locale locale) {
    return SHORT_FORMAT.withLocale(locale).format(Instant.ofEpochMilli(timestampMs));
  }

  /**
   * Appends a clickable learn more link to the end of the text view specified.
   */
  public static void appendLearnMoreLink(TextView textView, String href) {
    ClickableSpan clickableSpan =
        new ClickableSpan() {
          @Override
          public void onClick(View widget) {
            textView.getContext()
                .startActivity(new Intent(Intent.ACTION_VIEW).setData(Uri.parse(href))
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
          }
        };
    String learnMoreText = textView.getContext().getString(R.string.learn_more);
    SpannableString learnMoreSpannable = new SpannableString(learnMoreText);
    learnMoreSpannable.setSpan(
        clickableSpan, 0, learnMoreText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    textView.setText(
        TextUtils.concat(textView.getText(), "\n", learnMoreSpannable));
    textView.setMovementMethod(LinkMovementMethod.getInstance());
  }

  /**
   * Finds a substring in a long string, attach a link to it, and set the entire string in a
   * TextView to display.
   */
  public static void linkifyTextView(TextView textView, String fullText, String linkText,
      String href) {
    if (TextUtils.isEmpty(fullText) || TextUtils.isEmpty(linkText) || TextUtils.isEmpty(href)) {
      textView.setText(fullText);
      return;
    }
    int startIndex = fullText.indexOf(linkText);
    if (startIndex < 0) {
      textView.setText(fullText);
      return;
    }
    ClickableSpan clickableSpan =
        new ClickableSpan() {
          @Override
          public void onClick(View widget) {
            textView.getContext()
                .startActivity(new Intent(Intent.ACTION_VIEW).setData(Uri.parse(href))
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
          }
        };

    SpannableString linkSpannable = new SpannableString(fullText);
    linkSpannable.setSpan(
        clickableSpan, startIndex, linkText.length() + startIndex,
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    textView.setText(linkSpannable);
    textView.setMovementMethod(LinkMovementMethod.getInstance());
  }
}
