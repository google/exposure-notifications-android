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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.apps.exposurenotification.R;
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
    viewModel.getCurrentDiagnosisLiveData().observe(getViewLifecycleOwner(), diagnosisEntity ->
        binding.home.setOnClickListener(v -> {
          // Only show a warning dialog if the entity has been verified.
          if (DiagnosisEntityHelper.hasVerified(diagnosisEntity)) {
            ShareDiagnosisActivity.showCloseWarningAlertDialog(requireActivity(), viewModel);
          } else {
            requireActivity().finish();
          }
        }));
    binding.home.setContentDescription(getString(R.string.btn_cancel));

    TextView title = binding.edgecaseMainTitle;
    TextView text = binding.edgecaseMainText;
    Button button = binding.edgecaseMainButton;
    button.setEnabled(true);

    switch (state) {
      case ENABLED:
      case PAUSED_LOCATION_BLE:
      case PAUSED_BLE:
      case PAUSED_LOCATION:
      case STORAGE_LOW:
      default:
        setContainerVisibility(containerView, false);
        break;
      case DISABLED:
        setContainerVisibility(containerView, true);
        title.setText(R.string.exposure_notifications_are_turned_off);
        text.setText(R.string.notify_turn_on_exposure_notifications_header);
        button.setText(R.string.turn_on_exposure_notifications_action);
        configureButtonForStartEn(button, isInFlight);
        break;
    }
  }

}
