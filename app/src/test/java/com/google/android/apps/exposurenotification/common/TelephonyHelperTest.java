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

package com.google.android.apps.exposurenotification.common;

import static com.google.android.apps.exposurenotification.common.TelephonyHelper.US_ISO;
import static com.google.common.truth.Truth.assertThat;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.res.Configuration;
import android.telephony.TelephonyManager;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.apps.exposurenotification.testsupport.ExposureNotificationRules;
import com.google.android.apps.exposurenotification.testsupport.FakeShadowResources;
import com.google.common.collect.ImmutableSet;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import java.util.Locale;
import java.util.Set;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowTelecomManager;
import org.robolectric.shadows.ShadowTelephonyManager;

/**
 * Tests for {@link TelephonyHelper} utility function helper class.
 */
@RunWith(AndroidJUnit4.class)
@HiltAndroidTest
@Config(application = HiltTestApplication.class, shadows = {FakeShadowResources.class,
    ShadowTelecomManager.class})
public class TelephonyHelperTest {

  private static final String PHONE_NUMBER_GB_INTERNATIONAL = "+447911123456";
  private static final String PHONE_NUMBER_GB_LOCALE = "07911123456";
  private static final String PHONE_NUMBER_US_INTERNATIONAL = "+15417543010";
  private static final String PHONE_NUMBER_US_LOCALE = "15417543010";
  private static final String PHONE_NUMBER_DE_INTERNATIONAL = "+4915223433333";
  private static final String PHONE_NUMBER_DE_LOCALE = "015223433333";
  private static final String PHONE_NUMBER_ES_INTERNATIONAL = "+34608123456";
  private static final String PHONE_NUMBER_ES_LOCALE = "901123456";

  @Rule
  public ExposureNotificationRules rules = ExposureNotificationRules.forTest(this).withMocks()
      .build();

  @Inject
  TelephonyHelper telephonyHelper;

  @Inject
  TelephonyManager telephonyManager;
  ShadowTelephonyManager shadowTelephonyManager;

  Context context = ApplicationProvider.getApplicationContext();

  @Before
  public void setup() {
    rules.hilt().inject();
    shadowTelephonyManager = shadowOf(telephonyManager);
  }

  @Test
  public void getCurrentCountryIso_allIsoSourcesSet_returnsSimCountry() {
    shadowTelephonyManager.setSimCountryIso("Sim");
    shadowTelephonyManager.setNetworkCountryIso("Net");
    setResourceLocale("res", "RES");
    setDefaultLocale("def", "DEF");

    assertThat(telephonyHelper.getCurrentCountryIso()).isEqualTo("SIM");
  }

  @Test
  public void getCurrentCountryIso_isoSimUnavailable_returnsNetworkCountry() {
    shadowTelephonyManager.setSimCountryIso("");
    shadowTelephonyManager.setNetworkCountryIso("Net");
    setResourceLocale("res", "RES");
    setDefaultLocale("def", "DEF");

    assertThat(telephonyHelper.getCurrentCountryIso()).isEqualTo("NET");
  }

  @Test
  public void getCurrentCountryIso_isoSimNetUnavailable_returnsResourceCountry() {
    shadowTelephonyManager.setSimCountryIso(null);
    shadowTelephonyManager.setNetworkCountryIso(null);
    setResourceLocale("res", "RES");
    setDefaultLocale("def", "DEF");

    assertThat(telephonyHelper.getCurrentCountryIso()).isEqualTo("RES");
  }

  @Test
  public void getCurrentCountryIso_isoSimNetResourceUnavailable_returnsDefaultLocaleCountry() {
    shadowTelephonyManager.setSimCountryIso("");
    shadowTelephonyManager.setNetworkCountryIso("");
    setResourceLocale("", "");
    setDefaultLocale("def", "DEF");

    assertThat(telephonyHelper.getCurrentCountryIso()).isEqualTo("DEF");
  }

  @Test
  public void getCurrentCountryIso_isoAllSourcesUnavailable_returnsUSCountry() {
    shadowTelephonyManager.setSimCountryIso(null);
    shadowTelephonyManager.setNetworkCountryIso(null);
    setResourceLocale("", "");
    setDefaultLocale("", "");

    assertThat(telephonyHelper.getCurrentCountryIso()).isEqualTo(US_ISO);
  }

