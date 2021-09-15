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
import android.widget.Toast;
import androidx.annotation.NonNull;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.databinding.FragmentShareDiagnosisViewBinding;
import dagger.hilt.android.AndroidEntryPoint;
import org.threeten.bp.format.DateTimeFormatter;
import org.threeten.bp.format.FormatStyle;

/**
 * A view that shows the details of a diagnosis previously entered. It may have been successfully
 * uploaded previously, or it may have failed or been canceled by the user.
 */
@AndroidEntryPoint
public class ShareDiagnosisViewFragment extends ShareDiagnosisBaseFragment {

  private final DateTimeFormatter dateTimeFormatter =
      DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM);

  private FragmentShareDiagnosisViewBinding binding;

  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
    binding = FragmentShareDiagnosisViewBinding.inflate(inflater, parent, false);
    return binding.getRoot();
  }

  @Override
  public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    requireActivity().setTitle(R.string.status_shared_detail_title);
    setupShadowAtBottom(binding.shareDiagnosisScrollView, binding.buttonContainer);

    binding.home.setOnClickListener(v -> closeShareDiagnosisFlowImmediately());

    shareDiagnosisViewModel
        .getCurrentDiagnosisLiveData()
        .observe(
            getViewLifecycleOwner(),
            diagnosisEntity -> {
              // Can become null during the delete case.
              if (diagnosisEntity == null) {
                return;
              }

              binding.diagnosisDeleteButton.setOnClickListener(
                  v -> showDeleteDiagnosisAlertDialog(diagnosisEntity));
              if (shareDiagnosisViewModel.isDeleteOpen()) {
                showDeleteDiagnosisAlertDialog(diagnosisEntity);
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
        .getDeletedSingleLiveEvent()
        .observe(
            getViewLifecycleOwner(),
            unused -> {
              Toast.makeText(getContext(), R.string.delete_test_result_confirmed,
                  Toast.LENGTH_LONG).show();
              closeShareDiagnosisFlowImmediately();
            });
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    binding = null;
  }

  @Override
  public boolean onBackPressed() {
    closeShareDiagnosisFlowImmediately();
    return true;
  }
}
