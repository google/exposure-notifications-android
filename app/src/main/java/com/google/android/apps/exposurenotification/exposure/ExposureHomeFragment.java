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

package com.google.android.apps.exposurenotification.exposure;

import static android.text.format.DateUtils.DAY_IN_MILLIS;

import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ViewFlipper;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.StringUtils;
import com.google.android.apps.exposurenotification.databinding.FragmentExposureHomeBinding;
import com.google.android.apps.exposurenotification.edgecases.MainEdgeCaseFragment;
import com.google.android.apps.exposurenotification.home.ExposureNotificationViewModel;
import com.google.android.apps.exposurenotification.home.ExposureNotificationViewModel.ExposureNotificationState;
import com.google.android.apps.exposurenotification.riskcalculation.ExposureClassification;
import com.google.android.apps.exposurenotification.storage.ExposureCheckEntity;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences.BadgeStatus;
import com.google.android.apps.exposurenotification.utils.UrlUtils;
import dagger.hilt.android.AndroidEntryPoint;

/**
 * Fragment for Exposures tab on home screen.
 */
@AndroidEntryPoint
public class ExposureHomeFragment extends Fragment {

  private static final String TAG = "ExposureHomeFragment";

  private static final int EXPOSURE_BANNER_EDGE_CASE_CHILD = 0;
  private static final int EXPOSURE_BANNER_EXPOSURE_CHILD = 1;

  private static final int EXPOSURE_INFORMATION_FLIPPER_NO_EXPOSURE_CHILD = 0;
  private static final int EXPOSURE_INFORMATION_FLIPPER_INFORMATION_AVAILABLE_CHILD = 1;

  private static final int HOW_EN_WORK_NO_EXPOSURE = 0;
  private static final int HOW_EN_WORK_EXPOSURE = 1;

