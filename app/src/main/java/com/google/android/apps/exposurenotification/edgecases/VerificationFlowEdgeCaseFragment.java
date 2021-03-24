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
import android.text.style.URLSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.StringUtils;
import com.google.android.apps.exposurenotification.databinding.FragmentEdgeCasesVerificationBinding;
import com.google.android.apps.exposurenotification.home.ExposureNotificationViewModel.ExposureNotificationState;
import com.google.android.apps.exposurenotification.notify.DiagnosisEntityHelper;
import com.google.android.apps.exposurenotification.notify.ShareDiagnosisActivity;
import com.google.android.apps.exposurenotification.notify.ShareDiagnosisViewModel;
import dagger.hilt.android.AndroidEntryPoint;

/**
 * Fragment definition for the edge cases layout in a 'Share Diagnosis' (verification) flow.
 */
@AndroidEntryPoint
public class VerificationFlowEdgeCaseFragment extends AbstractEdgeCaseFragment {

  private FragmentEdgeCasesVerificationBinding binding;

  public static VerificationFlowEdgeCaseFragment newInstance(boolean handleApiErrorLiveEvents,
      boolean handleResolutions) {
    return (VerificationFlowEdgeCaseFragment) newInstance(
        new VerificationFlowEdgeCaseFragment(), handleApiErrorLiveEvents, handleResolutions);
  }

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent,
      Bundle savedInstanceState) {
    binding = FragmentEdgeCasesVerificationBinding.inflate(inflater, parent, false);
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
    ShareDiagnosisViewModel viewModel =
        new ViewModelProvider(getActivity()).get(ShareDiagnosisViewModel.class);
    viewModel.getCurrentDiagnosisLiveData().observe(getViewLifecycleOwner(), diagnosisEntity -> {
      // No need to display edge case errors for already shared diagnoses.
      if (DiagnosisEntityHelper.hasBeenShared(diagnosisEntity)) {
        setContainerVisibility(containerView, false);
      }

      binding.home.setOnClickListener(v -> {
        // Only show a warning dialog if the entity has been verified.
        if (DiagnosisEntityHelper.hasVerified(diagnosisEntity)) {
          ShareDiagnosisActivity.showCloseWarningAlertDialog(requireActivity(), viewModel);
        } else {
          requireActivity().finish();
        }
      });
    });

    TextView title = binding.edgecaseMainTitle;
    TextView text = binding.edgecaseMainText;
    Button button = binding.edgecaseMainButton;
    button.setEnabled(true);

    switch (state) {
      case DISABLED:
        setContainerVisibility(containerView, true);
        title.setText(R.string.exposure_notifications_are_turned_off);
        text.setText(R.string.notify_turn_on_exposure_notifications_header);
        button.setText(R.string.turn_on_exposure_notifications_action);
        configureButtonForStartEn(button, isInFlight);
        break;
      case FOCUS_LOST:
        setContainerVisibility(containerView, true);
        title.setText(R.string.switch_app_for_exposure_notifications);
        text.setText(getString(R.string.focus_lost_warning,
            StringUtils.getApplicationName(requireContext())));
        button.setText(R.string.switch_app_for_exposure_notifications_action);
        configureButtonForStartEn(button, isInFlight);
        break;
      case PAUSED_USER_PROFILE_NOT_SUPPORT:
        setContainerVisibility(containerView, true);
        title.setText(R.string.exposure_notifications_are_inactive);
        text.setText(R.string.user_profile_not_supported_warning);
        button.setVisibility(View.GONE);
        break;
      case PAUSED_NOT_IN_ALLOWLIST:
        String approvedAppsLinkText = getString(R.string.approved_apps_link_text);

        setContainerVisibility(containerView, true);
        title.setText(R.string.exposure_notifications_are_inactive);
        text.setText(StringUtils.generateTextWithHyperlink(
            new URLSpan(getString(R.string.allowlisted_en_apps_link)),
            getString(R.string.not_in_allowlist_warning, approvedAppsLinkText),
            approvedAppsLinkText));
        text.setMovementMethod(LinkMovementMethod.getInstance());
        button.setVisibility(View.GONE);
        break;
      case PAUSED_HW_NOT_SUPPORT:
        String deviceRequirementsLinkText = getString(R.string.device_requirements_link_text);

        setContainerVisibility(containerView, true);
        title.setText(R.string.exposure_notifications_are_inactive);
        text.setText(StringUtils.generateTextWithHyperlink(
            new URLSpan(getString(R.string.device_requirements_link)),
            getString(R.string.hw_not_supported_warning, deviceRequirementsLinkText),
            deviceRequirementsLinkText));
        text.setMovementMethod(LinkMovementMethod.getInstance());
        button.setVisibility(View.GONE);
        break;
      case PAUSED_EN_NOT_SUPPORT:
        String learnMoreLinkText = getString(R.string.learn_more);

        setContainerVisibility(containerView, true);
        title.setText(R.string.exposure_notifications_are_inactive);
        text.setText(StringUtils.generateTextWithHyperlink(
            new URLSpan(getString(R.string.en_info_main_page_link)),
            getString(R.string.en_not_supported_warning, learnMoreLinkText),
            learnMoreLinkText));
        text.setMovementMethod(LinkMovementMethod.getInstance());
        button.setVisibility(View.GONE);
        break;
      default:
        setContainerVisibility(containerView, false);
        break;
    }
  }

}
