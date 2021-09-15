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

import static com.google.android.apps.exposurenotification.storage.DiagnosisEntity.TravelStatus.NOT_ATTEMPTED;
import static com.google.android.apps.exposurenotification.storage.DiagnosisEntity.TravelStatus.NOT_TRAVELED;
import static com.google.android.apps.exposurenotification.storage.DiagnosisEntity.TravelStatus.NO_ANSWER;
import static com.google.android.apps.exposurenotification.storage.DiagnosisEntity.TravelStatus.TRAVELED;

import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.AbstractTextWatcher;
import com.google.android.apps.exposurenotification.common.KeyboardHelper;
import com.google.android.apps.exposurenotification.common.PairLiveData;
import com.google.android.apps.exposurenotification.common.SnackbarUtil;
import com.google.android.apps.exposurenotification.common.StringUtils;
import com.google.android.apps.exposurenotification.common.logging.Logger;
import com.google.android.apps.exposurenotification.databinding.FragmentShareDiagnosisUploadBinding;
import com.google.android.apps.exposurenotification.notify.ShareDiagnosisViewModel.Step;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity.HasSymptoms;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity.Shared;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity.TravelStatus;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.datepicker.MaterialDatePicker;
import dagger.hilt.android.AndroidEntryPoint;
import org.threeten.bp.Instant;
import org.threeten.bp.LocalDate;
import org.threeten.bp.LocalTime;
import org.threeten.bp.ZoneOffset;
import org.threeten.bp.ZonedDateTime;

/**
 * The last step of the diagnosis flow, asking the user for their travel status in the past 14 days
 * and the symptom onset date.
 */
@AndroidEntryPoint
public class ShareDiagnosisUploadFragment extends ShareDiagnosisBaseFragment {

  private static final String DATE_PICKER_TAG = "ShareDiagnosisUploadFragment.DATE_PICKER_TAG";

  private static final Logger logger = Logger.getLogger("ShareDiagnosisUploadFragment");

