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

package com.google.android.apps.exposurenotification.debug;

import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.TextView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.home.ExposureNotificationViewModel;
import com.google.android.apps.exposurenotification.network.Uris;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences.NetworkMode;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.switchmaterial.SwitchMaterial;

/** Fragment for Debug tab on home screen */
public class DebugHomeFragment extends Fragment {

  private static final String TAG = "DebugHomeFragment";

  private ExposureNotificationViewModel exposureNotificationViewModel;
  private DebugHomeViewModel debugHomeViewModel;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_debug_home, parent, false);
  }

  @Override
  public void onViewCreated(View view, Bundle savedInstanceState) {
    exposureNotificationViewModel =
        new ViewModelProvider(requireActivity()).get(ExposureNotificationViewModel.class);
    debugHomeViewModel =
        new ViewModelProvider(this, getDefaultViewModelProviderFactory())
            .get(DebugHomeViewModel.class);

    debugHomeViewModel
        .getSnackbarSingleLiveEvent()
        .observe(
            this,
            message -> {
              View rootView = getView();
              if (rootView == null) {
                return;
              }
              Snackbar.make(rootView, message, Snackbar.LENGTH_LONG).show();
            });

    exposureNotificationViewModel
        .getIsEnabledLiveData()
        .observe(getViewLifecycleOwner(), isEnabled -> refreshUiForEnabled(isEnabled));

    exposureNotificationViewModel
        .getApiErrorLiveEvent()
        .observe(
            getViewLifecycleOwner(),
            unused -> maybeShowSnackbar(getString(R.string.generic_error_message)));

    // Version
    TextView appVersion = view.findViewById(R.id.debug_app_version);
    appVersion.setText(
        getString(
            R.string.debug_version_app, getVersionNameForPackage(getContext().getPackageName())));

    TextView gmsVersion = view.findViewById(R.id.debug_gms_version);
    gmsVersion.setText(
        getString(
            R.string.debug_version_gms,
            getVersionNameForPackage(GoogleApiAvailability.GOOGLE_PLAY_SERVICES_PACKAGE)));

    // Master switch
    SwitchMaterial masterSwitch = view.findViewById(R.id.master_switch);
    masterSwitch.setOnCheckedChangeListener(masterSwitchChangeListener);
    exposureNotificationViewModel
        .getInFlightLiveData()
        .observe(getViewLifecycleOwner(), isInFlight -> masterSwitch.setEnabled(!isInFlight));

    // Test exposure notification
    view.findViewById(R.id.debug_test_exposure_notify_button)
        .setOnClickListener(
            v -> debugHomeViewModel.addTestExposures(getString(R.string.generic_error_message)));

    view.findViewById(R.id.debug_exposure_reset_button)
        .setOnClickListener(
            v ->
                debugHomeViewModel.resetExposures(
                    getString(R.string.debug_test_exposure_reset_success),
                    getString(R.string.generic_error_message)));

    // Matching
    Button manualMatching = view.findViewById(R.id.debug_matching_manual_button);
    manualMatching.setOnClickListener(
        v -> startActivity(new Intent(requireContext(), MatchingDebugActivity.class)));

    view.findViewById(R.id.debug_provide_keys_button)
        .setOnClickListener(
            v -> {
              debugHomeViewModel.provideKeys();
              maybeShowSnackbar(getString(R.string.debug_provide_keys_enqueued));
            });

    // Network
    SwitchMaterial networkSwitch = view.findViewById(R.id.network_mode);
    networkSwitch.setOnCheckedChangeListener(networkModeChangeListener);
    networkSwitch.setChecked(
        debugHomeViewModel.getNetworkMode(NetworkMode.FAKE).equals(NetworkMode.TEST));
  }

  @Override
  public void onResume() {
    super.onResume();
    refreshUi();
  }

  private final OnCheckedChangeListener masterSwitchChangeListener =
      new OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
          buttonView.setOnCheckedChangeListener(null);
          // Set the toggle back. It will only toggle to correct state if operation succeeds.
          buttonView.setChecked(!isChecked);
          if (isChecked) {
            exposureNotificationViewModel.startExposureNotifications();
          } else {
            exposureNotificationViewModel.stopExposureNotifications();
          }
        }
      };

  private final OnCheckedChangeListener networkModeChangeListener =
      (buttonView, isChecked) -> {
        Uris uris = new Uris(requireContext());
        if (uris.hasDefaultUris()) {
          debugHomeViewModel.setNetworkMode(NetworkMode.FAKE);
          maybeShowSnackbar(getString(R.string.debug_network_mode_default_uri));
          ((SwitchMaterial) requireView().findViewById(R.id.network_mode)).setChecked(false);
          return;
        }
        if (isChecked) {
          debugHomeViewModel.setNetworkMode(NetworkMode.TEST);
        } else {
          debugHomeViewModel.setNetworkMode(NetworkMode.FAKE);
        }
      };

  /** Update UI state after Exposure Notifications client state changes */
  private void refreshUi() {
    exposureNotificationViewModel.refreshIsEnabledState();
  }

  /**
   * Update UI to match Exposure Notifications client has become enabled/not-enabled.
   *
   * @param currentlyEnabled True if Exposure Notifications is enabled
   */
  private void refreshUiForEnabled(Boolean currentlyEnabled) {
    View rootView = getView();
    if (rootView == null) {
      return;
    }
    SwitchMaterial masterSwitch = rootView.findViewById(R.id.master_switch);
    masterSwitch.setOnCheckedChangeListener(null);
    masterSwitch.setChecked(currentlyEnabled);
    masterSwitch.setOnCheckedChangeListener(masterSwitchChangeListener);
  }

  /** Gets the version name for a specified package. Returns a debug string if not found. */
  private String getVersionNameForPackage(String packageName) {
    try {
      return getContext().getPackageManager().getPackageInfo(packageName, 0).versionName;
    } catch (NameNotFoundException e) {
      Log.e(TAG, "Couldn't get the app version", e);
    }
    return getString(R.string.debug_version_not_available);
  }

  private void maybeShowSnackbar(String message) {
    View rootView = getView();
    if (rootView != null) {
      Snackbar.make(rootView, message, Snackbar.LENGTH_LONG).show();
    }
  }
}
