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

import static android.app.Activity.RESULT_OK;

import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.databinding.FragmentShareDiagnosisReviewBinding;
import com.google.android.apps.exposurenotification.network.Connectivity;
import com.google.android.apps.exposurenotification.notify.ShareDiagnosisViewModel.Step;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity.Shared;
import com.google.android.apps.exposurenotification.utils.RequestCodes;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import dagger.hilt.android.AndroidEntryPoint;
import javax.inject.Inject;
import org.threeten.bp.format.DateTimeFormatter;
import org.threeten.bp.format.FormatStyle;

/**
 * Page of the diagnosis flow that allows the user to see their diagnosis info and choose whether or
 * not to share their keys with the upload server.
 */
@AndroidEntryPoint
public class ShareDiagnosisReviewFragment extends Fragment {

  private static final String TAG = "ShareExposureReviewFrag";

  private static final DateTimeFormatter dateTimeFormatter =
      DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM);

  private FragmentShareDiagnosisReviewBinding binding;
  private ShareDiagnosisViewModel shareDiagnosisViewModel;

  @Inject
  Connectivity connectivity;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
    binding = FragmentShareDiagnosisReviewBinding.inflate(inflater, parent, false);
    return binding.getRoot();
  }

  @Override
  public void onViewCreated(View view, Bundle savedInstanceState) {
    getActivity().setTitle(R.string.share_review_title);

    shareDiagnosisViewModel =
        new ViewModelProvider(getActivity()).get(ShareDiagnosisViewModel.class);

    binding.home.setContentDescription(getString(R.string.navigate_up));
    binding.home.setOnClickListener(v -> closeAction());

    binding.shareShareButton.setOnClickListener(v -> {
      if (connectivity.hasInternet()) {
        shareAction();
      } else {
        maybeShowSnackbar(requireContext().getString(R.string.share_error_no_internet));
      }
    });

    shareDiagnosisViewModel
        .getCurrentDiagnosisLiveData()
        .observe(
            getViewLifecycleOwner(),
            diagnosisEntity -> {
              // Can become null during the delete case.
              if (diagnosisEntity == null) {
                return;
              }

              binding.shareReviewDelete.setOnClickListener(v -> deleteAction(diagnosisEntity));
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
                binding.shareShareButton.setText(R.string.btn_share_positive);
                binding.shareProgressBar.setVisibility(View.INVISIBLE);
              }
            });

    shareDiagnosisViewModel
        .getResolutionRequiredLiveEvent()
        .observe(
            getViewLifecycleOwner(),
            apiException -> {
              try {
                apiException.getStatus().startResolutionForResult(
                    requireActivity(), RequestCodes.REQUEST_CODE_GET_TEMP_EXPOSURE_KEY_HISTORY);
              } catch (SendIntentException e) {
                Log.w(TAG, "Error calling startResolutionForResult", apiException);
                maybeShowSnackbar(getString(R.string.generic_error_message));
              }
            });

    shareDiagnosisViewModel
        .getSharedLiveEvent()
        .observe(
            getViewLifecycleOwner(),
            shared -> shareDiagnosisViewModel
                .nextStepIrreversible(shared ? Step.SHARED : Step.NOT_SHARED));

    shareDiagnosisViewModel.getSnackbarSingleLiveEvent()
        .observe(getViewLifecycleOwner(), this::maybeShowSnackbar);

    shareDiagnosisViewModel.getPreviousStepLiveData(Step.REVIEW).observe(
        getViewLifecycleOwner(),
        step -> binding.sharePreviousButton.setOnClickListener(
            v -> shareDiagnosisViewModel.previousStep(step)));

    shareDiagnosisViewModel
        .getDeletedSingleLiveEvent()
        .observe(
            getViewLifecycleOwner(),
            unused -> {
              if (getActivity() != null) {
                Toast.makeText(getContext(), R.string.delete_test_result_confirmed,
                    Toast.LENGTH_LONG).show();
                getActivity().finish();
              }
            });
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    if (requestCode == RequestCodes.REQUEST_CODE_GET_TEMP_EXPOSURE_KEY_HISTORY) {
      if (resultCode == RESULT_OK) {
        // Okay to share, submit data.
        shareDiagnosisViewModel.uploadKeys();
      } else {
        // Not okay to share, just store for later.
        shareDiagnosisViewModel.setIsShared(Shared.NOT_ATTEMPTED);
      }
    }
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

  private void closeAction() {
    ShareDiagnosisActivity.showCloseWarningAlertDialog(requireActivity(), shareDiagnosisViewModel);
  }

  private void deleteAction(DiagnosisEntity diagnosis) {
    shareDiagnosisViewModel.setDeleteOpen(true);
    new MaterialAlertDialogBuilder(requireContext())
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

  /**
   * Shows a snackbar with a given message if the {@link View} is visible.
   */
  private void maybeShowSnackbar(String message) {
    View rootView = getView();
    if (rootView != null) {
      Snackbar.make(rootView, message, Snackbar.LENGTH_LONG).show();
    }
  }
}
