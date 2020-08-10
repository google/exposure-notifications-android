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

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.ViewAnimator;
import android.widget.ViewFlipper;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.StorageManagementHelper;
import com.google.android.apps.exposurenotification.home.ExposureNotificationViewModel;
import com.google.android.apps.exposurenotification.home.ExposureNotificationViewModel.ExposureNotificationState;
import com.google.android.apps.exposurenotification.storage.ExposureEntity;
import com.google.android.material.snackbar.Snackbar;
import com.mikepenz.aboutlibraries.LibsBuilder;
import java.util.List;

/**
 * Fragment for Exposures tab on home screen.
 */
public class ExposureHomeFragment extends Fragment {

  private static final String TAG = "ExposureHomeFragment";

  private ExposureNotificationViewModel exposureNotificationViewModel;
  private ExposureHomeViewModel exposureHomeViewModel;
  private ExposureEntityAdapter adapter;

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

    view.findViewById(R.id.exposure_menu).setOnClickListener(v -> showPopup(v));

    Button startButton = view.findViewById(R.id.start_api_button);
    startButton.setOnClickListener(v -> exposureNotificationViewModel.startExposureNotifications());
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

    view.findViewById(R.id.exposure_about_button).setOnClickListener(v -> launchAboutAction());
    view.findViewById(R.id.ble_settings_button).setOnClickListener(v -> launchEnSettings());
    view.findViewById(R.id.location_settings_button).setOnClickListener(v -> launchEnSettings());
    view.findViewById(R.id.manage_storage_button)
        .setOnClickListener(v -> StorageManagementHelper.launchStorageManagement(getContext()));

    RecyclerView exposuresList = view.findViewById(R.id.exposures_list);
    adapter =
        new ExposureEntityAdapter(
            exposureEntity -> {
              ExposureBottomSheetFragment sheet =
                  ExposureBottomSheetFragment.newInstance(exposureEntity);
              sheet.show(getChildFragmentManager(), ExposureBottomSheetFragment.TAG);
            });
    exposuresList.setItemAnimator(null);
    exposuresList.setLayoutManager(
        new LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false));
    exposuresList.setAdapter(adapter);

    exposureHomeViewModel
        .getAllExposureEntityLiveData()
        .observe(getViewLifecycleOwner(), this::refreshUiForExposureEntities);

    IntentFilter intentFilter = new IntentFilter();
    intentFilter.addAction(LocationManager.PROVIDERS_CHANGED_ACTION);
    intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
    requireContext().registerReceiver(refreshBroadcastReceiver, intentFilter);
  }

  private final BroadcastReceiver refreshBroadcastReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      refreshUi();
    }
  };

  @Override
  public void onResume() {
    super.onResume();
    refreshUi();
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    requireContext().unregisterReceiver(refreshBroadcastReceiver);
  }

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

    ViewFlipper settingsBannerFlipper = rootView.findViewById(R.id.settings_banner_flipper);
    TextView exposureNotificationStatus = rootView.findViewById(R.id.exposure_notifications_status);
    TextView infoStatus = rootView.findViewById(R.id.info_status);
    Button manageStorageButton = rootView.findViewById(R.id.manage_storage_button);

    switch (state) {
      case ENABLED:
        settingsBannerFlipper.setDisplayedChild(1);
        exposureNotificationStatus.setText(R.string.on);
        infoStatus.setText(R.string.notifications_enabled_info);
        break;
      case PAUSED_BLE:
        settingsBannerFlipper.setDisplayedChild(2);
        exposureNotificationStatus.setText(R.string.on);
        infoStatus.setText(R.string.notifications_enabled_info);
        break;
      case PAUSED_LOCATION:
        settingsBannerFlipper.setDisplayedChild(3);
        exposureNotificationStatus.setText(R.string.on);
        infoStatus.setText(R.string.notifications_enabled_info);
        break;
      case STORAGE_LOW:
        settingsBannerFlipper.setDisplayedChild(4);
        exposureNotificationStatus.setText(R.string.on);
        infoStatus.setText(R.string.notifications_enabled_info);
        manageStorageButton.setVisibility(
            StorageManagementHelper.isStorageManagementAvailable(getContext())
            ? Button.VISIBLE : Button.GONE);
        break;
      case DISABLED:
      default:
        settingsBannerFlipper.setDisplayedChild(0);
        exposureNotificationStatus.setText(R.string.off);
        infoStatus.setText(R.string.notifications_disabled_info);
        break;
    }
  }

  /**
   * Display new exposure information
   *
   * @param exposureEntities List of potential exposures
   */
  private void refreshUiForExposureEntities(@Nullable List<ExposureEntity> exposureEntities) {
    View rootView = getView();
    if (rootView == null) {
      return;
    }

    if (adapter != null) {
      adapter.submitList(exposureEntities);
    }

    ViewAnimator switcher = rootView.findViewById(R.id.exposures_list_empty_switcher);
    switcher.setDisplayedChild(exposureEntities == null || exposureEntities.isEmpty() ? 0 : 1);
  }

  /**
   * Open the Exposure Notifications about screen.
   */
  private void launchAboutAction() {
    startActivity(new Intent(requireContext(), ExposureAboutActivity.class));
  }

  /**
   * Open the Exposure Notifications Settings screen.
   */
  private void launchEnSettings() {
    Intent intent = new Intent(ACTION_EXPOSURE_NOTIFICATION_SETTINGS);
    startActivity(intent);
  }

  public void showPopup(View v) {
    PopupMenu popup = new PopupMenu(getContext(), v);
    popup.setOnMenuItemClickListener(menuItem -> {
      showOsLicenses();
      return true;
    });
    MenuInflater inflater = popup.getMenuInflater();
    inflater.inflate(R.menu.popup_menu, popup.getMenu());
    popup.show();
  }

  private void showOsLicenses(){
    new LibsBuilder().withLicenseShown(true).start(getActivity());
  }

}
