// Copyright 2020 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.android.apps.exposurenotification.privateanalytics;

import android.util.Log;
import com.google.android.apps.exposurenotification.proto.CreatePacketsParameters;
import com.google.android.apps.exposurenotification.proto.CreatePacketsResponse;
import com.google.android.apps.exposurenotification.proto.ResponseStatus.StatusCode;

/**
 * A class for generating encrypted packets to be dispatched to ingestion servers for further
 * analytics.
 */
public class PrioJni implements Prio {

  private static final String TAG = "PrioJni";

  static {
    System.loadLibrary("prioclient");
  }

  PrioJni() {
  }

  private static native byte[] createPackets(byte[] paramsBytes);

  @Override
  public CreatePacketsResponse getPackets(CreatePacketsParameters params) {
    byte[] responseBytes = createPackets(params.toByteArray());
    try {
      CreatePacketsResponse createPacketsResponse = CreatePacketsResponse.parseFrom(responseBytes);
      Log.d(TAG, "Response Status: " + createPacketsResponse.getResponseStatus().getStatusCode());
      if (createPacketsResponse.getResponseStatus().getStatusCode() != StatusCode.OK) {
        Log.w(TAG, "Error when creating packets: " + createPacketsResponse.getResponseStatus()
            .getErrorDetails());
      }
      return createPacketsResponse;
    } catch (Exception e) {
      Log.w(TAG, "Unable to parse responseBytes");
    }
    return null;
  }
}
