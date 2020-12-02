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

package com.google.android.apps.exposurenotification.home;

import static com.google.android.apps.exposurenotification.home.ExposureNotificationActivity.ACTION_LAUNCH_FROM_EXPOSURE_NOTIFICATION;
import static com.google.common.truth.Truth.assertThat;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;
import com.google.android.apps.exposurenotification.onboarding.OnboardingPermissionDisabledFragment;
import com.google.android.apps.exposurenotification.onboarding.OnboardingPermissionEnabledFragment;
import com.google.android.apps.exposurenotification.onboarding.OnboardingPrivateAnalyticsFragment;
import com.google.android.apps.exposurenotification.privateanalytics.PrivateAnalyticsFirebaseModule;
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
    assertEnumeratedCases(null,
        /* isEnabledStates= */ Sets.newHashSet(true),
        /* isOnboardingStateSetStates= */ Sets.newHashSet(false),
        /* isPrivateAnalyticsSetStates= */ Sets.newHashSet(true, false),
        /* isPrivateAnalyticsSupportedAndConfiguredStates= */ Sets.newHashSet(true, false),
        /* expected= */ OnboardingPermissionEnabledFragment.class);
  }

  @Test
  public void nullAction_notEnabled_onboardingStateNotSet_nextFragmentIsOnboardingPermissionDisabledFragment() {
    assertEnumeratedCases(null,
        /* isEnabledStates= */ Sets.newHashSet(false),
        /* isOnboardingStateSetStates= */ Sets.newHashSet(false),
        /* isPrivateAnalyticsSetStates= */ Sets.newHashSet(true, false),
        /* isPrivateAnalyticsSupportedAndConfiguredStates= */ Sets.newHashSet(true, false),
        /* expected= */ OnboardingPermissionDisabledFragment.class);
  }

  @Test
  public void nullAction_enabled_onboardingStateSet_privateAnalyticsSet_privateAnalyticsSupportedAndConfigured_nextFragmentIsOnboardingPrivateAnalyticsFragment() {
    assertEnumeratedCases(null,
        /* isEnabledStates= */ Sets.newHashSet(true),
        /* isOnboardingStateSetStates= */ Sets.newHashSet(true),
        /* isPrivateAnalyticsSetStates= */ Sets.newHashSet(false),
        /* isPrivateAnalyticsSupportedAndConfiguredStates= */ Sets.newHashSet(true),
        /* expected= */ OnboardingPrivateAnalyticsFragment.class);
  }

  @Test
  public void nullAction_notEnabled_onboardingStateSet_nextFragmentIsHomeFragment() {
    assertEnumeratedCases(null,
        /* isEnabledStates= */ Sets.newHashSet(false),
        /* isOnboardingStateSetStates= */ Sets.newHashSet(true),
        /* isPrivateAnalyticsSetStates= */ Sets.newHashSet(true, false),
        /* isPrivateAnalyticsSupportedAndConfiguredStates= */ Sets.newHashSet(true, false),
        /* expected= */ HomeFragment.class);
  }

  @Test
  public void nullAction_enabled_onboardingStateSet_privateAnalyticsSet_privateAnalyticsNotSupportedAndConfigured_nextFragmentIsHomeFragment() {
    assertEnumeratedCases(null,
        /* isEnabledStates= */ Sets.newHashSet(true),
        /* isOnboardingStateSetStates= */ Sets.newHashSet(true),
        /* isPrivateAnalyticsSetStates= */ Sets.newHashSet(true),
        /* isPrivateAnalyticsSupportedAndConfiguredStates= */ Sets.newHashSet(true),
        /* expected= */ HomeFragment.class);
  }

  @Test
  public void nullAction_enabled_onboardingStateSet_privateAnalyticsNotSupportedAndConfigured_nextFragmentIsHomeFragment() {
    assertEnumeratedCases(null,
        /* isEnabledStates= */ Sets.newHashSet(true),
        /* isOnboardingStateSetStates= */ Sets.newHashSet(true),
        /* isPrivateAnalyticsSetStates= */ Sets.newHashSet(true, false),
        /* isPrivateAnalyticsSupportedAndConfiguredStates= */ Sets.newHashSet(false),
        /* expected= */ HomeFragment.class);
  }

  @Test
  public void unknownAction_enabled_onboardingStateNotSet_nextFragmentIsOnboardingPermissionEnabledFragment() {
    assertEnumeratedCases("unknown",
        /* isEnabledStates= */ Sets.newHashSet(true),
        /* isOnboardingStateSetStates= */ Sets.newHashSet(false),
        /* isPrivateAnalyticsSetStates= */ Sets.newHashSet(true, false),
        /* isPrivateAnalyticsSupportedAndConfiguredStates= */ Sets.newHashSet(true, false),
        /* expected= */ OnboardingPermissionEnabledFragment.class);
  }

  @Test
  public void unknownAction_notEnabled_onboardingStateNotSet_nextFragmentIsOnboardingPermissionDisabledFragment() {
    assertEnumeratedCases("unknown",
        /* isEnabledStates= */ Sets.newHashSet(false),
        /* isOnboardingStateSetStates= */ Sets.newHashSet(false),
        /* isPrivateAnalyticsSetStates= */ Sets.newHashSet(true, false),
        /* isPrivateAnalyticsSupportedAndConfiguredStates= */ Sets.newHashSet(true, false),
        /* expected= */ OnboardingPermissionDisabledFragment.class);
  }

  @Test
  public void unknownAction_enabled_onboardingStateSet_privateAnalyticsSet_privateAnalyticsSupportedAndConfigured_nextFragmentIsOnboardingPrivateAnalyticsFragment() {
    assertEnumeratedCases("unknown",
        /* isEnabledStates= */ Sets.newHashSet(true),
        /* isOnboardingStateSetStates= */ Sets.newHashSet(true),
        /* isPrivateAnalyticsSetStates= */ Sets.newHashSet(false),
        /* isPrivateAnalyticsSupportedAndConfiguredStates= */ Sets.newHashSet(true),
        /* expected= */ OnboardingPrivateAnalyticsFragment.class);
  }

  @Test
  public void unknownAction_notEnabled_onboardingStateSet_nextFragmentIsHomeFragment() {
    assertEnumeratedCases("unknown",
        /* isEnabledStates= */ Sets.newHashSet(false),
        /* isOnboardingStateSetStates= */ Sets.newHashSet(true),
        /* isPrivateAnalyticsSetStates= */ Sets.newHashSet(true, false),
        /* isPrivateAnalyticsSupportedAndConfiguredStates= */ Sets.newHashSet(true, false),
        /* expected= */ HomeFragment.class);
  }

  @Test
  public void unknownAction_enabled_onboardingStateSet_privateAnalyticsSet_privateAnalyticsNotSupportedAndConfigured_nextFragmentIsHomeFragment() {
    assertEnumeratedCases("unknown",
        /* isEnabledStates= */ Sets.newHashSet(true),
        /* isOnboardingStateSetStates= */ Sets.newHashSet(true),
        /* isPrivateAnalyticsSetStates= */ Sets.newHashSet(true),
        /* isPrivateAnalyticsSupportedAndConfiguredStates= */ Sets.newHashSet(true),
        /* expected= */ HomeFragment.class);
  }

  @Test
  public void unknownAction_enabled_onboardingStateSet_privateAnalyticsNotSupportedAndConfigured_nextFragmentIsHomeFragment() {
    assertEnumeratedCases("unknown",
        /* isEnabledStates= */ Sets.newHashSet(true),
        /* isOnboardingStateSetStates= */ Sets.newHashSet(true),
        /* isPrivateAnalyticsSetStates= */ Sets.newHashSet(true, false),
        /* isPrivateAnalyticsSupportedAndConfiguredStates= */ Sets.newHashSet(false),
        /* expected= */ HomeFragment.class);
  }

  @Test
  public void actionFromExposureNotification_nextFragmentIsHomeFragment() {
    assertEnumeratedCases(ACTION_LAUNCH_FROM_EXPOSURE_NOTIFICATION,
        /* isEnabledStates= */ Sets.newHashSet(true, false),
        /* isOnboardingStateSetStates= */ Sets.newHashSet(true, false),
        /* isPrivateAnalyticsSetStates= */ Sets.newHashSet(true, false),
        /* isPrivateAnalyticsSupportedAndConfiguredStates= */ Sets.newHashSet(true, false),
        /* expected= */ HomeFragment.class);
  }

  private void assertEnumeratedCases(
      String action,
      Set<Boolean> isEnabledStates,
      Set<Boolean> isOnboardingStateSetStates,
      Set<Boolean> isPrivateAnalyticsSetStates,
      Set<Boolean> isPrivateAnalyticsSupportedAndConfiguredStates,
      Class expected) {
    SplashNextFragmentLiveData splashNextFragmentLiveData = SplashNextFragmentLiveData.create(
        action,
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