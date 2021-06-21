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
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.SnackbarUtil;
import com.google.android.apps.exposurenotification.databinding.FragmentShareDiagnosisReviewBinding;
import com.google.android.apps.exposurenotification.network.Connectivity;
import com.google.android.apps.exposurenotification.notify.ShareDiagnosisViewModel.Step;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import dagger.hilt.android.AndroidEntryPoint;
import javax.inject.Inject;
import org.threeten.bp.format.DateTimeFormatter;
import org.threeten.bp.format.FormatStyle;

/**
 * Page of the diagnosis flow that allows the user to see their diagnosis info and choose whether or
 * not to share their keys with the upload server.
 */
@AndroidEntryPoint
public class ShareDiagnosisReviewFragment extends ShareDiagnosisBaseFragment {

  private static final String TAG = "ShareExposureReviewFrag";

  private static final DateTimeFormatter dateTimeFormatter =
      DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM);

  private FragmentShareDiagnosisReviewBinding binding;

  @Inject
  Connectivity connectivity;

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent,
      Bundle savedInstanceState) {
    binding = FragmentShareDiagnosisReviewBinding.inflate(inflater, parent, false);
    return binding.getRoot();
  }

  @Override
  public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    requireActivity().setTitle(R.string.share_review_title);

    binding.home.setOnClickListener(v -> showCloseWarningAlertDialog());

    binding.shareShareButton.setOnClickListener(v -> {
      if (connectivity.hasInternet()) {
        shareAction();
      } else {
        SnackbarUtil
            .maybeShowRegularSnackbar(getView(), getString(R.string.share_error_no_internet));
      }
    });

    shareDiagnosisViewModel.registerResolutionForActivityResult(this);

    shareDiagnosisViewModel
        .getCurrentDiagnosisLiveData()
        .observe(
            getViewLifecycleOwner(),
            diagnosisEntity -> {
              // Can become null during the delete case.
              if (diagnosisEntity == null) {
                return;
              }

              binding.deleteDiagnosis.setOnClickListener(v -> deleteAction(diagnosisEntity));
              if (shareDiagnosisViewModel.isDeleteOpen()) {
                deleteAction(diagnosisEntity);
              }

              String onsetDate = "";
              if (diagnosisEntity.getOnsetDate() != null) {
                onsetDate = requireContext()
                    .getString(R.string.share_review_onset_date,
                        dateTimeFormatter.withLocale(getResources().getConfiguration().locale)
                            .format(diagnosisEntity.getOnsetDate()));
              }
              boolean skipTravelStatusStep = shareDiagnosisViewModel.isTravelStatusStepSkippable();
              DiagnosisEntityHelper.populateViewBinding(
                  binding, diagnosisEntity, onsetDate, skipTravelStatusStep);
            });

    shareDiagnosisViewModel
        .getInFlightLiveData()
        .observe(
            getViewLifecycleOwner(),
            hasInFlightResolution -> {
              if (hasInFlightResolution) {
                binding.shareShareButton.setEnabled(false);
                binding.shareShareButton.setText("");
                binding.shareProgressBar.setVisibility(View.VISIBLE);
              } else {
                binding.shareShareButton.setEnabled(true);
                binding.shareShareButton.setText(R.string.btn_share);
                binding.shareProgressBar.setVisibility(View.INVISIBLE);
              }
            });

    shareDiagnosisViewModel
        .getSharedLiveEvent()
        .observe(
            getViewLifecycleOwner(),
            shared -> shareDiagnosisViewModel.nextStepIrreversible(
                shared ? Step.SHARED : Step.NOT_SHARED));

    shareDiagnosisViewModel.getSnackbarSingleLiveEvent()
        .observe(getViewLifecycleOwner(),
            message -> SnackbarUtil.maybeShowRegularSnackbar(getView(), message));

    shareDiagnosisViewModel.getPreviousStepLiveData(Step.REVIEW).observe(
        getViewLifecycleOwner(),
        step -> binding.sharePreviousButton.setOnClickListener(
            v -> shareDiagnosisViewModel.previousStep(step)));

    shareDiagnosisViewModel
        .getDeletedSingleLiveEvent()
        .observe(
            getViewLifecycleOwner(),
            unused -> {
              Toast.makeText(getContext(), R.string.delete_test_result_confirmed,
                  Toast.LENGTH_LONG).show();
              closeShareDiagnosisFlow();
            });
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    binding = null;
  }

  private void shareAction() {
    Log.d(TAG, "Submitting diagnosis keys...");
    shareDiagnosisViewModel.uploadKeys();
  }

  private void deleteAction(DiagnosisEntity diagnosis) {
    shareDiagnosisViewModel.setDeleteOpen(true);
    new MaterialAlertDialogBuilder(requireContext(), R.style.ExposureNotificationAlertDialogTheme)
        .setTitle(R.string.delete_test_result_title)
        .setMessage(R.string.delete_test_result_detail)
        .setCancelable(true)
        .setPositiveButton(
            R.string.btn_delete,
            (d, w) -> {
              shareDiagnosisViewModel.setDeleteOpen(false);
              shareDiagnosisViewModel.deleteEntity(diagnosis);
            })
        .setNegativeButton(R.string.btn_cancel,
            (d, w) -> shareDiagnosisViewModel.setDeleteOpen(false))
        .setOnDismissListener(d -> shareDiagnosisViewModel.setDeleteOpen(false))
        .setOnCancelListener(d -> shareDiagnosisViewModel.setDeleteOpen(false))
        .show();
  }
}
