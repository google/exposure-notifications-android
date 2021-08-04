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

package com.google.android.apps.exposurenotification.onboarding;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.databinding.FragmentOnboardingBinding;
import com.google.android.apps.exposurenotification.home.BaseActivity;
import com.google.android.apps.exposurenotification.home.BaseFragment;
import dagger.hilt.android.AndroidEntryPoint;
import java.util.Objects;

/**
 * Main fragment that handles the onboarding flow.
 */
@AndroidEntryPoint
public class OnboardingV3Fragment extends BaseFragment {

  public static final String ONBOARDING_FRAGMENT_TAG = "OnboardingFragment.ONBOARDING_FRAGMENT_TAG";
  public static final String EXTRA_NOT_NOW_CONFIRMATION = "not_now_confirmation";

  private static final String SAVED_INSTANCE_STATE_FRAGMENT_KEY =
      "OnboardingFragment.SAVED_INSTANCE_STATE_FRAGMENT_KEY";

  private FragmentOnboardingBinding binding;

  public static OnboardingV3Fragment newInstance(boolean notNowConfirmation) {
    OnboardingV3Fragment onboardingV3Fragment = new OnboardingV3Fragment();
    Bundle args = new Bundle();
    args.putBoolean(EXTRA_NOT_NOW_CONFIRMATION, notNowConfirmation);
    onboardingV3Fragment.setArguments(args);
    return onboardingV3Fragment;
  }

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent,
      Bundle savedInstanceState) {
    binding = FragmentOnboardingBinding.inflate(getLayoutInflater());
    return binding.getRoot();
  }

  @Override
  public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    getActivity().setTitle(R.string.onboarding_activity_title);

    Fragment onboardingFragment;
    if (savedInstanceState != null) {
      // If this is a configuration change such as rotation, restore the onboarding fragment that
      // was previously saved in onSaveInstanceState.
      onboardingFragment = Objects.requireNonNull(getChildFragmentManager()
          .getFragment(savedInstanceState, SAVED_INSTANCE_STATE_FRAGMENT_KEY));
    } else {
      // This is a fresh launch.
      onboardingFragment = OnboardingPermissionV3Fragment.newInstance(
          getArguments() != null && getArguments().getBoolean(EXTRA_NOT_NOW_CONFIRMATION, false));
    }

    getChildFragmentManager()
        .beginTransaction()
        .replace(
            R.id.onboarding_fragment,
            onboardingFragment,
            ONBOARDING_FRAGMENT_TAG)
        .commit();
  }

  /**
   * Save the fragment across rotations or other configuration changes.
   *
   * @param outState passed to onCreate when the app finishes the configuration change.
   */
  @Override
  public void onSaveInstanceState(@NonNull Bundle outState) {
    super.onSaveInstanceState(outState);
    getChildFragmentManager()
        .putFragment(
            outState,
            SAVED_INSTANCE_STATE_FRAGMENT_KEY,
            Objects.requireNonNull(
                getChildFragmentManager().findFragmentByTag(ONBOARDING_FRAGMENT_TAG)));
  }

  @Override
  public boolean onBackPressed() {
    OnboardingViewModel onboardingViewModel =
        new ViewModelProvider(this).get(OnboardingViewModel.class);
    BaseActivity parentActivity = requireBaseActivity();
    if (onboardingViewModel.isResultOkSet()) {
      parentActivity.setResult(Activity.RESULT_OK);
    }
    parentActivity.finish();
    return true;
  }

}
