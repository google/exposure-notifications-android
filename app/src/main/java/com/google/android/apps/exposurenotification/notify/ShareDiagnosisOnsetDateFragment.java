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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.RadioGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.AbstractTextWatcher;
import com.google.android.apps.exposurenotification.common.KeyboardHelper;
import com.google.android.apps.exposurenotification.common.PairLiveData;
import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.android.apps.exposurenotification.databinding.FragmentShareDiagnosisOnsetDateBinding;
import com.google.android.apps.exposurenotification.notify.ShareDiagnosisViewModel.Step;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity.HasSymptoms;
import com.google.android.material.datepicker.MaterialDatePicker;
import dagger.hilt.android.AndroidEntryPoint;
import javax.inject.Inject;
import org.threeten.bp.Instant;
import org.threeten.bp.LocalDate;
import org.threeten.bp.LocalTime;
import org.threeten.bp.ZoneOffset;
import org.threeten.bp.ZonedDateTime;

/**
 * An optional second page of user input for the diagnosis flow. If the verification server supplies
 * the symptom onset date, this screen is not needed, so is skipped automatically.
 */
@AndroidEntryPoint
public class ShareDiagnosisOnsetDateFragment extends ShareDiagnosisBaseFragment {

  private static final String DATE_PICKER_TAG = "ShareDiagnosisOnsetDateFragment.DATE_PICKER_TAG";

  @Inject
  Clock clock;

