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
import com.google.android.apps.exposurenotification.databinding.FragmentEdgeCasesAboutBinding;
import com.google.android.apps.exposurenotification.home.ExposureNotificationViewModel.ExposureNotificationState;
import com.google.android.apps.exposurenotification.proto.UiInteraction.EventType;
import com.google.android.apps.exposurenotification.utils.UrlUtils;
import dagger.hilt.android.AndroidEntryPoint;

/**
 * Fragment definition for the edge cases layout in the Settings -> About screen.
 */
@AndroidEntryPoint
public class AboutEdgeCaseFragment extends AbstractEdgeCaseFragment {

  private FragmentEdgeCasesAboutBinding binding;

  public static AboutEdgeCaseFragment newInstance(boolean handleApiErrorLiveEvents,
      boolean handleResolutions) {
    return (AboutEdgeCaseFragment) newInstance(
        new AboutEdgeCaseFragment(), handleApiErrorLiveEvents, handleResolutions);
  }

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent,
      Bundle savedInstanceState) {
    binding = FragmentEdgeCasesAboutBinding.inflate(inflater, parent, false);
    return binding.getRoot();
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    binding = null;
  }

  @Override
  protected void fillUIContent(View rootView, View containerView, ExposureNotificationState state,
      boolean isInFlight) {
    TextView title = binding.edgecaseAboutTitle;
    TextView text = binding.edgecaseAboutText;
    Button button = binding.edgecaseAboutButton;
    button.setEnabled(true);

    switch (state) {
      case ENABLED:
      case FOCUS_LOST:
      case DISABLED:
        setContainerVisibility(containerView, false);
        break;
      case PAUSED_LOCATION_BLE:
        setContainerVisibility(containerView, true);
        title.setText(R.string.exposure_notifications_are_inactive);
        text.setText(R.string.location_ble_off_warning);
        button.setText(R.string.device_settings);
        configureButtonForOpenSettings(button);
        logUiInteraction(EventType.LOCATION_PERMISSION_WARNING_SHOWN);
        logUiInteraction(EventType.BLUETOOTH_DISABLED_WARNING_SHOWN);
        break;
      case PAUSED_BLE:
        setContainerVisibility(containerView, true);
        title.setText(R.string.exposure_notifications_are_inactive);
        text.setText(R.string.ble_off_warning);
        button.setText(R.string.device_settings);
        configureButtonForOpenSettings(button);
        logUiInteraction(EventType.BLUETOOTH_DISABLED_WARNING_SHOWN);
        break;
      case PAUSED_LOCATION:
        setContainerVisibility(containerView, true);
        title.setText(R.string.exposure_notifications_are_inactive);
        text.setText(R.string.location_off_warning);
        button.setText(R.string.device_settings);
        configureButtonForOpenSettings(button);
        logUiInteraction(EventType.LOCATION_PERMISSION_WARNING_SHOWN);
        break;
      case PAUSED_NOT_IN_ALLOWLIST:
        setContainerVisibility(containerView, true);
        title.setText(R.string.en_turndown_for_area_title);
        text.setText(R.string.en_turndown_for_area_contents);
        button.setVisibility(View.GONE);
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
        button.setVisibility(View.GONE);
        break;
      case PAUSED_EN_NOT_SUPPORT:
        setContainerVisibility(containerView, true);
        title.setText(R.string.en_turndown_title);
        text.setText(R.string.en_turndown_contents);
        button.setVisibility(View.GONE);
        break;
      case PAUSED_USER_PROFILE_NOT_SUPPORT:
        setContainerVisibility(containerView, true);
        title.setText(R.string.exposure_notifications_are_turned_off);
        text.setText(R.string.user_profile_not_supported_warning);
        button.setVisibility(View.GONE);
        break;
      case STORAGE_LOW:
        setContainerVisibility(containerView, true);
        containerView.setVisibility(View.VISIBLE);
        title.setText(R.string.exposure_notifications_are_inactive);
        text.setText(R.string.storage_low_warning);
        button.setText(R.string.manage_storage);
        configureButtonForManageStorage(button);
        logUiInteraction(EventType.LOW_STORAGE_WARNING_SHOWN);
        break;
    }
  }

}
