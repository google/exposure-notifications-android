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

import static com.google.android.gms.nearby.exposurenotification.ExposureNotificationClient.ACTION_EXPOSURE_NOTIFICATION_SETTINGS;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ViewFlipper;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.ErrorStateViewFlipperChildren;
import com.google.android.apps.exposurenotification.common.StorageManagementHelper;
import com.google.android.apps.exposurenotification.common.StringUtils;
import com.google.android.apps.exposurenotification.home.ExposureNotificationViewModel;
import com.google.android.apps.exposurenotification.home.ExposureNotificationViewModel.ExposureNotificationState;
import com.google.android.apps.exposurenotification.riskcalculation.ExposureClassification;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences.BadgeStatus;
import com.google.android.material.snackbar.Snackbar;
import dagger.hilt.android.AndroidEntryPoint;

/**
 * Fragment for Exposures tab on home screen.
 */
@AndroidEntryPoint
public class ExposureHomeFragment extends Fragment {

  private static final String TAG = "ExposureHomeFragment";

  private static final int EXPOSURE_BANNER_EDGE_CASE_CHILD = 0;
  private static final int EXPOSURE_BANNER_EXPOSURE_CHILD = 1;

  private static final int EXPOSURE_INFORMATION_FLIPPER_NO_EXPOSURE_CHILD = 0;
  private static final int EXPOSURE_INFORMATION_FLIPPER_INFORMATION_AVAILABLE_CHILD = 1;

