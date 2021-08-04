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

package com.google.android.apps.exposurenotification.notify;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.databinding.FragmentShareDiagnosisIsCodeNeededBinding;
import com.google.android.apps.exposurenotification.notify.ShareDiagnosisFlowHelper.ShareDiagnosisFlow;
import com.google.android.apps.exposurenotification.notify.ShareDiagnosisViewModel.Step;
import dagger.hilt.android.AndroidEntryPoint;

/**
 * First step in the diagnosis flow, in which the user specifies if they need a verification code
 * from the Health Authority or not.
 */
@AndroidEntryPoint
public class ShareDiagnosisIsCodeNeededFragment extends ShareDiagnosisBaseFragment {

  FragmentShareDiagnosisIsCodeNeededBinding binding;

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent,
      Bundle savedInstanceState) {
    binding = FragmentShareDiagnosisIsCodeNeededBinding.inflate(inflater, parent, false);
    return binding.getRoot();
  }

  @Override
  public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    String doYouHaveCodeTitle = getString(R.string.do_you_have_code_title);

    requireActivity().setTitle(doYouHaveCodeTitle);

    // Text for the binding.doYouHaveCodeContent view may be provided by the Health Authority (HA).
    // If that's the case, populate this view with an HA-provided value.
    if (!TextUtils.isEmpty(getString(R.string.self_report_intro))) {
      binding.doYouHaveCodeContent.setText(R.string.self_report_intro);
    }

    shareDiagnosisViewModel.getCurrentDiagnosisLiveData().observe(
        getViewLifecycleOwner(), diagnosisEntity -> binding.home.setOnClickListener(
            v -> maybeCloseShareDiagnosisFlow(DiagnosisEntityHelper.hasVerified(diagnosisEntity))));

    binding.btnIHaveCode.setOnClickListener(v -> {
      shareDiagnosisViewModel.setShareDiagnosisFlow(ShareDiagnosisFlow.DEFAULT);
      shareDiagnosisViewModel.nextStep(Step.CODE);
    });

    shareDiagnosisViewModel.getRecentlySharedPositiveDiagnosisLiveData().observe(
        getViewLifecycleOwner(),
        diagnosis -> binding.btnINeedCode.setOnClickListener(v -> {
          if (diagnosis.isPresent()) {
            shareDiagnosisViewModel.nextStep(Step.ALREADY_REPORTED);
          } else {
            shareDiagnosisViewModel.setShareDiagnosisFlow(ShareDiagnosisFlow.SELF_REPORT);
            shareDiagnosisViewModel.nextStep(Step.GET_CODE);
          }
        })
    );
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    binding = null;
  }
}
