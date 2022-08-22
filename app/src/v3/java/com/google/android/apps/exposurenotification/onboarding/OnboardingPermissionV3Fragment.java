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

import static android.app.Activity.RESULT_FIRST_USER;
import static com.google.android.apps.exposurenotification.onboarding.OnboardingV3Fragment.EXTRA_NOT_NOW_CONFIRMATION;

import android.app.Activity;
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
import androidx.lifecycle.ViewModelProvider;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.databinding.FragmentOnboardingV3Binding;
import com.google.android.apps.exposurenotification.home.ExposureNotificationViewModel.ExposureNotificationState;
import com.google.android.apps.exposurenotification.settings.PrivateAnalyticsViewModel;
import com.google.android.apps.exposurenotification.utils.UrlUtils;
import dagger.hilt.android.AndroidEntryPoint;

/**
 * Consent dialog for the API not already enabled case.
 */
@AndroidEntryPoint
public class OnboardingPermissionV3Fragment extends AbstractOnboardingFragment {

  private static final String SAVED_INSTANCE_STATE_SHOULD_REQUEST_NOTIFICATION_PERMISSION =
      "should_request_notification_permission";
  private static final int RESULT_NOT_NOW = RESULT_FIRST_USER + 4;

  private FragmentOnboardingV3Binding binding;
  private PrivateAnalyticsViewModel privateAnalyticsViewModel;
  private ExposureNotificationState state;

  private LinearLayout onboardingButtons;
  private NestedScrollView scroller;
  private Button continueButton;
  private boolean showNotNowConfirmation = false;
  private boolean isLocationEnableRequired = true;
  private boolean shouldRequestForNotificationPermission = true;
  private ActivityResultLauncher<String> requestNotificationPermissionLauncher;

  public static OnboardingPermissionV3Fragment newInstance(boolean notNowConfirmation) {
    OnboardingPermissionV3Fragment onboardingPermissionV3Fragment =
        new OnboardingPermissionV3Fragment();
    Bundle args = new Bundle();
    args.putBoolean(EXTRA_NOT_NOW_CONFIRMATION, notNowConfirmation);
    onboardingPermissionV3Fragment.setArguments(args);
    return onboardingPermissionV3Fragment;
  }

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent,
      Bundle savedInstanceState) {
    binding = FragmentOnboardingV3Binding.inflate(inflater, parent, false);
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

    privateAnalyticsViewModel = new ViewModelProvider(this).get(PrivateAnalyticsViewModel.class);

    onboardingButtons = binding.onboardingButtons;
    continueButton = binding.onboardingContinueButton;
    scroller = binding.onboardingScroll;

    showNotNowConfirmation =
        getArguments() != null && getArguments().getBoolean(EXTRA_NOT_NOW_CONFIRMATION, false);

    setupUpdateAtBottom(scroller, onboardingButtons, continueButton);

    setupExposureNotificationDetailText();

    exposureNotificationViewModel
        .getStateLiveData()
        .observe(getViewLifecycleOwner(), state -> this.state = state);

    binding.onboardingNotNowButton.setOnClickListener(v -> {
      if (showNotNowConfirmation) {
        // Show "Keep EN off" confirmation dialog.
        showSkipOnboardingDialog();
      } else {
        // Don't show "Keep EN off" confirmation dialog and return immediately.
        skipOnboardingImmediate();
      }
    });

    exposureNotificationViewModel.getAreNotificationsEnabledLiveData()
        .observe(getViewLifecycleOwner(), areNotificationsEnabled -> {
          // If we don't know the notification permission state. Assume notifications are not
          // enabled. If its enabled already, android would ignore our permission request.
          if (areNotificationsEnabled.or( /* default = */ false)) {
            shouldRequestForNotificationPermission = false;
          }
        });

    exposureNotificationViewModel.getIsLocationEnableRequired()
        .observe(getViewLifecycleOwner(), iLER -> isLocationEnableRequired = iLER);

    exposureNotificationViewModel
        .getEnEnabledLiveDataNoCache()
        .observe(
            getViewLifecycleOwner(),
            isEnabled -> {
              if (isEnabled) {
                onboardingViewModel.setOnboardedState(true);
                // We now can call RESULT_OK.
                onboardingViewModel.setResultOk(true);
                transitionNext();
              }
            });

    if (savedInstanceState != null &&
        savedInstanceState.containsKey(
            SAVED_INSTANCE_STATE_SHOULD_REQUEST_NOTIFICATION_PERMISSION)) {
      shouldRequestForNotificationPermission = savedInstanceState.getBoolean(
          SAVED_INSTANCE_STATE_SHOULD_REQUEST_NOTIFICATION_PERMISSION,
          true);
    }
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
    requireActivity().setResult(RESULT_NOT_NOW);
    requireActivity().finish();
  }

  @Override
  void nextAction() {
    onboardingViewModel.markSmsInterceptNoticeSeenAsync();

    if (ExposureNotificationState.STORAGE_LOW.equals(this.state)) {
      showManageStorageDialog();
    } else {
      if (isLocationEnableRequired) {
        exposureNotificationViewModel.startExposureNotifications();
      } else {
        onboardingViewModel.setOnboardedState(true);
        // We now can call RESULT_OK.
        onboardingViewModel.setResultOk(true);
        transitionNext();
      }
    }
  }

  private void transitionNext() {
    onboardingViewModel.markSmsInterceptNoticeSeenAsync();

    if (shouldRequestForNotificationPermission) {
      // Request for notification just once, If the user denies or dismisses the popup the first
      // time- users should be able to proceed with app on-boarding irrespective their decision.
      shouldRequestForNotificationPermission = false;
      // If the permission request could not be made, proceed with onboarding.
      if (exposureNotificationViewModel.maybeRequestNotificationPermission(requireActivity(),
          requestNotificationPermissionLauncher)) {
        return;
      }
    }

    if (privateAnalyticsViewModel.showPrivateAnalyticsSection()) {
      OnboardingPrivateAnalyticsV3Fragment.transitionToOnboardingPrivateAnalyticsV3Fragment(this);
    } else {
      // Call RESULT_OK and finish the activity.
      requireActivity().setResult(Activity.RESULT_OK);
      requireActivity().finish();
    }
  }
}