  @Test
  public void isValidPhoneNumber_invalidPhoneNumber_returnsFalse() {
    Set<String> invalidPhoneNumbers = ImmutableSet.of("123", "123456789", "", "+1", "+");

    for (String phoneNumber : invalidPhoneNumbers) {
      assertThat(telephonyHelper.isValidPhoneNumber(phoneNumber)).isFalse();
    }
  }

  @Test
  public void isValidPhoneNumber_validPhoneNumber_internationalFormat_returnsTrue() {
    assertThat(telephonyHelper.isValidPhoneNumber(PHONE_NUMBER_DE_INTERNATIONAL)).isTrue();
    assertThat(telephonyHelper.isValidPhoneNumber(PHONE_NUMBER_GB_INTERNATIONAL)).isTrue();
    assertThat(telephonyHelper.isValidPhoneNumber(PHONE_NUMBER_US_INTERNATIONAL)).isTrue();
    assertThat(telephonyHelper.isValidPhoneNumber(PHONE_NUMBER_ES_INTERNATIONAL)).isTrue();
  }

  @Test
  public void isValidPhoneNumber_validPhoneNumber_localeGERMANY_onlyTrueForDENumber() {
    setResourceLocale("de", "DE");

    assertThat(telephonyHelper.isValidPhoneNumber(PHONE_NUMBER_DE_LOCALE)).isTrue();
    assertThat(telephonyHelper.isValidPhoneNumber(PHONE_NUMBER_US_LOCALE)).isFalse();
    assertThat(telephonyHelper.isValidPhoneNumber(PHONE_NUMBER_ES_LOCALE)).isFalse();
  }

  @Test
  public void isValidPhoneNumber_validPhoneNumber_localeGB_onlyTrueForGBNumber() {
    setResourceLocale("en", "GB");

    assertThat(telephonyHelper.isValidPhoneNumber(PHONE_NUMBER_GB_LOCALE)).isTrue();
    assertThat(telephonyHelper.isValidPhoneNumber(PHONE_NUMBER_DE_LOCALE)).isFalse();
    assertThat(telephonyHelper.isValidPhoneNumber(PHONE_NUMBER_US_LOCALE)).isFalse();
    assertThat(telephonyHelper.isValidPhoneNumber(PHONE_NUMBER_ES_LOCALE)).isFalse();
  }

  @Test
  public void isValidPhoneNumber_validPhoneNumber_localeUS_onlyTrueForUSNumber() {
    setResourceLocale("en", "US");

    assertThat(telephonyHelper.isValidPhoneNumber(PHONE_NUMBER_US_LOCALE)).isTrue();
    assertThat(telephonyHelper.isValidPhoneNumber(PHONE_NUMBER_GB_LOCALE)).isFalse();
    assertThat(telephonyHelper.isValidPhoneNumber(PHONE_NUMBER_DE_LOCALE)).isFalse();
    assertThat(telephonyHelper.isValidPhoneNumber(PHONE_NUMBER_ES_LOCALE)).isFalse();
  }

  @Test
  public void isValidPhoneNumber_validPhoneNumber_localeSPAIN_onlyTrueForESNumber() {
    setResourceLocale("es", "ES");

    assertThat(telephonyHelper.isValidPhoneNumber(PHONE_NUMBER_ES_LOCALE)).isTrue();
    assertThat(telephonyHelper.isValidPhoneNumber(PHONE_NUMBER_GB_LOCALE)).isFalse();
    assertThat(telephonyHelper.isValidPhoneNumber(PHONE_NUMBER_US_LOCALE)).isFalse();
    assertThat(telephonyHelper.isValidPhoneNumber(PHONE_NUMBER_DE_LOCALE)).isFalse();
  }

  private void setResourceLocale(String language, String country) {
    Locale locale = new Locale(language, country);
    // Update fake resources with new locale
    FakeShadowResources resources = (FakeShadowResources) shadowOf(context.getResources());
    Configuration config = resources.getConfiguration();
    config.locale = locale;
    resources.updateConfiguration(config);
  }

  private void setDefaultLocale(String language, String country) {
    Locale locale = new Locale(language, country);
    Locale.setDefault(locale);
  }

}
