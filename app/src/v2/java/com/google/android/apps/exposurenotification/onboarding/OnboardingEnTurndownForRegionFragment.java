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

package com.google.android.apps.exposurenotification.onboarding;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import com.google.android.apps.exposurenotification.databinding.FragmentOnboardingEnTurndownForRegionBinding;
import com.google.android.apps.exposurenotification.home.BaseFragment;
import dagger.hilt.android.AndroidEntryPoint;

/**
 * Onboarding displayed if the Exposure Notifications has been turned down for the given app (i.e.
 * region)
 */
@AndroidEntryPoint
public class OnboardingEnTurndownForRegionFragment extends BaseFragment {

  private FragmentOnboardingEnTurndownForRegionBinding binding;

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent,
      Bundle savedInstanceState) {
    binding = FragmentOnboardingEnTurndownForRegionBinding.inflate(inflater, parent, false);
    return binding.getRoot();
  }

  @Override
  public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    binding.btnCloseApp.setOnClickListener(v -> closeAppAndFinishAppTask());
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    binding = null;
  }

  @Override
  public boolean onBackPressed() {
    closeAppAndFinishAppTask();
    return true;
  }

}
