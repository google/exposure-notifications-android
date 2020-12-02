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
import androidx.core.widget.NestedScrollView;
import androidx.core.widget.NestedScrollView.OnScrollChangeListener;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.home.HomeFragment;
import dagger.hilt.android.AndroidEntryPoint;

/**
 * Consent dialog for the API already enabled case.
 */
@AndroidEntryPoint
public class OnboardingPermissionEnabledFragment extends Fragment {

  private OnboardingViewModel onboardingViewModel;

  private RelativeLayout onboardingButtons;
  private Button nextButton;
  private NestedScrollView scroller;

  private boolean shouldShowPrivateAnalyticsOnboarding = false;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_onboarding_permission_enabled, parent, false);
  }

  @Override
  public void onViewCreated(View view, Bundle savedInstanceState) {
    onboardingViewModel = new ViewModelProvider(this).get(OnboardingViewModel.class);

    onboardingButtons = view.findViewById(R.id.onboarding_buttons);
    nextButton = view.findViewById(R.id.onboarding_next_button);
    scroller = view.findViewById(R.id.onboarding_scroll);

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
      HomeFragment.transitionToHomeFragment(this);
    }
  }

}
