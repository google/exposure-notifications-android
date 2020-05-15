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
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.storage.PositiveDiagnosisEntity;
import com.google.android.apps.exposurenotification.storage.PositiveDiagnosisViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;
import org.threeten.bp.format.DateTimeFormatter;
import org.threeten.bp.format.FormatStyle;

/**
 * Page 1 of the viewing (and potentially updating) a positive diagnosis flow
 */
public class ShareExposureViewFragment extends Fragment {

  private static final String TAG = "ShareExposureNotSharedF";

  private static final String STATE_DELETE_OPEN = "DebugFragment.STATE_DELETE_OPEN";
  private static final String KEY_POSITIVE_DIAGNOSIS_ID =
      "PositiveDiagnosisViewFragment.KEY_POSITIVE_DIAGNOSIS_ID";

  private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter
      .ofLocalizedDate(FormatStyle.MEDIUM);

  private boolean deleteOpen = false;
  private PositiveDiagnosisEntity currentPositiveDiagnosisEntity;

  public static ShareExposureViewFragment newInstance(long positiveDiagnosisId) {
    ShareExposureViewFragment fragment = new ShareExposureViewFragment();
    Bundle args = new Bundle();
    args.putLong(KEY_POSITIVE_DIAGNOSIS_ID, positiveDiagnosisId);
    fragment.setArguments(args);
    return fragment;
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_share_exposure_view, parent, false);
  }

  @Override
  public void onViewCreated(View view, Bundle savedInstanceState) {
    TextView testDate = view.findViewById(R.id.test_date);

    PositiveDiagnosisViewModel positiveDiagnosisViewModel =
        new ViewModelProvider(this, getDefaultViewModelProviderFactory())
            .get(PositiveDiagnosisViewModel.class);

    TextView title = view.findViewById(R.id.share_exposure_view_title);

    Button shareButton = view.findViewById(R.id.positive_diagnosis_share_button);
    shareButton.setOnClickListener(v -> shareAction());

    positiveDiagnosisViewModel
        .getByIdLiveData(getArguments().getLong(KEY_POSITIVE_DIAGNOSIS_ID, -1))
        .observe(getViewLifecycleOwner(), positiveDiagnosisEntity -> {
          if (positiveDiagnosisEntity != null) {
            currentPositiveDiagnosisEntity = positiveDiagnosisEntity;
            if (positiveDiagnosisEntity.isShared()) {
              shareButton.setEnabled(false);
              shareButton.setText(R.string.btn_share_already_shared);
              title.setText(R.string.positive_test_shared_title);
            } else {
              shareButton.setEnabled(true);
              shareButton.setText(R.string.btn_share_positive);
              title.setText(R.string.positive_test_not_shared_title);
            }
            testDate.setText(dateTimeFormatter.format(positiveDiagnosisEntity.getTestTimestamp()));
          }
        });

    Button deleteButton = view.findViewById(R.id.positive_diagnosis_delete_button);
    deleteButton.setOnClickListener(v -> deleteAction(positiveDiagnosisViewModel));

    View upButton = view.findViewById(android.R.id.home);
    upButton.setContentDescription(getString(R.string.navigate_up));
    upButton.setOnClickListener((v) -> navigateUp());

    if (savedInstanceState != null) {
      deleteOpen = savedInstanceState.getBoolean(STATE_DELETE_OPEN, false);
    }

    if (deleteOpen) {
      deleteAction(positiveDiagnosisViewModel);
    }
  }

  @Override
  public void onSaveInstanceState(@NonNull Bundle bundle) {
    super.onSaveInstanceState(bundle);
    bundle.putBoolean(STATE_DELETE_OPEN, deleteOpen);
  }

  private void shareAction() {
    getParentFragmentManager()
        .beginTransaction()
        .replace(
            R.id.share_exposure_fragment,
            ShareExposureReviewFragment.newInstanceForUpdate(currentPositiveDiagnosisEntity),
            SHARE_EXPOSURE_FRAGMENT_TAG)
        .addToBackStack(null)
        .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
        .commit();
  }

  private void deleteAction(PositiveDiagnosisViewModel positiveDiagnosisViewModel) {
    deleteOpen = true;
    new MaterialAlertDialogBuilder(requireContext())
        .setTitle(R.string.dialog_delete_positive_test_title)
        .setMessage(R.string.dialog_delete_positive_test_description)
        .setPositiveButton(
            R.string.dialog_delete_positive_test_action,
            (d, w) -> {
              deleteOpen = false;
              Futures.addCallback(positiveDiagnosisViewModel
                      .deleteByIdAsync(currentPositiveDiagnosisEntity.getId()),
                  new FutureCallback<Void>() {
                    @Override
                    public void onSuccess(@NullableDecl Void result) {
                      requireActivity().finish();
                    }

                    @Override
                    public void onFailure(Throwable t) {
                      Log.w(TAG, "Failed to delete", t);
                    }
                  }, ContextCompat.getMainExecutor(requireContext()));
            })
        .setNegativeButton(android.R.string.cancel, (d, w) -> deleteOpen = false)
        .setOnDismissListener((d) -> deleteOpen = false)
        .setOnCancelListener((d) -> deleteOpen = false)
        .show();
  }


  private void navigateUp() {
    requireActivity().finish();
  }

}