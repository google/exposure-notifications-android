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

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.RelativeLayout;
import androidx.annotation.NonNull;
import androidx.core.widget.NestedScrollView;
import androidx.core.widget.NestedScrollView.OnScrollChangeListener;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.databinding.FragmentOnboardingPermissionEnabledBinding;
import com.google.android.apps.exposurenotification.home.BaseFragment;
import com.google.android.apps.exposurenotification.home.SinglePageHomeFragment;
import dagger.hilt.android.AndroidEntryPoint;

/**
 * Consent dialog for the API already enabled case.
 */
@AndroidEntryPoint
public class OnboardingPermissionEnabledFragment extends BaseFragment {

  private FragmentOnboardingPermissionEnabledBinding binding;
  private OnboardingViewModel onboardingViewModel;

  private RelativeLayout onboardingButtons;
  private Button nextButton;
  private NestedScrollView scroller;

  private boolean shouldShowPrivateAnalyticsOnboarding = false;

  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
    binding = FragmentOnboardingPermissionEnabledBinding.inflate(inflater, parent, false);
    return binding.getRoot();
  }

  @Override
  public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    onboardingViewModel = new ViewModelProvider(this).get(OnboardingViewModel.class);

    onboardingButtons = binding.onboardingButtons;
    nextButton = binding.onboardingNextButton;
    scroller = binding.onboardingScroll;

    // Set to false, this will be overridden by scroll events or if not scrollable.
    updateAtBottom(false);
    scroller.setOnScrollChangeListener(
        (OnScrollChangeListener) (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
          if (scroller.getChildAt(0).getBottom()
              <= (scroller.getHeight() + scroller.getScrollY())) {
            updateAtBottom(true);
          } else {
            updateAtBottom(false);
          }
        });
    ViewTreeObserver observer = scroller.getViewTreeObserver();
    observer.addOnGlobalLayoutListener(() -> {
      if (scroller.getMeasuredHeight() >= scroller.getChildAt(0).getHeight()) {
        // Not scrollable so set at bottom.
        updateAtBottom(true);
      }
    });

    onboardingViewModel.shouldShowPrivateAnalyticsOnboardingLiveData()
        .observe(getViewLifecycleOwner(),
            shouldShowPrivateAnalyticsOnboarding ->
                this.shouldShowPrivateAnalyticsOnboarding = shouldShowPrivateAnalyticsOnboarding);
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    binding = null;
  }

  /**
   * Update the UI depending on whether scrolling is at the bottom or not.
   */
  private void updateAtBottom(boolean atBottom) {
    if (atBottom) {
      nextButton.setText(R.string.btn_got_it);
      nextButton.setOnClickListener(v2 -> {
        onboardingViewModel.setOnboardedState(true);
        transitionNext();
      });
      onboardingButtons.setElevation(0F);
    } else {
      nextButton.setText(R.string.btn_continue);
      nextButton.setOnClickListener(v2 -> scroller.fullScroll(View.FOCUS_DOWN));
      onboardingButtons
          .setElevation(getResources().getDimension(R.dimen.onboarding_button_elevation));
    }
    if (nextButton.isAccessibilityFocused()) {
      // Let accessibility service announce when button text change.
      nextButton.sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
    }
  }

  private void transitionNext() {
    if (shouldShowPrivateAnalyticsOnboarding) {
      OnboardingPrivateAnalyticsFragment.transitionToOnboardingPrivateAnalyticsFragment(this);
    } else {
      SinglePageHomeFragment.transitionToSinglePageHomeFragment(this);
    }
  }

}
