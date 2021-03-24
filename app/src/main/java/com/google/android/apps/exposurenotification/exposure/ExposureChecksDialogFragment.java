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

package com.google.android.apps.exposurenotification.exposure;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.android.apps.exposurenotification.databinding.FragmentExposureChecksDialogBinding;
import dagger.hilt.android.AndroidEntryPoint;
import javax.inject.Inject;

/**
 * Simple custom dialog to display list of the exposure checks.
 */
@AndroidEntryPoint
public class ExposureChecksDialogFragment extends DialogFragment {

  public static String TAG = "ExposureChecksDialogFragment";

  private FragmentExposureChecksDialogBinding binding;

  @Inject
  Clock clock;

  public static ExposureChecksDialogFragment newInstance() {
    return new ExposureChecksDialogFragment();
  }

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent,
      Bundle savedInstanceState) {
    binding = FragmentExposureChecksDialogBinding.inflate(inflater, parent, false);

    if (getDialog() != null) {
      getDialog().setTitle(getString(R.string.recent_check_dialog_title));
    }

    // Needed to properly round corners of the dialog window
    if (getDialog() != null && getDialog().getWindow() != null) {
      getDialog().getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
    }

    return binding.getRoot();
  }

  @Override
  public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
    ExposureHomeViewModel exposureHomeViewModel = new ViewModelProvider(requireActivity())
        .get(ExposureHomeViewModel.class);

    ExposureCheckEntityAdapter exposureCheckEntityAdapter =
        new ExposureCheckEntityAdapter(requireContext(), clock);
    final LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
    binding.checksRecyclerView.setLayoutManager(layoutManager);
    binding.checksRecyclerView.setAdapter(exposureCheckEntityAdapter);

    binding.checksDialogClose.setOnClickListener(v -> dismiss());

    exposureHomeViewModel
        .getExposureChecksLiveData()
        .observe(
            getViewLifecycleOwner(), exposureChecks -> {
              if (exposureChecks.isEmpty()) {
                binding.dialogTitle.setText(getString(R.string.no_recent_exposure_checks));
                binding.checksRecyclerView.setVisibility(View.GONE);
              } else {
                binding.dialogTitle.setText(getString(R.string.recent_check_dialog_title));
                binding.checksRecyclerView.setVisibility(View.VISIBLE);
              }
              exposureCheckEntityAdapter.setExposureChecks(exposureChecks);
            });
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    binding = null;
  }
}
