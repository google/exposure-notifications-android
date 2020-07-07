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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.home.ExposureNotificationViewModel;
import com.google.android.apps.exposurenotification.home.HomeFragment;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.material.snackbar.Snackbar;

/** Page 2 of the onboarding flow {@link Fragment} where the API is started. */
public class OnboardingPermissionFragment extends Fragment {

  private static final String TAG = "OnboardingPermission";

  private ExposureNotificationViewModel exposureNotificationViewModel;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_onboarding_permission, parent, false);
  }

  @Override
  public void onViewCreated(View view, Bundle savedInstanceState) {
    exposureNotificationViewModel =
        new ViewModelProvider(requireActivity()).get(ExposureNotificationViewModel.class);

    exposureNotificationViewModel
        .getIsEnabledLiveData()
        .observe(
            getViewLifecycleOwner(),
            isEnabled -> {
              if (isEnabled) {
                transitionToFinishFragment();
              }
            });

    exposureNotificationViewModel
        .getApiErrorLiveEvent()
        .observe(getViewLifecycleOwner(), unused -> {
          View rootView = getView();
          if (rootView != null) {
            Snackbar.make(rootView, R.string.generic_error_message, Snackbar.LENGTH_LONG).show();
          }
        });

    Button nextButton = view.findViewById(R.id.onboarding_next_button);
    nextButton.setOnClickListener(v -> nextAction());
    ProgressBar progressBar = view.findViewById(R.id.onboarding_progress_bar);
    exposureNotificationViewModel
        .getInFlightLiveData()
        .observe(getViewLifecycleOwner(), inFlight -> {
          nextButton.setEnabled(!inFlight);
          progressBar.setVisibility(inFlight ? View.VISIBLE : View.INVISIBLE);
          nextButton.setText(inFlight ? "" : getString(R.string.btn_turn_on));
        });

    view.findViewById(R.id.onboarding_no_thanks_button).setOnClickListener(v -> skipOnboarding());
  }

  private void skipOnboarding() {
    ExposureNotificationSharedPreferences exposureNotificationSharedPreferences =
        new ExposureNotificationSharedPreferences(requireContext());
    exposureNotificationSharedPreferences.setOnboardedState(false);
    HomeFragment.transitionToHomeFragment(this);
  }

  private void nextAction() {
    exposureNotificationViewModel.startExposureNotifications();
  }

  private void transitionToFinishFragment() {
    // Remove previous fragment from the stack if it is there so we can't go back.
    getParentFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);

    getParentFragmentManager()
        .beginTransaction()
        .replace(R.id.home_fragment, new OnboardingFinishFragment(), HOME_FRAGMENT_TAG)
        .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
        .commit();
  }
}
