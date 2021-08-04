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

/**
 * String consts for all the JSON keys in upload and verification requests and responses.
 */
public class ApiConstants {

  public static final String CHAFF_HEADER = "X-Chaff";

  /**
   * Consts for the second version of the keyserver api, called "v1". The first API version is
   * called "v1alpha1" but only post-hoc. We didn't have API versions in the beginning.
   */
  public static class UploadV1 {

    public static final String REVISION_TOKEN = "revisionToken";
    public static final String NUM_INSERTED_EXPOSURES = "insertedExposures";
    public static final String KEY = "key";
    public static final String ROLLING_START_NUM = "rollingStartNumber";
    public static final String ROLLING_PERIOD = "rollingPeriod";
    public static final String TRANSMISSION_RISK = "transmissionRisk";
    public static final String KEYS = "temporaryExposureKeys";
    public static final String APP_PACKAGE = "healthAuthorityID";
    public static final String HMAC_KEY = "hmacKey";
    public static final String ONSET = "symptomOnsetInterval";
    public static final String TRAVELER = "traveler";
    public static final String VERIFICATION_CERT = "verificationPayload";
    public static final String PADDING = "padding";
    public static final String ERR_MESSAGE = "error";
    public static final String ERR_CODE = "code";

    public static class Error {

      public static final String UNKNOWN_APP = "unknown_health_authority_id";
      public static final String HA_CONFIG_LOAD_FAIL = "unable_to_load_health_authority";
      public static final String HA_REGION_CONFIG = "health_authority_missing_region_config";
      public static final String CERT_INVALID = "health_authority_verification_certificate_invalid";
      public static final String BAD_REQUEST = "bad_request";
      public static final String INTERNAL_ERROR = "internal_error";
      public static final String MISSING_REVISION_TOKEN = "missing_revision_token";
      public static final String INVALID_REVISION_TOKEN = "invalid_revision_token";
      public static final String KEY_ALREADY_REVISED = "key_already_revised";
      public static final String INVALID_REVISION_TRANSITION = "invalid_report_type_transition";
      public static final String PARTIAL_FAILURE = "partial_failure";

      // Prevent instantiation
      private Error() {
      }
    }

    // Prevent instantiation
    private UploadV1() {
    }
  }

  /**
   * Consts for the first version of the verification server api.
   */
  public static class VerifyV1 {
    public static final String API_KEY_HEADER = "X-API-Key";

    public static final String CSRF_TOKEN = "csrftoken";
    public static final String ONSET_DATE = "symptomDate";
    public static final String TEST_DATE = "testDate";
    public static final String TEST_TYPE = "testtype";
    public static final String PHONE = "phone";
    public static final String UUID = "uuid";
    public static final String VERIFICATION_CODE = "code";
    public static final String VERIFICATION_TOKEN = "token";
    public static final String EXPIRY_STR = "expiresAt";
    public static final String EXPIRY_TIMESTAMP = "expiresAtTimestamp";
    public static final String CLAIMED = "claimed";
    public static final String ACCEPT_TEST_TYPES = "accept";
    public static final String HMAC_KEY = "ekeyhmac";
    public static final String CERT = "certificate";
    public static final String PADDING = "padding";
    public static final String ERR_MESSAGE = "error";
    public static final String ERR_CODE = "errorCode";
    public static final String TZ_OFFSET = "tzOffset";
    public static final String NONCE = "nonce";

    public static class Error {

      public static final String UNPARSEABLE = "unparsable_request";
      public static final String INTERNAL = "internal_server_error";
      public static final String CODE_INVALID = "code_invalid";
      public static final String CODE_EXPIRED = "code_expired";
      public static final String CODE_NOT_FOUND = "code_not_found";
      public static final String CODE_USER_UNAUTHORIZED = "code_user_unauthorized";
      public static final String UNSUPPORTED_TEST_TYPE = "unsupported_test_type";
      public static final String INVALID_TEST_TYPE = "invalid_test_type";
      public static final String TOKEN_INVALID = "token_invalid";
      public static final String TOKEN_EXPIRED = "token_expired";
      public static final String HMAC_INVALID = "hmac_invalid";
      public static final String MISSING_DATE = "missing_date";
      public static final String INVALID_DATE = "invalid_date";
      public static final String MISSING_NONCE = "missing_nonce";
      public static final String MISSING_PHONE = "missing_phone";

      // Prevent instantiation
      private Error() {
      }
    }

    // Prevent instantiation
    private VerifyV1() {
    }
  }

  // Prevent instantiation
  private ApiConstants() {
  }
}
