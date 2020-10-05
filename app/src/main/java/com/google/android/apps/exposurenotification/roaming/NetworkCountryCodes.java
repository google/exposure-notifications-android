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

package com.google.android.apps.exposurenotification.roaming;

import android.telephony.TelephonyManager;
import com.google.common.base.Strings;
import androidx.annotation.Nullable;
import javax.inject.Inject;

public final class NetworkCountryCodes {

  private final TelephonyManager telephonyManager;

  @Inject
  NetworkCountryCodes(TelephonyManager telephonyManager) {
    this.telephonyManager = telephonyManager;
  }

  /**
   * Returns the current network country code (country of the current registered operator or the
   * cell nearby), or null if no mobile signals.
   */
  @Nullable
  public String getCurrentCountryCode() {
    String countryCode = telephonyManager.getNetworkCountryIso();
    // TelephonyManager.getNetworkCountryIso() returns empty string when nothing available thus we
    // convert it to null.
    if (Strings.isNullOrEmpty(countryCode)) {
      return null;
    }
    return countryCode;
  }
}
