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
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ViewSwitcher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.AbstractTextWatcher;
import com.google.android.apps.exposurenotification.common.KeyboardHelper;
import com.google.android.apps.exposurenotification.common.SnackbarUtil;
import com.google.android.apps.exposurenotification.databinding.FragmentShareDiagnosisCodeBinding;
import com.google.android.apps.exposurenotification.notify.ShareDiagnosisViewModel.EnterCodeStepReturnValue;
import com.google.android.apps.exposurenotification.notify.ShareDiagnosisViewModel.Step;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity.Shared;
import com.google.android.apps.exposurenotification.utils.UrlUtils;
import dagger.hilt.android.AndroidEntryPoint;

/**
 * The first step of the diagnosis flow, in which the user enters a verification code.
 */
@AndroidEntryPoint
public class ShareDiagnosisCodeFragment extends ShareDiagnosisBaseFragment {

  private static final String TAG = "ShareDiagnosisCodeFrag";
  private static final int VIEW_NOT_SET = -1;

  private final TextWatcher verificationCodeTextWatcher = new AbstractTextWatcher() {
    @Override
    public void afterTextChanged(Editable s) {
      binding.shareVerifyButton.setEnabled(
          !TextUtils.isEmpty(binding.shareTestIdentifier.getText()));

      // Catch user re-entering the code that has been already verified.
      String code = binding.shareTestIdentifier.getText() == null
          ? "" : binding.shareTestIdentifier.getText().toString();
      binding.shareAdvanceSwitcher.setDisplayedChild(code.equals(verifiedCode) ? 1 : 0);
      binding.shareTestVerified.setVisibility(code.equals(verifiedCode) ? View.VISIBLE : View.GONE);
      if (code.equals(verifiedCode)) {
        binding.shareTestError.setVisibility(View.GONE);
        shareDiagnosisViewModel.invalidateVerificationError();
      }
    }
  };

  private FragmentShareDiagnosisCodeBinding binding;