  private FragmentShareDiagnosisOnsetDateBinding binding;

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent,
      Bundle savedInstanceState) {
    binding = FragmentShareDiagnosisOnsetDateBinding.inflate(inflater, parent, false);
    return binding.getRoot();
  }

  @Override
  public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    requireActivity().setTitle(R.string.share_onset_title);

    // Enabled next button when the user answers "have you had symptoms", but if it's "yes", require
    // that they enter a valid onset date.
    binding.shareNextButton.setEnabled(false);
    // Let's also enable the date field only after the user checks yes (but retain its entry if they
    // change their mind, to save time if they change it back)
    binding.shareTestDate.setEnabled(false);
    binding.hasSymptomsRadioGroup.setOnCheckedChangeListener(
        (radioGroup, checkedId) -> {
          binding.shareTestDate.setEnabled(checkedId == R.id.has_symptoms_yes);
          maybeSave(binding.hasSymptomsRadioGroup, binding.shareTestDate);
        });
    // Must also watch the date entry to see when to enable the next button.
    binding.shareTestDate.addTextChangedListener(new AbstractTextWatcher() {
      @Override
      public void afterTextChanged(Editable var1) {
        maybeSave(binding.hasSymptomsRadioGroup, binding.shareTestDate);
      }
    });
    binding.shareTestDate.setOnClickListener(v -> maybeShowMaterialDatePicker());

    // Keep input fields up to date with the diagnosis entity.
    PairLiveData<DiagnosisEntity, Boolean> diagnosisEntityAndIsEnabledPairLiveData =
        PairLiveData.of(shareDiagnosisViewModel.getCurrentDiagnosisLiveData(),
            exposureNotificationViewModel.getEnEnabledLiveData());
    diagnosisEntityAndIsEnabledPairLiveData
        .observe(
            getViewLifecycleOwner(),
            (diagnosisEntity, isEnabled) -> {
              String shareTestDateText;
              if (diagnosisEntity.getOnsetDate() == null) {
                shareTestDateText = "";
              } else {
                shareTestDateText = getDateTimeFormatter().format(diagnosisEntity.getOnsetDate());
              }

              // Only change the text field if its content changes
              if (!binding.shareTestDate.getText().toString().equals(shareTestDateText)) {
                binding.shareTestDate.setText(shareTestDateText);
              }

              // Maybe show the date picker if the symptom onset date is still not set and the user
              // answered that they had symptoms
              if (diagnosisEntity.getOnsetDate() == null && isEnabled) {
                maybeShowMaterialDatePicker();
              }

              switch (diagnosisEntity.getHasSymptoms()) {
                case NO:
                  binding.hasSymptomsNo.setChecked(true);
                  break;
                case YES:
                  binding.hasSymptomsYes.setChecked(true);
                  break;
                case WITHHELD:
                  binding.hasSymptomsWithheld.setChecked(true);
                  break;
              }

              binding.shareNextButton
                  .setEnabled(DiagnosisEntityHelper.hasCompletedOnset(diagnosisEntity, clock));
            });

    binding.home.setOnClickListener(v -> showCloseShareDiagnosisFlowAlertDialog());

    // If the date picker already exists upon view creation it means that view was just recreated
    // upon rotation. If so, its listeners need to be cleared and replaced as they hold references
    // to pre-rotation views.
    MaterialDatePicker<Long> datePicker = findMaterialDatePicker(DATE_PICKER_TAG);
    if (datePicker != null) {
      // Need to be cleared as most likely the view was just re-created after rotation
      datePicker.clearOnPositiveButtonClickListeners();
      addOnPositiveButtonClickListener(datePicker);
    }

    shareDiagnosisViewModel.getNextStepLiveData(Step.ONSET).observe(getViewLifecycleOwner(),
        step -> binding.shareNextButton.setOnClickListener(
            v -> shareDiagnosisViewModel.nextStep(step)));

    shareDiagnosisViewModel.getPreviousStepLiveData(Step.ONSET).observe(getViewLifecycleOwner(),
        step -> binding.sharePreviousButton.setOnClickListener(v -> {
          KeyboardHelper.maybeHideKeyboard(requireContext(), view);
          shareDiagnosisViewModel.previousStep(step);
        }));
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    binding = null;
  }

  private void maybeSave(RadioGroup hasSymptomsRadioGroup, EditText dateEditText) {
    // Always save hasSymptoms input if there is one
    int checkedId = hasSymptomsRadioGroup.getCheckedRadioButtonId();
    if (checkedId > 0) {
      shareDiagnosisViewModel.setHasSymptoms(hasSymptomsFromButtonId(checkedId));
    }

    // Symptom onset date should be set only if user answered "yes" and the entered value onset date
    // is formatted correctly
    final String dateStr = dateEditText.getText().toString();
    if (checkedId == R.id.has_symptoms_yes && isValidDate(dateStr, d -> true)) {
      shareDiagnosisViewModel.setSymptomOnsetDate(LocalDate.parse(dateStr, getDateTimeFormatter()));
    }
  }

  private HasSymptoms hasSymptomsFromButtonId(int checkedButtonId) {
    switch (checkedButtonId) {
      case R.id.has_symptoms_yes:
        return HasSymptoms.YES;
      case R.id.has_symptoms_no:
        return HasSymptoms.NO;
      case R.id.has_symptoms_withheld:
        return HasSymptoms.WITHHELD;
    }
    return HasSymptoms.UNSET;
  }

  /**
   * Shows material date picker, but only if there isn't one already shown and if user selected
   * "yes" when asked about whether they had symptoms.
   */
  private void maybeShowMaterialDatePicker() {
    int checkedId = binding.hasSymptomsRadioGroup.getCheckedRadioButtonId();
    if (findMaterialDatePicker(DATE_PICKER_TAG) != null || checkedId != R.id.has_symptoms_yes) {
      return;
    }
    MaterialDatePicker<Long> dialog = createMaterialDatePicker(getOnsetDateOrNow());
    addOnPositiveButtonClickListener(dialog);
    dialog.show(getParentFragmentManager(), DATE_PICKER_TAG);
  }

  private void addOnPositiveButtonClickListener(MaterialDatePicker<Long> dialog) {
    dialog.addOnPositiveButtonClickListener(
        selection -> {
          String dateStr = getDateTimeFormatter()
              .format(Instant.ofEpochMilli(selection).atZone(ZoneOffset.UTC));
          binding.shareTestDate.setText(dateStr);
          // Check if selected date is valid and if so show snackbar
          if (!inputIsValidToProceed(binding.hasSymptomsRadioGroup, dateStr)) {
            maybeShowInvalidDateSnackbar(dateStr);
          }
        });
  }

  /**
   * Return true if user's hasSymptom input and symptom onset date is valid and they should be
   * allowed to proceed to the next step in the share diagnosis flow.
   */
  private boolean inputIsValidToProceed(RadioGroup hasSymptomsRadioGroup, String dateStr) {
    int checkedId = hasSymptomsRadioGroup.getCheckedRadioButtonId();
    boolean questionAnswered = checkedId > 0;
    boolean hasSymptoms = checkedId == R.id.has_symptoms_yes;
    boolean haveValidDate = isValidDate(dateStr, symptomOnsetOrTestDateValidator::isValid);

    return questionAnswered && (!hasSymptoms || haveValidDate);
  }

  @Nullable
  private LocalDate getOnsetDateOrNull() {
    DiagnosisEntity diagnosis = shareDiagnosisViewModel.getCurrentDiagnosisLiveData().getValue();
    if (diagnosis != null && diagnosis.getOnsetDate() != null) {
      return diagnosis.getOnsetDate();
    }
    return null;
  }

  private Instant getOnsetDateOrNow() {
    LocalDate onset = getOnsetDateOrNull();
    if (onset != null) {
      return ZonedDateTime.of(onset, LocalTime.MIN, ZoneOffset.UTC).toInstant();
    }
    return Instant.now();
  }
}
