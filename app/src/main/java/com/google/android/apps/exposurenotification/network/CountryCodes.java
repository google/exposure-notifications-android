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

import android.content.Context;
import android.telephony.TelephonyManager;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;

/**
 * Encapsulates the logic and persistence of ISO-Alpha-2 country codes used to shard Diagnosis Key
 * uploads and downloads
 */
class CountryCodes {
  // TODO: This default is only to ease testing while development progresses. A production
  // implementation should not have such a default.
  private static final String DEFAULT_COUNTRY = "US";

  private final TelephonyManager telephonyManager;

  CountryCodes(Context context) {
    telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
  }

  /**
   * Returns a list of country codes covering the epidemiologically relevant past N days of the
   * user's possible exposure risk.
   *
   * <p>This sample implementation returns only the current country code from the TelephonyManager,
   * based on MCC. A production implementation might retain and return a list of the user's relevant
   * country codes for the past N days.
   */
  ListenableFuture<List<String>> getExposureRelevantCountryCodes() {
    String countryCode = telephonyManager.getNetworkCountryIso().toUpperCase();
    if (Strings.isNullOrEmpty(countryCode)) {
      countryCode = DEFAULT_COUNTRY;
    }
    return Futures.immediateFuture(ImmutableList.of(DEFAULT_COUNTRY));
  }
}
