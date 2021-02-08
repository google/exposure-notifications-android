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

import static com.google.android.apps.exposurenotification.home.ExposureNotificationActivity.HOME_FRAGMENT_TAG;

import android.content.Intent;
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
import com.google.android.apps.exposurenotification.databinding.FragmentOnboardingStartBinding;
import dagger.hilt.android.AndroidEntryPoint;
import javax.inject.Inject;
import org.threeten.bp.Duration;

/**
 * Splash screen for app first launch.
 */
@AndroidEntryPoint
public class SplashFragment extends Fragment {

  private static final Duration SPLASH_DURATION = Duration.ofMillis(2500L);

  @Inject
  Clock clock;

  // Default to home fragment.
  private Fragment nextFragment = new HomeFragment();
  private long startTime;
  private CountDownTimer countdownTimer = null;

  private FragmentOnboardingStartBinding binding;
  private ExposureNotificationViewModel exposureNotificationViewModel;
  private SplashViewModel splashViewModel;

  public static SplashFragment newInstance() {
    return new SplashFragment();
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
    binding = FragmentOnboardingStartBinding.inflate(inflater, parent, false);
    return binding.getRoot();
  }

  @Override
  public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
    startTime = clock.currentTimeMillis();

    exposureNotificationViewModel =
        new ViewModelProvider(requireActivity()).get(ExposureNotificationViewModel.class);
    splashViewModel = new ViewModelProvider(this).get(SplashViewModel.class);

    Intent intent = getActivity().getIntent();
    splashViewModel
        .getNextFragmentLiveData(
            intent == null ? null : intent.getAction(),
            exposureNotificationViewModel.getEnEnabledLiveData())
        .observe(getViewLifecycleOwner(), nextFragment -> this.nextFragment = nextFragment);

    view.setOnClickListener(v -> {
      if (clock.currentTimeMillis() - startTime > SPLASH_DURATION.toMillis()) {
        launchNextFragment();
      }
    });
  }

  @Override
  public void onResume() {
    super.onResume();
    exposureNotificationViewModel.refreshState();
    startTimer();
  }

  @Override
  public void onPause() {
    super.onPause();
    if (countdownTimer != null) {
      countdownTimer.cancel();
    }
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    binding = null;
  }

  void startTimer() {
    if (countdownTimer != null) {
      countdownTimer.cancel();
    }
    countdownTimer = new CountDownTimer(SPLASH_DURATION.toMillis(), 1000) {
      public void onTick(long millisUntilFinished) {
      }

      public void onFinish() {
        launchNextFragment();
      }
    };
    countdownTimer.start();
  }

  private void launchNextFragment() {
    getParentFragmentManager()
        .beginTransaction()
        .replace(R.id.home_fragment, nextFragment, HOME_FRAGMENT_TAG)
        .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
        .commit();
  }

}
