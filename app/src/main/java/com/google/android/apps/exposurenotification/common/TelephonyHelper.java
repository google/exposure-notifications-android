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

package com.google.android.apps.exposurenotification.common;

import android.Manifest.permission;
import android.annotation.SuppressLint;
import android.content.res.Resources;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import androidx.annotation.RequiresPermission;
import androidx.annotation.VisibleForTesting;
import com.google.android.apps.exposurenotification.common.logging.Logger;
import com.google.common.base.Optional;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;
import java.util.Locale;
import java.util.Objects;
import javax.annotation.Nullable;

/** Helper class related to Phone number. */
public class TelephonyHelper {
  private static final Logger logger = Logger.getLogger("TelephonyHelper");

  private final TelephonyManager telephonyManager;
  private final Resources resources;

  TelephonyHelper(TelephonyManager telephonyManager, Resources resources) {
    this.telephonyManager = telephonyManager;
    this.resources = resources;
  }

  // Used as a default country code when all the sources of country data have failed.
  @VisibleForTesting
  static final String US_ISO = "US";

  /**
   * Normalizes a phone number to standard E.164.
   *
   * @param number A non-normalized phone number
   * @return A string with the phone number formatted according to E.164
   */
  @Nullable
  public String normalizePhoneNumber(@Nullable String number) {
    if (number == null) {
      return null;
    }
    final String strippedNumber = PhoneNumberUtils.stripSeparators(number);
    if (strippedNumber == null) {
      return null;
    }

    String normalizedNumber =
        PhoneNumberUtils.formatNumberToE164(strippedNumber, getCurrentCountryIso());
    if (TextUtils.isEmpty(normalizedNumber)) {
      normalizedNumber = strippedNumber;
    }

    return normalizedNumber;
  }

  /**
   * Query the phone's number from telephony.
   *
   * @return Phone number if available (e.g. device contains a sim card) and READ_PHONE_STATE
   * permission granted, Optional.absend() otherwise.
   */
  @RequiresPermission(permission.READ_PHONE_STATE)
  @SuppressLint({"HardwareIds"})
  public Optional<String> maybeGetPhoneNumber() {
    String phoneNumber = null;
    try {
      if (telephonyManager == null) {
        logger.d("No TelephonyManager system service");
        return Optional.absent();
      }
      phoneNumber = normalizePhoneNumber(telephonyManager.getLine1Number());
    } catch (SecurityException e) {
      logger.d("No permission to read phone number");
    }
    return TextUtils.isEmpty(phoneNumber) ? Optional.absent() : Optional.of(phoneNumber);
  }

  /**
   * Retrieves the user's country code from the following sources
   * (in order with highest preference first, first non-empty result will be used)
   * - Sim based country
   * - Network based country
   * - Resources locale
   * - Default locale
   * - Fallback if no other source returns a country: US_ISO
   *
   * @return best available information on the user's country code
   */
  @VisibleForTesting
  String getCurrentCountryIso() {
    String result = getSimBasedCountryIso();
    if (TextUtils.isEmpty(result)) {
      result = getNetworkBasedCountryIso();
    }
    if (TextUtils.isEmpty(result)) {
      result = getResourceLocaleBasedCountryIso();
    }
    if (TextUtils.isEmpty(result)) {
      result = getDefaultLocaleBasedCountryIso();
    }
    if (TextUtils.isEmpty(result)) {
      return US_ISO;
    }
    return Objects.requireNonNull(result).toUpperCase(Locale.US);
  }

  @Nullable
  private String getSimBasedCountryIso() {
    return telephonyManager != null ? telephonyManager.getSimCountryIso() : null;
  }

  @Nullable
  private String getNetworkBasedCountryIso() {
    return telephonyManager != null ? telephonyManager.getNetworkCountryIso() : null;
  }

  private String getResourceLocaleBasedCountryIso() {
    return resources.getConfiguration().locale.getCountry();
  }

  private String getDefaultLocaleBasedCountryIso() {
    final Locale defaultLocale = Locale.getDefault();
    return defaultLocale.getCountry();
  }

  /**
   * Checks if the given phoneNumber is a valid phone number.
   * For validation, this method retrieves the user's country code from the following sources
   * (in order with highest preference first, first non-empty result will be used)
   * - Sim based country
   * - Network based country
   * - Resources locale
   * - Default locale
   * - Fallback if no other source returns a country: US_ISO
   *
   * @param phone       user-provided phone number
   * @return true if the number is valid for the user's locale, false otherwise
   *
   */
  public boolean isValidPhoneNumber(@Nullable String phone) {
    if (TextUtils.isEmpty(phone)) {
      return false;
    }
    String countryCode = getCurrentCountryIso();
    final PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();
    try {
      PhoneNumber phoneNumber = phoneNumberUtil.parse(phone, countryCode);
      return phoneNumberUtil.isValidNumber(phoneNumber);
    } catch (NumberParseException e) {
      return false;
    }
  }
}