  private FragmentShareDiagnosisUploadBinding binding;

  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
    binding = FragmentShareDiagnosisUploadBinding.inflate(inflater, parent, false);
    return binding.getRoot();
  }

  @Override
  public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    requireActivity().setTitle(R.string.verify_test_result_title);
    setupShadowAtBottom(binding.shareDiagnosisScrollView, binding.buttonContainer);
    binding.home.setOnClickListener(v -> showCloseShareDiagnosisFlowAlertDialog());

    setupStepNotice();

    setupTestInformation();

    setupSymptomsQuestion();

    setupTravelQuestion();

    setupBottomButtons();

    setupStepAndErrorHandlers(view);

    setupTekReleaseHandlers();

    updateInputFieldsWithDiagnosisEntity();
  }

  private void setupStepAndErrorHandlers(View view) {
    PairLiveData.of(shareDiagnosisViewModel.getCurrentDiagnosisLiveData(),
        shareDiagnosisViewModel.getSharedLiveEvent())
        .observe(getViewLifecycleOwner(),
        (diagnosis, isShared) -> {
          if (isShared != null) {
            // Move to the next step only after there's been an attempt to share a diagnosis.
            Step step = ShareDiagnosisFlowHelper.getNextStep(
                Step.UPLOAD, diagnosis, shareDiagnosisViewModel.getShareDiagnosisFlow(),
                shareDiagnosisViewModel.showVaccinationQuestion(getResources()), isShared,
                requireContext());
            shareDiagnosisViewModel.nextStepIrreversible(step);
          }
        });

    shareDiagnosisViewModel.getPreviousStepLiveData(Step.UPLOAD).observe(getViewLifecycleOwner(),
        step -> binding.sharePreviousButton.setOnClickListener(v -> {
          KeyboardHelper.maybeHideKeyboard(requireContext(), view);
          shareDiagnosisViewModel.previousStep(step);
        }));

    shareDiagnosisViewModel.getSnackbarSingleLiveEvent()
        .observe(getViewLifecycleOwner(),
            message -> SnackbarUtil.maybeShowRegularSnackbar(getView(), message));
  }

  private void setupTekReleaseHandlers() {
    ActivityResultLauncher<IntentSenderRequest> activityResultLauncher =
        registerForActivityResult(new StartIntentSenderForResult(), activityResult -> {
          if (activityResult.getResultCode() == Activity.RESULT_OK) {
            // Okay to share, submit data.
            shareDiagnosisViewModel.uploadKeys();
          } else {
            // Not okay to share, just store for later.
            shareDiagnosisViewModel.setIsShared(Shared.NOT_ATTEMPTED);
          }
        });

    shareDiagnosisViewModel.getTeksReleaseResolutionRequiredLiveEvent()
        .observe(getViewLifecycleOwner(), apiException ->
            shareDiagnosisViewModel.startResolution(apiException, activityResultLauncher));
  }

  private void setupTestInformation() {
    binding.shareUploadTestResultDescription.setText(
        getString(R.string.share_upload_test_result_source_description,
            StringUtils.getHealthAuthorityName(requireContext())));
  }

  private void setupStepNotice() {
    shareDiagnosisViewModel.getStepXofYLiveData()
        .observe(getViewLifecycleOwner(), pair -> {
          String text = getString(R.string.share_diagnosis_progress_tracker,
              pair.first, pair.second);
          binding.stepXOfYTextView.setText(text);
        });
  }

  private void setupTravelQuestion() {
    if (!shareDiagnosisViewModel.isTravelStatusStepSkippable()) {
      binding.travelHistoryLayout.setVisibility(View.VISIBLE);
    }

    // If the date picker already exists upon view creation it means that view was just recreated
    // upon rotation. If so, its listeners need to be cleared and replaced as they hold references
    // to pre-rotation views.
    MaterialDatePicker<Long> datePicker = findMaterialDatePicker(DATE_PICKER_TAG);
    if (datePicker != null) {
      // Need to be cleared as most likely the view was just re-created after rotation
      datePicker.clearOnPositiveButtonClickListeners();
      addOnPositiveButtonClickListener(datePicker);
    }

    binding.travelHistoryChipGroup.setOnCheckedChangeListener((chipGroup, checkedId) -> {
      TravelStatus travelStatus = mapChipIdToTravelStatus(checkedId);
      shareDiagnosisViewModel.setTravelStatus(travelStatus);
    });
  }

  private void setupSymptomsQuestion() {
    binding.hasSymptomChipGroup.setOnCheckedChangeListener((chipGroup, checkedId) -> {
      // Always save hasSymptoms input if there is one
      shareDiagnosisViewModel.setHasSymptoms(hasSymptomsFromChipId(checkedId));
    });

    binding.hasSymptomConfirmedSelectedDateChoice.addTextChangedListener(new AbstractTextWatcher(){
      @Override
      public void afterTextChanged(Editable var1) {
        maybeSaveDateSymptomsStarted();
      }
    });
  }

  private void setupBottomButtons() {
    binding.shareButton.setEnabled(false);
    binding.shareButton.setOnClickListener(v -> {
      if (shareDiagnosisViewModel.deviceHasInternet()) {
        logger.d("Submitting diagnosis keys...");
        shareDiagnosisViewModel.uploadKeys();
      } else {
        SnackbarUtil.maybeShowRegularSnackbar(
            getView(), getString(R.string.share_error_no_internet));
      }
    });

    shareDiagnosisViewModel
        .getInFlightLiveData()
        .observe(
            getViewLifecycleOwner(),
            hasInFlightResolution -> {
              if (hasInFlightResolution) {
                binding.shareButton.setText("");
                binding.shareButton.setEnabled(false);
                binding.shareProgressBar.setVisibility(View.VISIBLE);
                binding.hasSymptomConfirmedSelectedDateChoice.setEnabled(false);
                binding.skipSymptomDateChoice.setEnabled(false);
                binding.travelConfirmedChoice.setEnabled(false);
                binding.noTravelChoice.setEnabled(false);
                binding.skipTravelHistoryChoice.setEnabled(false);
              } else {
                binding.shareButton.setEnabled(true);
                binding.shareButton.setText(R.string.btn_share);
                binding.shareProgressBar.setVisibility(View.INVISIBLE);
                binding.hasSymptomConfirmedSelectedDateChoice.setEnabled(true);
                binding.skipSymptomDateChoice.setEnabled(true);
                binding.travelConfirmedChoice.setEnabled(true);
                binding.noTravelChoice.setEnabled(true);
                binding.skipTravelHistoryChoice.setEnabled(true);
              }
            });
  }

  /**
   * One method updating everything that depends on CurrentDiagnosisLiveData
   * - The test result information (positive/negative)
   * - The has-symptoms picker and symptom onset date
   * - The travel question
   * - The bottom share button (whether it is enabled or not yet)
   */
  private void updateInputFieldsWithDiagnosisEntity() {
    shareDiagnosisViewModel.getCurrentDiagnosisLiveData().observe(
        getViewLifecycleOwner(),
        diagnosisEntity -> {
          updateTestInformationWithDiagnosisEntity(diagnosisEntity);
          updateSymptomsQuestionWithDiagnosisEntity(diagnosisEntity);
          updateTravelQuestionWithDiagnosisEntity(diagnosisEntity);
          updateBottomButtonsWithDiagnosisEntity(diagnosisEntity);
        });
  }

  private void updateTestInformationWithDiagnosisEntity(DiagnosisEntity diagnosisEntity) {
    binding.shareUploadStatus
        .setText(DiagnosisEntityHelper.getTestResultStringResource(diagnosisEntity));
  }

  private void updateSymptomsQuestionWithDiagnosisEntity(DiagnosisEntity diagnosisEntity) {
    if (diagnosisEntity.getHasSymptoms() == HasSymptoms.YES) {
      String shareTestDateText;

      // If getOnsetDate() == null (even though we have HasSymptoms.YES),
      // the user pressed cancel on the MaterialDatePicker and did not enter another date before.
      // For consistency with the old flow, we don't change the chip selection (still leave
      // the "Select a date" chip selected), but keep the share button disabled.
      if (diagnosisEntity.getOnsetDate() == null) {
        shareTestDateText = getString(R.string.share_upload_select_a_date_choice);
      } else {
        shareTestDateText = getDateTimeFormatter().format(diagnosisEntity.getOnsetDate());
      }
      // Only change the text field if its content changes
      if (!binding.hasSymptomConfirmedSelectedDateChoice.getText().toString()
          .equals(shareTestDateText)) {
        binding.hasSymptomConfirmedSelectedDateChoice.setText(shareTestDateText);
      }

      // The state of the chips depend on whether the diagnosis date comes from the server.
      // Only if it does we disable the chip, otherwise we always let the user change it
      if (diagnosisEntity.getIsServerOnsetDate()) {
        binding.skipSymptomDateChoice.setVisibility(View.GONE);
        binding.hasSymptomConfirmedSelectedDateChoice.setEnabled(false);
        binding.hasSymptomConfirmedSelectedDateChoice.setChecked(true);
        binding.hasSymptomsDateFixedDescription.setVisibility(View.VISIBLE);
        binding.hasSymptomsDateFixedDescription.setText(
            getString(R.string.share_upload_symptoms_date_fixed_description,
                StringUtils.getHealthAuthorityName(requireContext())));
      }

      // If the date changes and we do not have the date chip selected, we can assume it's a flow
      // continuation. In this case we select the date chip.
      if (binding.hasSymptomChipGroup.getCheckedChipIds().isEmpty()) {
        binding.hasSymptomConfirmedSelectedDateChoice.setChecked(true);
      }
    }

    binding.hasSymptomConfirmedSelectedDateChoice
        .setOnClickListener(v -> {
          if (((Chip) v).isChecked()) {
            maybeShowMaterialDatePicker(getOnsetDateOrNow(diagnosisEntity));
          }
        });
  }

  private void updateTravelQuestionWithDiagnosisEntity(DiagnosisEntity diagnosisEntity) {
    TravelStatus travelStatus = diagnosisEntity.getTravelStatus();
    if (!shareDiagnosisViewModel.isTravelStatusStepSkippable()
        && !NOT_ATTEMPTED.equals(travelStatus)) {
      int chipId = mapTravelStatusToChipId(travelStatus);
      Chip chip = binding.travelHistoryChipGroup.findViewById(chipId);
      chip.setChecked(true);
    }
  }

  private void updateBottomButtonsWithDiagnosisEntity(DiagnosisEntity diagnosisEntity) {
    boolean enableShareButton = (DiagnosisEntityHelper.hasCompletedOnset(diagnosisEntity, clock)
        && (shareDiagnosisViewModel.isTravelStatusStepSkippable()
        || diagnosisEntity.getTravelStatus() != TravelStatus.NOT_ATTEMPTED));
    binding.shareButton.setEnabled(enableShareButton);
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    binding = null;
  }

  private TravelStatus mapChipIdToTravelStatus(@IdRes int checkedChipId) {
    switch (checkedChipId) {
      case R.id.travel_confirmed_choice:
        return TRAVELED;
      case R.id.no_travel_choice:
        return NOT_TRAVELED;
      case R.id.skip_travel_history_choice:
        return NO_ANSWER;
      case -1:
        // This happens if a already selected chip is clicked again, thus selecting no chip
        return NOT_ATTEMPTED;
      default:
        throw new IllegalStateException("Failed to map checked button to travel status");
    }
  }

  private @IdRes int mapTravelStatusToChipId(TravelStatus travelStatus) {
    switch (travelStatus) {
      case TRAVELED:
        return R.id.travel_confirmed_choice;
      case NOT_TRAVELED:
        return R.id.no_travel_choice;
      case NO_ANSWER:
        return R.id.skip_travel_history_choice;
      case NOT_ATTEMPTED:
      default:
        throw new IllegalStateException("Failed to map travel status to radio button id");
    }
  }

  private HasSymptoms hasSymptomsFromChipId(@IdRes int checkedChipId) {
    switch (checkedChipId) {
      case R.id.has_symptom_confirmed_selected_date_choice:
        return HasSymptoms.YES;
      case R.id.skip_symptom_date_choice:
        return HasSymptoms.WITHHELD;
      default:
        return HasSymptoms.UNSET;
    }
  }

  private void maybeSaveDateSymptomsStarted() {
    int checkedChipId = binding.hasSymptomChipGroup.getCheckedChipId();
    final String dateStr = binding.hasSymptomConfirmedSelectedDateChoice.getText().toString();

    if (checkedChipId == R.id.has_symptom_confirmed_selected_date_choice
        && isValidDate(dateStr, d -> true)) {
      shareDiagnosisViewModel.setSymptomOnsetDate(LocalDate.parse(dateStr, getDateTimeFormatter()));
    }
  }

  /**
   * Shows material date picker, but only if there isn't one already shown and if user selected
   * "yes" when asked about whether they had symptoms.
   */
  private void maybeShowMaterialDatePicker(Instant pickerDate) {
    int checkedId = binding.hasSymptomChipGroup.getCheckedChipId();
    if (findMaterialDatePicker(DATE_PICKER_TAG) != null
        || checkedId != R.id.has_symptom_confirmed_selected_date_choice) {
      return;
    }
    MaterialDatePicker<Long> dialog = createMaterialDatePicker(pickerDate);
    addOnPositiveButtonClickListener(dialog);
    dialog.show(getParentFragmentManager(), DATE_PICKER_TAG);
  }

  private void addOnPositiveButtonClickListener(MaterialDatePicker<Long> dialog) {
    dialog.addOnPositiveButtonClickListener(
        selection -> {
          String dateStr = getDateTimeFormatter()
              .format(Instant.ofEpochMilli(selection).atZone(ZoneOffset.UTC));
          binding.hasSymptomConfirmedSelectedDateChoice.setText(dateStr);
          // Check if selected date is valid and if so show snack bar
          if (!inputIsValidToProceed(binding.hasSymptomChipGroup, dateStr)) {
            maybeShowInvalidDateSnackbar(dateStr);
          }
        });
  }

  /**
   * Return true if user's hasSymptom input and symptom onset date is valid and they should be
   * allowed to proceed to the next step in the share diagnosis flow.
   */
  private boolean inputIsValidToProceed(ChipGroup symptomOnsetChipGroup, String dateStr) {
    int checkedId = symptomOnsetChipGroup.getCheckedChipId();
    boolean questionAnswered = checkedId > 0;
    boolean hasSymptoms = checkedId == R.id.has_symptom_confirmed_selected_date_choice;
    boolean haveValidDate = isValidDate(dateStr, symptomOnsetOrTestDateValidator::isValid);

    return questionAnswered && (!hasSymptoms || haveValidDate);
  }

  private Instant getOnsetDateOrNow(DiagnosisEntity currentDiagnosis) {
    if (currentDiagnosis != null && currentDiagnosis.getOnsetDate() != null) {
      return ZonedDateTime.of(currentDiagnosis.getOnsetDate(), LocalTime.MIN, ZoneOffset.UTC)
          .toInstant();
    }
    return Instant.now();
  }
}