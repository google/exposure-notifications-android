/*
 * Copyright 2021 Google LLC
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

import androidx.annotation.Nullable;
import com.google.auto.value.AutoValue;
import org.threeten.bp.LocalDate;

/**
 * A value class to carry data through the very first part of the self-report verification flow,
 * which is to request a verification code.
 *
 * <p>We use this class for both inputs and outputs of the
 * {@link UploadController#requestCode(UserReportUpload userReportUpload)} because the self-report
 * part of the verification flow and its parameters may change frequently, and this request/response
 * object makes it easy to alter/extend the parameters and return values without changing method
 * signatures.
 *
 * <p> See a similar {@link Upload} class to carry data through the diagnosis verification and
 * key upload flow, from start to finish.
 */
@AutoValue
public abstract class UserReportUpload {

  /**
   * Date of the COVID-19 test the user requests a verification code for.
   */
  @Nullable public abstract LocalDate testDate();

  /**
   * Phone number to send the SMS to. Must be in a E164 format.
   */
  @Nullable public abstract String phoneNumber();

  /**
   * Must be exactly 256 bytes of random data and base64 encoded. This same nonce must be passed
   * later when sending a verification code to the verification server.
   */
  @Nullable public abstract String nonceBase64();

  /**
   * RFC1123 formatted string timestamp at which the requested verification code expires.
   */
  @Nullable public abstract String expiresAt();

  /**
   * Offset in minutes of the user's timezone. Positive, negative, 0, or omitted (using the default
   * of 0) are all valid. 0 is considered to be UTC.
   */
  public abstract long tzOffsetMin();

  /**
   * Unix timestamp (in seconds) at which the requested verification code expires.
   */
  public abstract long expiresAtTimestampSec();

  /**
   * A boolean flag to indicate if this is a chaff (i.e. fake) request.
   */
  public abstract boolean isCoverTraffic();

  /**
   * Every {@link UserReportUpload} for a self-report sharing flow starts with these givens.
   */
  public static UserReportUpload.Builder newBuilder(
      String phoneNumber, String nonceBase64, LocalDate testDate, long tzOffsetMin) {
    return new AutoValue_UserReportUpload.Builder()
        .setTestDate(testDate)
        .setTzOffsetMin(tzOffsetMin)
        .setPhoneNumber(phoneNumber)
        .setNonceBase64(nonceBase64)
        .setExpiresAtTimestampSec(0)
        .setIsCoverTraffic(false);
  }

  public abstract UserReportUpload.Builder toBuilder();

  @AutoValue.Builder
  public abstract static class Builder {

    public abstract UserReportUpload.Builder setTestDate(LocalDate date);

    public abstract UserReportUpload.Builder setPhoneNumber(String phoneNumber);

    public abstract UserReportUpload.Builder setNonceBase64(String nonce);

    public abstract UserReportUpload.Builder setExpiresAt(String expiresAt);

    public abstract UserReportUpload.Builder setTzOffsetMin(long tzOffset);

    public abstract UserReportUpload.Builder setExpiresAtTimestampSec(long expiresAtTimestampSec);

    public abstract UserReportUpload.Builder setIsCoverTraffic(boolean isFake);

    public abstract UserReportUpload build();
  }

}
