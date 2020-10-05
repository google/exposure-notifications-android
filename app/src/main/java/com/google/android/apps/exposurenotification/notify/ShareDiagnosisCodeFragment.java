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

import static com.google.android.apps.exposurenotification.notify.ShareDiagnosisActivity.ACTIVITY_START_INTENT;

import android.content.Intent;
import android.net.Uri;
import android.net.UrlQuerySanitizer;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.ViewSwitcher;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.AbstractTextWatcher;
import com.google.android.apps.exposurenotification.common.KeyboardHelper;
import com.google.android.apps.exposurenotification.network.Connectivity;
import com.google.android.apps.exposurenotification.notify.ShareDiagnosisViewModel.Step;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity.Shared;
import com.google.android.material.progressindicator.ProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import dagger.hilt.android.AndroidEntryPoint;
import javax.inject.Inject;

/**
 * The first step of the diagnosis flow, in which the user enters a verification code.
 */
@AndroidEntryPoint
public class ShareDiagnosisCodeFragment extends Fragment {

  private static final String TAG = "ShareDiagnosisCodeFrag";

  private ShareDiagnosisViewModel viewModel;
  private boolean skippingToNextStep = false;

  @Inject
  Connectivity connectivity;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_share_diagnosis_code, parent, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
    getActivity().setTitle(R.string.verify_test_result_title);

    viewModel =
        new ViewModelProvider(getActivity()).get(ShareDiagnosisViewModel.class);

    EditText verificationCode = view.findViewById(R.id.share_test_identifier);
    ViewSwitcher verifySwitcher = view.findViewById(R.id.share_verify_switcher);
    ViewSwitcher advanceSwitcher = view.findViewById(R.id.share_advance_switcher);
    Button nextButton = view.findViewById(R.id.share_next_button);
    Button verifyButton = view.findViewById(R.id.share_verify_button);
    Button previousButton = view.findViewById(R.id.share_previous_button);
    Button learnMoreButton = view.findViewById(R.id.learn_more_button);
    View closeButton = view.findViewById(android.R.id.home);
    View verified = view.findViewById(R.id.share_test_verified);
    View error = view.findViewById(R.id.share_test_error);
    TextView errorText = view.findViewById(R.id.share_test_error_text);
    View verifyMask = view.findViewById(R.id.verify_mask);
    ProgressIndicator verifyProgressIndicator = view.findViewById(R.id.verify_progress_indicator);

    verifyButton.setEnabled(!TextUtils.isEmpty(verificationCode.getText()));
    verificationCode.addTextChangedListener(new AbstractTextWatcher() {
      @Override
      public void afterTextChanged(Editable s) {
        verifyButton.setEnabled(!TextUtils.isEmpty(verificationCode.getText()));
      }
    });

    viewModel.getInFlightLiveData()
        .observe(getViewLifecycleOwner(),
            inFlight -> verifySwitcher.setDisplayedChild(inFlight ? 1 : 0));

    viewModel.getVerificationCodeSubmittedEvent()
        .observe(getViewLifecycleOwner(), diagnosisEntityOptional -> {
          if (diagnosisEntityOptional.isPresent() && diagnosisEntityOptional.get()
              .getIsCodeFromLink()) {
            skipToNextStep(diagnosisEntityOptional.get());
          } else {
            verifyMask.setVisibility(View.GONE);
            verifyProgressIndicator.hide();
          }
        });

