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

package com.google.android.apps.exposurenotification.network;

import com.android.volley.VolleyError;
import java.nio.charset.StandardCharsets;

/** Static utils to ease common tasks dealing with Volley requests & responses. */
public class VolleyUtils {

  // Prevent instantiation.
  private VolleyUtils(){}

  public static int getHttpStatus(VolleyError err, int defaultStatus) {
    return err.networkResponse == null ? defaultStatus : err.networkResponse.statusCode;
  }

  public static String getErrorMessage(VolleyError err, String defaultMsg) {
    return (err.networkResponse == null || err.networkResponse.data == null)
        ? defaultMsg
        : new String(err.networkResponse.data, StandardCharsets.UTF_8);
  }
}
