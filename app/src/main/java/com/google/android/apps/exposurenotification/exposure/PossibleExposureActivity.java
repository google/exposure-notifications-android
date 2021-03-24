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

package com.google.android.apps.exposurenotification.exposure;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.StringUtils;
import com.google.android.apps.exposurenotification.databinding.ActivityPossibleExposureBinding;
import com.google.android.apps.exposurenotification.riskcalculation.ExposureClassification;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences.BadgeStatus;
import com.google.android.apps.exposurenotification.utils.UrlUtils;
import dagger.hilt.android.AndroidEntryPoint;

/**
 * Possible Exposure details Activity for the new UX flow.
 */
@AndroidEntryPoint
public class PossibleExposureActivity extends AppCompatActivity {

  private static final String TAG = "PossibleExposureActvty";
  private static final String ALLOWED_SCHEME = "https";

  private ActivityPossibleExposureBinding binding;
  private ExposureHomeViewModel exposureHomeViewModel;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    binding = ActivityPossibleExposureBinding.inflate(getLayoutInflater());
    setContentView(binding.getRoot());

    exposureHomeViewModel =
        new ViewModelProvider(this, getDefaultViewModelProviderFactory())
            .get(ExposureHomeViewModel.class);

    binding.home.setContentDescription(getString(R.string.navigate_up));
    binding.home.setOnClickListener(v -> onBackPressed());

    exposureHomeViewModel
        .getIsExposureClassificationNewLiveData()
        .observe(this, badgeStatus ->
            binding.exposureDetailsNewBadge.setVisibility(
                (badgeStatus != BadgeStatus.DISMISSED) ? TextView.VISIBLE : TextView.GONE));

    exposureHomeViewModel
        .getIsExposureClassificationDateNewLiveData()
        .observe(this, badgeStatus ->
            binding.exposureDateNewBadge.setVisibility(
                (badgeStatus != BadgeStatus.DISMISSED) ? TextView.VISIBLE : TextView.GONE));

    exposureHomeViewModel
        .getExposureClassificationLiveData()
        .observe(this,
            exposureClassification -> {
              boolean isRevoked = exposureHomeViewModel.getIsExposureClassificationRevoked();
              populateExposureDetails(exposureClassification, isRevoked);
            });

    /*
     If this activity is created, we assume that "new" badges were seen.
     If they were previously BadgeStatus.NEW, we now set them to BadgeStatus.SEEN
     */
    exposureHomeViewModel.tryTransitionExposureClassificationNew(BadgeStatus.NEW, BadgeStatus.SEEN);
    exposureHomeViewModel.tryTransitionExposureClassificationDateNew(BadgeStatus.NEW,
        BadgeStatus.SEEN);
  }

  /**
   * Populate views with the current exposure classification details
   *
   * @param exposureClassification the {@link ExposureClassification} as returned by
   *                               DailySummaryRiskCalculator
   * @param isRevoked              a boolean indicating a "revoked" state transition
   */
  private void populateExposureDetails(ExposureClassification exposureClassification,
      boolean isRevoked) {
    TextView exposureDetailsDateExposedText = binding.exposureDetailsDateExposedText;
    TextView exposureDetailsText = binding.exposureDetailsText;
    Button exposureDetailsUrlButton = binding.exposureDetailsUrlButton;

    exposureDetailsDateExposedText.setText(
        StringUtils.epochDaysTimestampToMediumUTCDateString(
            exposureClassification.getClassificationDate(),
            getResources().getConfiguration().locale)
    );

    // Catch the revoked edge case
    if (isRevoked) {
      exposureDetailsUrlButton.setText(R.string.exposure_details_url_revoked);
      exposureDetailsText.setText(R.string.exposure_details_text_revoked);
    }

    // All the other "normal" classifications
    else {
      switch (exposureClassification.getClassificationIndex()) {
        case 1:
          exposureDetailsUrlButton.setText(R.string.exposure_details_url_1);
          exposureDetailsText.setText(R.string.exposure_details_text_1);
          setUrlOnClickListener(getString(R.string.exposure_details_url_1));
          break;
        case 2:
          exposureDetailsUrlButton.setText(R.string.exposure_details_url_2);
          exposureDetailsText.setText(R.string.exposure_details_text_2);
          setUrlOnClickListener(getString(R.string.exposure_details_url_2));
          break;
        case 3:
          exposureDetailsUrlButton.setText(R.string.exposure_details_url_3);
          exposureDetailsText.setText(R.string.exposure_details_text_3);
          setUrlOnClickListener(getString(R.string.exposure_details_url_3));
          break;
        case 4:
          exposureDetailsUrlButton.setText(R.string.exposure_details_url_4);
          exposureDetailsText.setText(R.string.exposure_details_text_4);
          setUrlOnClickListener(getString(R.string.exposure_details_url_4));
          break;
      }
    }
  }

  /**
   * Set onClickListener for the Exposure Details URL
   */
  private void setUrlOnClickListener(String url) {
    binding.exposureDetailsUrlButton.setOnClickListener(v -> UrlUtils.openUrl(this, url));
  }

}
