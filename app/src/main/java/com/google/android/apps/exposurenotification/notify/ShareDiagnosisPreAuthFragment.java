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

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult;
import androidx.annotation.NonNull;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.PairLiveData;
import com.google.android.apps.exposurenotification.databinding.FragmentShareDiagnosisPreAuthBinding;
import com.google.android.apps.exposurenotification.notify.ShareDiagnosisViewModel.Step;
import com.google.android.apps.exposurenotification.utils.UrlUtils;
import com.google.common.base.Optional;

/**
 * Final page of the self-report sharing flow, shown when the user's keys have been successfully
 * uploaded to the keyserver and the HA enabled Pre-auth for self-reporting.
 */
public class ShareDiagnosisPreAuthFragment extends ShareDiagnosisBaseFragment {

  private FragmentShareDiagnosisPreAuthBinding binding;

  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
    binding = FragmentShareDiagnosisPreAuthBinding.inflate(inflater, parent, false);
    return binding.getRoot();
  }

  @Override
  public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    requireActivity().setTitle(R.string.share_confirm_title);

    binding.preAuthLayout.preAuthCardContent.setText(
        getString(R.string.notify_others_if_result_updated_content,
            getString(R.string.health_authority_name)));

    binding.preAuthLayout.learnMoreButton.setOnClickListener(
        v -> UrlUtils.openUrl(v, getString(R.string.en_reporting_info_link)));

    shareDiagnosisViewModel
        .maybeGetNextStepLiveData(Step.PRE_AUTH)
        .observe(
            getViewLifecycleOwner(),
            step ->
                binding.preAuthLayout.noThanksButton.setOnClickListener(
                    v -> maybeMoveToNextStep(step)));

    binding.preAuthLayout.yesButton.setOnClickListener(v ->
        shareDiagnosisViewModel.preAuthorizeTeksRelease());

    ActivityResultLauncher<IntentSenderRequest> activityResultLauncher =
        registerForActivityResult(new StartIntentSenderForResult(), activityResult -> {
          if (activityResult.getResultCode() == Activity.RESULT_OK) {
            // Okay to pre-release TEKs.
            shareDiagnosisViewModel.preAuthorizeTeksRelease();
          } else {
            // Not okay to pre-release TEKs.
            shareDiagnosisViewModel.skipPreAuthorizedTEKsRelease();
          }
        });

    PairLiveData<Optional<Step>, Boolean> nextStepLiveData = PairLiveData.of(
        shareDiagnosisViewModel.maybeGetNextStepLiveData(Step.PRE_AUTH),
        shareDiagnosisViewModel.getPreAuthFlowCompletedLiveEvent()
    );
    nextStepLiveData.observe(getViewLifecycleOwner(), (step, preAuthFlowCompleted) -> {
      if (preAuthFlowCompleted) {
        // Move to the next step only when the Pre-auth has been completed.
        maybeMoveToNextStep(step);
      }
    });

    shareDiagnosisViewModel.getTeksPreReleaseResolutionRequiredLiveEvent()
        .observe(getViewLifecycleOwner(), apiException ->
            shareDiagnosisViewModel.startResolution(apiException, activityResultLauncher));
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    binding = null;
  }

  @Override
  public boolean onBackPressed() {
    closeShareDiagnosisFlowImmediately();
    return true;
  }

  private void maybeMoveToNextStep(Optional<Step> step) {
    if (step.isPresent()) {
      shareDiagnosisViewModel.nextStepIrreversible(step.get());
    } else {
      closeShareDiagnosisFlowImmediately();
    }
  }

}
