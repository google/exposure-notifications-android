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

import static com.google.android.gms.nearby.exposurenotification.ExposureNotificationClient.ACTION_EXPOSURE_NOTIFICATION_SETTINGS;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ViewFlipper;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.ErrorStateViewFlipperChildren;
import com.google.android.apps.exposurenotification.common.StorageManagementHelper;
import com.google.android.apps.exposurenotification.home.ExposureNotificationViewModel;
import com.google.android.apps.exposurenotification.home.ExposureNotificationViewModel.ExposureNotificationState;
import com.google.android.apps.exposurenotification.proto.UiInteraction.EventType;
import com.google.android.material.snackbar.Snackbar;
import dagger.hilt.android.AndroidEntryPoint;

/**
 * Fragment for Notify tab on home screen
 */
@AndroidEntryPoint
public class NotifyHomeFragment extends Fragment {

  private static final String TAG = "NotifyHomeFragment";

  private static final int NOTIFY_BANNER_EDGE_CASE_CHILD = 0;
  private static final int NOTIFY_BANNER_SHARE_CHILD = 1;

  private static final int NOTIFY_VIEW_FLIPPER_NOTIFY =
      ErrorStateViewFlipperChildren.END_OF_COMMON /*+ 0*/;

  private ExposureNotificationViewModel exposureNotificationViewModel;
  private NotifyHomeViewModel notifyHomeViewModel;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_notify_home, parent, false);
  }

  @Override
  public void onViewCreated(View view, Bundle savedInstanceState) {
    exposureNotificationViewModel =
        new ViewModelProvider(requireActivity()).get(ExposureNotificationViewModel.class);
    notifyHomeViewModel =
        new ViewModelProvider(this, getDefaultViewModelProviderFactory())
            .get(NotifyHomeViewModel.class);

    exposureNotificationViewModel
        .getStateLiveData()
        .observe(getViewLifecycleOwner(), this::refreshUiForState);

    Button startApiButton = view.findViewById(R.id.start_api_button);
    startApiButton.setOnClickListener(
        v -> exposureNotificationViewModel.startExposureNotifications());
    exposureNotificationViewModel
        .getInFlightLiveData()
        .observe(getViewLifecycleOwner(), isInFlight -> startApiButton.setEnabled(!isInFlight));

    exposureNotificationViewModel
        .getApiErrorLiveEvent()
        .observe(getViewLifecycleOwner(), unused -> {
          View rootView = getView();
          if (rootView != null) {
            Snackbar.make(rootView, R.string.generic_error_message, Snackbar.LENGTH_LONG).show();
          }
        });

    Button shareButton = view.findViewById(R.id.fragment_notify_share_button);
    shareButton.setOnClickListener(
        v -> startActivity(ShareDiagnosisActivity.newIntentForAddFlow(requireContext())));

    DiagnosisEntityAdapter notifyViewAdapter =
        new DiagnosisEntityAdapter(
            diagnosis ->
                startActivity(
                    ShareDiagnosisActivity.newIntentForViewFlow(
                        requireContext(), diagnosis)));
    final LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
    RecyclerView recyclerView = view.findViewById(R.id.notify_recycler_view);
    recyclerView.setLayoutManager(layoutManager);
    recyclerView.setAdapter(notifyViewAdapter);

    view.findViewById(R.id.ble_settings_button).setOnClickListener(v -> launchEnSettings());
    view.findViewById(R.id.location_settings_button).setOnClickListener(v -> launchEnSettings());
    view.findViewById(R.id.location_ble_settings_button).setOnClickListener(v -> launchEnSettings());
    view.findViewById(R.id.manage_storage_button)
        .setOnClickListener(v -> StorageManagementHelper.launchStorageManagement(getContext()));

    View diagnosisHistoryContainer = view.findViewById(R.id.diagnosis_history_container);

    notifyHomeViewModel
        .getAllDiagnosisEntityLiveData()
        .observe(
            getViewLifecycleOwner(),
            l -> {
              diagnosisHistoryContainer.setVisibility(l.isEmpty() ? View.GONE : View.VISIBLE);
              notifyViewAdapter.setDiagnosisEntities(l);
            });
  }

  @Override
  public void onResume() {
    super.onResume();
    refreshUi();
  }

  /**
   * Update UI state after Exposure Notifications client state changes
   */
  private void refreshUi() {
    exposureNotificationViewModel.refreshState();
  }

  /**
   * Update UI to match Exposure Notifications state.
   *
   * @param state the {@link ExposureNotificationState} of the API
   */
  private void refreshUiForState(ExposureNotificationState state) {
    View rootView = getView();
    if (rootView == null) {
      return;
    }

    ViewFlipper bannerFlipper = rootView.findViewById(R.id.notify_header_banner);
    ViewFlipper viewFlipper = rootView.findViewById(R.id.notify_header_flipper);
    Button manageStorageButton = rootView.findViewById(R.id.manage_storage_button);

    switch (state) {
      case ENABLED:
        bannerFlipper.setDisplayedChild(NOTIFY_BANNER_SHARE_CHILD);
        viewFlipper.setDisplayedChild(NOTIFY_VIEW_FLIPPER_NOTIFY);
        break;
      case PAUSED_LOCATION_BLE:
        bannerFlipper.setDisplayedChild(NOTIFY_BANNER_EDGE_CASE_CHILD);
        viewFlipper.setDisplayedChild(ErrorStateViewFlipperChildren.LOCATION_BLE_ERROR_CHILD);
        exposureNotificationViewModel.logUiInteraction(EventType.LOCATION_PERMISSION_WARNING_SHOWN);
        exposureNotificationViewModel.logUiInteraction(EventType.BLUETOOTH_DISABLED_WARNING_SHOWN);
        break;
      case PAUSED_BLE:
        bannerFlipper.setDisplayedChild(NOTIFY_BANNER_EDGE_CASE_CHILD);
        viewFlipper.setDisplayedChild(ErrorStateViewFlipperChildren.BLE_ERROR_CHILD);
        exposureNotificationViewModel.logUiInteraction(EventType.BLUETOOTH_DISABLED_WARNING_SHOWN);
        break;
      case PAUSED_LOCATION:
        bannerFlipper.setDisplayedChild(NOTIFY_BANNER_EDGE_CASE_CHILD);
        viewFlipper.setDisplayedChild(ErrorStateViewFlipperChildren.LOCATION_ERROR_CHILD);
        exposureNotificationViewModel.logUiInteraction(EventType.LOCATION_PERMISSION_WARNING_SHOWN);
        break;
      case STORAGE_LOW:
        viewFlipper.setDisplayedChild(ErrorStateViewFlipperChildren.LOW_STORAGE_ERROR_CHILD);
        bannerFlipper.setDisplayedChild(NOTIFY_BANNER_EDGE_CASE_CHILD);
        manageStorageButton.setVisibility(
            StorageManagementHelper.isStorageManagementAvailable(getContext())
                ? Button.VISIBLE : Button.GONE);
        exposureNotificationViewModel.logUiInteraction(EventType.LOW_STORAGE_WARNING_SHOWN);
        break;
      case DISABLED:
      default:
        bannerFlipper.setDisplayedChild(NOTIFY_BANNER_EDGE_CASE_CHILD);
        viewFlipper.setDisplayedChild(ErrorStateViewFlipperChildren.DISABLED_ERROR_CHILD);
        break;
    }
  }

  /**
   * Open the Exposure Notifications Settings screen.
   */
  private void launchEnSettings() {
    Intent intent = new Intent(ACTION_EXPOSURE_NOTIFICATION_SETTINGS);
    startActivity(intent);
  }

}
