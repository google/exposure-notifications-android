/*
 * Copyright 2021 Google LLC
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

package com.google.android.apps.exposurenotification.utils;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.text.style.URLSpan;
import android.view.View;
import android.widget.TextView;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.logging.Logger;
import com.google.android.material.snackbar.Snackbar;

/**
 * Simple util class for manipulating URLs.
 */
public final class UrlUtils {

  private static final Logger logger = Logger.getLogger("UrlUtils");
  private static final String ALLOWED_SCHEME = "https";

  /**
   * Attempts to parse {@link Uri} from the provided URL String and then open it. Error cases will
   * post a {@link Snackbar}.
   *
   * @param view {@link View} to attach a {@link Snackbar to}
   * @param url  URL String
   */
  public static void openUrl(View view, String url) {
    Uri uri = Uri.parse(url);

    if (!ALLOWED_SCHEME.equals(uri.getScheme())) {
      Uri.Builder uriBuilder = uri.buildUpon();
      uriBuilder.scheme(ALLOWED_SCHEME);
      uri = uriBuilder.build();
    }

    try {
      Intent i = new Intent(Intent.ACTION_VIEW);
      i.setData(uri);
      view.getContext().startActivity(i);
    } catch (ActivityNotFoundException e) {
      Snackbar.make(view, R.string.browser_unavailable_error, Snackbar.LENGTH_LONG).show();
    } catch (Exception e) {
      // This might fail if the URL provided by the HA can not be parsed.
      logger.e(
          String.format("Exception while launching ACTION_VIEW with URL %s", uri.toString()), e);
      Snackbar.make(view, R.string.generic_error_message, Snackbar.LENGTH_LONG).show();
    }
  }

  /**
   * Creates a {@link URLSpan} that has better error handling by using a {@link Snackbar} error
   * message when no browser is available.
   */
  public static URLSpan createURLSpan(String url) {
    return new URLSpan(url) {
      @Override
      public void onClick(View widget) {
        openUrl(widget, getURL());
      }
    };
  }

  private UrlUtils() {
    // Prevent instantiation.
  }
}
