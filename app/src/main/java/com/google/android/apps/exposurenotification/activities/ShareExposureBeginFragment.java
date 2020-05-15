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

package com.google.android.apps.exposurenotification.activities;

import static com.google.android.apps.exposurenotification.activities.ShareExposureActivity.SHARE_EXPOSURE_FRAGMENT_TAG;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import com.google.android.apps.exposurenotification.R;

/**
 * Page 1 of the adding a positive diagnosis flow
 */
public class ShareExposureBeginFragment extends Fragment {

  private static final String TAG = "ShareExposureBeginFrag";

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_share_exposure_begin, parent, false);
  }

  @Override
  public void onViewCreated(View view, Bundle savedInstanceState) {
    Button nextButton = view.findViewById(R.id.share_next_button);
    Button cancelButton = view.findViewById(R.id.share_cancel_button);
    View upButton = view.findViewById(android.R.id.home);

    nextButton.setOnClickListener(
        v -> getParentFragmentManager()
            .beginTransaction()
            .replace(
                R.id.share_exposure_fragment,
                new ShareExposureEditFragment(),
                SHARE_EXPOSURE_FRAGMENT_TAG)
            .addToBackStack(null)
            .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
            .commit());
    cancelButton.setOnClickListener((v) -> cancelAction());
    upButton.setOnClickListener((v) -> cancelAction());
  }

  private void cancelAction() {
    requireActivity().finish();
  }

}
