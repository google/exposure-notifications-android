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
import androidx.fragment.app.Fragment;
import com.google.android.apps.exposurenotification.R;

/**
 * Learn more sub-page for {@link ShareDiagnosisEditFragment} in adding a positive diagnosis flow
 */
public class ShareDiagnosisLearnMoreFragment extends Fragment {

  private static final String TAG = "ShareExposureLearnMoreFrag";

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_share_diagnosis_learn_more, parent, false);
  }

  @Override
  public void onViewCreated(View view, Bundle savedInstanceState) {
    View upButton = view.findViewById(android.R.id.home);
    upButton.setOnClickListener(v -> navigateUp());
  }

  private void navigateUp() {
    getParentFragmentManager().popBackStack();
  }
}
