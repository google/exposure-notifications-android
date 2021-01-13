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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.android.volley.NetworkError;
import com.android.volley.NoConnectionError;
import com.android.volley.ParseError;
import com.android.volley.TimeoutError;
import com.android.volley.VolleyError;
import com.google.android.apps.exposurenotification.keyupload.ApiConstants.UploadV1;
import com.google.android.apps.exposurenotification.keyupload.ApiConstants.VerifyV1;
import com.google.android.apps.exposurenotification.proto.RpcCall.RpcCallResult;
import java.nio.charset.StandardCharsets;
import org.json.JSONException;
import org.json.JSONObject;

/** Static utils to ease common tasks dealing with Volley requests & responses. */
public class VolleyUtils {

  private static final int DEFAULT_HTTP_STATUS = 0;
  private static final String DEFAULT_ERROR_CODE = "error_code_not_parsed";
  private static final String DEFAULT_ERROR_MESSAGE = "Call failed, unknown reason.";

  // Prevent instantiation.
  private VolleyUtils(){}

  /**
   * Returns the HTTP status code from {@code error} if it can be read, otherwise zero.
   */
  public static int getHttpStatus(@Nullable Throwable error) {
    if (!(error instanceof VolleyError)) {
      return DEFAULT_HTTP_STATUS;
    }
    VolleyError volleyError = (VolleyError) error;
    if (volleyError.networkResponse == null) {
      return DEFAULT_HTTP_STATUS;
    }
    return volleyError.networkResponse.statusCode;
  }

  /**
   * Returns the machine-readable error code from {@code error} if it can be parsed, otherwise a
   * default error code.
   */
  @NonNull
  public static String getErrorCode(@Nullable Throwable error) {
    if (!(error instanceof VolleyError)) {
      return DEFAULT_ERROR_CODE;
    }
    VolleyError volleyError = (VolleyError) error;
    JSONObject errorBody = getErrorBodyWithoutPadding(volleyError);
    // The two servers use different JSON keys for the error code.
    if (errorBody.has(VerifyV1.ERR_CODE)) {
      return errorBody.optString(VerifyV1.ERR_CODE);
    }
    return errorBody.optString(UploadV1.ERR_CODE, DEFAULT_ERROR_CODE);
  }

  /**
   * Returns the human-readable error message from {@code error} if it can be parsed, otherwise a
   * default error message.
   */
  @NonNull
  public static String getErrorMessage(@Nullable Throwable error) {
    if (!(error instanceof VolleyError)) {
      return DEFAULT_ERROR_MESSAGE;
    }
    VolleyError volleyError = (VolleyError) error;
    JSONObject errorBody = getErrorBodyWithoutPadding(volleyError);
    // The two servers happen to use the same JSON key for error message (unlike error code).
    return errorBody.optString(VerifyV1.ERR_MESSAGE, DEFAULT_ERROR_MESSAGE);
  }

  /**
   * Returns the {@link RpcCallResult} for logging.
   */
  @NonNull
  public static RpcCallResult getLoggableResult(@Nullable Throwable error) {
    if (error == null) {
      // Can't say much more specific with a null error.
      return RpcCallResult.RESULT_FAILED_UNKNOWN;
    }
    if (error instanceof TimeoutError) {
      return RpcCallResult.RESULT_FAILED_TIMEOUT;
    }
    if (error instanceof ParseError) {
      return RpcCallResult.RESULT_FAILED_PARSING;
    }
    if (error instanceof NoConnectionError) {
      return RpcCallResult.RESULT_FAILED_NO_CONNECTION;
    }
    if (error instanceof NetworkError) {
      // From inspecting the Volley code, looks like NetworkError indicates Volley failed to capture
      // the HTTP response bytes.
      return RpcCallResult.RESULT_FAILED_NETWORK_ERROR;
    }

    // Other kinds of VolleyError are ServerError, ClientError, and AuthFailureError. We log these
    // 400 and 500 class errors based on HTTP status code instead of Volley exception types.
    int httpStatus = getHttpStatus(error);
    if (httpStatus / 100 == 5) {
      return RpcCallResult.RESULT_FAILED_GENERIC_5XX;
    }
    if (httpStatus / 100 == 4) {
      return RpcCallResult.RESULT_FAILED_GENERIC_4XX;
    }

    // Default "unknown" error.
    return RpcCallResult.RESULT_FAILED_UNKNOWN;
  }

  /**
   * Returns the {@link JSONObject} parsed from the body of {@code error}, or an empty {@link
   * JSONObject} if body is absent or failed to parse.
   *
   * <p>If a {@code padding} field is present in the response, we remove it.
   */
  @NonNull
  public static JSONObject getErrorBodyWithoutPadding(@Nullable Throwable error) {
    if (!(error instanceof VolleyError)) {
      return new JSONObject();
    }
    VolleyError volleyError = (VolleyError) error;
    if (volleyError.networkResponse == null || volleyError.networkResponse.data == null) {
      return new JSONObject();
    }

    try {
      JSONObject body =
          new JSONObject(new String(volleyError.networkResponse.data, StandardCharsets.UTF_8));
      // Both servers use the same key for padding.
      if (body.has(VerifyV1.PADDING)) {
        body.remove(VerifyV1.PADDING);
      }
      return body;
    } catch (JSONException e) {
      return new JSONObject();
    }
  }
}
