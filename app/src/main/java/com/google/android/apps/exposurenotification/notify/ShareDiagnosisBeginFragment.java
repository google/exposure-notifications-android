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
import android.view.ViewTreeObserver;
import android.widget.RelativeLayout;
import androidx.annotation.NonNull;
import androidx.core.widget.NestedScrollView;
import androidx.core.widget.NestedScrollView.OnScrollChangeListener;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.PairLiveData;
import com.google.android.apps.exposurenotification.common.StringUtils;
import com.google.android.apps.exposurenotification.databinding.FragmentShareDiagnosisBeginBinding;
import com.google.android.apps.exposurenotification.notify.ShareDiagnosisViewModel.Step;
import com.google.common.base.Optional;
import dagger.hilt.android.AndroidEntryPoint;

/**
 * A preamble to the diagnosis flow, showing some info for the user.
 */
@AndroidEntryPoint
public class ShareDiagnosisBeginFragment extends ShareDiagnosisBaseFragment {

  private FragmentShareDiagnosisBeginBinding binding;

  private RelativeLayout buttonContainer;
  private NestedScrollView scroller;
  private Optional<Boolean> lastUpdateAtBottom = Optional.absent();

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent,
      Bundle savedInstanceState) {
    binding = FragmentShareDiagnosisBeginBinding.inflate(inflater, parent, false);
    return binding.getRoot();
  }

  @Override
  public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    scroller = binding.shareDiagnosisScrollView;
    buttonContainer = binding.buttonContainer;

    requireActivity().setTitle(R.string.share_begin_title);
    setupView();

    shareDiagnosisViewModel.getCurrentDiagnosisLiveData().observe(
        getViewLifecycleOwner(), diagnosisEntity -> binding.home.setOnClickListener(
            v -> maybeCloseShareDiagnosisFlow(DiagnosisEntityHelper.hasVerified(diagnosisEntity))));

    determineNextStep();
  }

  private void determineNextStep() {
    PairLiveData<Step, Boolean> nextStepAndIsCodeInvalidLiveData = PairLiveData.of(
        shareDiagnosisViewModel.getNextStepLiveData(Step.BEGIN),
        shareDiagnosisViewModel.isCodeInvalidForCodeStepLiveData());
    nextStepAndIsCodeInvalidLiveData.observe(
        this, (step, isCodeInvalid) -> {
          if (isCodeInvalid) {
            // Never skip the Code step if user has previously input an invalid code.
            binding.shareNextButton.setOnClickListener(
                v -> shareDiagnosisViewModel.nextStep(Step.CODE));
          } else {
            binding.shareNextButton.setOnClickListener(v -> shareDiagnosisViewModel.nextStep(step));
          }
        }
    );
  }

  private void setupView() {
    binding.shareTestResultTitleTextView.setText(
        getString(R.string.share_diagnosis_share_test_result_title,
            StringUtils.getApplicationName(requireContext())));

    setupUpdateAtBottom(scroller, buttonContainer);
  }

  /**
   * Set up UI components to update the UI depending on the scrolling.
   */
  void setupUpdateAtBottom(NestedScrollView scroller, RelativeLayout buttonContainer) {
    scroller.setOnScrollChangeListener(
        (OnScrollChangeListener) (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
          if (scroller.getChildAt(0).getBottom()
              <= (scroller.getHeight() + scroller.getScrollY())) {
            updateAtBottom(buttonContainer, true);
          } else {
            updateAtBottom( buttonContainer, false);
          }
        });
    ViewTreeObserver observer = scroller.getViewTreeObserver();
    observer.addOnGlobalLayoutListener(() -> {
      if (scroller.getMeasuredHeight() >= scroller.getChildAt(0).getHeight()) {
        // Not scrollable so set at bottom.
        updateAtBottom(buttonContainer, true);
      }
    });
  }

  /**
   * Update the UI depending on whether scrolling is at the bottom or not.
   */
  void updateAtBottom(RelativeLayout buttonContainer, boolean atBottom) {
    if (lastUpdateAtBottom.isPresent() && lastUpdateAtBottom.get() == atBottom) {
      // Don't update if already at set.
      return;
    }
    lastUpdateAtBottom = Optional.of(atBottom);
    if (atBottom) {
      buttonContainer.setElevation(0F);
    } else {
      buttonContainer
          .setElevation(getResources().getDimension(R.dimen.bottom_button_container_elevation));
    }
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    binding = null;
  }

}
