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
import com.google.android.apps.exposurenotification.databinding.FragmentShareDiagnosisNotSharedBinding;
import dagger.hilt.android.AndroidEntryPoint;

/**
 * Result page shown when the diagnosis has not been shared with the keyserver, for example when the
 * user cancels upload, or when there is an upload failure.
 */
@AndroidEntryPoint
public class ShareDiagnosisNotSharedFragment extends ShareDiagnosisBaseFragment {

  private static final String TAG = "ShareExposureNotSharedFrag";

  private FragmentShareDiagnosisNotSharedBinding binding;

  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
    binding = FragmentShareDiagnosisNotSharedBinding.inflate(inflater, parent, false);
    return binding.getRoot();
  }

  @Override
  public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    requireActivity().setTitle(R.string.not_shared_confirm_title);
    binding.shareDoneButton.setOnClickListener(v -> closeShareDiagnosisFlow());
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    binding = null;
  }

}
