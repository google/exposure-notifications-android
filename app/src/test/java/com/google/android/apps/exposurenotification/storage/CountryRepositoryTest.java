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

package com.google.android.apps.exposurenotification.storage;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.android.apps.exposurenotification.common.time.RealTimeModule;
import com.google.android.apps.exposurenotification.testsupport.ExposureNotificationRules;
import com.google.android.apps.exposurenotification.testsupport.FakeClock;
import com.google.android.apps.exposurenotification.testsupport.InMemoryDb;
import dagger.hilt.android.testing.BindValue;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import dagger.hilt.android.testing.UninstallModules;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;
import org.threeten.bp.Instant;

/**
 * Tests for operations in {@link CountryRepository} and by extension, the DAO underneath.
 */
@RunWith(AndroidJUnit4.class)
@HiltAndroidTest
@Config(application = HiltTestApplication.class)
@UninstallModules({DbModule.class, RealTimeModule.class})
public class CountryRepositoryTest {

  @Rule
  public ExposureNotificationRules rules = ExposureNotificationRules.forTest(this).build();

  @BindValue
  ExposureNotificationDatabase db = InMemoryDb.create();
  @BindValue
  Clock clock = new FakeClock();

  @Inject
  CountryRepository countryRepository;

  @Before
  public void setUp() {
    rules.hilt().inject();
  }

  @Test
  public void shouldMarkCountrySeen_andGetRecentlySeenCountryCodes() {
    countryRepository.markCountrySeen("US");

    assertThat(countryRepository.getRecentlySeenCountryCodes(Instant.ofEpochMilli(0)))
        .containsExactly("US");
  }

  @Test
  public void shouldMarkCountrySeenAsync_andGetRecentlySeenCountryCodes()
      throws Exception {
    countryRepository.markCountrySeenAsync("US").get();

    assertThat(countryRepository.getRecentlySeenCountryCodes(Instant.ofEpochMilli(0)))
        .containsExactly("US");
  }

  @Test
  public void getRecentlySeenCountryCodes_shouldIncludeCountriesSeenAfterGivenTime() {
    // GIVEN
    countryRepository.markCountrySeen("US");

    // THEN
    Instant oneMsEarlier = clock.now().minusMillis(1);
    assertThat(countryRepository.getRecentlySeenCountryCodes(oneMsEarlier)).containsExactly("US");
  }

  @Test
  public void getRecentlySeenCountryCodes_shouldIncludeCountriesSeenAtGivenTime() {
    // GIVEN
    countryRepository.markCountrySeen("US");

    // THEN
    assertThat(countryRepository.getRecentlySeenCountryCodes(clock.now())).containsExactly("US");
  }

  @Test
  public void getRecentlySeenCountryCodes_shouldExcludeCountriesSeenBeforeGiventime() {
    // GIVEN
    countryRepository.markCountrySeen("US");
    ((FakeClock) clock).advance();
    countryRepository.markCountrySeen("CA");

    // THEN
    assertThat(countryRepository.getRecentlySeenCountryCodes(clock.now())).containsExactly("CA");
  }

  @Test
  public void markCountrySeen_shouldUpdateSecondTime() {
    // GIVEN
    countryRepository.markCountrySeen("US");

    // WHEN
    ((FakeClock) clock).advance();
    countryRepository.markCountrySeen("US");

    // THEN
    assertThat(countryRepository.getRecentlySeenCountryCodes(clock.now())).containsExactly("US");
  }

  @Test
  public void shouldDeleteObsoleteCountryCodes() {
    // GIVEN
    countryRepository.markCountrySeen("US");
    ((FakeClock) clock).advance();
    countryRepository.markCountrySeen("CA");

    // WHEN
    countryRepository.deleteObsoleteCountryCodes(clock.now());

    // THEN
    assertThat(countryRepository.getRecentlySeenCountryCodes(Instant.ofEpochMilli(0)))
        .containsExactly("CA");
  }

  @Test
  public void shouldDeleteObsoleteCountryCodesAsync() throws Exception {
    // GIVEN
    countryRepository.markCountrySeen("US");
    ((FakeClock) clock).advance();
    countryRepository.markCountrySeen("CA");

    // WHEN
    countryRepository.deleteObsoleteCountryCodesAsync(clock.now()).get();

    // THEN
    assertThat(countryRepository.getRecentlySeenCountryCodes(Instant.ofEpochMilli(0)))
        .containsExactly("CA");
  }
}
