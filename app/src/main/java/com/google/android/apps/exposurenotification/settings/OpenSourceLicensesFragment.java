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

package com.google.android.apps.exposurenotification.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentManager;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.databinding.FragmentOpenSourceLicensesBinding;
import com.google.android.apps.exposurenotification.home.BaseFragment;
import com.mikepenz.aboutlibraries.LibsBuilder;
import com.mikepenz.aboutlibraries.ui.LibsSupportFragment;
import org.jetbrains.annotations.NotNull;

/**
 * Fragment for information about open source licenses.
 */
public class OpenSourceLicensesFragment extends BaseFragment {

  private FragmentOpenSourceLicensesBinding binding;

  public static OpenSourceLicensesFragment newInstance() {
    return new OpenSourceLicensesFragment();
  }

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent,
      Bundle savedInstanceState) {
    binding = FragmentOpenSourceLicensesBinding
        .inflate(getLayoutInflater());
    addOpenSourceLicensesFragment();
    return binding.getRoot();
  }

  @Override
  public void onViewCreated(@NonNull @NotNull View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    binding.home.setContentDescription(getString(R.string.navigate_up));
    binding.home.setOnClickListener(v -> requireActivity().onBackPressed());
  }

  private void addOpenSourceLicensesFragment() {
    LibsSupportFragment fragment = new LibsBuilder()
        .withFields(R.string.class.getFields())
        .withLicenseShown(true)
        .supportFragment();

    FragmentManager fragmentManager = getChildFragmentManager();
    fragmentManager.beginTransaction().replace(R.id.main_container, fragment).commit();
  }

}