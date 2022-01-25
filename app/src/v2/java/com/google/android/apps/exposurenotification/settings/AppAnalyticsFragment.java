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

package com.google.android.apps.exposurenotification.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.databinding.FragmentAppAnalyticsBinding;
import com.google.android.apps.exposurenotification.home.BaseFragment;
import com.google.android.apps.exposurenotification.utils.UrlUtils;
import dagger.hilt.android.AndroidEntryPoint;

/**
 * Fragment to learn more and turn on and off app analytics.
 */
@AndroidEntryPoint
public class AppAnalyticsFragment extends BaseFragment {

  private FragmentAppAnalyticsBinding binding;

  public static AppAnalyticsFragment newInstance() {
    return new AppAnalyticsFragment();
  }

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent,
      Bundle savedInstanceState) {
    binding = FragmentAppAnalyticsBinding.inflate(getLayoutInflater());
    return binding.getRoot();
  }

  @Override
  public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    requireActivity().setTitle(R.string.app_analytics_title);

    AppAnalyticsViewModel appAnalyticsViewModel =
        new ViewModelProvider(this).get(AppAnalyticsViewModel.class);

    binding.home.setContentDescription(getString(R.string.navigate_up));
    binding.home.setOnClickListener(v -> requireActivity().onBackPressed());

    appAnalyticsViewModel.getAppAnalyticsLiveData().observe(getViewLifecycleOwner(), isEnabled -> {
      binding.appAnalyticsSwitch.setOnCheckedChangeListener(null);
      binding.appAnalyticsSwitch.setChecked(isEnabled);
      binding.appAnalyticsSwitch.setOnCheckedChangeListener(
          (v, checked) -> appAnalyticsViewModel.setAppAnalyticsState(checked));
    });

    binding.appAnalyticsLearnMode.setOnClickListener(
        v -> UrlUtils.openUrl(binding.getRoot(), getString(R.string.app_analytics_link)));
  }

}
