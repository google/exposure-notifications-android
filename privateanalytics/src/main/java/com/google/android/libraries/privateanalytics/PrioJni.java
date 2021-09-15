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

package com.google.android.libraries.privateanalytics;

import static com.google.android.apps.exposurenotification.privateanalytics.PrioJni.createPackets;

import com.google.android.libraries.privateanalytics.proto.CreatePacketsParameters;
import com.google.android.libraries.privateanalytics.proto.CreatePacketsResponse;
import com.google.android.libraries.privateanalytics.proto.ResponseStatus;
import com.google.android.libraries.privateanalytics.proto.ResponseStatus.StatusCode;

/**
 * A class for generating encrypted packets to be dispatched to ingestion servers for further
 * analytics.
 */
public class PrioJni implements Prio {

  private static final String TAG = "PAPrioJni";
  private static final Initializer INITIALIZER_INSTANCE = new Initializer();

  private final PrivateAnalyticsLogger logger;

  public PrioJni(PrivateAnalyticsLogger.Factory loggerFactory) {
    logger = loggerFactory.create(TAG);
  }

  @Override
  public CreatePacketsResponse getPackets(CreatePacketsParameters params) {
    ResponseStatus.Builder responseStatusBuilder = ResponseStatus.newBuilder();
    if (INITIALIZER_INSTANCE.isAvailable(logger)) {
      byte[] responseBytes = createPackets(params.toByteArray());
      try {
        CreatePacketsResponse createPacketsResponse = CreatePacketsResponse
            .parseFrom(responseBytes);
        logger.d("Response Status: " + createPacketsResponse.getResponseStatus().getStatusCode());
        if (createPacketsResponse.getResponseStatus().getStatusCode() != StatusCode.OK) {
          logger.w("Error when creating packets: " + createPacketsResponse.getResponseStatus()
              .getErrorDetails());
        }
        return createPacketsResponse;
      } catch (Exception e) {
        responseStatusBuilder.setStatusCode(StatusCode.UNKNOWN_FAILURE);
        responseStatusBuilder.setErrorDetails("Unable to parse responseBytes");
        logger.w("Unable to parse responseBytes");
      }
    } else {
      responseStatusBuilder.setStatusCode(StatusCode.LIBRARY_UNAVAILABLE);
      responseStatusBuilder.setErrorDetails("Prio is not available.");
      logger.e("Prio is not available.");
    }
    // Return a CreatePacketResponse with a non-OK status code.
    CreatePacketsResponse createPacketsResponse = CreatePacketsResponse.newBuilder()
        .setResponseStatus(responseStatusBuilder.build()).build();
    return createPacketsResponse;
  }

  private static class Initializer {

    private boolean hasInitializationSucceeded = false;
    private boolean wasInitializationAttempted = false;

    // Must be called at least once before any calls to JNI libraries.
    synchronized boolean isAvailable(PrivateAnalyticsLogger logger) {
      if (!wasInitializationAttempted) {
        wasInitializationAttempted = true;
        try {
          logger.d("Loading Prio native library");
          System.loadLibrary("prioclient");
          hasInitializationSucceeded = true;
          logger.d("Prio native library loaded successfully");
        } catch (SecurityException | UnsatisfiedLinkError | NullPointerException e) {
          hasInitializationSucceeded = false;
          logger.e("Prio native library load failed.", e);
        }
      } else {
        logger.d("Prio native library load skipped; already attempted with result="
            + hasInitializationSucceeded);
      }
      return hasInitializationSucceeded;
    }
  }
}
