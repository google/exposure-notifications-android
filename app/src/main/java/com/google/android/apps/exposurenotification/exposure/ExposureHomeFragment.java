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

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ViewAnimator;
import android.widget.ViewSwitcher;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.home.ExposureNotificationViewModel;
import com.google.android.apps.exposurenotification.storage.ExposureEntity;
import com.google.android.material.snackbar.Snackbar;
import java.util.List;

/** Fragment for Exposures tab on home screen. */
public class ExposureHomeFragment extends Fragment {

  private final String TAG = "ExposureHomeFragment";

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
        .getIsEnabledLiveData()
        .observe(getViewLifecycleOwner(), isEnabled -> refreshUiForEnabled(isEnabled));

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
  }

  @Override
  public void onResume() {
    super.onResume();
    refreshUi();
  }

  private void refreshUi() {
    exposureNotificationViewModel.refreshIsEnabledState();
  }

  /**
   * Update UI to match Exposure Notifications client has become enabled/not-enabled.
   *
   * @param isEnabled True if Exposure Notifications is enabled
   */
  private void refreshUiForEnabled(Boolean isEnabled) {
    View rootView = getView();
    if (rootView == null) {
      return;
    }

    ViewSwitcher settingsBannerSwitcher = rootView.findViewById(R.id.settings_banner_switcher);
    TextView exposureNotificationStatus = rootView.findViewById(R.id.exposure_notifications_status);
    TextView infoStatus = rootView.findViewById(R.id.info_status);

    settingsBannerSwitcher.setDisplayedChild(isEnabled ? 1 : 0);

    if (isEnabled) {
      exposureNotificationStatus.setText(R.string.on);
      infoStatus.setText(R.string.notifications_enabled_info);
    } else {
      exposureNotificationStatus.setText(R.string.off);
      infoStatus.setText(R.string.notifications_disabled_info);
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

  /** Open the Exposure Notifications about screen. */
  private void launchAboutAction() {
    startActivity(new Intent(requireContext(), ExposureAboutActivity.class));
  }
}
