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

package com.google.android.apps.exposurenotification.notify;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ViewFlipper;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.home.ExposureNotificationViewModel;
import com.google.android.apps.exposurenotification.notify.ShareDiagnosisViewModel.Step;
import com.google.android.material.snackbar.Snackbar;
import dagger.hilt.android.AndroidEntryPoint;

/**
 * A preamble to the diagnosis flow, showing some info for the user.
 */
@AndroidEntryPoint
public class ShareDiagnosisBeginFragment extends Fragment {

  private static final String TAG = "ShareExposureBeginFrag";

  private static final int EN_ENABLED_VIEWFLIPPER_ENABLED = 0;
  private static final int EN_ENABLED_VIEWFLIPPER_DISABLED = 1;

  ExposureNotificationViewModel exposureNotificationViewModel;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_share_diagnosis_begin, parent, false);
  }

  @Override
  public void onViewCreated(View view, Bundle savedInstanceState) {
    getActivity().setTitle(R.string.share_begin_title);

    exposureNotificationViewModel =
        new ViewModelProvider(requireActivity()).get(ExposureNotificationViewModel.class);

    Button startApiButton = view.findViewById(R.id.start_api_button);
    startApiButton.setOnClickListener(
        v -> exposureNotificationViewModel.startExposureNotifications());

    exposureNotificationViewModel
        .getInFlightLiveData()
        .observe(getViewLifecycleOwner(), isInFlight -> startApiButton.setEnabled(!isInFlight));

    exposureNotificationViewModel
        .getApiErrorLiveEvent()
        .observe(getViewLifecycleOwner(), unused -> {
          View rootView = getView();
          if (rootView != null) {
            Snackbar.make(rootView, R.string.generic_error_message, Snackbar.LENGTH_LONG).show();
          }
        });

    ShareDiagnosisViewModel viewModel =
        new ViewModelProvider(getActivity()).get(ShareDiagnosisViewModel.class);

    View closeButton = view.findViewById(android.R.id.home);
    Button nextButton = view.findViewById(R.id.share_next_button);

    viewModel.getCurrentDiagnosisLiveData().observe(getViewLifecycleOwner(), diagnosisEntity -> {
      nextButton.setOnClickListener(v -> viewModel.nextStep(
          ShareDiagnosisFlowHelper.getNextStep(Step.BEGIN, diagnosisEntity, getContext())));
      closeButton.setOnClickListener(v -> {
        // Only show the dialog if has been verified.
        if (DiagnosisEntityHelper.hasVerified(diagnosisEntity)) {
          ShareDiagnosisActivity.showCloseWarningAlertDialog(requireActivity(), viewModel);
        } else {
          requireActivity().finish();
        }
      });
    });
    closeButton.setContentDescription(getString(R.string.btn_cancel));

    /*
     * Make sure to only allow the user to continue if EN is enabled.
     * If it is disabled, show warning and disable the continue button (independent of whether
     * this was called from a deeplink)
     *
     * The IsEnabledLiveData is update every time onResume() is called, so the UI should always
     * reflect the correct state.
     */
    ViewFlipper isEnabledFlipper = view.findViewById(R.id.en_enabled_flipper);
    exposureNotificationViewModel
        .getEnEnabledLiveData()
        .observe(
            getViewLifecycleOwner(),
            isEnabled -> {
              if (isEnabled) {
                isEnabledFlipper.setDisplayedChild(EN_ENABLED_VIEWFLIPPER_ENABLED);
                nextButton.setVisibility(View.VISIBLE);
              } else {
                isEnabledFlipper.setDisplayedChild(EN_ENABLED_VIEWFLIPPER_DISABLED);
                nextButton.setVisibility(View.INVISIBLE);
              }
            });
  }

  @Override
  public void onResume() {
    super.onResume();
    exposureNotificationViewModel.refreshState();
  }

}
