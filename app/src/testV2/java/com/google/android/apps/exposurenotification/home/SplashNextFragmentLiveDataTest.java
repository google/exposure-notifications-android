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

package com.google.android.apps.exposurenotification.home;

import static com.google.common.truth.Truth.assertThat;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;
import com.google.android.apps.exposurenotification.onboarding.OnboardingPermissionDisabledFragment;
import com.google.android.apps.exposurenotification.onboarding.OnboardingPermissionEnabledFragment;
import com.google.android.apps.exposurenotification.onboarding.OnboardingPrivateAnalyticsFragment;
import com.google.android.apps.exposurenotification.testsupport.ExposureNotificationRules;
import com.google.common.collect.Sets;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@HiltAndroidTest
@RunWith(RobolectricTestRunner.class)
@Config(application = HiltTestApplication.class)
public class SplashNextFragmentLiveDataTest {

  @Rule
  public ExposureNotificationRules rules = ExposureNotificationRules.forTest(this).build();

  private MutableLiveData<Boolean> isEnabledLiveData = new MutableLiveData<>(false);
  private MutableLiveData<Boolean> isOnboardingStateSetLiveData = new MutableLiveData<>(false);
  private MutableLiveData<Boolean> isPrivateAnalyticsSetLiveData = new MutableLiveData<>(false);
  private MutableLiveData<Boolean> isPrivateAnalyticsSupportedAndConfiguredLiveData =
      new MutableLiveData<>(false);

  @Test
  public void nullAction_enabled_onboardingStateNotSet_nextFragmentIsOnboardingPermissionEnabledFragment() {
    assertEnumeratedCases(
        /* isEnabledStates= */ Sets.newHashSet(true),
        /* isOnboardingStateSetStates= */ Sets.newHashSet(false),
        /* isPrivateAnalyticsSetStates= */ Sets.newHashSet(true, false),
        /* isPrivateAnalyticsSupportedAndConfiguredStates= */ Sets.newHashSet(true, false),
        /* expected= */ OnboardingPermissionEnabledFragment.class);
  }

  @Test
  public void nullAction_notEnabled_onboardingStateNotSet_nextFragmentIsOnboardingPermissionDisabledFragment() {
    assertEnumeratedCases(
        /* isEnabledStates= */ Sets.newHashSet(false),
        /* isOnboardingStateSetStates= */ Sets.newHashSet(false),
        /* isPrivateAnalyticsSetStates= */ Sets.newHashSet(true, false),
        /* isPrivateAnalyticsSupportedAndConfiguredStates= */ Sets.newHashSet(true, false),
        /* expected= */ OnboardingPermissionDisabledFragment.class);
  }

  @Test
  public void nullAction_enabled_onboardingStateSet_privateAnalyticsSet_privateAnalyticsSupportedAndConfigured_nextFragmentIsOnboardingPrivateAnalyticsFragment() {
    assertEnumeratedCases(
        /* isEnabledStates= */ Sets.newHashSet(true),
        /* isOnboardingStateSetStates= */ Sets.newHashSet(true),
        /* isPrivateAnalyticsSetStates= */ Sets.newHashSet(false),
        /* isPrivateAnalyticsSupportedAndConfiguredStates= */ Sets.newHashSet(true),
        /* expected= */ OnboardingPrivateAnalyticsFragment.class);
  }

  @Test
  public void nullAction_notEnabled_onboardingStateSet_nextFragmentIsHomeFragment() {
    assertEnumeratedCases(
        /* isEnabledStates= */ Sets.newHashSet(false),
        /* isOnboardingStateSetStates= */ Sets.newHashSet(true),
        /* isPrivateAnalyticsSetStates= */ Sets.newHashSet(true, false),
        /* isPrivateAnalyticsSupportedAndConfiguredStates= */ Sets.newHashSet(true, false),
        /* expected= */ SinglePageHomeFragment.class);
  }

  @Test
  public void nullAction_enabled_onboardingStateSet_privateAnalyticsSet_privateAnalyticsNotSupportedAndConfigured_nextFragmentIsHomeFragment() {
    assertEnumeratedCases(
        /* isEnabledStates= */ Sets.newHashSet(true),
        /* isOnboardingStateSetStates= */ Sets.newHashSet(true),
        /* isPrivateAnalyticsSetStates= */ Sets.newHashSet(true),
        /* isPrivateAnalyticsSupportedAndConfiguredStates= */ Sets.newHashSet(true),
        /* expected= */ SinglePageHomeFragment.class);
  }

  @Test
  public void nullAction_enabled_onboardingStateSet_privateAnalyticsNotSupportedAndConfigured_nextFragmentIsHomeFragment() {
    assertEnumeratedCases(
        /* isEnabledStates= */ Sets.newHashSet(true),
        /* isOnboardingStateSetStates= */ Sets.newHashSet(true),
        /* isPrivateAnalyticsSetStates= */ Sets.newHashSet(true, false),
        /* isPrivateAnalyticsSupportedAndConfiguredStates= */ Sets.newHashSet(false),
        /* expected= */ SinglePageHomeFragment.class);
  }

