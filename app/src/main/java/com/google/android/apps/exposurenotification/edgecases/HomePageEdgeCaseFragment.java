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

package com.google.android.apps.exposurenotification.edgecases;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.databinding.FragmentEdgeCaseHomePageBinding;
import com.google.android.apps.exposurenotification.exposure.ExposureChecksDialogFragment;
import com.google.android.apps.exposurenotification.home.ExposureNotificationViewModel.ExposureNotificationState;
import com.google.android.apps.exposurenotification.proto.UiInteraction.EventType;
import com.google.android.apps.exposurenotification.utils.UrlUtils;
import dagger.hilt.android.AndroidEntryPoint;

/**
 * Fragment definition for the edge cases layout on the single-paged home screen.
 */
@AndroidEntryPoint
public class HomePageEdgeCaseFragment extends AbstractEdgeCaseFragment {

  private static final String TAG = "HomePageEdgeCaseFragment";

  private FragmentEdgeCaseHomePageBinding binding;

  public static HomePageEdgeCaseFragment newInstance(boolean handleApiErrorLiveEvents,
      boolean handleResolutions) {
    return (HomePageEdgeCaseFragment) newInstance(
        new HomePageEdgeCaseFragment(), handleApiErrorLiveEvents, handleResolutions);
  }

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent,
      Bundle savedInstanceState) {
    binding = FragmentEdgeCaseHomePageBinding.inflate(inflater, parent, false);
    return binding.getRoot();
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    binding = null;
  }

  /*
   * Fill in the correct view texts / button actions
   */
  @Override
  protected void fillUIContent(View rootView, View containerView, ExposureNotificationState state,
      boolean isInFlight) {
    TextView title = binding.edgeCaseTitle;
    TextView text = binding.edgeCaseContent;
    Button actionButton = binding.edgeCaseActionButton;
    actionButton.setEnabled(true);

    binding.howEnWorkButton.setOnClickListener(
        unused -> UrlUtils.openUrl(
            requireContext(), getString(R.string.how_exposure_notifications_work_actual_link)));

    switch (state) {
      case ENABLED:
        /*
         * For the ENABLED state, hide the edge cases layout altogether as we have a separate logic
         * to handle this state in the parent SinglePageHomeFragment.
         */
        setContainerVisibility(containerView, false);
        break;
      case PAUSED_LOCATION_BLE:
        setContainerVisibility(containerView, true);
        dismissExposureChecksDialogIfOpen();
        title.setText(R.string.exposure_notifications_are_inactive);
        text.setText(R.string.location_ble_off_warning);
        actionButton.setText(R.string.device_settings);
        configureButtonForOpenSettings(actionButton);
        logUiInteraction(EventType.LOCATION_PERMISSION_WARNING_SHOWN);
        logUiInteraction(EventType.BLUETOOTH_DISABLED_WARNING_SHOWN);
        break;
      case PAUSED_BLE:
        setContainerVisibility(containerView, true);
        dismissExposureChecksDialogIfOpen();
        title.setText(R.string.exposure_notifications_are_inactive);
        text.setText(R.string.ble_off_warning);
        actionButton.setText(R.string.device_settings);
        configureButtonForOpenSettings(actionButton);
        logUiInteraction(EventType.BLUETOOTH_DISABLED_WARNING_SHOWN);
        break;
      case PAUSED_LOCATION:
        setContainerVisibility(containerView, true);
        dismissExposureChecksDialogIfOpen();
        title.setText(R.string.exposure_notifications_are_inactive);
        text.setText(R.string.location_off_warning);
        actionButton.setText(R.string.device_settings);
        configureButtonForOpenSettings(actionButton);
        logUiInteraction(EventType.LOCATION_PERMISSION_WARNING_SHOWN);
        break;
      case STORAGE_LOW:
        setContainerVisibility(containerView, true);
        dismissExposureChecksDialogIfOpen();
        title.setText(R.string.exposure_notifications_are_inactive);
        text.setText(R.string.storage_low_warning);
        actionButton.setText(R.string.manage_storage);
        configureButtonForManageStorage(actionButton);
        logUiInteraction(EventType.LOW_STORAGE_WARNING_SHOWN);
        break;
      case DISABLED:
      default:
        setContainerVisibility(containerView, true);
        dismissExposureChecksDialogIfOpen();
        title.setText(R.string.en_off_card_title);
        text.setText(R.string.notify_turn_on_exposure_notifications_header);
        actionButton.setText(R.string.btn_turn_on);
        configureButtonForStartEn(actionButton, isInFlight);
        break;
    }
  }

  private void dismissExposureChecksDialogIfOpen() {
    Fragment exposureChecksFragment = getParentFragmentManager()
        .findFragmentByTag(ExposureChecksDialogFragment.TAG);
    if (exposureChecksFragment != null) {
      DialogFragment exposureChecksDialog = (DialogFragment) exposureChecksFragment;
      exposureChecksDialog.dismiss();
    }
  }

}
