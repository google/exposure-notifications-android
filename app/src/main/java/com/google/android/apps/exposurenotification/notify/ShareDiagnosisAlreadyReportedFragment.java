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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.databinding.FragmentShareDiagnosisAlreadyReportedBinding;
import dagger.hilt.android.AndroidEntryPoint;

/**
 * Screen in a self-report flow shown if user tries to self-report even though they've already
 * recently self-reported or shared a confirmed positive test result.
 */
@AndroidEntryPoint
public class ShareDiagnosisAlreadyReportedFragment extends ShareDiagnosisBaseFragment {

  FragmentShareDiagnosisAlreadyReportedBinding binding;

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent,
      Bundle savedInstanceState) {
    binding = FragmentShareDiagnosisAlreadyReportedBinding.inflate(inflater, parent, false);
    return binding.getRoot();
  }

  @Override
  public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    requireActivity().setTitle(R.string.positive_result_already_reported_title);
    binding.btnDone.setOnClickListener(v -> closeShareDiagnosisFlowImmediately());

    String healthAuthorityName = getString(R.string.health_authority_name);
    binding.alreadyReportedContent.setText(
        getString(R.string.positive_result_already_reported_content, healthAuthorityName));
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    binding = null;
  }

}
