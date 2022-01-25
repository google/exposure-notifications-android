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

package com.google.android.apps.exposurenotification.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.databinding.FragmentEnTurndownNoticeBinding;
import com.google.android.apps.exposurenotification.nearby.EnStateUtil;
import dagger.hilt.android.AndroidEntryPoint;

/**
 * Fragment that holds information about the EN turndown.
 */
@AndroidEntryPoint
public class EnTurndownNoticeFragment extends BaseFragment {

  /**
   * Creates a {@link EnTurndownNoticeFragment} fragment.
   */
  public static EnTurndownNoticeFragment newInstance() {
    return new EnTurndownNoticeFragment();
  }

  private FragmentEnTurndownNoticeBinding binding;
  private ExposureNotificationViewModel exposureNotificationViewModel;

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent,
      Bundle savedInstanceState) {
    binding = FragmentEnTurndownNoticeBinding.inflate(getLayoutInflater());
    return binding.getRoot();
  }

  @Override
  public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    exposureNotificationViewModel =
        new ViewModelProvider(requireActivity()).get(ExposureNotificationViewModel.class);

    exposureNotificationViewModel
        .getStateLiveData()
        .observe(getViewLifecycleOwner(), state -> {
          if (EnStateUtil.isEnTurndownForRegion(state)) {
            requireActivity().setTitle(R.string.en_turndown_for_area_title);
            binding.enTurndownTitle.setText(R.string.en_turndown_for_area_title);
            binding.enTurndownContents.setText(R.string.en_turndown_for_area_contents);
            if (EnStateUtil.isAgencyTurndownMessagePresent(requireContext())) {
              binding.healthAuthorityTurndownContent.setVisibility(View.VISIBLE);
            }
          } else {
            requireActivity().setTitle(R.string.en_turndown_title);
            binding.enTurndownTitle.setText(R.string.en_turndown_title);
            binding.enTurndownContents.setText(R.string.en_turndown_contents);
            binding.healthAuthorityTurndownContent.setVisibility(View.GONE);
          }
        });

    binding.home.setOnClickListener(v -> onBackPressed());
  }

  @Override
  public boolean onBackPressed() {
    getParentFragmentManager().popBackStack();
    return true;
  }

}
