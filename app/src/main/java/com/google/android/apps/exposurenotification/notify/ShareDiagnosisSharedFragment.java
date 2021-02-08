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
import androidx.fragment.app.Fragment;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.databinding.FragmentShareDiagnosisSharedBinding;
import dagger.hilt.android.AndroidEntryPoint;

/**
 * Result page the diagnosis flow, shown when the user's keys have been successfully uploaded to the
 * keyserver.
 */
@AndroidEntryPoint
public class ShareDiagnosisSharedFragment extends Fragment {

  private static final String TAG = "ShareExposureSharedFrag";

  private FragmentShareDiagnosisSharedBinding binding;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
    binding = FragmentShareDiagnosisSharedBinding.inflate(inflater, parent, false);
    return binding.getRoot();
  }

  @Override
  public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
    getActivity().setTitle(R.string.share_confirm_title);
    binding.shareDoneButton.setOnClickListener(v -> requireActivity().finish());
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    binding = null;
  }
}
