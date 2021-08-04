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

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.android.apps.exposurenotification.databinding.FragmentExposureChecksBinding;
import com.google.android.apps.exposurenotification.home.BaseFragment;
import dagger.hilt.android.AndroidEntryPoint;
import javax.inject.Inject;

/**
 * Recent exposure check-ins fragment.
 */
@AndroidEntryPoint
public class ExposureChecksFragment extends BaseFragment {

  private FragmentExposureChecksBinding binding;

  public static ExposureChecksFragment newInstance() {
    return new ExposureChecksFragment();
  }

  @Inject
  Clock clock;

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent,
      Bundle savedInstanceState) {
    binding = FragmentExposureChecksBinding.inflate(getLayoutInflater());
    return binding.getRoot();
  }

  @Override
  public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    requireActivity().setTitle(R.string.exposure_checks_activity_title);

    ExposureHomeViewModel exposureHomeViewModel = new ViewModelProvider(
        this, getDefaultViewModelProviderFactory()).get(ExposureHomeViewModel.class);

    binding.home.setOnClickListener(v -> requireActivity().onBackPressed());

    ExposureCheckEntityAdapter exposureCheckEntityAdapter =
        new ExposureCheckEntityAdapter(requireContext(), clock);
    final LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
    binding.checksRecyclerView.setLayoutManager(layoutManager);
    binding.checksRecyclerView.setAdapter(exposureCheckEntityAdapter);

    exposureHomeViewModel
        .getExposureChecksLiveData()
        .observe(getViewLifecycleOwner(), exposureChecks -> {
          if (exposureChecks.isEmpty()) {
            binding.checksRecyclerView.setVisibility(View.GONE);
            binding.noRecentExposureChecks.setVisibility(View.VISIBLE);
          } else {
            binding.checksRecyclerView.setVisibility(View.VISIBLE);
            binding.noRecentExposureChecks.setVisibility(View.GONE);
          }
          exposureCheckEntityAdapter.setExposureChecks(exposureChecks);
        });
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    binding = null;
  }
}
