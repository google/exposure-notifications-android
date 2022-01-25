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

package com.google.android.apps.exposurenotification.utils;

public final class RequestCodes {

  public static final int REQUEST_CODE_UNKNOWN = -1;
  public static final int REQUEST_CODE_START_EXPOSURE_NOTIFICATION = 1111;
  public static final int REQUEST_CODE_GET_TEMP_EXPOSURE_KEY_HISTORY = 2222;
  public static final int REQUEST_CODE_PREAUTHORIZE_TEMP_EXPOSURE_KEY_RELEASE = 3333;
  public static final int REQUEST_CODE_APP_UPDATE = 4444;

  private RequestCodes() {
  }
}
