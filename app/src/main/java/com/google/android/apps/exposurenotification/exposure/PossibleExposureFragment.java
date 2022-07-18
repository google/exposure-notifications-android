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

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.BuildUtils;
import com.google.android.apps.exposurenotification.common.BuildUtils.Type;
import com.google.android.apps.exposurenotification.common.IntentUtil;
import com.google.android.apps.exposurenotification.databinding.FragmentPossibleExposureBinding;
import com.google.android.apps.exposurenotification.home.BaseFragment;
import com.google.android.apps.exposurenotification.riskcalculation.ExposureClassification;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences.BadgeStatus;
import com.google.android.apps.exposurenotification.utils.UrlUtils;
import dagger.hilt.android.AndroidEntryPoint;
import org.threeten.bp.ZoneId;

/**
 * Possible Exposure details fragment.
 */
@AndroidEntryPoint
public class PossibleExposureFragment extends BaseFragment {

  private FragmentPossibleExposureBinding binding;
  private ExposureHomeViewModel exposureHomeViewModel;

  /**
   * Creates a {@link PossibleExposureFragment} fragment.
   */
  public static PossibleExposureFragment newInstance() {
    return new PossibleExposureFragment();
  }

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent,
      Bundle savedInstanceState) {
    binding = FragmentPossibleExposureBinding.inflate(inflater, parent, false);
    return binding.getRoot();
  }

  public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    requireActivity().setTitle(R.string.possible_exposures_activity_title);

    exposureHomeViewModel =
        new ViewModelProvider(this, getDefaultViewModelProviderFactory())
            .get(ExposureHomeViewModel.class);

    binding.home.setContentDescription(getString(R.string.navigate_up));
    binding.home.setOnClickListener(v -> onBackPressed());

    binding.exposureNotificationExplanationButton.setOnClickListener(v ->
        launchHowExposureNotificationsWork());

    if (BuildUtils.getType() == Type.V3) {
      binding.activityTitle.setVisibility(View.VISIBLE);
      binding.exposureNotificationExplanationButton.setVisibility(View.VISIBLE);
    }

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

    exposureHomeViewModel
        .getExposureClassificationLiveData()
        .observe(getViewLifecycleOwner(),
            exposureClassification -> {
              boolean isRevoked = exposureHomeViewModel.getIsExposureClassificationRevoked();
              populateExposureDetails(exposureClassification, isRevoked);
            });

    if (savedInstanceState == null) {
      /*
       Dismiss the badges that may have been already seen but avoid doing this on device
       configuration changes e.g. on screen rotations.
      */
      exposureHomeViewModel.tryTransitionExposureClassificationNew(
          BadgeStatus.SEEN, BadgeStatus.DISMISSED);
      exposureHomeViewModel.tryTransitionExposureClassificationDateNew(
          BadgeStatus.SEEN, BadgeStatus.DISMISSED);
    }

    /*
     If this fragment is created, we assume that "new" badges were seen.
     If they were previously BadgeStatus.NEW, we now set them to BadgeStatus.SEEN
     */
    exposureHomeViewModel.tryTransitionExposureClassificationNew(BadgeStatus.NEW, BadgeStatus.SEEN);
    exposureHomeViewModel.tryTransitionExposureClassificationDateNew(BadgeStatus.NEW,
        BadgeStatus.SEEN);
  }

  @Override
  public boolean onBackPressed() {
    // For V2, we pop the current fragment transaction off the stack to land to the home screen.
    if (BuildUtils.getType() == Type.V2) {
      getParentFragmentManager().popBackStack();
    }
    // For V3, we need to return to the EN settings page.
    else /*BuildUtils.getType() == Type.V3*/ {
      launchSettingsForBackPressed();
    }
    return true;
  }

  /**
   * Populate views with the current exposure classification details
   *
   * @param exposureClassification the {@link ExposureClassification} as returned by
   *                               DailySummaryRiskCalculator
   * @param isRevoked              a boolean indicating a "revoked" state transition
   */
  private void populateExposureDetails(ExposureClassification exposureClassification,
      boolean isRevoked) {
    TextView exposureDetailsDateExposedText = binding.exposureDetailsDateExposedText;
    TextView exposureDetailsText = binding.exposureDetailsText;
    Button exposureDetailsUrlButton = binding.exposureDetailsUrlButton;

    exposureDetailsDateExposedText.setText(
        exposureHomeViewModel.getExposureDateRangeString(exposureClassification,
            requireContext(), ZoneId.systemDefault()));

    // Catch the revoked edge case
    if (isRevoked) {
      exposureDetailsUrlButton.setText(R.string.exposure_details_url_revoked);
      exposureDetailsText.setText(R.string.exposure_details_text_revoked);
      setUrlOnClickListener(getString(R.string.exposure_details_url_revoked));
    }

    // All the other "normal" classifications
    else {
      switch (exposureClassification.getClassificationIndex()) {
        case 1:
          exposureDetailsUrlButton.setText(R.string.exposure_details_url_1);
          exposureDetailsText.setText(R.string.exposure_details_text_1);
          setUrlOnClickListener(getString(R.string.exposure_details_url_1));
          break;
        case 2:
          exposureDetailsUrlButton.setText(R.string.exposure_details_url_2);
          exposureDetailsText.setText(R.string.exposure_details_text_2);
          setUrlOnClickListener(getString(R.string.exposure_details_url_2));
          break;
        case 3:
          exposureDetailsUrlButton.setText(R.string.exposure_details_url_3);
          exposureDetailsText.setText(R.string.exposure_details_text_3);
          setUrlOnClickListener(getString(R.string.exposure_details_url_3));
          break;
        case 4:
          exposureDetailsUrlButton.setText(R.string.exposure_details_url_4);
          exposureDetailsText.setText(R.string.exposure_details_text_4);
          setUrlOnClickListener(getString(R.string.exposure_details_url_4));
          break;
      }
    }
  }

  /**
   * Set onClickListener for the Exposure Details URL
   */
  private void setUrlOnClickListener(String url) {
    binding.exposureDetailsUrlButton
        .setOnClickListener(v -> UrlUtils.openUrl(binding.getRoot(), url));
  }

  /**
   * Launches the help and support link in the device browser.
   */
  private void launchHowExposureNotificationsWork() {
    UrlUtils.openUrl(binding.getRoot(),
        getString(R.string.how_exposure_notifications_work_actual_link));
  }


  /**
   * Launches the EN settings screen and clears the activity stack to make it seems like a real back
   * action.
   */
  private void launchSettingsForBackPressed() {
    Intent intent = IntentUtil.getExposureNotificationsSettingsIntent();
    startActivity(intent);
    requireActivity().finishAndRemoveTask();
  }
}
