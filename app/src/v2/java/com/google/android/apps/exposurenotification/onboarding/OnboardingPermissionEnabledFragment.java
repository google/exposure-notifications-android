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
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.URLSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.RelativeLayout;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission;
import androidx.annotation.NonNull;
import androidx.core.widget.NestedScrollView;
import androidx.core.widget.NestedScrollView.OnScrollChangeListener;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.databinding.FragmentOnboardingPermissionEnabledBinding;
import com.google.android.apps.exposurenotification.home.BaseFragment;
import com.google.android.apps.exposurenotification.home.SinglePageHomeFragment;
import com.google.android.apps.exposurenotification.utils.UrlUtils;
import dagger.hilt.android.AndroidEntryPoint;

/**
 * Consent dialog for the API already enabled case.
 */
@AndroidEntryPoint
public class OnboardingPermissionEnabledFragment extends BaseFragment {

  private static final String SAVED_INSTANCE_STATE_SHOULD_REQUEST_NOTIFICATION_PERMISSION =
      "should_request_notification_permission";

  private FragmentOnboardingPermissionEnabledBinding binding;
  private OnboardingViewModel onboardingViewModel;

  private RelativeLayout onboardingButtons;
  private Button nextButton;
  private NestedScrollView scroller;

  private boolean shouldShowPrivateAnalyticsOnboarding = false;
  private boolean shouldRequestForNotificationPermission = true;
  private ActivityResultLauncher<String> requestNotificationPermissionLauncher;

  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
    binding = FragmentOnboardingPermissionEnabledBinding.inflate(inflater, parent, false);
    return binding.getRoot();
  }

  @Override
  public void onSaveInstanceState(@NonNull Bundle savedInstanceState) {
    super.onSaveInstanceState(savedInstanceState);
    savedInstanceState.putBoolean(SAVED_INSTANCE_STATE_SHOULD_REQUEST_NOTIFICATION_PERMISSION,
        shouldRequestForNotificationPermission);
  }

  @Override
  public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    requestNotificationPermissionLauncher = registerForActivityResult(new RequestPermission(),
        isGranted -> {
          // Proceed with on-boarding irrespective of the user's decision
          transitionNext();
        });

    onboardingViewModel = new ViewModelProvider(this).get(OnboardingViewModel.class);

    onboardingButtons = binding.onboardingButtons;
    nextButton = binding.onboardingNextButton;
    scroller = binding.onboardingScroll;

    // Set up additional information text for the app analytics.
    String learnMore = getString(R.string.learn_more);
    URLSpan learnMoreClickableSpan = UrlUtils.createURLSpan(getString(R.string.app_analytics_link));
    String onboardingMetricsDescription =
        getString(R.string.onboarding_metrics_description, learnMore);
    SpannableString spannableString = new SpannableString(onboardingMetricsDescription);
    int learnMoreStart = onboardingMetricsDescription.indexOf(learnMore);
    spannableString
        .setSpan(learnMoreClickableSpan, learnMoreStart, learnMoreStart + learnMore.length(),
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    binding.appAnalyticsDetail.setText(spannableString);
    binding.appAnalyticsDetail.setMovementMethod(LinkMovementMethod.getInstance());

    // Display the app analytics section only for those apps that should migrate and haven't
    // had the app analytics opt-in during Play onboarding.
    onboardingViewModel
        .getShouldShowAppAnalyticsLiveData()
        .observe(
            getViewLifecycleOwner(),
            optionalShowAppAnalytics -> {
              if (optionalShowAppAnalytics.isPresent()) {
                binding.appAnalyticsSection.setVisibility(
                    optionalShowAppAnalytics.get() ? View.VISIBLE : View.GONE);
              }
            });

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

    exposureNotificationViewModel.getAreNotificationsEnabledLiveData()
        .observe(getViewLifecycleOwner(), areNotificationsEnabled -> {
          // If we don't know the notification permission state. Assume notifications are not
          // enabled. If its enabled already, android would ignore our permission request.
          if (areNotificationsEnabled.or( /* default = */ false)) {
            shouldRequestForNotificationPermission = false;
          }
        });

    // If we are currently onboarding a migrating user, mark that now this user is onboarded.
    onboardingViewModel.maybeMarkMigratingUserAsOnboarded(requireContext());

    if (savedInstanceState != null &&
        savedInstanceState.containsKey(
            SAVED_INSTANCE_STATE_SHOULD_REQUEST_NOTIFICATION_PERMISSION)) {
      shouldRequestForNotificationPermission = savedInstanceState.getBoolean(
          SAVED_INSTANCE_STATE_SHOULD_REQUEST_NOTIFICATION_PERMISSION,
          true);
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    onboardingViewModel.updateShouldShowAppAnalytics(requireContext().getResources());
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
        if (binding.appAnalyticsSection.getVisibility() == View.VISIBLE) {
          // Store whether the user opted into the app analytics.
          onboardingViewModel.setAppAnalyticsState(
              binding.onboardingAppAnalyticsSwitch.isChecked());
        }
        transitionNext();
      });
      onboardingButtons.setElevation(0F);
    } else {
      nextButton.setText(R.string.btn_continue);
      nextButton.setOnClickListener(v2 -> scroller.fullScroll(View.FOCUS_DOWN));
      onboardingButtons
          .setElevation(getResources().getDimension(R.dimen.bottom_button_container_elevation));
    }
    if (nextButton.isAccessibilityFocused()) {
      // Let accessibility service announce when button text change.
      nextButton.sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
    }
  }

  private void transitionNext() {
    if (shouldRequestForNotificationPermission) {
      // Request for notification just once, If the user denies/dismisses the popup the first
      // time; they should be able to proceed with app on-boarding irrespective their decision.
      shouldRequestForNotificationPermission = false;
      // If the permission request could not be made, proceed with onboarding.
      if (exposureNotificationViewModel.maybeRequestNotificationPermission(requireActivity(),
          requestNotificationPermissionLauncher)) {
        return;
      }
    }

    if (shouldShowPrivateAnalyticsOnboarding) {
      OnboardingPrivateAnalyticsFragment.transitionToOnboardingPrivateAnalyticsFragment(this);
    } else {
      SinglePageHomeFragment.transitionToSinglePageHomeFragment(this);
    }
  }

}
