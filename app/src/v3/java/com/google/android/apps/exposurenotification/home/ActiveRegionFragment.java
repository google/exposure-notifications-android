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

package com.google.android.apps.exposurenotification.home;

import static android.app.Activity.RESULT_FIRST_USER;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.apps.exposurenotification.BuildConfig;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.databinding.FragmentActiveRegionBinding;
import com.google.android.apps.exposurenotification.exposure.ExposureHomeViewModel;
import com.google.android.apps.exposurenotification.exposure.PossibleExposureFragment;
import com.google.android.apps.exposurenotification.home.ExposureNotificationViewModel.ExposureNotificationState;
import com.google.android.apps.exposurenotification.notify.ShareDiagnosisFlowHelper;
import com.google.android.apps.exposurenotification.riskcalculation.ExposureClassification;
import com.google.android.apps.exposurenotification.settings.AgencyFragment;
import com.google.android.apps.exposurenotification.settings.LegalTermsFragment;
import com.google.android.apps.exposurenotification.settings.PrivateAnalyticsFragment;
import com.google.android.apps.exposurenotification.settings.PrivateAnalyticsViewModel;
import com.google.android.apps.exposurenotification.utils.UrlUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.mikepenz.aboutlibraries.LibsBuilder;
import dagger.hilt.android.AndroidEntryPoint;

/**
 * Fragment that holds main information on the currently active region.
 */
@AndroidEntryPoint
public class ActiveRegionFragment extends BaseFragment {

  private static final String STATE_REMOVE_REGION_DIALOG_OPEN = "STATE_REMOVE_REGION_DIALOG_OPEN";
  // Result code to report that the current region has been removed from active regions.
  private static final int RESULT_REMOVE_REGION = RESULT_FIRST_USER;
  // Intent extra key that communicates if we need to display the possible exposure info.
  private static final String EXTRA_POSSIBLE_EXPOSURE_SLICE_DISABLED = "slice_disabled";

  private @NonNull FragmentActiveRegionBinding binding;
  private ExposureHomeViewModel exposureHomeViewModel;
  private boolean isRemoveRegionDialogOpen = false;
  private boolean showPossibleExposureIfAny = false;

