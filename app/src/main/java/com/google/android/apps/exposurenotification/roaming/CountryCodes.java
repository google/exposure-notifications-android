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

import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.android.apps.exposurenotification.roaming.Qualifiers.HomeCountry;
import com.google.android.apps.exposurenotification.storage.CountryRepository;
import com.google.common.collect.ImmutableSet;
import java.util.List;
import javax.inject.Inject;
import org.threeten.bp.Duration;
import org.threeten.bp.Instant;

/**
 * Encapsulates the logic and persistence of ISO-Alpha-2 country codes used to shard Diagnosis Key
 * uploads and downloads
 */
public class CountryCodes {
  private static final int COUNTRY_CODE_TTL_DAYS = 14;

  private final NetworkCountryCodes networkCountryCodes;
  private final CountryRepository countryRepository;
  private final Clock clock;
  // An ISO-Alpha-2 country code.
  private final String homeCountryCode;

  @Inject
  public CountryCodes(
      NetworkCountryCodes networkCountryCodes,
      CountryRepository countryRepository,
      Clock clock,
      @HomeCountry String homeCountryCode) {
    this.networkCountryCodes = networkCountryCodes;
    this.countryRepository = countryRepository;
    this.clock = clock;
    this.homeCountryCode = homeCountryCode;
  }

  /**
   * Returns a list of country codes covering the epidemiologically relevant past N days of the
   * user's possible exposure risk.
   *
   * <p>Based on the user's home region and MCC codes from TelephonyManager.
   */
  public List<String> getExposureRelevantCountryCodes() {
    updateDatabaseWithCurrentCountryCode();
    return ImmutableSet.<String>builder()
        .addAll(countryRepository.getRecentlySeenCountryCodes(getEarliestTimestamp()))
        .add(homeCountryCode)
        .build()
        .asList();
  }

  /**
   * Get the current country code, update the database with it, and return it. It is guaranteed that
   * any read operations after this function returns will get the updated results.
   */
  public void updateDatabaseWithCurrentCountryCode() {
    String countryCode = networkCountryCodes.getCurrentCountryCode();
    if (countryCode != null) {
      countryRepository.markCountrySeen(countryCode);
    }
  }

  public void deleteObsoleteCountryCodes() {
    countryRepository.deleteObsoleteCountryCodes(getEarliestTimestamp());
  }

  private Instant getEarliestTimestamp() {
    return clock.now().minus(Duration.ofDays(COUNTRY_CODE_TTL_DAYS));
  }
}
