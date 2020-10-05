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
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import dagger.hilt.android.AndroidEntryPoint;
import org.threeten.bp.format.DateTimeFormatter;
import org.threeten.bp.format.FormatStyle;

/**
 * A view that shows the details of a diagnosis previously entered. It may have been successfully
 * uploaded previously, or it may have failed or been canceled by the user.
 */
@AndroidEntryPoint
public class ShareDiagnosisViewFragment extends Fragment {

  private static final String TAG = "ShareDiagnosisViewFrag";

  private static final String STATE_DELETE_OPEN = "DebugFragment.STATE_DELETE_OPEN";

  private final DateTimeFormatter dateTimeFormatter =
      DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM);

  private ShareDiagnosisViewModel shareDiagnosisViewModel;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_share_diagnosis_view, parent, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
    shareDiagnosisViewModel =
        new ViewModelProvider(getActivity()).get(ShareDiagnosisViewModel.class);

    TextView covidStatus = view.findViewById(R.id.share_review_status);
    TextView travelStatus = view.findViewById(R.id.share_review_travel);
    TextView date = view.findViewById(R.id.share_review_date);
    Button deleteButton = view.findViewById(R.id.positive_diagnosis_delete_button);
    View closeButton = view.findViewById(android.R.id.home);

    getActivity().setTitle(R.string.status_shared_detail_title);

    shareDiagnosisViewModel
        .getCurrentDiagnosisLiveData()
        .observe(
            getViewLifecycleOwner(),
            diagnosis -> {
              if (diagnosis != null) {
                // TODO: this is all duplicated from ShareDiagnosisReviewFragment. Refactor.
                if (diagnosis.getTestResult() != null) {
                  switch (diagnosis.getTestResult()) {
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

                if (diagnosis.getTravelStatus() != null) {
                  switch (diagnosis.getTravelStatus()) {
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

                // HasSymptoms cannot be null.
                // TODO make the other enums like this.
                switch (diagnosis.getHasSymptoms()) {
                  case YES:
                    date.setText(
                        requireContext()
                            .getString(
                                R.string.share_review_onset_date,
                                dateTimeFormatter
                                    .withLocale(getResources().getConfiguration().locale)
                                    .format(diagnosis.getOnsetDate())));
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
                deleteButton.setOnClickListener(v -> deleteAction(diagnosis));

                if (shareDiagnosisViewModel.isDeleteOpen()) {
                  deleteAction(diagnosis);
                }
              }
            });

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

    closeButton.setContentDescription(getString(R.string.navigate_up));
    closeButton.setOnClickListener((v) -> closeAction());
  }

  private void deleteAction(DiagnosisEntity diagnosis) {
    shareDiagnosisViewModel.setDeleteOpen(true);
    new MaterialAlertDialogBuilder(requireContext())
        .setTitle(R.string.delete_test_result_title)
        .setMessage(R.string.delete_test_result_detail)
        .setPositiveButton(
            R.string.btn_delete,
            (d, w) -> {
              shareDiagnosisViewModel.setDeleteOpen(false);
              shareDiagnosisViewModel.deleteEntity(diagnosis);
            })
        .setNegativeButton(R.string.btn_cancel,
            (d, w) -> shareDiagnosisViewModel.setDeleteOpen(false))
        .setOnDismissListener((d) -> shareDiagnosisViewModel.setDeleteOpen(false))
        .setOnCancelListener((d) -> shareDiagnosisViewModel.setDeleteOpen(false))
        .show();
  }

  private void closeAction() {
    requireActivity().finish();
  }
}