  @Test
  public void unknownAction_enabled_onboardingStateNotSet_nextFragmentIsOnboardingPermissionEnabledFragment() {
    assertEnumeratedCases(
        /* isEnabledStates= */ Sets.newHashSet(true),
        /* isOnboardingStateSetStates= */ Sets.newHashSet(false),
        /* isPrivateAnalyticsSetStates= */ Sets.newHashSet(true, false),
        /* isPrivateAnalyticsSupportedAndConfiguredStates= */ Sets.newHashSet(true, false),
        /* expected= */ OnboardingPermissionEnabledFragment.class);
  }

  @Test
  public void unknownAction_notEnabled_onboardingStateNotSet_nextFragmentIsOnboardingPermissionDisabledFragment() {
    assertEnumeratedCases(
        /* isEnabledStates= */ Sets.newHashSet(false),
        /* isOnboardingStateSetStates= */ Sets.newHashSet(false),
        /* isPrivateAnalyticsSetStates= */ Sets.newHashSet(true, false),
        /* isPrivateAnalyticsSupportedAndConfiguredStates= */ Sets.newHashSet(true, false),
        /* expected= */ OnboardingPermissionDisabledFragment.class);
  }

  @Test
  public void unknownAction_enabled_onboardingStateSet_privateAnalyticsSet_privateAnalyticsSupportedAndConfigured_nextFragmentIsOnboardingPrivateAnalyticsFragment() {
    assertEnumeratedCases(
        /* isEnabledStates= */ Sets.newHashSet(true),
        /* isOnboardingStateSetStates= */ Sets.newHashSet(true),
        /* isPrivateAnalyticsSetStates= */ Sets.newHashSet(false),
        /* isPrivateAnalyticsSupportedAndConfiguredStates= */ Sets.newHashSet(true),
        /* expected= */ OnboardingPrivateAnalyticsFragment.class);
  }

  @Test
  public void unknownAction_notEnabled_onboardingStateSet_nextFragmentIsHomeFragment() {
    assertEnumeratedCases(
        /* isEnabledStates= */ Sets.newHashSet(false),
        /* isOnboardingStateSetStates= */ Sets.newHashSet(true),
        /* isPrivateAnalyticsSetStates= */ Sets.newHashSet(true, false),
        /* isPrivateAnalyticsSupportedAndConfiguredStates= */ Sets.newHashSet(true, false),
        /* expected= */ SinglePageHomeFragment.class);
  }

  @Test
  public void unknownAction_enabled_onboardingStateSet_privateAnalyticsSet_privateAnalyticsNotSupportedAndConfigured_nextFragmentIsHomeFragment() {
    assertEnumeratedCases(
        /* isEnabledStates= */ Sets.newHashSet(true),
        /* isOnboardingStateSetStates= */ Sets.newHashSet(true),
        /* isPrivateAnalyticsSetStates= */ Sets.newHashSet(true),
        /* isPrivateAnalyticsSupportedAndConfiguredStates= */ Sets.newHashSet(true),
        /* expected= */ SinglePageHomeFragment.class);
  }

  @Test
  public void unknownAction_enabled_onboardingStateSet_privateAnalyticsNotSupportedAndConfigured_nextFragmentIsHomeFragment() {
    assertEnumeratedCases(
        /* isEnabledStates= */ Sets.newHashSet(true),
        /* isOnboardingStateSetStates= */ Sets.newHashSet(true),
        /* isPrivateAnalyticsSetStates= */ Sets.newHashSet(true, false),
        /* isPrivateAnalyticsSupportedAndConfiguredStates= */ Sets.newHashSet(false),
        /* expected= */ SinglePageHomeFragment.class);
  }

  /*
   * This method checks if the next fragment after SplashFragment is as expected using Sets of
   * booleans to test many scenarios under which SplashNextFragmentLiveData is created.
   */
  private void assertEnumeratedCases(
      Set<Boolean> isEnabledStates,
      Set<Boolean> isOnboardingStateSetStates,
      Set<Boolean> isPrivateAnalyticsSetStates,
      Set<Boolean> isPrivateAnalyticsSupportedAndConfiguredStates,
      Class expected) {
    SplashNextFragmentLiveData splashNextFragmentLiveData = SplashNextFragmentLiveData.create(
        isEnabledLiveData,
        isOnboardingStateSetLiveData,
        isPrivateAnalyticsSetLiveData,
        isPrivateAnalyticsSupportedAndConfiguredLiveData);
    AtomicReference<Fragment> current = new AtomicReference<>();
    splashNextFragmentLiveData.observeForever(current::set);

    Set<List<Boolean>> states =
        Sets.cartesianProduct(isEnabledStates, isOnboardingStateSetStates,
            isPrivateAnalyticsSetStates, isPrivateAnalyticsSupportedAndConfiguredStates);
    for (List<Boolean> state : states) {
      isEnabledLiveData.setValue(state.get(0));
      isOnboardingStateSetLiveData.setValue(state.get(1));
      isPrivateAnalyticsSetLiveData.setValue(state.get(2));
      isPrivateAnalyticsSupportedAndConfiguredLiveData.setValue(state.get(3));
      assertThat(current.get()).isInstanceOf(expected);
    }
  }

}