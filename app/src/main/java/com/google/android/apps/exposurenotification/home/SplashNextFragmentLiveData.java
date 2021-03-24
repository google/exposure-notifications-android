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

import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import com.google.android.apps.exposurenotification.onboarding.OnboardingPermissionDisabledFragment;
import com.google.android.apps.exposurenotification.onboarding.OnboardingPermissionEnabledFragment;
import com.google.android.apps.exposurenotification.onboarding.OnboardingPrivateAnalyticsFragment;
import javax.annotation.Nullable;

/**
 * A {@link MediatorLiveData} that represents the next {@link Fragment} to go to after the splash
 * screen.
 */
public class SplashNextFragmentLiveData extends MediatorLiveData<Fragment> {

  private final @Nullable String action;
  private boolean isEnabled = false;
  private boolean isOnboardingStateSet = false;
  private boolean isPrivateAnalyticsSet = false;
  private boolean isPrivateAnalyticsSupported = false;
  private boolean isEnabledNewUXFlow = false;

  public static SplashNextFragmentLiveData create(
      @Nullable String action,
      LiveData<Boolean> enEnabledLiveData,
      LiveData<Boolean> isOnboardingStateSetLiveData,
      LiveData<Boolean> isPrivateAnalyticsSetLiveData,
      LiveData<Boolean> isPrivateAnalyticsSupportedLiveData,
      boolean isEnabledNewUXFlow) {
    SplashNextFragmentLiveData splashNextFragmentLiveData = new SplashNextFragmentLiveData(action);
    splashNextFragmentLiveData
        .addSource(enEnabledLiveData, splashNextFragmentLiveData::setIsEnabled);
    splashNextFragmentLiveData.addSource(isOnboardingStateSetLiveData,
        splashNextFragmentLiveData::setIsOnboardingStatSet);
    splashNextFragmentLiveData
        .addSource(isPrivateAnalyticsSetLiveData,
            splashNextFragmentLiveData::setIsPrivateAnalyticsSet);
    splashNextFragmentLiveData
        .addSource(isPrivateAnalyticsSupportedLiveData,
            splashNextFragmentLiveData::setIsPrivateAnalyticsSupported);
    splashNextFragmentLiveData.setIsEnabledNewUXFlow(isEnabledNewUXFlow);
    return splashNextFragmentLiveData;
  }

  private SplashNextFragmentLiveData(@Nullable String action) {
    this.action = action;
  }

  private void update() {
    // Always go to the home fragment if a notification was the intent.
    if (ACTION_LAUNCH_FROM_EXPOSURE_NOTIFICATION.equals(action)) {
      if (isEnabledNewUXFlow) {
        setValue((new SinglePageHomeFragment()));
      } else {
        setValue(new HomeFragment());
      }
      return;
    }
    if (!isOnboardingStateSet) {
      if (isEnabled) {
        setValue(new OnboardingPermissionEnabledFragment());
      } else {
        setValue(new OnboardingPermissionDisabledFragment());
      }
    } else {
      if (isPrivateAnalyticsSupported
          && !isPrivateAnalyticsSet
          && isEnabled) {
        // private analytics promo
        setValue(new OnboardingPrivateAnalyticsFragment());
      } else {
        if (isEnabledNewUXFlow) {
          setValue((new SinglePageHomeFragment()));
        } else {
          setValue(new HomeFragment());
        }
      }
    }
  }

  private void setIsEnabled(boolean isEnabled) {
    this.isEnabled = isEnabled;
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

  private void setIsEnabledNewUXFlow(boolean isEnabledNewUXFlow) {
    this.isEnabledNewUXFlow = isEnabledNewUXFlow;
    update();
  }
}
