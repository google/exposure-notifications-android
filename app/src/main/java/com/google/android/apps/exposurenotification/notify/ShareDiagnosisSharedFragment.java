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
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import androidx.annotation.NonNull;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.databinding.FragmentShareDiagnosisSharedBinding;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences.VaccinationStatus;
import com.google.android.apps.exposurenotification.utils.UrlUtils;
import dagger.hilt.android.AndroidEntryPoint;

/**
 * Result page the diagnosis flow, shown when the user's keys have been successfully uploaded to the
 * keyserver.
 */
@AndroidEntryPoint
public class ShareDiagnosisSharedFragment extends ShareDiagnosisBaseFragment {

  private static final String TAG = "ShareExposureSharedFrag";

  private static final int VIEWSWITCHER_VACCINATION_QUESTION = 0;
  private static final int VIEWSWITCHER_BANNER_ONLY = 1;

  private FragmentShareDiagnosisSharedBinding binding;

  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
    binding = FragmentShareDiagnosisSharedBinding.inflate(inflater, parent, false);
    return binding.getRoot();
  }

  @Override
  public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    requireActivity().setTitle(R.string.share_confirm_title);

    binding.shareDoneButton.setOnClickListener(v -> handleDone());

    if (shareDiagnosisViewModel.showVaccinationQuestion(getResources())) {
      binding.vaccinationQuestionSwitcher.setDisplayedChild(VIEWSWITCHER_VACCINATION_QUESTION);
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
    } else {
      binding.vaccinationQuestionSwitcher.setDisplayedChild(VIEWSWITCHER_BANNER_ONLY);
    }
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    binding = null;
  }

  /**
   * Launches the ENPA link in the device browser.
   */
  private void launchLearnMore() {
    UrlUtils.openUrl(binding.getRoot(), getString(R.string.private_analytics_link));
  }

  /**
   * Handle presses on "done", e.g. store vaccination question answer into shared-prefs if enabled
   */
  private void handleDone() {
    if (shareDiagnosisViewModel.showVaccinationQuestion(getResources())) {
      RadioGroup radioGroup = binding.vaccinationStatusLayout.vaccinationQuestionRadioGroup;
      VaccinationStatus vaccinationStatus =
          mapRadioButtonIdToVaccinationStatus(radioGroup.getCheckedRadioButtonId());
      Log.d(TAG, "setLastVaccinationResponse to " + vaccinationStatus.name());
      shareDiagnosisViewModel.setLastVaccinationResponse(vaccinationStatus);
    }
    closeShareDiagnosisFlow();
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