    viewModel.getCurrentDiagnosisLiveData().observe(getViewLifecycleOwner(), diagnosisEntity -> {
      verifyButton
          .setOnClickListener(v -> {
            KeyboardHelper.maybeHideKeyboard(requireContext(), view);
            verify(verificationCode, false);
          });
      nextButton.setOnClickListener(
          v -> {
            KeyboardHelper.maybeHideKeyboard(requireContext(), view);
            viewModel.nextStep(ShareDiagnosisFlowHelper.getNextStep(Step.CODE, diagnosisEntity));
          });

      if (Shared.SHARED.equals(diagnosisEntity.getSharedStatus())) {
        // Already shared, show an error and don't allow continue.
        verified.setVisibility(View.GONE);
        error.setVisibility(View.VISIBLE);
        errorText.setText(R.string.code_error_already_submitted);

        verificationCode.setEnabled(true);
        verificationCode.setText(diagnosisEntity.getVerificationCode());

        advanceSwitcher.setDisplayedChild(0);
      } else if (DiagnosisEntityHelper.hasVerified(diagnosisEntity)) {
        if (diagnosisEntity.getIsCodeFromLink()) {
          skipToNextStep(diagnosisEntity);
        } else {
          // Entity is verified, show verified and allow continue.
          verified.setVisibility(View.VISIBLE);
          verified.announceForAccessibility(getString(R.string.share_test_identifier_verified));
          error.setVisibility(View.GONE);
          verificationCode.setEnabled(false);
          verificationCode.setText(diagnosisEntity.getVerificationCode());

          advanceSwitcher.setDisplayedChild(1);
        }
      } else {
        // Not yet verified, allow user to enter a code and verify.
        verified.setVisibility(View.GONE);
        error.setVisibility(View.GONE);

        advanceSwitcher.setDisplayedChild(0);

        // If the activity was started from an url containing the verification field, we set it and
        // trigger `verify` immediately. Only do this once per ViewModel lifecycle to avoid
        // interrupting user navigation between steps.
        Intent startIntent = getArguments().getParcelable(ACTIVITY_START_INTENT);
        if (startIntent != null
            && startIntent.getData() != null
            && !viewModel.isCodeFromLinkUsed()) {
          viewModel.setCodeFromUrlUsed();
          String codeFromLink = new UrlQuerySanitizer(
              startIntent.getData().toString())
              .getValue("c");
          if (codeFromLink != null) {
            verificationCode.setText(codeFromLink);
            if (verify(verificationCode, true)) {
              verifyProgressIndicator.show();
              verifyMask.setVisibility(View.VISIBLE);
            }
          }
        }
      }

      if (nextButton.isAccessibilityFocused()) {
        // Let accessibility service announce when button text change.
        nextButton.sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
      }

      closeButton.setOnClickListener(v -> {
        KeyboardHelper.maybeHideKeyboard(requireContext(), view);
        // Only show the dialog if has been verified.
        if (DiagnosisEntityHelper.hasVerified(diagnosisEntity)) {
          ShareDiagnosisActivity.showCloseWarningAlertDialog(requireActivity(), viewModel);
        } else {
          requireActivity().finish();
        }
      });

      previousButton.setOnClickListener((v) -> {
        KeyboardHelper.maybeHideKeyboard(requireContext(), view);
        viewModel
            .previousStep(ShareDiagnosisFlowHelper.getPreviousStep(Step.CODE, diagnosisEntity));
      });
    });
    closeButton.setContentDescription(getString(R.string.btn_cancel));

    learnMoreButton
        .setOnClickListener(
            v -> startActivity(new Intent(Intent.ACTION_VIEW,
                Uri.parse(getString(R.string.share_verification_code_learn_more_url)))));

    viewModel.getSnackbarSingleLiveEvent()
        .observe(getViewLifecycleOwner(), this::maybeShowSnackbar);
  }

  private boolean verify(EditText verificationCode, boolean isCodeFromLink) {
    if (connectivity.hasInternet()) {
      viewModel.submitCode(verificationCode.getText().toString(), isCodeFromLink);
      return true;
    } else {
      maybeShowSnackbar(requireContext().getString(R.string.share_error_no_internet));
      return false;
    }
  }

  private void skipToNextStep(DiagnosisEntity diagnosisEntity) {
    Log.d(TAG, "Verification code from link is/was accepted, skipping to next step");
    // Avoid double skipping.
    if (!skippingToNextStep) {
      skippingToNextStep = true;
      viewModel.removeCurrentStepFromBackStack();
      viewModel.nextStep(ShareDiagnosisFlowHelper.getNextStep(Step.CODE, diagnosisEntity));
    }
  }

  private void maybeShowSnackbar(String message) {
    View rootView = getView();
    if (rootView != null) {
      Snackbar.make(rootView, message, Snackbar.LENGTH_LONG).show();
    }
  }

}
