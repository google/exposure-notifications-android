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

package com.google.android.apps.exposurenotification.settings;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.apps.exposurenotification.BuildConfig;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.databinding.FragmentSettingsHomeBinding;
import com.google.android.apps.exposurenotification.home.ExposureNotificationViewModel;
import com.google.android.apps.exposurenotification.privateanalytics.PrivateAnalyticsSettingsUtil;
import com.google.android.apps.exposurenotification.proto.UiInteraction.EventType;
import com.mikepenz.aboutlibraries.LibsBuilder;
import dagger.hilt.android.AndroidEntryPoint;

/**
 * Fragment for Settings tab on home screen.
 */
@AndroidEntryPoint
public class SettingsHomeFragment extends Fragment {

  private static final String TAG = "SettingsFragment";
  private static ExposureNotificationViewModel exposureNotificationViewModel;

  public static SettingsHomeFragment newInstance() {
    return new SettingsHomeFragment();
  }

  private FragmentSettingsHomeBinding binding;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
    binding = FragmentSettingsHomeBinding.inflate(inflater, parent, false);
    return binding.getRoot();
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    exposureNotificationViewModel =
        new ViewModelProvider(requireActivity()).get(ExposureNotificationViewModel.class);
    AppAnalyticsViewModel appAnalyticsViewModel =
        new ViewModelProvider(this).get(AppAnalyticsViewModel.class);
    PrivateAnalyticsViewModel privateAnalyticsViewModel =
        new ViewModelProvider(this).get(PrivateAnalyticsViewModel.class);

    binding.appAnalyticsLink.setOnClickListener(v -> launchAppAnalytics());
    appAnalyticsViewModel.getAppAnalyticsLiveData().observe(getViewLifecycleOwner(),
        isEnabled ->
            binding.appAnalyticsStatus.setText(
                isEnabled ? R.string.settings_analytics_on : R.string.settings_analytics_off));

    binding.privateAnalyticsLink.setOnClickListener(v -> launchPrivateAnalytics());
    privateAnalyticsViewModel
        .getPrivateAnalyticsLiveData()
        .observe(
            getViewLifecycleOwner(),
            isEnabled ->
                binding.privateAnalyticsStatus.setText(
                    isEnabled
                        ? R.string.settings_analytics_on
                        : R.string.settings_analytics_off));

    binding.privateAnalyticsLink
        .setVisibility(
            PrivateAnalyticsSettingsUtil.isPrivateAnalyticsSupported() ? View.VISIBLE : View.GONE);

    binding.agencyLink.setOnClickListener(v -> launchAgencyLink());
    binding.legalLink.setOnClickListener(v -> launchLegalLink());
    binding.privacyPolicyLink.setOnClickListener(v -> launchPrivacyPolicyLink());
    binding.helpAndSupportLink.setOnClickListener(v -> launchHelpAndSupport());
    binding.openSourceLink.setOnClickListener(v -> showOsLicenses());
    binding.exposureNotificationsLink.setOnClickListener(
        v -> launchExposureNotificationsAboutActivity());

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

    binding.shareAppButton.setOnClickListener(v -> launchShareApp());
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    binding = null;
  }

  /**
   * Open the share analytics screen.
   */
  private void launchAppAnalytics() {
    startActivity(new Intent(requireContext(), AppAnalyticsActivity.class));
  }

  /**
   * Open the share private analytics screen.
   */
  private void launchPrivateAnalytics() {
    startActivity(new Intent(requireContext(), PrivateAnalyticsActivity.class));
  }

  /**
   * Launches the agency screen.
   */
  private void launchAgencyLink() {
    startActivity(new Intent(getContext(), AgencyActivity.class));
  }

  /**
   * Launches the legal screen.
   */
  private void launchLegalLink() {
    startActivity(new Intent(getContext(), LegalTermsActivity.class));
  }

  /**
   * Launches the privacy policy link in the device browser.
   */
  private void launchPrivacyPolicyLink() {
    startActivity(
        new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.enx_agencyPrivacyPolicy))));
  }

  /**
   * Launches the help and support link in the device browser.
   */
  private void launchHelpAndSupport() {
    startActivity(
        new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.help_and_support_link))));
  }

  /**
   * Open the exposure notifications about activity.
   */
  private void launchExposureNotificationsAboutActivity() {
    startActivity(new Intent(requireContext(), ExposureAboutActivity.class));
  }

  private void launchShareApp() {
    Intent sendIntent = new Intent();
    sendIntent.setAction(Intent.ACTION_SEND);
    sendIntent.setType("text/plain");
    sendIntent.putExtra(Intent.EXTRA_TEXT, getContext().getString(R.string.settings_share_message,
        String.format("https://play.google.com/store/apps/details?id=%s",
            BuildConfig.APPLICATION_ID)));
    startActivity(Intent.createChooser(sendIntent, null));
    exposureNotificationViewModel.logUiInteraction(EventType.SHARE_APP_CLICKED);
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
    new LibsBuilder()
        .withFields(R.string.class.getFields())
        .withLicenseShown(true)
        .start(getActivity());
  }
}
