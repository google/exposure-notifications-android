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

package com.google.android.apps.exposurenotification.keyupload;

import android.content.res.Resources;
import androidx.annotation.NonNull;
import com.android.volley.VolleyError;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.keyupload.ApiConstants.UploadV1;
import com.google.android.apps.exposurenotification.keyupload.ApiConstants.VerifyV1;
import com.google.android.apps.exposurenotification.network.VolleyUtils;
import org.json.JSONObject;

/**
 * Type of errors that the server communication may encounter. The errors are derived from two
 * sources:
 *
 * <ul>
 *   <li>HTTP status code</li>
 *   <li>Server returned error code (https://github.com/google/exposure-notifications-verification-server/blob/main/pkg/api/api.go)</li>
 * </ul>
 * <p>
 * Note that the errors here are not one-one mapping from the sources above. Instead each error may
 * represent more than one underlying error code as long as the error messaging and handling are to
 * be the same. This type focuses on providing the abstraction such that `View` can directly act on.
 */
public enum UploadError {
  UNKNOWN,

  // Cause: serverErrorCode = code_invalid | code_not_found | code_user_unauthorized
  // Action: Show "invalid code" error message to user
  CODE_INVALID,

  // Cause: serverErrorCode = code_expired
  // Action: Show "code expired" error message to user
  CODE_EXPIRED,

  // Cause: serverErrorCode = unsupported_test_type
  // Action: Ask user to update app
  UNSUPPORTED_TEST_TYPE,

  // Cause:
  //   serverErrorCode = invalid_test_type | token_invalid | token_expired | hmac_invalid
  //                      | missing_date | invalid_date | missing_phone
  //   HTTP status = 401 | 403 | 404 | 405
  // Action: show a "generic" app internal error message
  APP_ERROR,

  // Cause: TODO
  // Action: show a "generic" server error message
  SERVER_ERROR,

  // Cause: HTTP status = 429 (5??)
  // Action: Retry after cool-down. TODO: whether to show error message?
  RATE_LIMITED,

  // Cause: HTTP status = 401 (perhaps, a Verification Server API key revocation issue?)
  // Action: Check if there is an app update available. If yes, prompt user to update the app.
  //         Otherwise, show a "generic" server error message.
  UNAUTHORIZED_CLIENT;

  public static UploadError fromVolleyError(VolleyError error) {
    if (error.networkResponse == null) {
      return UNKNOWN;
    }

    String errorCode = getErrorCode(error);

    switch (error.networkResponse.statusCode) {
      case 400:
        switch (errorCode) {
          // --------------------------------------------------------------------
          // Keyserver error codes.
          // --------------------------------------------------------------------
          case UploadV1.Error.UNKNOWN_APP:
          case UploadV1.Error.HA_CONFIG_LOAD_FAIL:
          case UploadV1.Error.HA_REGION_CONFIG:
            // Even though the keyserver thinks "bad cert" is the app's fault, we got the cert from
            // the verification server, so we think it's the verification server's fault.
          case UploadV1.Error.CERT_INVALID:
          case UploadV1.Error.INTERNAL_ERROR:
            return SERVER_ERROR;
          case UploadV1.Error.BAD_REQUEST:
          case UploadV1.Error.MISSING_REVISION_TOKEN:
          case UploadV1.Error.INVALID_REVISION_TOKEN:
          case UploadV1.Error.KEY_ALREADY_REVISED:
          case UploadV1.Error.INVALID_REVISION_TRANSITION:
            return APP_ERROR;

          // --------------------------------------------------------------------
          // Verification server error codes.
          // --------------------------------------------------------------------
          case VerifyV1.Error.CODE_INVALID:
          case VerifyV1.Error.CODE_NOT_FOUND:
          case VerifyV1.Error.CODE_USER_UNAUTHORIZED:
          case VerifyV1.Error.TOKEN_INVALID:
            return CODE_INVALID;
          case VerifyV1.Error.CODE_EXPIRED:
            // If the long term token expires, the user has to start at the beginning again with a
            // new code, so we use the same error as "code expired".
          case VerifyV1.Error.TOKEN_EXPIRED:
            return CODE_EXPIRED;
          case VerifyV1.Error.UNSUPPORTED_TEST_TYPE:
            return UNSUPPORTED_TEST_TYPE;
          case VerifyV1.Error.INVALID_TEST_TYPE:
          case VerifyV1.Error.HMAC_INVALID:
          case VerifyV1.Error.MISSING_DATE:
          case VerifyV1.Error.INVALID_DATE:
          case VerifyV1.Error.MISSING_PHONE:
          case VerifyV1.Error.MISSING_NONCE:
          default:
            return APP_ERROR;
        }
      case 401:
        return UNAUTHORIZED_CLIENT;
      case 403:
      case 404:
      case 405:
        return APP_ERROR;
      case 429:
        return RATE_LIMITED;
      case 500:
        return SERVER_ERROR;
      default:
        return UNKNOWN;
    }
  }

  @NonNull
  private static String getErrorCode(VolleyError error) {
    JSONObject errorBody = VolleyUtils.getErrorBodyWithoutPadding(error);
    // The two servers use different JSON keys for the error code.
    if (errorBody.has(VerifyV1.ERR_CODE)) {
      return errorBody.optString(VerifyV1.ERR_CODE);
    }
    return errorBody.optString(UploadV1.ERR_CODE);
  }

  @NonNull
  public String getErrorMessage(Resources resources, boolean isSelfReportFlow) {
    switch (this) {
      case CODE_INVALID:
        return isSelfReportFlow ? resources.getString(R.string.self_report_bad_code)
            : resources.getString(R.string.network_error_code_invalid);
      case CODE_EXPIRED:
        return resources.getString(R.string.network_error_code_expired,
            resources.getString(R.string.error_agency_name));
      case UNSUPPORTED_TEST_TYPE:
        return resources.getString(R.string.network_error_unsupported_test_type);
      case SERVER_ERROR:
        return resources.getString(R.string.network_error_server_error);
      case UNAUTHORIZED_CLIENT:
        return resources.getString(R.string.try_again_later_error_message);
      case UNKNOWN:
      case APP_ERROR:
      default:
        return resources.getString(R.string.generic_error_message);
    }
  }
}
