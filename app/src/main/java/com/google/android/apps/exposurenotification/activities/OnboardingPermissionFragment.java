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

package com.google.android.apps.exposurenotification.activities;

import static com.google.android.apps.exposurenotification.activities.ExposureNotificationActivity.HOME_FRAGMENT_TAG;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.activities.utils.ExposureNotificationPermissionHelper;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;

/**
 * Page 2 of the onboarding flow {@link Fragment} where permissions are gained and permissions set.
 */
public class OnboardingPermissionFragment extends Fragment {

  private static final String TAG = "OnboardingPermission";

  private final ExposureNotificationPermissionHelper permissionHelper;

  public OnboardingPermissionFragment() {
    permissionHelper = new ExposureNotificationPermissionHelper(this,
        this::transitionToFinishFragment);
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.fragment_onboarding_permission, parent, false);
    view.findViewById(R.id.onboarding_next_button).setOnClickListener(v -> acceptAction());
    view.findViewById(R.id.onboarding_no_thanks_button).setOnClickListener(v -> skipOnboarding());
    return view;
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    permissionHelper.onResolutionComplete(requestCode, resultCode, requireView());
  }

  private void skipOnboarding() {
    ExposureNotificationSharedPreferences exposureNotificationSharedPreferences =
        new ExposureNotificationSharedPreferences(requireContext());
    exposureNotificationSharedPreferences.setOnboardedState(false);
    HomeFragment.transitionToHomeFragment(this);
  }

  /**
   * When the user grants permission, save it to shared prefs so we don't show the onboarding
   * screens again then transition to the {@link OnboardingFinishFragment}
   */
  private void acceptAction() {
    ExposureNotificationSharedPreferences exposureNotificationSharedPreferences =
        new ExposureNotificationSharedPreferences(requireContext());
    exposureNotificationSharedPreferences.setOnboardedState(true);

    permissionHelper.optInAndStartExposureTracing(requireView());
  }

  private void transitionToFinishFragment() {
    // Remove previous fragment from the stack if it is there so we can't go back.
    getParentFragmentManager()
        .popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);

    getParentFragmentManager()
        .beginTransaction()
        .replace(R.id.home_fragment, new OnboardingFinishFragment(), HOME_FRAGMENT_TAG)
        .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
        .commit();
  }
}
