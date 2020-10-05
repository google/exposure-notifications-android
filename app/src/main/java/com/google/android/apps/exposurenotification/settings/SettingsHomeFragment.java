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
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.apps.exposurenotification.BuildConfig;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.home.ExposureNotificationViewModel;
import com.google.android.apps.exposurenotification.proto.UiInteraction.EventType;
import com.google.android.material.snackbar.Snackbar;
import com.mikepenz.aboutlibraries.Libs;
import com.mikepenz.aboutlibraries.LibsBuilder;
import dagger.hilt.android.AndroidEntryPoint;

/**
 * Fragment for Exposures tab on home screen.
 */
@AndroidEntryPoint
public class SettingsHomeFragment extends Fragment {

  private static final String TAG = "SettingsFragment";
  private static ExposureNotificationViewModel exposureNotificationViewModel;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_settings_home, parent, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    exposureNotificationViewModel =
        new ViewModelProvider(requireActivity()).get(ExposureNotificationViewModel.class);
    AppAnalyticsViewModel appAnalyticsViewModel =
        new ViewModelProvider(this).get(AppAnalyticsViewModel.class);

    View appAnalyticsLink = view.findViewById(R.id.app_analytics_link);
    appAnalyticsLink.setOnClickListener(v -> launchAppAnalytics());
    TextView appAnalyticsStatus = view.findViewById(R.id.app_analytics_status);
    appAnalyticsViewModel.getAppAnalyticsLiveData().observe(getViewLifecycleOwner(),
        isEnabled ->
            appAnalyticsStatus.setText(
                isEnabled ? R.string.settings_analytics_on : R.string.settings_analytics_off));

    View agencyLink = view.findViewById(R.id.agency_link);
    agencyLink.setOnClickListener(v -> launchAgencyLink());

    View legalLink = view.findViewById(R.id.legal_link);
    legalLink.setOnClickListener(v -> launchLegalLink());

    View privacyPolicy = view.findViewById(R.id.privacy_policy_link);
    privacyPolicy.setOnClickListener(v -> launchPrivacyPolicyLink(view));

    View openSourceLicenses = view.findViewById(R.id.open_source_link);
    openSourceLicenses.setOnClickListener(v -> showOsLicenses());

    View exposureNotificationsLink = view.findViewById(R.id.exposure_notifications_link);
    exposureNotificationsLink.setOnClickListener(v -> launchExposureNotificationsAboutActivity());
    TextView exposureNotificationsStatus = view.findViewById(R.id.exposure_notifications_status);
    ImageView exposureNotificationsError = view.findViewById(R.id.exposure_notifications_error);
    exposureNotificationViewModel.getStateLiveData().observe(getViewLifecycleOwner(), status -> {
      switch (status) {
        case PAUSED_BLE:
        case PAUSED_LOCATION:
        case PAUSED_LOCATION_BLE:
        case STORAGE_LOW:
          exposureNotificationsError.setVisibility(View.VISIBLE);
          exposureNotificationsStatus.setText(R.string.settings_exposure_notifications_on);
          break;
        case ENABLED:
          exposureNotificationsError.setVisibility(View.GONE);
          exposureNotificationsStatus.setText(R.string.settings_exposure_notifications_on);
          break;
        case DISABLED:
          exposureNotificationsError.setVisibility(View.GONE);
          exposureNotificationsStatus.setText(R.string.settings_exposure_notifications_off);
          break;
      }
    });

    View debugMode = view.findViewById(R.id.debug_mode_link);
    if (BuildConfig.DEBUG) {
      debugMode.setVisibility(View.VISIBLE);
      debugMode.setOnClickListener(v -> launchDebugScreen());
    }

    Button shareButton = view.findViewById(R.id.share_app_button);
    shareButton.setOnClickListener((v) -> launchShareApp());

    super.onViewCreated(view, savedInstanceState);
  }

  /**
   * Open the share analytics screen.
   */
  private void launchAppAnalytics() {
    startActivity(new Intent(requireContext(), AppAnalyticsActivity.class));
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
  private void launchPrivacyPolicyLink(View v) {
    startActivity(
        new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.enx_agencyPrivacyPolicy))));
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
        .withLibraryModification(
            "javax_annotation__jsr250_api",
            Libs.LibraryFields.LIBRARY_DESCRIPTION,
            getString(R.string.jsr_cddl_description))
        .withLicenseShown(true)
        .start(getActivity());
  }
}