  private FragmentExposureHomeBinding binding;
  private FragmentManager childFragmentManager;
  private ExposureNotificationViewModel exposureNotificationViewModel;
  private ExposureHomeViewModel exposureHomeViewModel;
  private Animation pulseSmall;
  private Animation pulseMedium;
  private Animation pulseLarge;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
    binding = FragmentExposureHomeBinding.inflate(inflater, parent, false);
    return binding.getRoot();
  }

  @Override
  public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
    childFragmentManager = getChildFragmentManager();

    exposureNotificationViewModel =
        new ViewModelProvider(requireActivity()).get(ExposureNotificationViewModel.class);
    exposureHomeViewModel =
        new ViewModelProvider(this, getDefaultViewModelProviderFactory())
            .get(ExposureHomeViewModel.class);

    exposureNotificationViewModel
        .getStateWithExposureClassificationLiveData()
        .observe(getViewLifecycleOwner(), this::refreshUiForStateAndClassification);

    binding.seeMoreButton.setOnClickListener(
        unused -> new ExposureChecksDialogFragment()
            .show(childFragmentManager, ExposureChecksDialogFragment.TAG));

    binding.exposureDetailsUrlButton.setOnClickListener(
        v -> UrlUtils.openUrl(requireContext(), ((TextView) v).getText().toString()));

    binding.howEnWorkButtonNoExposure.setOnClickListener(
        unused -> UrlUtils.openUrl(
            requireContext(), getString(R.string.how_exposure_notifications_work_actual_link)));

    binding.howEnWorkButtonExposure.setOnClickListener(
        unused -> UrlUtils.openUrl(
            requireContext(), getString(R.string.how_exposure_notifications_work_actual_link)));

    exposureHomeViewModel
        .getIsExposureClassificationNewLiveData()
        .observe(getViewLifecycleOwner(), badgeStatus ->
            binding.exposureDetailsNewBadge.setVisibility(
                (badgeStatus != BadgeStatus.DISMISSED) ? TextView.VISIBLE : TextView.GONE));

    exposureHomeViewModel
        .getIsExposureClassificationDateNewLiveData()
        .observe(getViewLifecycleOwner(), badgeStatus ->
            binding.exposureDateNewBadge.setVisibility(
                (badgeStatus != BadgeStatus.DISMISSED) ? TextView.VISIBLE : TextView.GONE));

    /*
     If this view is created, we assume that "new" badges were seen.
     If they were previously BadgeStatus.NEW, we now set them to BadgeStatus.SEEN
     */
    exposureHomeViewModel.tryTransitionExposureClassificationNew(BadgeStatus.NEW, BadgeStatus.SEEN);
    exposureHomeViewModel.tryTransitionExposureClassificationDateNew(BadgeStatus.NEW,
        BadgeStatus.SEEN);

    /*
     * Attach the edge-case logic as a fragment
     */
    if (childFragmentManager.findFragmentById(R.id.edge_case_fragment) == null) {
      Fragment childFragment = MainEdgeCaseFragment
          .newInstance(/* handleApiErrorLiveEvents= */ true, /* handleResolutions= */ false);
      childFragmentManager.beginTransaction()
          .replace(R.id.edge_case_fragment, childFragment)
          .commit();
    }

    // Fade-in animation for the recent exposure check UI elements.
    Animation fadeIn = new AlphaAnimation(0, 1);
    fadeIn.setDuration(2000);

    exposureHomeViewModel
        .getExposureChecksLiveData()
        .observe(
            getViewLifecycleOwner(), exposureChecks -> {
              if (exposureChecks.size() > 0) {
                binding.noRecentExposureCheck.setAnimation(fadeIn);
                binding.lastChecked.setAnimation(fadeIn);
                binding.seeMoreButton.setAnimation(fadeIn);

                ExposureCheckEntity lastCheck = exposureChecks.get(0);
                CharSequence lastCheckedDateTime = DateUtils.getRelativeDateTimeString(
                    requireContext(), lastCheck.getCheckTime().toEpochMilli(), DAY_IN_MILLIS, 0, 0);
                binding.lastChecked
                    .setText(getString(R.string.recent_check_last_checked, lastCheckedDateTime));

                binding.lastChecked.setVisibility(View.VISIBLE);
                binding.seeMoreButton.setVisibility(View.VISIBLE);
              }
              binding.noRecentExposureCheck.setVisibility(View.VISIBLE);
            });

    // Add a pulse animation.
    pulseSmall = AnimationUtils.loadAnimation(requireContext(), R.anim.pulsation_small);
    pulseMedium = AnimationUtils.loadAnimation(requireContext(), R.anim.pulsation_medium);
    pulseLarge = AnimationUtils.loadAnimation(requireContext(), R.anim.pulsation_large);
    startPulseAnimation();
  }

  @Override
  public void onResume() {
    super.onResume();
    refreshUi();
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    binding = null;
  }

  private void refreshUi() {
    exposureNotificationViewModel.refreshState();
  }

  /**
   * Update UI to match Exposure Notifications state and the current exposure risk classification.
   *
   * @param state                  the {@link ExposureNotificationState} of the API.
   * @param exposureClassification the {@link ExposureClassification} as returned by
   *                               DailySummaryRiskCalculator.
   */
  private void refreshUiForStateAndClassification(
      ExposureNotificationState state, ExposureClassification exposureClassification) {
    View rootView = getView();
    if (rootView == null) {
      return;
    }

    ViewFlipper bannerFlipper = binding.exposuresBannerFlipper;
    ViewFlipper exposureInformationFlipper = binding.exposureInformationFlipper;
    ViewFlipper howEnWorkButtonFlipper = binding.howEnWorkButtonFlipper;
    TextView exposureDetailsDateExposedText = binding.exposureDetailsDateExposedText;
    TextView exposureDetailsText = binding.exposureDetailsText;
    Button exposureDetailsUrlButton = binding.exposureDetailsUrlButton;

    // Indicates a "revoked" state transition.
    boolean isRevoked = exposureHomeViewModel.getIsExposureClassificationRevoked();

    bannerFlipper.setDisplayedChild(
        state == ExposureNotificationState.ENABLED
            ? EXPOSURE_BANNER_EXPOSURE_CHILD : EXPOSURE_BANNER_EDGE_CASE_CHILD);

    /*
     * Switch to the right view in the ViewFlippers depending on whether we have an exposure.
     * Fill in information accordingly
     */
    if (exposureClassification.getClassificationIndex()
        == ExposureClassification.NO_EXPOSURE_CLASSIFICATION_INDEX
        && !isRevoked) {
      /*
       * No exposure
       */
      if (state == ExposureNotificationState.ENABLED) {
        // Without any actionable item, show the full "No exposures" view and banner
        exposureInformationFlipper
            .setDisplayedChild(EXPOSURE_INFORMATION_FLIPPER_NO_EXPOSURE_CHILD);
        exposureInformationFlipper.setVisibility(ViewFlipper.VISIBLE);
        howEnWorkButtonFlipper.setDisplayedChild(HOW_EN_WORK_NO_EXPOSURE);
      } else {
        // If there is an item the user needs to act upon (e.g. enabling ble), we hide this view
        exposureInformationFlipper.setVisibility(ViewFlipper.GONE);
      }
    } else {
      /*
       * We've got an exposure! Fill in all the details (exposure date, text, further info url)
       */
      bannerFlipper.setVisibility(
          state == ExposureNotificationState.ENABLED ? View.GONE : View.VISIBLE);
      exposureInformationFlipper
          .setDisplayedChild(EXPOSURE_INFORMATION_FLIPPER_INFORMATION_AVAILABLE_CHILD);
      exposureInformationFlipper.setVisibility(ViewFlipper.VISIBLE);
      exposureDetailsDateExposedText.setText(
          StringUtils.epochDaysTimestampToMediumUTCDateString(
              exposureClassification.getClassificationDate(), getResources().getConfiguration().locale)
      );

      // Hide all the components we don't want to display once there is an exposure reported
      howEnWorkButtonFlipper.setDisplayedChild(HOW_EN_WORK_EXPOSURE);
      Fragment exposureChecksFragment = childFragmentManager
          .findFragmentByTag(ExposureChecksDialogFragment.TAG);
      if (exposureChecksFragment != null) {
        DialogFragment exposureChecksDialog = (DialogFragment) exposureChecksFragment;
        exposureChecksDialog.dismiss();
      }

      // Catch the revoked edge case
      if (isRevoked) {
        exposureDetailsUrlButton.setText(R.string.exposure_details_url_revoked);
        exposureDetailsText.setText(R.string.exposure_details_text_revoked);
      }

      // All the other "normal" classifications
      else {
        switch (exposureClassification.getClassificationIndex()) {
          case 1:
            exposureDetailsUrlButton.setText(R.string.exposure_details_url_1);
            exposureDetailsText.setText(R.string.exposure_details_text_1);
            break;
          case 2:
            exposureDetailsUrlButton.setText(R.string.exposure_details_url_2);
            exposureDetailsText.setText(R.string.exposure_details_text_2);
            break;
          case 3:
            exposureDetailsUrlButton.setText(R.string.exposure_details_url_3);
            exposureDetailsText.setText(R.string.exposure_details_text_3);
            break;
          case 4:
            exposureDetailsUrlButton.setText(R.string.exposure_details_url_4);
            exposureDetailsText.setText(R.string.exposure_details_text_4);
            break;
        }
      }
    }

    updateFlipperDividerVisibility(state);
  }

  /**
   * The divider-bar between the ViewFlippers (Exposure module state/settings flipper and
   * ExposureClassification flipper) should only be visible if BOTH view flippers are visible.
   */
  private void updateFlipperDividerVisibility(ExposureNotificationState state) {
    if (binding.exposureInformationFlipper.getVisibility() == ViewFlipper.VISIBLE
        && state != ExposureNotificationState.ENABLED) {
      binding.viewFlipperDivider.setVisibility(View.VISIBLE);
    } else {
      binding.viewFlipperDivider.setVisibility(View.GONE);
    }
  }

  /**
   * Start the pulses.
   */
  private void startPulseAnimation() {
    binding.pulseSmall.startAnimation(pulseSmall);
    binding.pulseMedium.startAnimation(pulseMedium);
    binding.pulseLarge.startAnimation(pulseLarge);
  }

}
