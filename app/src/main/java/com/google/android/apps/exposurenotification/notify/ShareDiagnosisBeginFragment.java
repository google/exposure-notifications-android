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
import androidx.annotation.NonNull;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.PairLiveData;
import com.google.android.apps.exposurenotification.databinding.FragmentShareDiagnosisBeginBinding;
import com.google.android.apps.exposurenotification.notify.ShareDiagnosisViewModel.Step;
import dagger.hilt.android.AndroidEntryPoint;

/**
 * A preamble to the diagnosis flow, showing some info for the user.
 */
@AndroidEntryPoint
public class ShareDiagnosisBeginFragment extends ShareDiagnosisBaseFragment {

  private static final String TAG = "ShareExposureBeginFrag";

  private FragmentShareDiagnosisBeginBinding binding;

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent,
      Bundle savedInstanceState) {
    binding = FragmentShareDiagnosisBeginBinding.inflate(inflater, parent, false);
    return binding.getRoot();
  }

  @Override
  public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    requireActivity().setTitle(R.string.share_begin_title);

    shareDiagnosisViewModel.getCurrentDiagnosisLiveData().observe(
        getViewLifecycleOwner(), diagnosisEntity -> binding.home.setOnClickListener(v -> {
          // Only show the dialog if has been verified.
          if (DiagnosisEntityHelper.hasVerified(diagnosisEntity)) {
            showCloseWarningAlertDialog();
          } else {
            closeShareDiagnosisFlow();
          }
        }));

    // Determine the next step
    PairLiveData<Step, Boolean> nextStepAndIsCodeInvalidLiveData = PairLiveData.of(
        shareDiagnosisViewModel.getNextStepLiveData(Step.BEGIN),
        shareDiagnosisViewModel.isCodeInvalidForCodeStepLiveData());
    nextStepAndIsCodeInvalidLiveData.observe(
        this, (step, isCodeInvalid) -> {
          if (isCodeInvalid) {
            // Never skip the Code step if user has previously input an invalid code.
            binding.shareNextButton.setOnClickListener(
                v -> shareDiagnosisViewModel.nextStep(Step.CODE));
          } else {
            binding.shareNextButton.setOnClickListener(v -> shareDiagnosisViewModel.nextStep(step));
          }
        }
    );
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    binding = null;
  }

}