  public static ActiveRegionFragment newInstance(boolean possibleExposureSliceDisabled) {
    ActiveRegionFragment activeRegionFragment = new ActiveRegionFragment();
    Bundle args = new Bundle();
    args.putBoolean(EXTRA_POSSIBLE_EXPOSURE_SLICE_DISABLED, possibleExposureSliceDisabled);
    activeRegionFragment.setArguments(args);
    return activeRegionFragment;
  }

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent,
      Bundle savedInstanceState) {
    binding = FragmentActiveRegionBinding.inflate(getLayoutInflater());
    return binding.getRoot();
  }

  @Override
  public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    requireActivity().setTitle(R.string.enx_agencyDisplayName);

    exposureHomeViewModel =
        new ViewModelProvider(this).get(ExposureHomeViewModel.class);

    PrivateAnalyticsViewModel privateAnalyticsViewModel =
        new ViewModelProvider(this).get(PrivateAnalyticsViewModel.class);

    if (getArguments() != null) {
      showPossibleExposureIfAny = getArguments().getBoolean(
          EXTRA_POSSIBLE_EXPOSURE_SLICE_DISABLED, false);
    }

    if (savedInstanceState != null) {
      isRemoveRegionDialogOpen = savedInstanceState
          .getBoolean(STATE_REMOVE_REGION_DIALOG_OPEN, false);
    }

    // Decide whether to show the SMS notice card
    if (ShareDiagnosisFlowHelper.isSmsInterceptEnabled(getContext())) {
      exposureNotificationViewModel.getShouldShowSmsNoticeLiveData()
          .observe(getViewLifecycleOwner(),
              shouldShowSmsNotice -> binding.smsNoticeView.setVisibility(
                  shouldShowSmsNotice ? View.VISIBLE : View.GONE));
    }

    // Ensure we keep the open dialogs open upon rotations.
    if (isRemoveRegionDialogOpen) {
      showRemoveRegionDialog();
    }

    privateAnalyticsViewModel
        .getPrivateAnalyticsLiveData()
        .observe(getViewLifecycleOwner(), isEnabled -> binding.privateAnalyticsStatus.setText(
            isEnabled ? R.string.settings_analytics_on : R.string.settings_analytics_off));

    exposureNotificationViewModel
        .getStateLiveData()
        .observe(getViewLifecycleOwner(), state -> {
          if (state == ExposureNotificationState.ENABLED) {
            binding.enOffView.setVisibility(View.GONE);
            binding.activeRegionHeader.activeRegionSubtitle.setText(
                getString(R.string.active_region_subtitle,
                    getString(R.string.agency_message_subtitle_region)));
          } else {
            binding.enOffView.setVisibility(View.VISIBLE);
            binding.activeRegionHeader.activeRegionSubtitle.setText(
                getString(R.string.active_region_subtitle_en_off,
                    getString(R.string.agency_message_subtitle_region)));
          }
        });

    exposureNotificationViewModel
        .getEnStoppedLiveEvent()
        .observe(getViewLifecycleOwner(), enStopped -> {
          if (enStopped) {
            getActivity().setResult(RESULT_REMOVE_REGION);
            getActivity().finish();
          }
        });

    exposureHomeViewModel
        .getExposureClassificationLiveData()
        .observe(getViewLifecycleOwner(),
            exposureClassification -> {
              boolean isRevoked = exposureHomeViewModel.getIsExposureClassificationRevoked();
              refreshUiForClassification(exposureClassification, isRevoked);
            });

    binding.privateAnalyticsLink
        .setVisibility(
            privateAnalyticsViewModel.showPrivateAnalyticsSection() ? View.VISIBLE : View.GONE);
    binding.privateAnalyticsLinkDivider
        .setVisibility(
            privateAnalyticsViewModel.showPrivateAnalyticsSection() ? View.VISIBLE : View.GONE);

    // Set onClickListeners for all the clickable items on the screen.
    binding.privateAnalyticsLink.setOnClickListener(v -> launchPrivateAnalytics());
    binding.agencyLink.setOnClickListener(v -> launchAgencyLink());
    binding.helpAndSupportLink.setOnClickListener(v -> launchHelpAndSupport());
    binding.legalLink.setOnClickListener(v -> launchLegalLink());
    binding.privacyPolicyLink.setOnClickListener(v -> launchPrivacyPolicyLink());
    binding.possibleExposureLayout.possibleExposureCard.setOnClickListener(
        v -> transitionToFragmentWithBackStack(PossibleExposureFragment.newInstance()));
    binding.removeRegion.setOnClickListener(v -> showRemoveRegionDialog());
    binding.enOffView.setOnClickListener(v -> requireActivity().onBackPressed());
    binding.smsNoticeLayout.smsNoticeCard.setOnClickListener(
        v -> transitionToFragmentWithBackStack(SmsNoticeFragment.newInstance(false)));

    // Set up the Toolbar.
    Toolbar toolbar = binding.activeRegionToolbar;
    toolbar.inflateMenu(R.menu.active_region_menu);
    toolbar.setOnMenuItemClickListener(item -> {
      if (item.getItemId() == android.R.id.home) {
        requireActivity().onBackPressed();
        return true;
      } else if (item.getItemId() == R.id.action_show_os_licenses) {
        showOsLicenses();
        return true;
      }
      return false;
    });
    toolbar.setNavigationIcon(R.drawable.ic_back);
    toolbar.setNavigationOnClickListener(v -> requireActivity().onBackPressed());

    if (BuildConfig.DEBUG) {
      binding.debugModeLink.setVisibility(View.VISIBLE);
      binding.debugModeLink.setOnClickListener(v -> launchDebugScreen());
    }
  }

  @Override
  public void onSaveInstanceState(@NonNull Bundle bundle) {
    super.onSaveInstanceState(bundle);
    bundle.putBoolean(STATE_REMOVE_REGION_DIALOG_OPEN, isRemoveRegionDialogOpen);
  }

  private void showRemoveRegionDialog() {
    isRemoveRegionDialogOpen = true;
    new MaterialAlertDialogBuilder(
        requireContext(), R.style.ExposureNotificationAlertDialogTheme)
        .setTitle(R.string.remove_region_title)
        .setMessage(R.string.remove_region_detail)
        .setCancelable(true)
        .setNegativeButton(R.string.btn_cancel, (dialog, i) -> {
          isRemoveRegionDialogOpen = false;
          dialog.cancel();
        })
        .setPositiveButton(R.string.btn_remove, (dialog, i) -> {
          isRemoveRegionDialogOpen = false;
          exposureNotificationViewModel.stopExposureNotifications();
        })
        .setOnCancelListener(d -> isRemoveRegionDialogOpen = false)
        .show();
  }

  private void refreshUiForClassification(
      ExposureClassification exposureClassification, boolean isRevoked) {
    boolean isExposure = (exposureClassification.getClassificationIndex()
        != ExposureClassification.NO_EXPOSURE_CLASSIFICATION_INDEX) || isRevoked;

    if (showPossibleExposureIfAny && isExposure) {
      binding.possibleExposureView.setVisibility(View.VISIBLE);
      binding.possibleExposureLayout.possibleExposureDate.setText(
          exposureHomeViewModel
              .getDaysFromStartOfExposureString(exposureClassification, getContext())
      );
    } else {
      binding.possibleExposureView.setVisibility(View.GONE);
    }
  }

  /**
   * Open the share private analytics screen.
   */
  private void launchPrivateAnalytics() {
    transitionToFragmentWithBackStack(PrivateAnalyticsFragment.newInstance());
  }

  /**
   * Launches the agency screen.
   */
  private void launchAgencyLink() {
    transitionToFragmentWithBackStack(AgencyFragment.newInstance());
  }

  /**
   * Launches the help and support link in the device browser.
   */
  private void launchHelpAndSupport() {
    UrlUtils.openUrl(binding.getRoot(), getString(R.string.help_and_support_link));
  }

  /**
   * Launches the legal screen.
   */
  private void launchLegalLink() {
    transitionToFragmentWithBackStack(LegalTermsFragment.newInstance());
  }

  /**
   * Launches the privacy policy link in the device browser.
   */
  private void launchPrivacyPolicyLink() {
    UrlUtils.openUrl(binding.getRoot(), getString(R.string.enx_agencyPrivacyPolicy));
  }

  /**
   * Launches the debug screen.
   */
  private void launchDebugScreen() {
    try {
      startActivity(new Intent(getContext(),
          Class.forName("com.google.android.apps.exposurenotification.debug.DebugActivity")));
    } catch (ClassNotFoundException e) {
      // Do nothing.
    }
  }

  private void showOsLicenses() {
    new LibsBuilder()
        .withFields(R.string.class.getFields())
        .withLicenseShown(true)
        .withAboutIconShown(false)
        .withAboutVersionShown(false)
        .start(requireContext());
  }

}
