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
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.databinding.FragmentShareDiagnosisBeginBinding;
import com.google.android.apps.exposurenotification.home.ExposureNotificationViewModel;
import com.google.android.apps.exposurenotification.notify.ShareDiagnosisViewModel.Step;
import dagger.hilt.android.AndroidEntryPoint;

/**
 * A preamble to the diagnosis flow, showing some info for the user.
 */
@AndroidEntryPoint
public class ShareDiagnosisBeginFragment extends Fragment {

  private static final String TAG = "ShareExposureBeginFrag";

  private FragmentShareDiagnosisBeginBinding binding;
  ExposureNotificationViewModel exposureNotificationViewModel;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
    binding = FragmentShareDiagnosisBeginBinding.inflate(inflater, parent, false);
    return binding.getRoot();
  }

  @Override
  public void onViewCreated(View view, Bundle savedInstanceState) {
    getActivity().setTitle(R.string.share_begin_title);

    exposureNotificationViewModel =
        new ViewModelProvider(requireActivity()).get(ExposureNotificationViewModel.class);

    ShareDiagnosisViewModel viewModel =
        new ViewModelProvider(getActivity()).get(ShareDiagnosisViewModel.class);

    viewModel.getCurrentDiagnosisLiveData().observe(getViewLifecycleOwner(), diagnosisEntity -> {
      binding.home.setOnClickListener(v -> {
        // Only show the dialog if has been verified.
        if (DiagnosisEntityHelper.hasVerified(diagnosisEntity)) {
          ShareDiagnosisActivity.showCloseWarningAlertDialog(requireActivity(), viewModel);
        } else {
          requireActivity().finish();
        }
      });
    });
    binding.home.setContentDescription(getString(R.string.btn_cancel));

    viewModel.getNextStepLiveData(Step.BEGIN).observe(getViewLifecycleOwner(),
        step -> binding.shareNextButton.setOnClickListener(v -> viewModel.nextStep(step)));
  }

  @Override
  public void onResume() {
    super.onResume();
    exposureNotificationViewModel.refreshState();
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    binding = null;
  }
}
