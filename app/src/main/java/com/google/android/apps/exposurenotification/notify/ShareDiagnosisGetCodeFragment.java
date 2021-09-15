/*
 * Copyright 2021 Google LLC
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
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import androidx.annotation.NonNull;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.AbstractTextWatcher;
import com.google.android.apps.exposurenotification.common.KeyboardHelper;
import com.google.android.apps.exposurenotification.common.SnackbarUtil;
import com.google.android.apps.exposurenotification.common.StringUtils;
import com.google.android.apps.exposurenotification.databinding.FragmentShareDiagnosisGetCodeBinding;
import com.google.android.apps.exposurenotification.notify.ShareDiagnosisViewModel.Step;
import com.google.android.apps.exposurenotification.utils.UrlUtils;
import com.google.android.material.datepicker.MaterialDatePicker;
import org.threeten.bp.Instant;
import org.threeten.bp.LocalDate;
import org.threeten.bp.ZoneOffset;

/**
 * First step in a "Self-report" flow for users to report a COVID-19 test result on their own.
 *
 * <p> In this step the user specifies their phone number to get a verification code from the Health
 * Authority to proceed with self-reporting.
 */
public class ShareDiagnosisGetCodeFragment extends ShareDiagnosisBaseFragment {

  private static final String DATE_PICKER_TAG = "ShareDiagnosisGetCodeFragment.DATE_PICKER_TAG";
  private static final int VIEWSWITCHER_SEND_CODE_BUTTON = 0;
  private static final int VIEWSWITCHER_PROGRESS_BAR = 1;

  private final TextWatcher phoneTextWatcher = new AbstractTextWatcher() {
    @Override
    public void afterTextChanged(Editable s) {
      binding.phoneNumberError.setVisibility(View.GONE);
      setUpSendCodeButton(binding.testedForCovidCheckbox.isChecked());
    }
  };

  private final TextWatcher testDateTextWatcher = new AbstractTextWatcher() {
    @Override
    public void afterTextChanged(Editable s) {
      setUpSendCodeButton(binding.testedForCovidCheckbox.isChecked());
    }
  };

