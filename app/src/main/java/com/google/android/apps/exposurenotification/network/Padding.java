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

import androidx.annotation.VisibleForTesting;
import com.google.android.apps.exposurenotification.common.StringUtils;
import com.google.android.apps.exposurenotification.keyupload.ApiConstants.UploadV1;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * A utility for generating randomly sized base64 strings of random sizes within a range, useful as
 * padding in RPC requests to (somewhat) frustrate network observers.
 */
public class Padding {

  // To confound network observers, we pad out each request to this size.
  // The actual payload size may not be exactly this, but it's not important that the size be exact,
  // only that it be consistent from request to request.
  @VisibleForTesting
  static final int TARGET_PAYLOAD_SIZE_BYTES = 5000;

  public static JSONObject addPadding(JSONObject payload) throws JSONException {
    // Because the ultimate size of the JSON depends on some things not known here, we start small
    // and scale up until we reach the target size.
    int currentSize = payload.toString().getBytes().length;
    int paddingSize = 1;
    // If we're already big enough, this will get skipped, no padding field will be added.
    while (currentSize < TARGET_PAYLOAD_SIZE_BYTES) {
      payload.put(UploadV1.PADDING, StringUtils.randomBase64Data(paddingSize));
      currentSize = payload.toString().getBytes().length;
      paddingSize++;
    }
    return payload;
  }
}