  @Nullable
  private String verifiedCode = null;

  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
    binding = FragmentShareDiagnosisCodeBinding.inflate(inflater, parent, false);
    return binding.getRoot();
  }

  @Override
  public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    requireActivity().setTitle(R.string.verify_test_result_title);

    EditText verificationCode = binding.shareTestIdentifier;
    LinearLayout verified = binding.shareTestVerified;
    LinearLayout error = binding.shareTestError;
    ViewSwitcher shareAdvanceSwitcher = binding.shareAdvanceSwitcher;

    if (binding.shareNextButton.isAccessibilityFocused()) {
      // Let accessibility service announce when button text change.
      binding.shareNextButton.sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
    }

    binding.shareVerifyButton.setEnabled(!TextUtils.isEmpty(verificationCode.getText()));

    binding.shareVerifyButton
        .setOnClickListener(v -> {
          KeyboardHelper.maybeHideKeyboard(requireContext(), view);
          shareDiagnosisViewModel.submitCode(verificationCode.getText().toString(), false);
        });

    shareDiagnosisViewModel.getInFlightLiveData()
        .observe(getViewLifecycleOwner(),
            inFlight -> binding.shareVerifySwitcher.setDisplayedChild(inFlight ? 1 : 0));

    shareDiagnosisViewModel.getRevealCodeStepEvent()
        .observe(getViewLifecycleOwner(), revealCodeStep -> {
          if (revealCodeStep) {
            binding.verifyMask.setVisibility(View.GONE);
            binding.verifyProgressIndicator.hide();
          }
        });

    shareDiagnosisViewModel.getCurrentDiagnosisLiveData().observe(getViewLifecycleOwner(),
        diagnosisEntity -> {
          if (!shareDiagnosisViewModel.isUiToBeRestoredFromSavedState()) {
            // We didn't populate UI with data from the saved instance state, so populate UI now.
            if (DiagnosisEntityHelper.hasVerified(diagnosisEntity)
                && !Shared.SHARED.equals(diagnosisEntity.getSharedStatus())) {
              // Show that the entity is verified and allow users either to continue to the next
              // step or to discard a current flow (only if the flow has been started from the EN
              // settings).
              this.verifiedCode = diagnosisEntity.getVerificationCode();
              error.setVisibility(View.GONE);
              verified.setVisibility(View.VISIBLE);
              verified.announceForAccessibility(getString(R.string.share_test_identifier_verified));
              verificationCode.setEnabled(shareDiagnosisViewModel.isResumingAndNotConfirmed());
              verificationCode.setText(diagnosisEntity.getVerificationCode());
              shareAdvanceSwitcher.setDisplayedChild(1);
            } else {
              // Not yet verified, allow user to enter a code and verify.
              verified.setVisibility(View.GONE);
              shareAdvanceSwitcher.setDisplayedChild(0);
            }
          } else {
            shareDiagnosisViewModel.markUiToBeRestoredFromSavedState(false);
          }

          binding.home.setOnClickListener(v -> {
            KeyboardHelper.maybeHideKeyboard(requireContext(), view);
            // Only show the dialog if has been verified.
            if (DiagnosisEntityHelper.hasVerified(diagnosisEntity)) {
              showCloseWarningAlertDialog();
            } else {
              closeShareDiagnosisFlow();
            }
          });
        });

    binding.learnMoreButton.setText(getString(
        R.string.share_test_identifier_link, getString(R.string.health_authority_website_url)));

    binding.learnMoreButton.setOnClickListener(
        v -> UrlUtils
            .openUrl(view, getString(R.string.share_verification_code_learn_more_url)));

    shareDiagnosisViewModel.getSnackbarSingleLiveEvent()
        .observe(getViewLifecycleOwner(), message ->
            SnackbarUtil.maybeShowRegularSnackbar(getView(), message));

    shareDiagnosisViewModel.getVerificationErrorLiveData()
        .observe(getViewLifecycleOwner(), message -> {
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

    Bundle args = getParentFragment().getArguments();
    @Nullable String codeStepFromDeepLink = args == null
        ? null : args.getString(EXTRA_CODE_FROM_DEEP_LINK, null);
    EnterCodeStepReturnValue enterCodeStepReturnValue = shareDiagnosisViewModel
        .enterCodeStep(codeStepFromDeepLink);
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

    shareDiagnosisViewModel.getNextStepLiveData(Step.CODE).observe(getViewLifecycleOwner(),
        step -> binding.shareNextButton.setOnClickListener(v -> {
          KeyboardHelper.maybeHideKeyboard(requireContext(), view);
          shareDiagnosisViewModel.setResumingAndNotConfirmed(false);
          shareDiagnosisViewModel.nextStep(step);
        }));

    shareDiagnosisViewModel.getPreviousStepLiveData(Step.CODE).observe(getViewLifecycleOwner(),
        step -> binding.sharePreviousButton.setOnClickListener(v -> {
          KeyboardHelper.maybeHideKeyboard(requireContext(), view);
          saveInstanceState();
          shareDiagnosisViewModel.previousStep(step);
        }));

    // Populate UI data from the SavedStateHandle if any.
    shareDiagnosisViewModel.isCodeInvalidForCodeStepLiveData().observe(getViewLifecycleOwner(),
        isCodeInvalid -> error.setVisibility(isCodeInvalid ? View.VISIBLE : View.GONE));

    shareDiagnosisViewModel.isCodeVerifiedForCodeStepLiveData().observe(getViewLifecycleOwner(),
        isCodeVerified -> verified.setVisibility(isCodeVerified ? View.VISIBLE : View.GONE));

    shareDiagnosisViewModel.isCodeInputEnabledForCodeStepLiveData()
        .observe(getViewLifecycleOwner(), verificationCode::setEnabled);

    shareDiagnosisViewModel.getCodeInputForCodeStepLiveData().observe(getViewLifecycleOwner(),
        codeInput -> {
          if (codeInput != null) {
            verificationCode.setText(codeInput);
            binding.shareVerifyButton.setEnabled(!TextUtils.isEmpty(codeInput));
          }
        });

    shareDiagnosisViewModel.getSwitcherChildForCodeStepLiveData().observe(getViewLifecycleOwner(),
        displayedChild -> {
          if (displayedChild > VIEW_NOT_SET) {
            shareAdvanceSwitcher.setDisplayedChild(displayedChild);
          }
        });

    shareDiagnosisViewModel.getVerifiedCodeForCodeStepLiveData().observe(getViewLifecycleOwner(),
        verifiedCode -> {
          if (verifiedCode != null) {
            this.verifiedCode = verifiedCode;
          }
        });
  }

  @Override
  public void onResume() {
    super.onResume();
    // Listen to verification code input changes.
    binding.shareTestIdentifier.addTextChangedListener(verificationCodeTextWatcher);
  }

  @Override
  public void onPause() {
    super.onPause();
    binding.shareTestIdentifier.removeTextChangedListener(verificationCodeTextWatcher);
  }

  @Override
  public void onSaveInstanceState(@NonNull Bundle outState) {
    super.onSaveInstanceState(outState);
    saveInstanceState();
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    binding = null;
  }

  /**
   * Save all the necessary UI bits needed to be persisted for the {@link Step#CODE} step upon
   * configuration changes and when pressing back in the sharing flow.
   */
  private void saveInstanceState() {
    String codeInput = binding.shareTestIdentifier.getText() == null
        ? "" : binding.shareTestIdentifier.getText().toString();
    shareDiagnosisViewModel.setCodeIsInvalidForCodeStep(
        binding.shareTestError.getVisibility() == View.VISIBLE);
    shareDiagnosisViewModel.setCodeIsVerifiedForCodeStep(
        binding.shareTestVerified.getVisibility() == View.VISIBLE);
    shareDiagnosisViewModel.setSwitcherChildForCodeStep(
        binding.shareAdvanceSwitcher.getDisplayedChild());
    shareDiagnosisViewModel.setCodeInputEnabledForCodeStep(binding.shareTestIdentifier.isEnabled());
    shareDiagnosisViewModel.setCodeInputForCodeStep(codeInput);
    shareDiagnosisViewModel.setVerifiedCodeForCodeStep(verifiedCode);
    shareDiagnosisViewModel.markUiToBeRestoredFromSavedState(true);
  }
}