  FragmentShareDiagnosisGetCodeBinding binding;

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent,
      Bundle savedInstanceState) {
    binding = FragmentShareDiagnosisGetCodeBinding.inflate(inflater, parent, false);
    return binding.getRoot();
  }

  @Override
  public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    requireActivity().setTitle(R.string.get_verification_code_title);
    setupShadowAtBottom(binding.shareDiagnosisScrollView, binding.buttonContainer);

    setUpHAProvidedValuesIfAny();

    binding.phoneNumber.setOnEditorActionListener((v, actionId, event) -> {
      if (actionId == EditorInfo.IME_ACTION_DONE) {
        binding.phoneNumber.clearFocus();
        KeyboardHelper.maybeHideKeyboard(requireContext(), binding.phoneNumber);
        return true;
      }
      return false;
    });

    binding.testDate.setOnClickListener(v -> maybeShowMaterialDatePicker());

    binding.testedForCovidCheckbox.setOnCheckedChangeListener((v, isChecked) -> {
      setUpSendCodeButton(isChecked);
    });

    binding.btnSendCode.setOnClickListener(v -> {
      saveInstanceState();
      KeyboardHelper.maybeHideKeyboard(requireContext(), view);
      String phoneNumber = binding.phoneNumber.getText().toString();
      LocalDate testDate = LocalDate
          .parse(binding.testDate.getText().toString(), getDateTimeFormatter());
      shareDiagnosisViewModel.requestCode(phoneNumber, testDate);
    });

    binding.learnMoreButton.setOnClickListener(
        v -> UrlUtils.openUrl(view, getString(R.string.en_reporting_info_link)));

    shareDiagnosisViewModel.getCurrentDiagnosisLiveData().observe(
        getViewLifecycleOwner(), diagnosisEntity -> binding.home.setOnClickListener(
            v -> maybeCloseShareDiagnosisFlow(DiagnosisEntityHelper.hasVerified(diagnosisEntity))));

    shareDiagnosisViewModel.getPreviousStepLiveData(Step.GET_CODE).observe(getViewLifecycleOwner(),
        step -> binding.btnPrevious.setOnClickListener(v -> {
          saveInstanceState();
          KeyboardHelper.maybeHideKeyboard(requireContext(), view);
          shareDiagnosisViewModel.previousStep(step);
        }));

    shareDiagnosisViewModel.getInFlightLiveData().observe(getViewLifecycleOwner(),
        inFlight -> binding.sendCodeSwitcher.setDisplayedChild(
            inFlight ? VIEWSWITCHER_PROGRESS_BAR : VIEWSWITCHER_SEND_CODE_BUTTON));

    shareDiagnosisViewModel.getPhoneNumberErrorMessageLiveData().observe(getViewLifecycleOwner(),
        errorMessage -> {
          if (!TextUtils.isEmpty(errorMessage)) {
            setUpPhoneErrorMessage(errorMessage);
          } else {
            binding.phoneNumberError.setVisibility(View.GONE);
          }
        });

    shareDiagnosisViewModel.getRequestCodeErrorLiveData().observe(getViewLifecycleOwner(),
        errorMsg -> SnackbarUtil.maybeShowRegularSnackbar(getView(), errorMsg));

    // If the date picker already exists upon view creation it means that view was just recreated
    // upon rotation. If so, its listeners need to be cleared and replaced as they hold references
    // to pre-rotation views.
    MaterialDatePicker<Long> datePicker = findMaterialDatePicker(DATE_PICKER_TAG);
    if (datePicker != null) {
      // Need to be cleared as most likely the view was just re-created after rotation
      datePicker.clearOnPositiveButtonClickListeners();
      addOnPositiveButtonClickListener(datePicker);
    }

    // Populate UI data from the SavedStateHandle if any.
    shareDiagnosisViewModel.getPhoneNumberForGetCodeStepLiveData()
        .observe(getViewLifecycleOwner(), phoneNumber -> {
          if (phoneNumber != null) {
            binding.phoneNumber.setText(phoneNumber);
          }
        });

    shareDiagnosisViewModel.getTestDateForGetCodeStepLiveData()
        .observe(getViewLifecycleOwner(), testDate -> {
          if (testDate != null) {
            binding.testDate.setText(testDate);
          }
        });

    shareDiagnosisViewModel.getStepXofYLiveData()
        .observe(getViewLifecycleOwner(), pair -> {
          String text = getString(R.string.share_diagnosis_progress_tracker,
              pair.first , pair.second);
          binding.stepXOfYTextView.setText(text);
        });
  }

  /**
   * Texts for some of the views displayed on this screen may be provided by the Health Authority
   * (HA). If that's the case, populate those views with HA-provided values.
   */
  private void setUpHAProvidedValuesIfAny() {
    if (!TextUtils.isEmpty(getString(R.string.self_report_phone_number))) {
      binding.phoneNumberHelp.setText(R.string.self_report_phone_number);
    }

    if (!TextUtils.isEmpty(getString(R.string.self_report_test_date))) {
      binding.testDateHelp.setText(R.string.self_report_test_date);
    }

    if (!TextUtils.isEmpty(getString(R.string.self_report_checkbox))) {
      binding.testedForCovidCheckbox.setText(R.string.self_report_checkbox);
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    // Listen to the phone number and test date input changes.
    binding.phoneNumber.addTextChangedListener(phoneTextWatcher);
    binding.testDate.addTextChangedListener(testDateTextWatcher);
  }

  @Override
  public void onPause() {
    super.onPause();
    // Listen to the phone number and test date input changes.
    binding.phoneNumber.removeTextChangedListener(phoneTextWatcher);
    binding.testDate.removeTextChangedListener(testDateTextWatcher);
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    binding = null;
  }

  @Override
  public boolean onBackPressed() {
    super.onBackPressed();
    saveInstanceState();
    return true;
  }

  /** Sets up error message to be displayed under "Phone number" input field. */
  private void setUpPhoneErrorMessage(String errorMessage) {
    String learnMoreText = getString(R.string.learn_more);
    if (errorMessage.contains(learnMoreText)) {
      binding.phoneNumberError.setText(StringUtils.generateTextWithHyperlink(
          UrlUtils.createURLSpan(getString(R.string.en_reporting_info_link)),
          errorMessage, learnMoreText));
      binding.phoneNumberError.setMovementMethod(LinkMovementMethod.getInstance());
    } else {
      binding.phoneNumberError.setText(errorMessage);
    }
    binding.phoneNumberError.setVisibility(View.VISIBLE);
    binding.btnSendCode.setEnabled(false);
  }

  /**
   * Enables or disables the "Send code" button.
   *
   * <p>This button is enabled only if Phone Number and Test Date inputs are not empty and user
   * has checked the "tested for COVID-19" checkbox.
   * */
  private void setUpSendCodeButton(boolean isChecked) {
    binding.btnSendCode.setEnabled(isChecked && !TextUtils.isEmpty(binding.testDate.getText())
        && !TextUtils.isEmpty(binding.phoneNumber.getText()));
  }

  /**
   * Shows material date picker only if there isn't one already shown.
   */
  private void maybeShowMaterialDatePicker() {
    if (findMaterialDatePicker(DATE_PICKER_TAG) != null) {
      return;
    }
    MaterialDatePicker<Long> dialog = createMaterialDatePicker(clock.now());
    addOnPositiveButtonClickListener(dialog);
    dialog.show(getParentFragmentManager(), DATE_PICKER_TAG);
  }

  private void addOnPositiveButtonClickListener(MaterialDatePicker<Long> dialog) {
    dialog.addOnPositiveButtonClickListener(
        selection -> {
          String dateStr = getDateTimeFormatter()
              .format(Instant.ofEpochMilli(selection).atZone(ZoneOffset.UTC));
          binding.testDate.setText(dateStr);
          // Check if selected date is valid and if not so, show snackbar
          if (!isValidDate(dateStr, symptomOnsetOrTestDateValidator::isValid)) {
            maybeShowInvalidDateSnackbar(dateStr);
          }
        });
  }

  /**
   * Save all the necessary UI bits needed to be persisted for the {@link Step#GET_CODE} step when
   * going back or forward in the sharing flow.
   */
  private void saveInstanceState() {
    String phoneNumber = binding.phoneNumber.getText() == null
        ? "" : binding.phoneNumber.getText().toString();
    String testDate = binding.testDate.getText() == null
        ? "" : binding.testDate.getText().toString();
    shareDiagnosisViewModel.setPhoneNumberForGetCodeStep(phoneNumber);
    shareDiagnosisViewModel.setTestDateForGetCodeStep(testDate);
  }

}

