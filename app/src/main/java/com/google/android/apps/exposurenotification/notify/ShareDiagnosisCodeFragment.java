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
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.EditText;
import android.widget.LinearLayout;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.AbstractTextWatcher;
import com.google.android.apps.exposurenotification.common.KeyboardHelper;
import com.google.android.apps.exposurenotification.databinding.FragmentShareDiagnosisCodeBinding;
import com.google.android.apps.exposurenotification.notify.ShareDiagnosisViewModel.EnterCodeStepReturnValue;
import com.google.android.apps.exposurenotification.notify.ShareDiagnosisViewModel.Step;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity.Shared;
import com.google.android.material.snackbar.Snackbar;
import dagger.hilt.android.AndroidEntryPoint;

/**
 * The first step of the diagnosis flow, in which the user enters a verification code.
 */
@AndroidEntryPoint
public class ShareDiagnosisCodeFragment extends Fragment {

  private static final String TAG = "ShareDiagnosisCodeFrag";

  private FragmentShareDiagnosisCodeBinding binding;
  private ShareDiagnosisViewModel viewModel;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
    binding = FragmentShareDiagnosisCodeBinding.inflate(inflater, parent, false);
    return binding.getRoot();
  }

  @Override
  public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
    getActivity().setTitle(R.string.verify_test_result_title);

    viewModel =
        new ViewModelProvider(getActivity()).get(ShareDiagnosisViewModel.class);

    EditText verificationCode = binding.shareTestIdentifier;
    LinearLayout verified = binding.shareTestVerified;
    LinearLayout error = binding.shareTestError;

    binding.shareVerifyButton.setEnabled(!TextUtils.isEmpty(verificationCode.getText()));
    verificationCode.addTextChangedListener(new AbstractTextWatcher() {
      @Override
      public void afterTextChanged(Editable s) {
        binding.shareVerifyButton.setEnabled(!TextUtils.isEmpty(verificationCode.getText()));
      }
    });

    viewModel.getInFlightLiveData()
        .observe(getViewLifecycleOwner(),
            inFlight -> binding.shareVerifySwitcher.setDisplayedChild(inFlight ? 1 : 0));

    viewModel.getRevealCodeStepEvent().observe(getViewLifecycleOwner(), revealCodeStep -> {
      if (revealCodeStep) {
        binding.verifyMask.setVisibility(View.GONE);
        binding.verifyProgressIndicator.hide();
      }
    });
    viewModel.getCurrentDiagnosisLiveData().observe(getViewLifecycleOwner(), diagnosisEntity -> {
      binding.shareVerifyButton
          .setOnClickListener(v -> {
            KeyboardHelper.maybeHideKeyboard(requireContext(), view);
            viewModel.submitCode(verificationCode.getText().toString(), false);
          });

      if (DiagnosisEntityHelper.hasVerified(diagnosisEntity)
          && !Shared.SHARED.equals(diagnosisEntity.getSharedStatus())) {
        // Entity is verified, show verified and allow continue.
        verified.setVisibility(View.VISIBLE);
        verified.announceForAccessibility(getString(R.string.share_test_identifier_verified));
        error.setVisibility(View.GONE);
        verificationCode.setEnabled(false);
        verificationCode.setText(diagnosisEntity.getVerificationCode());

        binding.shareAdvanceSwitcher.setDisplayedChild(1);
      } else {
        // Not yet verified, allow user to enter a code and verify.
        verified.setVisibility(View.GONE);
        binding.shareAdvanceSwitcher.setDisplayedChild(0);
      }

      if (binding.shareNextButton.isAccessibilityFocused()) {
        // Let accessibility service announce when button text change.
        binding.shareNextButton.sendAccessibilityEvent(
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
      }

      binding.home.setOnClickListener(v -> {
        KeyboardHelper.maybeHideKeyboard(requireContext(), view);
        // Only show the dialog if has been verified.
        if (DiagnosisEntityHelper.hasVerified(diagnosisEntity)) {
          ShareDiagnosisActivity.showCloseWarningAlertDialog(requireActivity(), viewModel);
        } else {
          requireActivity().finish();
        }
      });
    });
    binding.home.setContentDescription(getString(R.string.btn_cancel));

    binding.learnMoreButton.setOnClickListener(
        v -> startActivity(new Intent(Intent.ACTION_VIEW,
            Uri.parse(getString(R.string.share_verification_code_learn_more_url)))));

    viewModel.getSnackbarSingleLiveEvent()
        .observe(getViewLifecycleOwner(), this::maybeShowSnackbar);

    viewModel.getVerificationErrorLiveData().observe(getViewLifecycleOwner(), message -> {
      if (TextUtils.isEmpty(message)) {
        error.setVisibility(View.GONE);
      } else {
        // Verification code failed to be verified. Show an error and don't allow to continue
        verified.setVisibility(View.GONE);
        error.setVisibility(View.VISIBLE);
        binding.shareTestErrorText.setText(message);
        verificationCode.setEnabled(true);
        binding.shareAdvanceSwitcher.setDisplayedChild(0);
      }
    });

    EnterCodeStepReturnValue enterCodeStepReturnValue =
        viewModel.enterCodeStep(getArguments().getParcelable(ACTIVITY_START_INTENT));
    if (enterCodeStepReturnValue.verificationCodeToPrefill().isPresent()) {
      verificationCode.setText(enterCodeStepReturnValue.verificationCodeToPrefill().get());
    }
    if (enterCodeStepReturnValue.revealPage()) {
      binding.verifyProgressIndicator.hide();
      binding.verifyMask.setVisibility(View.GONE);
    } else {
      binding.verifyProgressIndicator.show();
      binding.verifyMask.setVisibility(View.VISIBLE);
    }

    viewModel.getNextStepLiveData(Step.CODE).observe(getViewLifecycleOwner(),
        step -> binding.shareNextButton.setOnClickListener(v -> {
          KeyboardHelper.maybeHideKeyboard(requireContext(), view);
          viewModel.nextStep(step);
        }));

    viewModel.getPreviousStepLiveData(Step.CODE).observe(getViewLifecycleOwner(),
        step -> binding.sharePreviousButton.setOnClickListener(v -> {
          KeyboardHelper.maybeHideKeyboard(requireContext(), view);
          viewModel.previousStep(step);
        }));
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    binding = null;
  }

  private void maybeShowSnackbar(String message) {
    View rootView = getView();
    if (rootView != null) {
      Snackbar.make(rootView, message, Snackbar.LENGTH_LONG).show();
    }
  }
}
