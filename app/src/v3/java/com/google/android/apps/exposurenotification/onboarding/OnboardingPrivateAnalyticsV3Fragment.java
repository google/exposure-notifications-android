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

import static com.google.android.apps.exposurenotification.onboarding.OnboardingV3Fragment.ONBOARDING_FRAGMENT_TAG;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.databinding.FragmentOnboardingPrivateAnalyticsV3Binding;
import dagger.hilt.android.AndroidEntryPoint;

/**
 * Consent dialog private analytics.
 */
@AndroidEntryPoint
public class OnboardingPrivateAnalyticsV3Fragment extends
    AbstractOnboardingPrivateAnalyticsFragment {

  private static final String TAG = "PrioOnboardingV3";

  private FragmentOnboardingPrivateAnalyticsV3Binding binding;

  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
    binding = FragmentOnboardingPrivateAnalyticsV3Binding.inflate(inflater, parent, false);
    return binding.getRoot();
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    binding = null;
  }

  @Override
  void dontSharePrivateAnalytics() {
    onboardingViewModel.setPrivateAnalyticsState(false);
    // Call RESULT_OK and finish the activity.
    requireActivity().setResult(Activity.RESULT_OK);
    requireActivity().finish();
  }

  @Override
  void sharePrivateAnalytics() {
    Log.d(TAG, "Onboarding complete: private analytics enabled.");
    onboardingViewModel.setPrivateAnalyticsState(true);
    // Trigger a one-time submit
    onboardingViewModel.submitPrivateAnalytics();
    // Call RESULT_OK and finish the activity.
    requireActivity().setResult(Activity.RESULT_OK);
    requireActivity().finish();
  }

  /**
   * Helper to transition from one fragment to {@link OnboardingPrivateAnalyticsV3Fragment}.
   *
   * @param fragment The fragment to transit from
   */
  public static void transitionToOnboardingPrivateAnalyticsV3Fragment(Fragment fragment) {
    // Remove previous fragment from the stack if it is there.
    fragment
        .getParentFragmentManager()
        .popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);

    FragmentTransaction fragmentTransaction =
        fragment.getParentFragmentManager().beginTransaction();
    fragmentTransaction
        .replace(
            R.id.onboarding_fragment,
            new OnboardingPrivateAnalyticsV3Fragment(),
            ONBOARDING_FRAGMENT_TAG);
    fragmentTransaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
    fragmentTransaction.commit();
  }

}
