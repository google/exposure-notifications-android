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

package com.google.android.apps.exposurenotification.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ViewAnimator;
import android.widget.ViewSwitcher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.activities.utils.ExposureNotificationPermissionHelper;
import com.google.android.apps.exposurenotification.adapter.ExposureNotificationAdapter;
import com.google.android.apps.exposurenotification.nearby.ExposureNotificationClientWrapper;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.gms.nearby.exposurenotification.ExposureInformation;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.snackbar.Snackbar;
import java.util.List;

/**
 * Fragment for Exposures tab on home screen
 */
public class ExposureFragment extends Fragment {

  private ExposureNotificationAdapter adapter;
  private final String TAG = "ExposureFragment";

  private final ExposureNotificationPermissionHelper exposureNotificationPermissionHelper;

  public ExposureFragment() {
    exposureNotificationPermissionHelper =
        new ExposureNotificationPermissionHelper(this, this::refreshUi);
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_exposure, parent, false);
  }

  @Override
  public void onViewCreated(View view, Bundle savedInstanceState) {
    Button goToSettingsButton = view.findViewById(R.id.go_to_settings_button);
    View exposureNotificationsToggle = view.findViewById(R.id.exposure_notifications_toggle);

    goToSettingsButton.setOnClickListener(
        v -> exposureNotificationPermissionHelper.optInAndStartExposureTracing(view));
    exposureNotificationsToggle.setOnClickListener(v -> launchSettings());

    RecyclerView exposuresList = view.findViewById(R.id.exposures_list);
    adapter = new ExposureNotificationAdapter(new ExposureClick());
    exposuresList.setItemAnimator(null);
    exposuresList.setLayoutManager(new LinearLayoutManager(requireContext(),
        LinearLayoutManager.VERTICAL, false));
    exposuresList.setAdapter(adapter);
  }

  @Override
  public void onResume() {
    super.onResume();
    refreshUi();
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    exposureNotificationPermissionHelper
        .onResolutionComplete(requestCode, resultCode, requireView());
  }

  /**
   * Update UI state after Exposure Notifications client state changes
   */
  private void refreshUi() {
    ExposureNotificationClientWrapper exposureNotificationClientWrapper =
        ExposureNotificationClientWrapper.get(requireContext());

    exposureNotificationClientWrapper.isEnabled()
        .continueWithTask((isEnabled) -> {
          Boolean currentlyEnabled = isEnabled.getResult();

          refreshUiForEnabled(currentlyEnabled);

          if (currentlyEnabled) {
            // if we're seeing it enabled then permission has been granted
            noteOnboardingCompleted();
          }

          if (currentlyEnabled) {
            return exposureNotificationClientWrapper.getExposureInformation();
          } else {
            return Tasks.forCanceled();
          }
        })
        .addOnSuccessListener(this::refreshUiForExposureInformation)
        .addOnCanceledListener(() -> refreshUiForExposureInformation(null))
        .addOnFailureListener((cause) -> {
          refreshUiForEnabled(false);
          showExposureNotificationApiError(cause);
        });
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

    TextView exposureNotificationStatus = rootView.findViewById(R.id.exposure_notifications_status);
    ViewSwitcher settingsBannerSwitcher = rootView.findViewById(R.id.settings_banner_switcher);
    TextView infoStatus = rootView.findViewById(R.id.info_status);

    settingsBannerSwitcher.setDisplayedChild(currentlyEnabled ? 1 : 0);

    if (currentlyEnabled) {
      exposureNotificationStatus.setText(R.string.on);
    } else {
      exposureNotificationStatus.setText(R.string.off);
    }

    if (currentlyEnabled) {
      infoStatus.setText(R.string.notifications_enabled_info);
    } else {
      infoStatus.setText(R.string.notifications_disabled_info);
    }
  }

  /**
   * Display new exposure information
   *
   * @param exposures List of potential exposures
   */
  private void refreshUiForExposureInformation(@Nullable List<ExposureInformation> exposures) {
    View rootView = getView();
    if (rootView == null) {
      return;
    }

    if (adapter != null) {
      adapter.submitList(exposures);
    }

    ViewAnimator switcher = rootView.findViewById(R.id.exposures_list_empty_switcher);
    switcher.setDisplayedChild(exposures == null || exposures.isEmpty() ? 0 : 1);
  }

  /**
   * Display snackbar when Exposure Notifications client returns an error.
   *
   * @param cause
   */
  private void showExposureNotificationApiError(@NonNull Exception cause) {
    Log.w(TAG, "Unable to get exposure notifications", cause);
    View rootView = getView();
    if (rootView == null) {
      return;
    }

    Snackbar.make(rootView, R.string.generic_error_message, Snackbar.LENGTH_LONG).show();

    ViewAnimator exposuresListEmptySwitcher = rootView
        .findViewById(R.id.exposures_list_empty_switcher);
    exposuresListEmptySwitcher.setDisplayedChild(0);
  }

  /**
   * Record in SharedPreferences that the user has completed the Onboarding flow.
   */
  private void noteOnboardingCompleted() {
    ExposureNotificationSharedPreferences sharedPrefs = new ExposureNotificationSharedPreferences(
        requireContext());
    sharedPrefs.setOnboardedState(true);
  }

  /**
   * Open the Exposure Notifications system settings screen
   */
  private void launchSettings() {
    Intent intent = new Intent("com.google.android.gms.settings.EXPOSURE_NOTIFICATION_SETTINGS");
    startActivity(intent);
  }

  public class ExposureClick {

    public void onClicked(ExposureInformation exposureInformation) {
      ExposureBottomSheetFragment sheet = ExposureBottomSheetFragment
          .newInstance(exposureInformation);
      sheet.show(getChildFragmentManager(), ExposureBottomSheetFragment.TAG);
    }
  }
}
