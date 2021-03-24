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

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

/**
 * Simple util class for manipulating URLs.
 */
public final class UrlUtils {

  private static final String TAG = "UrlUtils";
  private static final String ALLOWED_SCHEME = "https";

  /**
   * Attempts to parse {@link Uri} from the provided URL String and then open it.
   *
   * @param context Application context.
   * @param url URL String provided by the HA.
   */
  public static void openUrl(Context context, String url) {
    Uri uri = Uri.parse(url);

    if (!ALLOWED_SCHEME.equals(uri.getScheme())) {
      Uri.Builder uriBuilder = uri.buildUpon();
      uriBuilder.scheme(ALLOWED_SCHEME);
      uri = uriBuilder.build();
    }

    try {
      Intent i = new Intent(Intent.ACTION_VIEW);
      i.setData(uri);
      context.startActivity(i);
    } catch (Exception e) {
      /*
       * This might fail either if the user does not have an App to handle the intent (Browser)
       * or the URL provided by the HA can not be parsed. In these cases we don't show an error to
       * the user, but log it.
       */
      Log.e(TAG, String.format("Exception while launching ACTION_VIEW with URL %s", uri.toString()),
          e);
    }
  }

  private UrlUtils() {
    // Prevent instantiation.
  }
}
