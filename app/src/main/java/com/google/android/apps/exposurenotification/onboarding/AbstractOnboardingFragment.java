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

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.core.widget.NestedScrollView;
import androidx.core.widget.NestedScrollView.OnScrollChangeListener;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.StorageManagementHelper;
import com.google.android.apps.exposurenotification.home.BaseFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.base.Optional;

/**
 * Abstract superclass fragment for onboarding fragments.
 */
public abstract class AbstractOnboardingFragment extends BaseFragment {

  static final String SAVED_INSTANCE_STATE_SKIP_DIALOG_SHOWN =
      "OnboardingFragment.SAVED_INSTANCE_STATE_SKIP_DIALOG_SHOWN";
  static final String SAVED_INSTANCE_STATE_MANAGE_STORAGE_DIALOG_SHOWN =
      "OnboardingFragment.SAVED_INSTANCE_STATE_MANAGE_STORAGE_DIALOG_SHOWN";

  private boolean skipDialogShown = false;
  private boolean manageStorageDialogShown = false;
  private Optional<Boolean> lastUpdateAtBottom = Optional.absent();

  protected OnboardingViewModel onboardingViewModel;

  @Override
  public abstract View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent,
      Bundle savedInstanceState);

  @CallSuper
  @Override
  public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    requireActivity().setTitle(R.string.onboarding_opt_in_title);

    onboardingViewModel = new ViewModelProvider(requireActivity()).get(OnboardingViewModel.class);

    if (savedInstanceState != null) {
      if (savedInstanceState.getBoolean(SAVED_INSTANCE_STATE_SKIP_DIALOG_SHOWN, false)) {
        showSkipOnboardingDialog();
      }
      if (savedInstanceState.getBoolean(SAVED_INSTANCE_STATE_MANAGE_STORAGE_DIALOG_SHOWN, false)) {
        showManageStorageDialog();
      }
    }
  }

  /**
   * Performs the next action in the onboarding flow triggered upon clicking the "Turn on" button.
   */
  abstract void nextAction();

  /**
   * Immediately ends the whole onboarding flow upon the user decision (expressed by clicking
   * the "Not now" button).
   */
  abstract void skipOnboardingImmediate();

  @Override
  public void onSaveInstanceState(@NonNull Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putBoolean(SAVED_INSTANCE_STATE_SKIP_DIALOG_SHOWN, skipDialogShown);
    outState.putBoolean(SAVED_INSTANCE_STATE_MANAGE_STORAGE_DIALOG_SHOWN, manageStorageDialogShown);
  }

  /**
   * Set up UI components to update the UI depending on the scrolling.
   */
  void setupUpdateAtBottom(NestedScrollView scroller, LinearLayout onboardingButtons,
      Button nextButton) {
    updateAtBottom(scroller, onboardingButtons, nextButton, false);
    scroller.setOnScrollChangeListener(
        (OnScrollChangeListener) (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
          if (scroller.getChildAt(0).getBottom()
              <= (scroller.getHeight() + scroller.getScrollY())) {
            updateAtBottom(scroller, onboardingButtons, nextButton, true);
          } else {
            updateAtBottom(scroller, onboardingButtons, nextButton, false);
          }
        });
    ViewTreeObserver observer = scroller.getViewTreeObserver();
    observer.addOnGlobalLayoutListener(() -> {
      if (scroller.getMeasuredHeight() >= scroller.getChildAt(0).getHeight()) {
        // Not scrollable so set at bottom.
        updateAtBottom(scroller, onboardingButtons, nextButton, true);
      }
    });
  }

  /**
   * Update the UI depending on whether scrolling is at the bottom or not.
   */
  void updateAtBottom(NestedScrollView scroller, LinearLayout onboardingButtons,
      Button nextButton, boolean atBottom) {
    if (lastUpdateAtBottom.isPresent() && lastUpdateAtBottom.get() == atBottom) {
      // Don't update if already at set.
      return;
    }
    lastUpdateAtBottom = Optional.of(atBottom);
    if (atBottom) {
      nextButton.setText(R.string.btn_turn_on);
      nextButton.setOnClickListener(v2 -> nextAction());
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

  /**
   * Display the "Free up storage space" dialog.
   */
  void showManageStorageDialog() {
    if (manageStorageDialogShown) {
      return;
    }
    manageStorageDialogShown = true;
    new MaterialAlertDialogBuilder(requireContext(), R.style.ExposureNotificationAlertDialogTheme)
        .setTitle(R.string.onboarding_free_up_storage_title)
        .setMessage(R.string.storage_low_warning)
        .setCancelable(true)
        .setNegativeButton(R.string.btn_cancel, (dialog, i) -> {
          manageStorageDialogShown = false;
          dialog.cancel();
        })
        .setPositiveButton(R.string.manage_storage,
            (dialog, i) -> {
              manageStorageDialogShown = false;
              StorageManagementHelper.launchStorageManagement(getContext());
            })
        .setOnCancelListener(dialog -> manageStorageDialogShown = false)
        .show();
  }

  /**
   * Display the "Keep Exposure Notification turned off?" dialog.
   */
  void showSkipOnboardingDialog() {
    if (skipDialogShown) {
      return;
    }
    skipDialogShown = true;
    new MaterialAlertDialogBuilder(requireContext(), R.style.ExposureNotificationAlertDialogTheme)
        .setTitle(R.string.onboarding_confirm_later_title)
        .setMessage(R.string.onboarding_confirm_later_detail)
        .setCancelable(true)
        .setNegativeButton(R.string.btn_no_go_back, (dialog, i) -> {
          skipDialogShown = false;
          dialog.cancel();
        })
        .setPositiveButton(R.string.btn_yes_continue, (dialog, i) -> {
          skipDialogShown = false;
          skipOnboardingImmediate();
        })
        .setOnCancelListener(dialog -> skipDialogShown = false).show();
  }
}
