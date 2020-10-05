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

/**
 * Common class to set the Children Ids for the different views in the "error case" view flipper.
 * Based on the views defined in "viewflipper_children_error_cases.xml"
 */
public class ErrorStateViewFlipperChildren {
  private ErrorStateViewFlipperChildren() {}

  public static final int DISABLED_ERROR_CHILD = 0;
  public static final int BLE_ERROR_CHILD = 1;
  public static final int LOCATION_ERROR_CHILD = 2;
  public static final int LOCATION_BLE_ERROR_CHILD = 3;
  public static final int LOW_STORAGE_ERROR_CHILD = 4;
  public static final int END_OF_COMMON = 5;
}
