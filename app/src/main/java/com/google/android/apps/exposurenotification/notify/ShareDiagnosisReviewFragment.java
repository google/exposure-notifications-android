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
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.apps.exposurenotification.R;
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

  private ShareDiagnosisViewModel shareDiagnosisViewModel;

  @Inject
  Connectivity connectivity;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_share_diagnosis_review, parent, false);
  }

  @Override
  public void onViewCreated(View view, Bundle savedInstanceState) {
    getActivity().setTitle(R.string.share_review_title);

    shareDiagnosisViewModel =
        new ViewModelProvider(getActivity()).get(ShareDiagnosisViewModel.class);

    TextView covidStatus = view.findViewById(R.id.share_review_status);
    TextView travelStatusSubtitle = view.findViewById(R.id.share_review_travel_subtitle);
    TextView travelStatus = view.findViewById(R.id.share_review_travel);
    TextView date = view.findViewById(R.id.share_review_date);
    Button shareButton = view.findViewById(R.id.share_share_button);
    Button previousButton = view.findViewById(R.id.share_previous_button);
    ProgressBar progressBar = view.findViewById(R.id.share_progress_bar);
    View closeButton = view.findViewById(android.R.id.home);
    View deleteButton = view.findViewById(R.id.share_review_delete);

    shareDiagnosisViewModel
        .getCurrentDiagnosisLiveData()
        .observe(
            getViewLifecycleOwner(),
            diagnosisEntity -> {
              // Can become null during the delete case.
              if (diagnosisEntity == null) {
                return;
              }

              deleteButton.setOnClickListener(v -> deleteAction(diagnosisEntity));
              if (shareDiagnosisViewModel.isDeleteOpen()) {
                deleteAction(diagnosisEntity);
              }

              previousButton.setOnClickListener((v) -> {
                shareDiagnosisViewModel.previousStep(
                    ShareDiagnosisFlowHelper.getPreviousStep(
                        Step.REVIEW, diagnosisEntity, getContext()));
              });

              if (diagnosisEntity.getTestResult() != null) {
                switch (diagnosisEntity.getTestResult()) {
                  case LIKELY:
                    covidStatus.setText(R.string.share_review_status_likely);
                    break;
                  case NEGATIVE:
                    covidStatus.setText(R.string.share_review_status_negative);
                    break;
                  case CONFIRMED:
                  default:
                    covidStatus.setText(R.string.share_review_status_confirmed);
                    break;
                }
              } else {
                // We "shouldn't" get here, but in case, default to the most likely value rather
                // than fail.
                covidStatus.setText(R.string.share_review_status_confirmed);
              }

              if (!ShareDiagnosisFlowHelper.isTravelStatusStepSkippable(getContext())) {
                travelStatusSubtitle.setVisibility(View.VISIBLE);
                travelStatus.setVisibility(View.VISIBLE);

                if (diagnosisEntity.getTravelStatus() != null) {
                  switch (diagnosisEntity.getTravelStatus()) {
                    case TRAVELED:
                      travelStatus.setText(R.string.share_review_travel_confirmed);
                      break;
                    case NOT_TRAVELED:
                      travelStatus.setText(R.string.share_review_travel_no_travel);
                      break;
                    case NO_ANSWER:
                    case NOT_ATTEMPTED:
                    default:
                      travelStatus.setText(R.string.share_review_travel_no_answer);
                  }
                } else {
                  travelStatus.setText(R.string.share_review_travel_no_answer);
                }
              }

              // HasSymptoms cannot be null.
              // TODO make the other enums like this.
              switch (diagnosisEntity.getHasSymptoms()) {
                case YES:
                  date.setText(requireContext()
                      .getString(R.string.share_review_onset_date,
                          dateTimeFormatter.withLocale(getResources().getConfiguration().locale)
                              .format(diagnosisEntity.getOnsetDate())));
                  break;
                case NO:
                  date.setText(R.string.share_review_onset_no_symptoms);
                  break;
                case WITHHELD:
                case UNSET:
                default:
                  date.setText(R.string.share_review_onset_no_answer);
                  break;
              }
            });

    shareButton.setOnClickListener(v -> {
      if (connectivity.hasInternet()) {
        shareAction();
      } else {
        maybeShowSnackbar(requireContext().getString(R.string.share_error_no_internet));
      }
    });
    shareDiagnosisViewModel
        .getInFlightLiveData()
        .observe(
            getViewLifecycleOwner(),
            hasInFlightResolution -> {
              if (hasInFlightResolution) {
                shareButton.setEnabled(false);
                shareButton.setText("");
                progressBar.setVisibility(View.VISIBLE);
              } else {
                shareButton.setEnabled(true);
                shareButton.setText(R.string.btn_share_positive);
                progressBar.setVisibility(View.INVISIBLE);
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

    shareDiagnosisViewModel.getSnackbarSingleLiveEvent()
        .observe(getViewLifecycleOwner(), this::maybeShowSnackbar);

    closeButton.setContentDescription(getString(R.string.navigate_up));
    closeButton.setOnClickListener((v) -> closeAction());
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
            (d, w) -> shareDiagnosisViewModel.deleteEntity(diagnosis))
        .setNegativeButton(R.string.btn_cancel, (d, w) -> shareDiagnosisViewModel.setDeleteOpen(false))
        .setOnCancelListener((d) -> shareDiagnosisViewModel.setDeleteOpen(false))
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
