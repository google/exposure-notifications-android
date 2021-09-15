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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import androidx.annotation.NonNull;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.logging.Logger;
import com.google.android.apps.exposurenotification.databinding.FragmentShareDiagnosisVaccinationBinding;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences.VaccinationStatus;
import com.google.android.apps.exposurenotification.utils.UrlUtils;

/**
 * Final page of the sharing flow, shown when the user's keys have been successfully uploaded to the
 * keyserver and the additional vaccination question has been enabled by the HA.
 */
public class ShareDiagnosisVaccinationFragment extends ShareDiagnosisBaseFragment {

  private static final Logger logger = Logger.getLogger("ShareExposureVaccFrag");

  private FragmentShareDiagnosisVaccinationBinding binding;

  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
    binding = FragmentShareDiagnosisVaccinationBinding.inflate(inflater, parent, false);
    return binding.getRoot();
  }

  @Override
  public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    requireActivity().setTitle(R.string.share_confirm_title);
    setupShadowAtBottom(binding.shareDiagnosisScrollView, binding.buttonContainer);

    binding.shareDoneButton.setOnClickListener(v -> handleDone());

    binding.helpMoreText.setText(getString(R.string.share_vaccination_description,
        getString(R.string.health_authority_name)));
    binding.learnMoreButton.setOnClickListener(v -> launchLearnMore());
    binding.vaccinationStatusLayout.vaccinationQuestionRadioGroup.setOnCheckedChangeListener(
        (radioGroup, id) ->
            shareDiagnosisViewModel
                .setVaccinationStatusForUI(mapRadioButtonIdToVaccinationStatus(id))
    );

    shareDiagnosisViewModel.getVaccinationStatusForUILiveData().observe(getViewLifecycleOwner(),
        vaccinationStatus -> {
          if (vaccinationStatus.isPresent()) {
            mapVaccinationStatusToRadioButton(vaccinationStatus.get())
                .setChecked(true);
          }
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

  /**
   * Launches the ENPA link in the device browser.
   */
  private void launchLearnMore() {
    UrlUtils.openUrl(binding.getRoot(), getString(R.string.private_analytics_link));
  }

  /**
   * Store vaccination question answer into shared-prefs if user opts in.
   */
  private void handleDone() {
    RadioGroup radioGroup = binding.vaccinationStatusLayout.vaccinationQuestionRadioGroup;
    VaccinationStatus vaccinationStatus =
        mapRadioButtonIdToVaccinationStatus(radioGroup.getCheckedRadioButtonId());
    logger.d("setLastVaccinationResponse to " + vaccinationStatus.name());
    shareDiagnosisViewModel.setLastVaccinationResponse(vaccinationStatus);
    closeShareDiagnosisFlowImmediately();
  }

  private VaccinationStatus mapRadioButtonIdToVaccinationStatus(int checkedRadioButtonId) {
    switch (checkedRadioButtonId) {
      case R.id.yes_radio_button:
        return VaccinationStatus.VACCINATED;
      case R.id.no_radio_button:
        return VaccinationStatus.NOT_VACCINATED;
      case R.id.unknown_radio_button:
      default:
        return VaccinationStatus.UNKNOWN;
    }
  }

  private RadioButton mapVaccinationStatusToRadioButton(VaccinationStatus vaccinationStatus) {
    switch (vaccinationStatus) {
      case VACCINATED:
        return binding.vaccinationStatusLayout.yesRadioButton;
      case NOT_VACCINATED:
        return binding.vaccinationStatusLayout.noRadioButton;
      case UNKNOWN:
        return binding.vaccinationStatusLayout.unknownRadioButton;
      default:
        throw new IllegalStateException("Failed to map vaccination status to radio button id");
    }
  }

}
