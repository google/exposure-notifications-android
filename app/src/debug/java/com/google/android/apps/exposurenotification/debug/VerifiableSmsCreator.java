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

package com.google.android.apps.exposurenotification.debug;

import com.google.android.apps.exposurenotification.BuildConfig;
import com.google.android.apps.exposurenotification.common.logging.Logger;
import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.android.apps.exposurenotification.debug.VerificationCodeCreator.VerificationCode;
import com.google.common.io.BaseEncoding;
import java.util.Locale;
import java.util.Objects;
import org.threeten.bp.ZoneId;
import org.threeten.bp.ZonedDateTime;
import org.threeten.bp.format.DateTimeFormatter;

/*
 * Class to generate verifiable SMS to test GMSCore's SMS verification
 */
public class VerifiableSmsCreator {
  private static final Logger logger = Logger.getLogger("VerifiableSmsCreator");

  private static final String AUTHENTICATION_PREFIX = "Authentication:";
  private static final String FULL_MESSAGE_FORMAT = "EN Report.%s.%s.%s" + AUTHENTICATION_PREFIX;

  private static final DateTimeFormatter TIME_FORMAT_SMS_SIGNATURE_CONTENT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US).withZone(ZoneId.of("UTC"));
  private static final DateTimeFormatter TIME_FORMAT_SMS_MESSAGE =
      DateTimeFormatter.ofPattern("MMdd", Locale.US).withZone(ZoneId.of("UTC"));

  Clock clock;
  KeyFileSigner keyFileSigner;

  public VerifiableSmsCreator(Clock clock) {
    this.clock = clock;
    this.keyFileSigner = KeyFileSigner.get();
  }

  /**
   * Generate a verifiedSms based on a verificationCode. Please note that:
   * - Each SMS can only be used once. EN_modules stores the link, if it was seen before,
   *   the SMS will not be processed a second time
   */
  public String craftVerifiableSmsFromVerificationCode(String phoneNumber,
      VerificationCode verificationCode) {
    // Note: The link must be in the form "https://.en.express/"
    String messageBody = "Exposure Notifications Verification code: "
        + "https://" + BuildConfig.APP_LINK_HOST + "/v?c=" + verificationCode.code()
        + " Expires in 24 hours"
        + " ";

    // Create signatures with EN_modules data structure
    SmsDataImpl smsData = new SmsDataImpl(phoneNumber, messageBody);

    // Craft the full SMS text based on the signature data
    ZonedDateTime smsServerDate = clock.now().atZone(ZoneId.of("UTC"));
    String smsMessageDate = TIME_FORMAT_SMS_MESSAGE.format(smsServerDate);
    String signature = smsData.getSignature();

    String sms = messageBody
        + AUTHENTICATION_PREFIX
        + smsMessageDate + ":"
        + smsData.getPublicKeyName() + ":"
        + signature;

    // Print all relevant signature info to logcat
    String publicKey =
        BuildConfig.APPLICATION_ID
            + ":" + smsData.getPublicKeyName()
            + "," + keyFileSigner.getPublicKeyBase64();
    logger.d("Public key / content of the phenotype flag: " + publicKey);
    logger.d("SmsData.getPublicKeyName: " + smsData.getPublicKeyName());
    logger.d("SmsData.getContentForSignature: " + smsData.getContentForSignature());
    logger.d("SmsData.getSignature: " + signature);
    logger.d("Final verifiableSms: " + sms);

    return sms;
  }

  /**
   * SmsData is EN_modules's interface that collects all the data needed for SMS verification.
   * On EN_modules's side, the SMS will be converted back into this representation to
   * verify the signature. The data that we generate here and that EN_modules uses for
   * signature verification needs to match bit-by-bit. For ease of debugging, we use the same
   * representation.
   */
  private class SmsDataImpl {
    private final String phoneNumber;
    private final String messageBody;

    public SmsDataImpl(String phoneNumber, String messageBody) {
      this.phoneNumber = phoneNumber;
      this.messageBody = messageBody;
    }

    /**
     * Create the actual signature. Please note that it is not created on basis of the
     * message content alone, but on the basis of the string from getContentForSignature();
     */
    public String getSignature() {
      byte[] smsSignatureBytes = keyFileSigner.sign(
          Objects.requireNonNull(getContentForSignature()).getBytes());
      return BaseEncoding.base64().encode(smsSignatureBytes);
    }

    /**
     * In practice EN_module will extract this directly from the SMS.
     * For our E2E testing with individually-generated keypairs, we agreed on the keyName-convention
     * "<keyId/>-<keyVersion/>".
     */
    public String getPublicKeyName() {
      return keyFileSigner.signatureInfo().getVerificationKeyId()
          + "-"
          + keyFileSigner.signatureInfo().getVerificationKeyVersion();
    }

    /**
     * Create the string that is actually used for the signature. In addition to the smsBody,
     * it contains the phone number and a complete date (including year).
     */
    public String getContentForSignature() {
      ZonedDateTime smsServerDate = clock.now().atZone(ZoneId.of("UTC"));
      String smsDateString = TIME_FORMAT_SMS_SIGNATURE_CONTENT.format(smsServerDate);
      return String.format(
          Locale.ENGLISH, FULL_MESSAGE_FORMAT, phoneNumber, smsDateString, messageBody);
    }
  }
}
