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

package com.google.android.apps.exposurenotification.onboarding;

import static com.google.android.apps.exposurenotification.home.ExposureNotificationActivity.HOME_FRAGMENT_TAG;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.android.apps.exposurenotification.home.ExposureNotificationViewModel;
import dagger.hilt.android.AndroidEntryPoint;
import javax.inject.Inject;
import org.threeten.bp.Duration;

/**
 * Splash screen of the on-boarding flow for app first launch.
 */
@AndroidEntryPoint
public class OnboardingStartFragment extends Fragment {

  private static final Duration SPLASH_DURATION = Duration.ofMillis(3500L);

  @Inject
  Clock clock;

  // Default to disabled fragment.
  private Fragment nextFragment = new OnboardingPermissionDisabledFragment();
  private long startTime;
  private CountDownTimer countdownTimer = null;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_onboarding_start, parent, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
    ExposureNotificationViewModel exposureNotificationViewModel =
        new ViewModelProvider(requireActivity()).get(ExposureNotificationViewModel.class);

    startTime = clock.currentTimeMillis();

    exposureNotificationViewModel
        .getIsEnabledLiveDataWithoutCache()
        .observe(
            getViewLifecycleOwner(),
            isEnabled -> {
              if (isEnabled) {
                // "Override" the default "disabled" fragment with the "enabled" one, if prior to
                // the countdown expiring, we learn that the API is enabled. If the "is enabled"
                // check fails we'll go through full onboarding, which may be redundant but is
                // harmless.
                nextFragment = new OnboardingPermissionEnabledFragment();
              }
            });

    view.setOnClickListener(v -> {
      if (clock.currentTimeMillis() - startTime > SPLASH_DURATION.toMillis()) {
        launchPermissionFragment();
      }
    });
  }

  @Override
  public void onResume() {
    super.onResume();
    startTimer();
  }

  @Override
  public void onPause() {
    super.onPause();
    if (countdownTimer != null) {
      countdownTimer.cancel();
    }
  }

  void startTimer() {
    if (countdownTimer != null) {
      countdownTimer.cancel();
    }
    countdownTimer = new CountDownTimer(SPLASH_DURATION.toMillis(), 1000) {
      public void onTick(long millisUntilFinished) {
      }

      public void onFinish() {
        launchPermissionFragment();
      }
    };
    countdownTimer.start();
  }

  private void launchPermissionFragment() {
    getParentFragmentManager()
        .beginTransaction()
        .replace(R.id.home_fragment, nextFragment, HOME_FRAGMENT_TAG)
        .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
        .commit();
  }

}
