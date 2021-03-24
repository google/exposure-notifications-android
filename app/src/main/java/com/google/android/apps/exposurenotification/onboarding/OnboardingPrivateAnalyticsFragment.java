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
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.URLSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.databinding.FragmentOnboardingPrivateAnalyticsBinding;
import com.google.android.apps.exposurenotification.home.HomeFragment;
import com.google.android.apps.exposurenotification.home.SinglePageHomeFragment;
import dagger.hilt.android.AndroidEntryPoint;

/**
 * Consent dialog private analytics.
 */
@AndroidEntryPoint
public class OnboardingPrivateAnalyticsFragment extends Fragment {

  private static final String TAG = "PrioOnboarding";

  private FragmentOnboardingPrivateAnalyticsBinding binding;
  private OnboardingViewModel onboardingViewModel;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
    binding = FragmentOnboardingPrivateAnalyticsBinding.inflate(inflater, parent, false);
    return binding.getRoot();
  }

  @Override
  public void onViewCreated(View view, Bundle savedInstanceState) {
    onboardingViewModel = new ViewModelProvider(this).get(OnboardingViewModel.class);

    binding.privateAnalyticsDismiss.setOnClickListener(
        v -> onboardingViewModel.setPrivateAnalyticsState(false));
    binding.privateAnalyticsAccept.setOnClickListener(
        v -> onboardingViewModel.setPrivateAnalyticsState(true));

    onboardingViewModel.isPrivateAnalyticsStateSetLiveData()
        .observe(getViewLifecycleOwner(), hasPrivateAnalyticsSet -> {
          if (hasPrivateAnalyticsSet) {
            Log.d(TAG, "Onboarding complete: private analytics enabled.");
            // Trigger a one-time submit
            onboardingViewModel.submitPrivateAnalytics();
            if (onboardingViewModel.isNewUxFlowEnabled()) {
              SinglePageHomeFragment.transitionToSinglePageHomeFragment(this);
            } else {
              HomeFragment.transitionToHomeFragment(this);
            }
          }
        });

    String learnMore = getString(R.string.private_analytics_footer_learn_more);
    URLSpan learnMoreClickableSpan = new URLSpan(getString(R.string.private_analytics_link));
    String footer = getString(R.string.private_analytics_footer_onboarding, learnMore);
    SpannableString footerSpannableString = new SpannableString(footer);
    int learnMoreStart = footer.indexOf(learnMore);
    footerSpannableString
        .setSpan(learnMoreClickableSpan, learnMoreStart, learnMoreStart + learnMore.length(),
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    binding.privateAnalyticsFooter.setText(footerSpannableString);
    binding.privateAnalyticsFooter.setMovementMethod(LinkMovementMethod.getInstance());
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    binding = null;
  }

  /**
   * Helper to transition from one fragment to {@link HomeFragment}
   *
   * @param fragment The fragment to transit from
   */
  public static void transitionToOnboardingPrivateAnalyticsFragment(Fragment fragment) {
    // Remove previous fragment from the stack if it is there.
    fragment
        .getParentFragmentManager()
        .popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);

    FragmentTransaction fragmentTransaction =
        fragment.getParentFragmentManager().beginTransaction();
    fragmentTransaction
        .replace(R.id.home_fragment, new OnboardingPrivateAnalyticsFragment(), HOME_FRAGMENT_TAG);
    fragmentTransaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
    fragmentTransaction.commit();
  }

}
