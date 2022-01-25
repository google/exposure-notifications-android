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
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.StringUtils;
import com.google.android.apps.exposurenotification.databinding.FragmentEdgeCasesHomeSinglePageBinding;
import com.google.android.apps.exposurenotification.home.ExposureNotificationViewModel.ExposureNotificationState;
import com.google.android.apps.exposurenotification.proto.UiInteraction.EventType;
import com.google.android.apps.exposurenotification.utils.UrlUtils;
import dagger.hilt.android.AndroidEntryPoint;

/**
 * Fragment definition for the edge cases layout on the single-paged home screen.
 */
@AndroidEntryPoint
public class SingleHomePageEdgeCaseFragment extends AbstractEdgeCaseFragment {

  private FragmentEdgeCasesHomeSinglePageBinding binding;

  public static SingleHomePageEdgeCaseFragment newInstance(boolean handleApiErrorLiveEvents,
      boolean handleResolutions) {
    return (SingleHomePageEdgeCaseFragment) newInstance(
        new SingleHomePageEdgeCaseFragment(), handleApiErrorLiveEvents, handleResolutions);
  }

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent,
      Bundle savedInstanceState) {
    binding = FragmentEdgeCasesHomeSinglePageBinding.inflate(inflater, parent, false);
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
        title.setText(R.string.exposure_notifications_are_inactive);
        text.setText(R.string.location_ble_off_warning);
        actionButton.setText(R.string.device_settings);
        configureButtonForOpenSettings(actionButton);
        logUiInteraction(EventType.LOCATION_PERMISSION_WARNING_SHOWN);
        logUiInteraction(EventType.BLUETOOTH_DISABLED_WARNING_SHOWN);
        break;
      case PAUSED_BLE:
        setContainerVisibility(containerView, true);
        title.setText(R.string.exposure_notifications_are_inactive);
        text.setText(R.string.ble_off_warning);
        actionButton.setText(R.string.device_settings);
        configureButtonForOpenSettings(actionButton);
        logUiInteraction(EventType.BLUETOOTH_DISABLED_WARNING_SHOWN);
        break;
      case PAUSED_LOCATION:
        setContainerVisibility(containerView, true);
        title.setText(R.string.exposure_notifications_are_inactive);
        text.setText(R.string.location_off_warning);
        actionButton.setText(R.string.device_settings);
        configureButtonForOpenSettings(actionButton);
        logUiInteraction(EventType.LOCATION_PERMISSION_WARNING_SHOWN);
        break;
      case STORAGE_LOW:
        setContainerVisibility(containerView, true);
        title.setText(R.string.exposure_notifications_are_inactive);
        text.setText(R.string.storage_low_warning);
        actionButton.setText(R.string.manage_storage);
        configureButtonForManageStorage(actionButton);
        logUiInteraction(EventType.LOW_STORAGE_WARNING_SHOWN);
        break;
      case PAUSED_NOT_IN_ALLOWLIST:
        setContainerVisibility(containerView, true);
        title.setText(R.string.en_turndown_for_area_title);
        text.setText(R.string.en_turndown_for_area_contents);
        actionButton.setVisibility(View.GONE);
      break;
      case PAUSED_HW_NOT_SUPPORT:
        String deviceRequirementsLinkText = getString(R.string.device_requirements_link_text);

        setContainerVisibility(containerView, true);
        title.setText(R.string.exposure_notifications_are_turned_off);
        text.setText(StringUtils.generateTextWithHyperlink(
            UrlUtils.createURLSpan(getString(R.string.device_requirements_link)),
            getString(R.string.hw_not_supported_warning, deviceRequirementsLinkText),
            deviceRequirementsLinkText));
        text.setMovementMethod(LinkMovementMethod.getInstance());
        actionButton.setVisibility(View.GONE);
        break;
      case PAUSED_EN_NOT_SUPPORT:
        setContainerVisibility(containerView, true);
        title.setText(R.string.en_turndown_title);
        text.setText(R.string.en_turndown_contents);
        actionButton.setVisibility(View.GONE);
        break;
      case FOCUS_LOST:
        setContainerVisibility(containerView, true);
        title.setText(R.string.switch_app_for_exposure_notifications);
        text.setText(getString(R.string.focus_lost_warning,
            StringUtils.getApplicationTitle(requireContext())));
        actionButton.setText(R.string.switch_app_for_exposure_notifications_action);
        configureButtonForStartEn(actionButton, isInFlight);
        break;
      case PAUSED_USER_PROFILE_NOT_SUPPORT:
        setContainerVisibility(containerView, true);
        title.setText(R.string.exposure_notifications_are_turned_off);
        text.setText(R.string.user_profile_not_supported_warning);
        actionButton.setVisibility(View.GONE);
        break;
      case DISABLED:
      default:
        setContainerVisibility(containerView, true);
        title.setText(R.string.exposure_notifications_are_turned_off);
        text.setText(getString(R.string.notify_turn_on_exposure_notifications_header,
            getString(R.string.using_en_helps_even_if_vaccinated)));
        actionButton.setText(R.string.turn_on_exposure_notifications_action);
        configureButtonForStartEn(actionButton, isInFlight);
        break;
    }
  }

}
