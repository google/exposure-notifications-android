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

package com.google.android.apps.exposurenotification.nearby;

import static com.google.common.truth.Truth.assertThat;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.home.ExposureNotificationViewModel.ExposureNotificationState;
import com.google.android.apps.exposurenotification.testsupport.ExposureNotificationRules;
import com.google.android.apps.exposurenotification.testsupport.FakeShadowResources;
import com.google.common.collect.ImmutableSet;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import java.util.Set;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

/**
 * Unit test for EnStateUtil helper class.
 */
@RunWith(AndroidJUnit4.class)
@HiltAndroidTest
@Config(application = HiltTestApplication.class, shadows = {FakeShadowResources.class})
public class EnStateUtilTest {

  @Rule
  public ExposureNotificationRules rules = ExposureNotificationRules.forTest(this).build();

  private static final Set<ExposureNotificationState> NOT_EN_TURNDOWN_STATES_SET =
      ImmutableSet.of(ExposureNotificationState.DISABLED,
          ExposureNotificationState.ENABLED,
          ExposureNotificationState.PAUSED_BLE,
          ExposureNotificationState.PAUSED_LOCATION,
          ExposureNotificationState.PAUSED_LOCATION_BLE,
          ExposureNotificationState.STORAGE_LOW,
          ExposureNotificationState.FOCUS_LOST,
          ExposureNotificationState.PAUSED_HW_NOT_SUPPORT,
          ExposureNotificationState.PAUSED_USER_PROFILE_NOT_SUPPORT);

  private static final ExposureNotificationState EN_TURNED_DOWN_STATE =
      ExposureNotificationState.PAUSED_EN_NOT_SUPPORT;

  private static final ExposureNotificationState EN_TURNED_DOWN_FOR_REGION_STATE =
      ExposureNotificationState.PAUSED_NOT_IN_ALLOWLIST;

  private final Context context = ApplicationProvider.getApplicationContext();
  private final FakeShadowResources resources =
      (FakeShadowResources) shadowOf(context.getResources());

  @Before
  public void setup() {
    rules.hilt().inject();
  }

  @Test
  public void isEnTurndown_enNotTurnedDown_returnsFalse() {
    for (ExposureNotificationState state : NOT_EN_TURNDOWN_STATES_SET) {
      assertThat(EnStateUtil.isEnTurndown(state)).isFalse();
    }
  }

  @Test
  public void isEnTurndown_enTurnedDown_returnsTrue() {
    assertThat(EnStateUtil.isEnTurndown(EN_TURNED_DOWN_STATE)).isTrue();
    assertThat(EnStateUtil.isEnTurndown(EN_TURNED_DOWN_FOR_REGION_STATE)).isTrue();
  }

  @Test
  public void isEnTurndownForRegion_enNotTurnedDownForRegion_returnsFalse() {
    for (ExposureNotificationState state : NOT_EN_TURNDOWN_STATES_SET) {
      assertThat(EnStateUtil.isEnTurndownForRegion(state)).isFalse();
    }
  }

  @Test
  public void isEnTurndownForRegion_enTurnedDownEntirely_returnsFalse() {
    assertThat(EnStateUtil.isEnTurndownForRegion(EN_TURNED_DOWN_STATE)).isFalse();
  }

  @Test
  public void isEnTurndownForRegion_enTurnedDownForRegion_returnsTrue() {
    assertThat(EnStateUtil.isEnTurndownForRegion(EN_TURNED_DOWN_FOR_REGION_STATE)).isTrue();
  }

  @Test
  public void isAgencyTurndownMessagePresent_agencyTurndownMessageEmpty_returnsFalse() {
    resources.addFakeResource(R.string.turndown_agency_message, "");

    assertThat(EnStateUtil.isAgencyTurndownMessagePresent(context)).isFalse();
  }

  @Test
  public void isAgencyTurndownMessagePresent_agencyTurndownMessagePresent_returnsTrue() {
    resources.addFakeResource(R.string.turndown_agency_message, "Agency turndown message");

    assertThat(EnStateUtil.isAgencyTurndownMessagePresent(context)).isTrue();
  }

}
