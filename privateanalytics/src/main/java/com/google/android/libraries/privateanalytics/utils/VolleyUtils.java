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

package com.google.android.libraries.privateanalytics.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.android.volley.VolleyError;
import java.nio.charset.StandardCharsets;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Static utils to ease common tasks dealing with Volley requests & responses.
 */
public class VolleyUtils {

  // Prevent instantiation.
  private VolleyUtils() {
  }

  /**
   * Returns the {@link JSONObject} parsed from the body of {@code error}, or an empty {@link
   * JSONObject} if body is absent or failed to parse.
   */
  @NonNull
  public static JSONObject getErrorBody(@Nullable Throwable error) {
    if (!(error instanceof VolleyError)) {
      return new JSONObject();
    }
    VolleyError volleyError = (VolleyError) error;
    if (volleyError.networkResponse == null || volleyError.networkResponse.data == null) {
      return new JSONObject();
    }

    try {
      return new JSONObject(new String(volleyError.networkResponse.data, StandardCharsets.UTF_8));
    } catch (JSONException e) {
      return new JSONObject();
    }
  }
}
