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
import com.google.android.apps.exposurenotification.common.BuildUtils;
import com.google.android.apps.exposurenotification.common.BuildUtils.Type;
import com.google.android.apps.exposurenotification.databinding.FragmentPreAuthTeksReleasedBinding;
import com.google.android.apps.exposurenotification.home.BaseFragment;

/**
 * Screen (which semantically belongs to the upload flow) that is displayed after TEKs have been
 * automatically released from the GMSCore to share the test result in the background.
 *
 * <p>Automatic TEKs release happens only per user's consent.
 */
public class PreAuthTEKsReleasedFragment extends BaseFragment {

  private FragmentPreAuthTeksReleasedBinding binding;

  /**
   * Creates a {@link PreAuthTEKsReleasedFragment} fragment.
   */
  public static PreAuthTEKsReleasedFragment newInstance() {
    return new PreAuthTEKsReleasedFragment();
  }

  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
    binding = FragmentPreAuthTeksReleasedBinding.inflate(inflater, parent, false);
    return binding.getRoot();
  }

  @Override
  public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    requireActivity().setTitle(R.string.thank_you_for_notifying_title);

    binding.mainContent.setText(getString(R.string.thank_you_for_notifying_content,
        getString(R.string.health_authority_name)));

    binding.btnDone.setOnClickListener(v -> onBackPressed());
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    binding = null;
  }

  @Override
  public boolean onBackPressed() {
    // For V2, we pop the current fragment transaction off the stack to land to the home screen.
    if (BuildUtils.getType() == Type.V2) {
      getParentFragmentManager().popBackStack();
    }
    return false;
  }

}