  private ExposureNotificationViewModel exposureNotificationViewModel;
  private ExposureHomeViewModel exposureHomeViewModel;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_exposure_home, parent, false);
  }

  @Override
  public void onViewCreated(View view, Bundle savedInstanceState) {
    exposureNotificationViewModel =
        new ViewModelProvider(requireActivity()).get(ExposureNotificationViewModel.class);
    exposureHomeViewModel =
        new ViewModelProvider(this, getDefaultViewModelProviderFactory())
            .get(ExposureHomeViewModel.class);

    exposureNotificationViewModel
        .getStateLiveData()
        .observe(getViewLifecycleOwner(), this::refreshUiForState);

    Button startButton = view.findViewById(R.id.start_api_button);
    startButton.setOnClickListener(
        v -> exposureNotificationViewModel.startExposureNotifications());
    exposureNotificationViewModel
        .getInFlightLiveData()
        .observe(getViewLifecycleOwner(), isInFlight -> startButton.setEnabled(!isInFlight));

    exposureNotificationViewModel
        .getApiErrorLiveEvent()
        .observe(getViewLifecycleOwner(), unused -> {
          View rootView = getView();
          if (rootView != null) {
            Snackbar.make(rootView, R.string.generic_error_message, Snackbar.LENGTH_LONG).show();
          }
        });

    view.findViewById(R.id.ble_settings_button).setOnClickListener(v -> launchEnSettings());
    view.findViewById(R.id.location_settings_button).setOnClickListener(v -> launchEnSettings());
    view.findViewById(R.id.location_ble_settings_button).setOnClickListener(v -> launchEnSettings());
    view.findViewById(R.id.manage_storage_button)
        .setOnClickListener(v -> StorageManagementHelper.launchStorageManagement(getContext()));
    view.findViewById(R.id.exposure_details_url_text).setOnClickListener(
        v -> openExposureDetailsUrl(
            ((TextView)v).getText().toString()
        ));

    TextView badgeNewClassification = view.findViewById(R.id.exposure_details_new_badge);
    exposureHomeViewModel
        .getIsExposureClassificationNewLiveData()
        .observe(getViewLifecycleOwner(), badgeStatus ->
            badgeNewClassification.setVisibility(
                (badgeStatus != BadgeStatus.DISMISSED) ? TextView.VISIBLE : TextView.GONE));

    TextView badgeNewDate = view.findViewById(R.id.exposure_date_new_badge);
    exposureHomeViewModel
        .getIsExposureClassificationDateNewLiveData()
        .observe(getViewLifecycleOwner(), badgeStatus ->
            badgeNewDate.setVisibility(
                (badgeStatus != BadgeStatus.DISMISSED) ? TextView.VISIBLE : TextView.GONE));

    exposureHomeViewModel
        .getExposureClassificationLiveData()
        .observe(getViewLifecycleOwner(),
            exposureClassification -> {
              boolean isRevoked = exposureHomeViewModel.getIsExposureClassificationRevoked();
              refreshUiForClassification(exposureClassification, isRevoked);
            });

    /*
     If this view is created, we assume that "new" badges were seen.
     If they were previously BadgeStatus.NEW, we now set them to BadgeStatus.SEEN
     */
    exposureHomeViewModel.tryTransitionExposureClassificationNew(BadgeStatus.NEW, BadgeStatus.SEEN);
    exposureHomeViewModel.tryTransitionExposureClassificationDateNew(BadgeStatus.NEW,
        BadgeStatus.SEEN);

  }

  @Override
  public void onResume() {
    super.onResume();
    refreshUi();
  }

  private void refreshUi() {
    exposureNotificationViewModel.refreshState();
  }

  /**
   * Update UI to match Exposure Notifications module state (disabled, enabled, error states).
   *
   * @param state the {@link ExposureNotificationState} of the API
   */
  private void refreshUiForState(ExposureNotificationState state) {
    View rootView = getView();
    if (rootView == null) {
      return;
    }

    ViewFlipper bannerFlipper =
        rootView.findViewById(R.id.exposures_banner_flipper);
    ViewFlipper edgeCaseFlipper = rootView.findViewById(R.id.edge_case_flipper);
    Button manageStorageButton = rootView.findViewById(R.id.manage_storage_button);

    switch (state) {
      case ENABLED:
        edgeCaseFlipper.setVisibility(ViewFlipper.GONE);
        bannerFlipper.setVisibility(View.GONE);
        break;
      case PAUSED_LOCATION_BLE:
        edgeCaseFlipper
            .setDisplayedChild(ErrorStateViewFlipperChildren.LOCATION_BLE_ERROR_CHILD);
        edgeCaseFlipper.setVisibility(ViewFlipper.VISIBLE);
        bannerFlipper.setDisplayedChild(EXPOSURE_BANNER_EDGE_CASE_CHILD);
        bannerFlipper.setVisibility(View.VISIBLE);
        break;
      case PAUSED_BLE:
        edgeCaseFlipper.setDisplayedChild(ErrorStateViewFlipperChildren.BLE_ERROR_CHILD);
        edgeCaseFlipper.setVisibility(ViewFlipper.VISIBLE);
        bannerFlipper.setDisplayedChild(EXPOSURE_BANNER_EDGE_CASE_CHILD);
        bannerFlipper.setVisibility(View.VISIBLE);
        break;
      case PAUSED_LOCATION:
        edgeCaseFlipper.setDisplayedChild(ErrorStateViewFlipperChildren.LOCATION_ERROR_CHILD);
        edgeCaseFlipper.setVisibility(ViewFlipper.VISIBLE);
        bannerFlipper.setDisplayedChild(EXPOSURE_BANNER_EDGE_CASE_CHILD);
        bannerFlipper.setVisibility(View.VISIBLE);
        break;
      case STORAGE_LOW:
        edgeCaseFlipper
            .setDisplayedChild(ErrorStateViewFlipperChildren.LOW_STORAGE_ERROR_CHILD);
        manageStorageButton.setVisibility(
            StorageManagementHelper.isStorageManagementAvailable(getContext())
                ? Button.VISIBLE : Button.GONE);
        edgeCaseFlipper.setVisibility(ViewFlipper.VISIBLE);
        bannerFlipper.setDisplayedChild(EXPOSURE_BANNER_EDGE_CASE_CHILD);
        bannerFlipper.setVisibility(View.VISIBLE);
        break;
      case DISABLED:
      default:
        edgeCaseFlipper.setDisplayedChild(ErrorStateViewFlipperChildren.DISABLED_ERROR_CHILD);
        edgeCaseFlipper.setVisibility(ViewFlipper.VISIBLE);
        bannerFlipper.setDisplayedChild(EXPOSURE_BANNER_EDGE_CASE_CHILD);
        bannerFlipper.setVisibility(View.VISIBLE);
        break;
    }

    /*
     * Depending on whether we have a state where we require user interaction, we show different
     * versions of the "no exposures" view and decide whether or not to show a banner.
     * Thus update the classification UI too.
     */
    refreshUiForClassification(exposureHomeViewModel.getExposureClassification(),
        exposureHomeViewModel.getIsExposureClassificationRevoked());

  }

  /**
   * Update UI to match the current exposure risk classification
   *
   * @param exposureClassification the {@link ExposureClassification} as returned by
   *                               DailySummaryRiskCalculator
   * @param isRevoked a boolean indicating a "revoked" state transition
   *
   */
  private void refreshUiForClassification(ExposureClassification exposureClassification,
      boolean isRevoked) {
    View rootView = getView();
    if (rootView == null) {
      return;
    }

    ViewFlipper bannerFlipper =
        rootView.findViewById(R.id.exposures_banner_flipper);
    ViewFlipper exposureInformationFlipper =
        rootView.findViewById(R.id.exposure_information_flipper);

    TextView exposureDetailsDateExposedText
        = rootView.findViewById(R.id.exposure_details_date_exposed_text);
    TextView exposureDetailsUrlText = rootView.findViewById(R.id.exposure_details_url_text);
    TextView exposureDetailsText = rootView.findViewById(R.id.exposure_details_text);

    /*
     * Switch to the right view in the ViewFlippers depending on whether we have an exposure.
     * Fill in information accordingly
     */
    if (exposureClassification.getClassificationIndex()
        == ExposureClassification.NO_EXPOSURE_CLASSIFICATION_INDEX
        && !isRevoked) {
      /*
       * No exposure
       */
      if (exposureNotificationViewModel.getStateLiveData().getValue()
          == ExposureNotificationState.ENABLED) {
        // Without any actionable item, show the full "No exposures" view and banner
        exposureInformationFlipper
            .setDisplayedChild(EXPOSURE_INFORMATION_FLIPPER_NO_EXPOSURE_CHILD);
        exposureInformationFlipper.setVisibility(ViewFlipper.VISIBLE);
        bannerFlipper.setDisplayedChild(EXPOSURE_BANNER_EXPOSURE_CHILD);
        bannerFlipper.setVisibility(View.VISIBLE);
      } else {
        // If there is an item the user needs to act upon (e.g. enabling ble), we hide this view
        exposureInformationFlipper.setVisibility(ViewFlipper.GONE);
      }
    } else {
      /*
       * We've got an exposure! Fill in all the details (exposure date, text, further info url)
       */
      exposureInformationFlipper
          .setDisplayedChild(EXPOSURE_INFORMATION_FLIPPER_INFORMATION_AVAILABLE_CHILD);
      exposureInformationFlipper.setVisibility(ViewFlipper.VISIBLE);
      exposureDetailsDateExposedText.setText(
          StringUtils.epochDaysTimestampToMediumUTCDateString(
              exposureClassification.getClassificationDate(), getResources().getConfiguration().locale)
      );

      // Catch the revoked edge case
      if (isRevoked) {
        exposureDetailsUrlText.setText(R.string.exposure_details_url_revoked);
        exposureDetailsText.setText(R.string.exposure_details_text_revoked);
      }

      // All the other "normal" classifications
      else {
        switch (exposureClassification.getClassificationIndex()) {
          case 1:
            exposureDetailsUrlText.setText(R.string.exposure_details_url_1);
            exposureDetailsText.setText(R.string.exposure_details_text_1);
            break;
          case 2:
            exposureDetailsUrlText.setText(R.string.exposure_details_url_2);
            exposureDetailsText.setText(R.string.exposure_details_text_2);
            break;
          case 3:
            exposureDetailsUrlText.setText(R.string.exposure_details_url_3);
            exposureDetailsText.setText(R.string.exposure_details_text_3);
            break;
          case 4:
            exposureDetailsUrlText.setText(R.string.exposure_details_url_4);
            exposureDetailsText.setText(R.string.exposure_details_text_4);
            break;
        }
      }
    }

    updateFlipperDividerVisibility(rootView);
  }

  /**
   * The divider-bar between the ViewFlippers (Exposure module state/settings flipper and
   * ExposureClassification flipper) should only be visible if BOTH view flippers are
   */
  private void updateFlipperDividerVisibility(View rootView) {
    ViewFlipper exposureInformationFlipper =
        rootView.findViewById(R.id.exposure_information_flipper);
    ViewFlipper edgeCaseFlipper = rootView.findViewById(R.id.edge_case_flipper);

    View horizontalDividerView = rootView.findViewById(R.id.view_flipper_divider);

    if (exposureInformationFlipper.getVisibility() == ViewFlipper.VISIBLE
    && edgeCaseFlipper.getVisibility() == ViewFlipper.VISIBLE) {
      horizontalDividerView.setVisibility(View.VISIBLE);
    } else {
      horizontalDividerView.setVisibility(View.GONE);
    }
  }

  /**
   * Open the Exposure Notifications Settings screen.
   */
  private void launchEnSettings() {
    Intent intent = new Intent(ACTION_EXPOSURE_NOTIFICATION_SETTINGS);
    startActivity(intent);
  }

  /**
   * Open the URL at "learn more"
   */
  private void openExposureDetailsUrl(String url) {
    if (!url.startsWith("http://") && !url.startsWith("https://")) {
      url = "https://" + url;
    }

    try {
      Intent i = new Intent(Intent.ACTION_VIEW);
      i.setData(Uri.parse(url));
      startActivity(i);
    } catch (Exception e) {
      /*
       * This might fail either if the user does not have an App to handle the intent (Browser)
       * or the URL provided by the HA can not be parsed. In these cases we don't show an error to
       * the user, but log it.
       */
      Log.e(TAG, "Exception while launching ACTION_VIEW with URL " + url , e);
    }
  }

}
