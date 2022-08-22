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
import android.widget.Button;
import android.widget.LinearLayout;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission;
import androidx.annotation.NonNull;
import androidx.core.widget.NestedScrollView;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.SnackbarUtil;
import com.google.android.apps.exposurenotification.databinding.FragmentOnboardingPermissionDisabledBinding;
import com.google.android.apps.exposurenotification.home.ExposureNotificationViewModel.ExposureNotificationState;
import com.google.android.apps.exposurenotification.home.SinglePageHomeFragment;
import com.google.android.apps.exposurenotification.utils.UrlUtils;
import dagger.hilt.android.AndroidEntryPoint;

/**
 * Consent dialog for the API not already enabled case.
 */
@AndroidEntryPoint
public class OnboardingPermissionDisabledFragment extends AbstractOnboardingFragment {

  private static final String SAVED_INSTANCE_STATE_SHOULD_REQUEST_NOTIFICATION_PERMISSION =
      "should_request_notification_permission";

  private FragmentOnboardingPermissionDisabledBinding binding;
  private ExposureNotificationState state;

  private LinearLayout onboardingButtons;
  private Button nextButton;
  private NestedScrollView scroller;
  private boolean shouldShowPrivateAnalyticsOnboarding = false;
  private boolean shouldRequestForNotificationPermission = true;
  private ActivityResultLauncher<String> requestNotificationPermissionLauncher;

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent,
      Bundle savedInstanceState) {
    binding = FragmentOnboardingPermissionDisabledBinding.inflate(inflater, parent, false);
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

    onboardingButtons = binding.onboardingButtons;
    nextButton = binding.onboardingNextButton;
    scroller = binding.onboardingScroll;

    setupAppAnalyticsText();
    setupExposureNotificationDetailText();

    setupUpdateAtBottom(scroller, onboardingButtons, nextButton);

    exposureNotificationViewModel
        .getEnEnabledLiveData()
        .observe(
            getViewLifecycleOwner(),
            isEnabled -> {
              if (isEnabled) {
                onboardingViewModel.setAppAnalyticsState(
                    binding.onboardingAppAnalyticsSwitch.isChecked());
                binding.onboardingButtonsLoadingSwitcher.setDisplayedChild(1);
                onboardingViewModel.setOnboardedState(true);
                transitionNext();
              } else {
                binding.onboardingButtonsLoadingSwitcher.setDisplayedChild(0);
              }
            });

    exposureNotificationViewModel.getAreNotificationsEnabledLiveData()
        .observe(getViewLifecycleOwner(), areNotificationsEnabled -> {
          // If we don't know the notification permission state. Assume notifications are not
          // enabled. If its enabled already, android would ignore our permission request.
          if (areNotificationsEnabled.or(/* default = */ false)) {
            shouldRequestForNotificationPermission = false;
          }
        });

    exposureNotificationViewModel
        .getApiErrorLiveEvent()
        .observe(getViewLifecycleOwner(), exception -> SnackbarUtil
            .maybeShowRegularSnackbar(getView(), getString(R.string.generic_error_message)));

    exposureNotificationViewModel
        .getApiUnavailableLiveEvent()
        .observe(
            getViewLifecycleOwner(),
            unused -> {
              View rootView = getView();
              if (rootView != null) {
                SnackbarUtil.createLargeSnackbar(rootView, R.string.gms_unavailable_error)
                    .setAction(R.string.learn_more,
                        v -> UrlUtils.openUrl(rootView, getString(R.string.gms_info_link)))
                    .show();
              }
            });

    exposureNotificationViewModel
        .getStateLiveData()
        .observe(getViewLifecycleOwner(), state -> this.state = state);

    binding.onboardingNoThanksButton.setOnClickListener(v -> showSkipOnboardingDialog());

    exposureNotificationViewModel
        .getInFlightLiveData()
        .observe(getViewLifecycleOwner(), inFlight -> {
          nextButton.setEnabled(!inFlight);
          binding.onboardingNoThanksButton.setEnabled(!inFlight);
          binding.onboardingProgressBar.setVisibility(inFlight ? View.VISIBLE : View.INVISIBLE);
          nextButton.setVisibility(inFlight ? View.INVISIBLE : View.VISIBLE);
        });

    onboardingViewModel.shouldShowPrivateAnalyticsOnboardingLiveData()
        .observe(getViewLifecycleOwner(),
            shouldShowPrivateAnalyticsOnboarding ->
                this.shouldShowPrivateAnalyticsOnboarding = shouldShowPrivateAnalyticsOnboarding);

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

  private void setupAppAnalyticsText() {
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
  }

  private void setupExposureNotificationDetailText() {
    String learnMore = getString(R.string.learn_more);
    URLSpan learnMoreClickableSpan = UrlUtils.createURLSpan(
        getString(R.string.en_notification_info_link));
    String exposureNotificationDescription =
        getString(R.string.onboarding_exposure_notifications_description, learnMore);
    SpannableString spannableString = new SpannableString(exposureNotificationDescription);
    int learnMoreStart = exposureNotificationDescription.indexOf(learnMore);
    spannableString
        .setSpan(learnMoreClickableSpan, learnMoreStart, learnMoreStart + learnMore.length(),
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    binding.onboardingExposureNotificationsDetail.setText(spannableString);
    binding.onboardingExposureNotificationsDetail.setMovementMethod(
        LinkMovementMethod.getInstance());
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    binding = null;
  }

  @Override
  void skipOnboardingImmediate() {
    onboardingViewModel.setOnboardedState(false);
    SinglePageHomeFragment.transitionToSinglePageHomeFragment(this);
  }

  @Override
  void nextAction() {
    onboardingViewModel.markSmsInterceptNoticeSeenAsync();

    if (ExposureNotificationState.STORAGE_LOW.equals(this.state)) {
      showManageStorageDialog();
    } else {
      exposureNotificationViewModel.startExposureNotifications();
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
