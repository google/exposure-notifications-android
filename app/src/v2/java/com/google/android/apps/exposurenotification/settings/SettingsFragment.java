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

package com.google.android.apps.exposurenotification.settings;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.apps.exposurenotification.BuildConfig;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.databinding.FragmentSettingsBinding;
import com.google.android.apps.exposurenotification.home.BaseFragment;
import com.google.android.apps.exposurenotification.utils.UrlUtils;
import dagger.hilt.android.AndroidEntryPoint;

/**
 * Settings fragment.
 */
@AndroidEntryPoint
public class SettingsFragment extends BaseFragment {

  private FragmentSettingsBinding binding;

  public static SettingsFragment newInstance() {
    return new SettingsFragment();
  }

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent,
      Bundle savedInstanceState) {
    binding = FragmentSettingsBinding.inflate(getLayoutInflater());
    return binding.getRoot();
  }

  @Override
  public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    requireActivity().setTitle(R.string.home_tab_settings_text);

    AppAnalyticsViewModel appAnalyticsViewModel =
        new ViewModelProvider(this).get(AppAnalyticsViewModel.class);
    PrivateAnalyticsViewModel privateAnalyticsViewModel =
        new ViewModelProvider(this).get(PrivateAnalyticsViewModel.class);

    binding.home.setOnClickListener(v -> requireActivity().onBackPressed());

    binding.appAnalyticsLink.setOnClickListener(v -> launchAppAnalytics());
    appAnalyticsViewModel.getAppAnalyticsLiveData().observe(getViewLifecycleOwner(), isEnabled ->
        binding.appAnalyticsStatus.setText(
            isEnabled ? R.string.settings_analytics_on : R.string.settings_analytics_off));

    binding.privateAnalyticsLink.setOnClickListener(v -> launchPrivateAnalytics());
    privateAnalyticsViewModel
        .getPrivateAnalyticsLiveData()
        .observe(getViewLifecycleOwner(), isEnabled ->
            binding.privateAnalyticsStatus.setText(
                isEnabled ? R.string.settings_analytics_on : R.string.settings_analytics_off));

    binding.privateAnalyticsLink
        .setVisibility(
            privateAnalyticsViewModel.showPrivateAnalyticsSection() ? View.VISIBLE : View.GONE);

    binding.agencyLink.setOnClickListener(v -> launchAgencyLink());
    binding.legalLink.setOnClickListener(v -> launchLegalLink());
    binding.privacyPolicyLink.setOnClickListener(v -> launchPrivacyPolicyLink());
    binding.helpAndSupportLink.setOnClickListener(v -> launchHelpAndSupport());
    binding.openSourceLink.setOnClickListener(v -> showOsLicenses());
    binding.exposureNotificationsLink.setOnClickListener(
        v -> launchExposureNotificationsAbout());

    exposureNotificationViewModel.getEnEnabledLiveData().observe(getViewLifecycleOwner(),
        isEnabled -> binding.exposureNotificationsStatus.setText(isEnabled
            ? R.string.settings_exposure_notifications_on
            : R.string.settings_exposure_notifications_off)
    );
    exposureNotificationViewModel.getStateLiveData().observe(getViewLifecycleOwner(), status -> {
      switch (status) {
        case PAUSED_BLE:
        case PAUSED_LOCATION:
        case PAUSED_LOCATION_BLE:
        case STORAGE_LOW:
          binding.exposureNotificationsError.setVisibility(View.VISIBLE);
          break;
        case ENABLED:
        case DISABLED:
          binding.exposureNotificationsError.setVisibility(View.GONE);
          break;
      }
    });

    if (BuildConfig.DEBUG) {
      binding.debugModeLink.setVisibility(View.VISIBLE);
      binding.debugModeLink.setOnClickListener(v -> launchDebugScreen());
    }
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    binding = null;
  }

  /**
   * Open the share analytics screen.
   */
  private void launchAppAnalytics() {
    transitionToFragmentWithBackStack(AppAnalyticsFragment.newInstance());
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
   * Launches the help and support link in the device browser.
   */
  private void launchHelpAndSupport() {
    UrlUtils.openUrl(binding.getRoot(), getString(R.string.help_and_support_link));
  }

  /**
   * Open the exposure notifications about fragment.
   */
  private void launchExposureNotificationsAbout() {
    transitionToFragmentWithBackStack(ExposureAboutFragment.newInstance());
  }

  private void launchDebugScreen() {
    try {
      startActivity(new Intent(requireContext(),
          Class.forName("com.google.android.apps.exposurenotification.debug.DebugActivity")));
    } catch (ClassNotFoundException e) {
      // do nothing
    }
  }

  private void showOsLicenses() {
    transitionToFragmentWithBackStack(OpenSourceLicensesFragment.newInstance());
  }

}
