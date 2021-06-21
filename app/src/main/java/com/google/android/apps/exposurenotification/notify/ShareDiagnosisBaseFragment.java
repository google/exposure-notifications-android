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
import android.view.View;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.home.BaseFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Base {@link Fragment} for the fragments in the Share Diagnosis flow. */
public abstract class ShareDiagnosisBaseFragment extends BaseFragment {

  protected ShareDiagnosisViewModel shareDiagnosisViewModel;

  @CallSuper
  @Override
  public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    shareDiagnosisViewModel = new ViewModelProvider(getParentFragment())
        .get(ShareDiagnosisViewModel.class);

    if (shareDiagnosisViewModel.isCloseOpen()) {
      showCloseWarningAlertDialog();
    }
  }

  /**
   * Shows an alert dialog warning of closing.
   */
  public void showCloseWarningAlertDialog() {
    shareDiagnosisViewModel.setCloseOpen(true);
    new MaterialAlertDialogBuilder(requireContext(), R.style.ExposureNotificationAlertDialogTheme)
        .setTitle(R.string.share_close_title)
        .setMessage(R.string.share_close_detail)
        .setPositiveButton(R.string.btn_resume_later, (d, w) -> {
          shareDiagnosisViewModel.setCloseOpen(false);
          closeShareDiagnosisFlow();
        })
        .setNegativeButton(R.string.btn_cancel,
            (d, w) -> {
              shareDiagnosisViewModel.setCloseOpen(false);
              d.dismiss();
            })
        .setOnCancelListener(d -> shareDiagnosisViewModel.setCloseOpen(false))
        .show();
  }

  protected void closeShareDiagnosisFlow() {
    if (!getParentFragment().getParentFragmentManager().popBackStackImmediate()) {
      getParentFragment().requireActivity().finish();
    }
  }

}
