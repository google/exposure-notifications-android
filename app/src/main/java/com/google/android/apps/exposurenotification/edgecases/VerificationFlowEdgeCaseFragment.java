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
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.PairLiveData;
import com.google.android.apps.exposurenotification.common.StringUtils;
import com.google.android.apps.exposurenotification.databinding.FragmentEdgeCasesVerificationBinding;
import com.google.android.apps.exposurenotification.home.ExposureNotificationViewModel.ExposureNotificationState;
import com.google.android.apps.exposurenotification.notify.DiagnosisEntityHelper;
import com.google.android.apps.exposurenotification.notify.ShareDiagnosisBaseFragment;
import com.google.android.apps.exposurenotification.notify.ShareDiagnosisFragment;
import com.google.android.apps.exposurenotification.notify.ShareDiagnosisViewModel;
import com.google.android.apps.exposurenotification.proto.UiInteraction.EventType;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity;
import com.google.android.apps.exposurenotification.utils.UrlUtils;
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
    ShareDiagnosisViewModel shareDiagnosisViewModel =
        new ViewModelProvider(getParentFragment()).get(ShareDiagnosisViewModel.class);

    PairLiveData<DiagnosisEntity, Boolean> displayEdgeCaseLiveData = PairLiveData.of(
        shareDiagnosisViewModel.getCurrentDiagnosisLiveData(),
        exposureNotificationViewModel.getEnEnabledLiveData());

    /*
     * Prevent users from continuing at any step in the diagnosis sharing flow if the EN is disabled
     * as they won't be able to share their keys in that case. All the EN states in a switch
     * statement below may be returned in that case. But never prevent users from viewing diagnoses
     * they've already shared.
     */
    displayEdgeCaseLiveData.observe(this,
        (currentDiagnosis, isEnabled) -> {
          if (DiagnosisEntityHelper.hasBeenShared(currentDiagnosis)) {
            setContainerVisibility(containerView, false);
          } else {
            setContainerVisibility(containerView, !isEnabled);
          }

          binding.home.setOnClickListener(v ->
              ((ShareDiagnosisFragment) getParentFragment()).onBackPressed());
        });

    TextView title = binding.edgecaseMainTitle;
    TextView text = binding.edgecaseMainText;
    Button button = binding.edgecaseMainButton;
    button.setEnabled(true);

    switch (state) {
      case DISABLED:
        title.setText(R.string.exposure_notifications_are_turned_off);
        text.setText(getString(R.string.notify_turn_on_exposure_notifications_header,
            getString(R.string.using_en_helps_even_if_vaccinated)));
        button.setText(R.string.turn_on_exposure_notifications_action);
        configureButtonForStartEn(button, isInFlight);
        break;
      case FOCUS_LOST:
        title.setText(R.string.switch_app_for_exposure_notifications);
        text.setText(getString(R.string.focus_lost_warning,
            StringUtils.getApplicationName(requireContext())));
        button.setText(R.string.switch_app_for_exposure_notifications_action);
        configureButtonForStartEn(button, isInFlight);
        break;
      case STORAGE_LOW:
        title.setText(R.string.exposure_notifications_are_turned_off);
        text.setText(R.string.storage_low_warning);
        button.setText(R.string.manage_storage);
        configureButtonForManageStorage(button);
        logUiInteraction(EventType.LOW_STORAGE_WARNING_SHOWN);
        break;
      case PAUSED_USER_PROFILE_NOT_SUPPORT:
        title.setText(R.string.exposure_notifications_are_turned_off);
        text.setText(R.string.user_profile_not_supported_warning);
        button.setVisibility(View.GONE);
        break;
      case PAUSED_NOT_IN_ALLOWLIST:
        title.setText(R.string.exposure_notifications_are_turned_off);
        text.setText(R.string.not_in_allowlist_warning);
        text.setMovementMethod(LinkMovementMethod.getInstance());
        button.setVisibility(View.GONE);
        break;
      case PAUSED_HW_NOT_SUPPORT:
        String deviceRequirementsLinkText = getString(R.string.device_requirements_link_text);

        title.setText(R.string.exposure_notifications_are_turned_off);
        text.setText(StringUtils.generateTextWithHyperlink(
            UrlUtils.createURLSpan(getString(R.string.device_requirements_link)),
            getString(R.string.hw_not_supported_warning, deviceRequirementsLinkText),
            deviceRequirementsLinkText));
        text.setMovementMethod(LinkMovementMethod.getInstance());
        button.setVisibility(View.GONE);
        break;
      case PAUSED_EN_NOT_SUPPORT:
        String learnMoreLinkText = getString(R.string.learn_more);

        title.setText(R.string.exposure_notifications_are_turned_off);
        text.setText(StringUtils.generateTextWithHyperlink(
            UrlUtils.createURLSpan(getString(R.string.en_info_main_page_link)),
            getString(R.string.en_not_supported_warning, learnMoreLinkText),
            learnMoreLinkText));
        text.setMovementMethod(LinkMovementMethod.getInstance());
        button.setVisibility(View.GONE);
        break;
      default:
        break;
    }
  }

}
