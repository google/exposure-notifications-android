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

import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import com.google.android.apps.exposurenotification.home.ExposureNotificationViewModel.ExposureNotificationState;
import com.google.android.apps.exposurenotification.onboarding.OnboardingEnTurndownForRegionFragment;
import com.google.android.apps.exposurenotification.onboarding.OnboardingEnTurndownFragment;
import com.google.android.apps.exposurenotification.onboarding.OnboardingPermissionDisabledFragment;
import com.google.android.apps.exposurenotification.onboarding.OnboardingPermissionEnabledFragment;
import com.google.android.apps.exposurenotification.onboarding.OnboardingPrivateAnalyticsFragment;

/**
 * A {@link MediatorLiveData} that represents the next {@link Fragment} to go to after the splash
 * screen.
 */
public class SplashNextFragmentLiveData extends MediatorLiveData<Fragment> {

  private boolean isEnabled = false;
  private ExposureNotificationState enState = ExposureNotificationState.DISABLED;
  private boolean isOnboardingStateSet = false;
  private boolean isPrivateAnalyticsSet = false;
  private boolean isPrivateAnalyticsSupported = false;
  private boolean onboardAsMigratingUser = false;

  public static SplashNextFragmentLiveData create(
      LiveData<Boolean> enEnabledLiveData,
      LiveData<ExposureNotificationState> enStateLiveData,
      LiveData<Boolean> isOnboardingStateSetLiveData,
      LiveData<Boolean> isPrivateAnalyticsSetLiveData,
      LiveData<Boolean> isPrivateAnalyticsSupportedLiveData,
      boolean onboardAsMigratingUser) {
    SplashNextFragmentLiveData splashNextFragmentLiveData = new SplashNextFragmentLiveData();
    splashNextFragmentLiveData
        .addSource(enEnabledLiveData, splashNextFragmentLiveData::setIsEnabled);
    splashNextFragmentLiveData
        .addSource(enStateLiveData, splashNextFragmentLiveData::setEnState);
    splashNextFragmentLiveData.addSource(isOnboardingStateSetLiveData,
        splashNextFragmentLiveData::setIsOnboardingStatSet);
    splashNextFragmentLiveData
        .addSource(isPrivateAnalyticsSetLiveData,
            splashNextFragmentLiveData::setIsPrivateAnalyticsSet);
    splashNextFragmentLiveData
        .addSource(isPrivateAnalyticsSupportedLiveData,
            splashNextFragmentLiveData::setIsPrivateAnalyticsSupported);
    splashNextFragmentLiveData.setOnboardAsMigratingUser(onboardAsMigratingUser);
    return splashNextFragmentLiveData;
  }

  private SplashNextFragmentLiveData() {
  }

  private void update() {
    if (!isOnboardingStateSet) {
      if (isEnabled) {
        setValue(new OnboardingPermissionEnabledFragment());
      } else {
        if (enState == ExposureNotificationState.PAUSED_EN_NOT_SUPPORT) {
          // EN has been turned down as a whole.
          setValue(new OnboardingEnTurndownFragment());
        } else if (enState == ExposureNotificationState.PAUSED_NOT_IN_ALLOWLIST) {
          // EN has been turned down for a given region (app).
          setValue(new OnboardingEnTurndownForRegionFragment());
        } else {
          setValue(new OnboardingPermissionDisabledFragment());
        }
      }
    } else {
      if (onboardAsMigratingUser) {
        // Even though onboarding state is set, a current user might be a migrating user with
        // their onboarded state set in the V1 app. In this case, we still need to onboard them.
        setValue(isEnabled ?
            new OnboardingPermissionEnabledFragment() : new OnboardingPermissionDisabledFragment());
      } else if (isPrivateAnalyticsSupported
          && !isPrivateAnalyticsSet
          && isEnabled) {
        // private analytics promo
        setValue(new OnboardingPrivateAnalyticsFragment());
      } else {
        setValue(new SinglePageHomeFragment());
      }
    }
  }

  private void setIsEnabled(boolean isEnabled) {
    this.isEnabled = isEnabled;
    update();
  }

  private void setEnState(ExposureNotificationState enState) {
    this.enState = enState;
    update();
  }

  private void setIsOnboardingStatSet(boolean isOnboardingStateSet) {
    this.isOnboardingStateSet = isOnboardingStateSet;
    update();
  }

  private void setIsPrivateAnalyticsSet(boolean isPrivateAnalyticsSet) {
    this.isPrivateAnalyticsSet = isPrivateAnalyticsSet;
    update();
  }

  private void setIsPrivateAnalyticsSupported(boolean isPrivateAnalyticsSupported) {
    this.isPrivateAnalyticsSupported = isPrivateAnalyticsSupported;
    update();
  }

  private void setOnboardAsMigratingUser(boolean setOnboardAsMigratingUser) {
    this.onboardAsMigratingUser = setOnboardAsMigratingUser;
  }

}
