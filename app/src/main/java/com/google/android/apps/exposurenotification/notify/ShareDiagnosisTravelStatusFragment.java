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

import static com.google.android.apps.exposurenotification.storage.DiagnosisEntity.TravelStatus.NOT_ATTEMPTED;
import static com.google.android.apps.exposurenotification.storage.DiagnosisEntity.TravelStatus.NOT_TRAVELED;
import static com.google.android.apps.exposurenotification.storage.DiagnosisEntity.TravelStatus.NO_ANSWER;
import static com.google.android.apps.exposurenotification.storage.DiagnosisEntity.TravelStatus.TRAVELED;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.KeyboardHelper;
import com.google.android.apps.exposurenotification.notify.ShareDiagnosisViewModel.Step;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity.TravelStatus;
import dagger.hilt.android.AndroidEntryPoint;

/**
 * A 4th page of user input for the diagnosis flow, asking user for their travel status in the past
 * 14 days.
 */
@AndroidEntryPoint
public class ShareDiagnosisTravelStatusFragment extends Fragment {

  private static final String TAG = "ShareExposureEditFrag";

  private ShareDiagnosisViewModel viewModel;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_share_diagnosis_travel_status, parent, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
    getActivity().setTitle(R.string.share_travel_title);

    viewModel =
        new ViewModelProvider(getActivity()).get(ShareDiagnosisViewModel.class);

    RadioGroup travelStatusRadioGroup = view.findViewById(R.id.hasTraveledRadioGroup);
    Button nextButton = view.findViewById(R.id.share_next_button);
    Button previousButton = view.findViewById(R.id.share_previous_button);
    View closeButton = view.findViewById(android.R.id.home);

    // "Next" button should be disabled until the travel status is selected
    nextButton.setEnabled(false);

    // Keep input fields up to date with the diagnosis entity.
    viewModel
        .getCurrentDiagnosisLiveData()
        .observe(
            getViewLifecycleOwner(),
            diagnosis -> {
              TravelStatus travelStatus = diagnosis.getTravelStatus();
              if (!NOT_ATTEMPTED.equals(travelStatus)) {
                changeRadioButtonStatusToChecked(mapTravelStatusToRadioButtonId(travelStatus));
                // Next button should be enabled if travel status is selected
                nextButton.setOnClickListener(v -> viewModel
                    .nextStep(ShareDiagnosisFlowHelper.getNextStep(Step.TRAVEL_STATUS, diagnosis)));
                nextButton.setEnabled(true);
              }
              previousButton.setOnClickListener((v) -> {
                KeyboardHelper.maybeHideKeyboard(requireContext(), view);
                viewModel.previousStep(
                    ShareDiagnosisFlowHelper.getPreviousStep(Step.TRAVEL_STATUS, diagnosis));
              });
            });

    travelStatusRadioGroup.setOnCheckedChangeListener((radioGroup, checkedId) -> {
      // Enabled next button only when the travel status is selected
      TravelStatus travelStatus =
          mapRadioButtonIdToTravelStatus(checkedId);
      viewModel.setTravelStatus(travelStatus);
    });

    closeButton.setContentDescription(getString(R.string.navigate_up));
    closeButton.setOnClickListener((v) -> closeAction());
  }

  private void closeAction() {
    ShareDiagnosisActivity.showCloseWarningAlertDialog(requireActivity(), viewModel);
  }

  private int mapTravelStatusToRadioButtonId(TravelStatus travelStatus) {
    switch (travelStatus) {
      case TRAVELED:
        return R.id.hasTraveledConfirmed;
      case NOT_TRAVELED:
        return R.id.hasTraveledNoTravel;
      case NO_ANSWER:
        return R.id.hasTraveledNoAnswer;
      case NOT_ATTEMPTED:
      default:
        throw new IllegalStateException("Failed to map travel status to radio button id");
    }
  }

  private TravelStatus mapRadioButtonIdToTravelStatus(int checkedRadioButtonId) {
    switch (checkedRadioButtonId) {
      case R.id.hasTraveledConfirmed:
        return TRAVELED;
      case R.id.hasTraveledNoTravel:
        return NOT_TRAVELED;
      case R.id.hasTraveledNoAnswer:
        return NO_ANSWER;
      default:
        throw new IllegalStateException("Failed to map checked button to travel status");
    }
  }

  private void changeRadioButtonStatusToChecked(int radioButtonId) {
    RadioButton radioButton = getView().findViewById(radioButtonId);
    radioButton.setChecked(true);
  }
}
