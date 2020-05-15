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

package com.google.android.apps.exposurenotification.activities;

import static com.google.android.apps.exposurenotification.nearby.ProvideDiagnosisKeysWorker.DEFAULT_API_TIMEOUT;

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
import com.google.android.apps.exposurenotification.activities.utils.ExposureNotificationPermissionHelper;
import com.google.android.apps.exposurenotification.adapter.ExposureAdapter;
import com.google.android.apps.exposurenotification.common.AppExecutors;
import com.google.android.apps.exposurenotification.common.TaskToFutureAdapter;
import com.google.android.apps.exposurenotification.nearby.ExposureNotificationClientWrapper;
import com.google.android.apps.exposurenotification.storage.ExposureEntity;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.apps.exposurenotification.storage.ExposureViewModel;
import com.google.android.apps.exposurenotification.storage.TokenEntity;
import com.google.android.apps.exposurenotification.storage.TokenViewModel;
import com.google.android.gms.nearby.exposurenotification.ExposureInformation;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Fragment for Exposures tab on home screen.
 */
public class ExposureFragment extends Fragment {

  private final String TAG = "ExposureFragment";

  private final ExposureNotificationPermissionHelper exposureNotificationPermissionHelper;
  private ExposureViewModel exposureViewModel;
  private TokenViewModel tokenViewModel;
  private ExposureAdapter adapter;

  public ExposureFragment() {
    exposureNotificationPermissionHelper =
        new ExposureNotificationPermissionHelper(this, this::refreshUi);
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_exposure, parent, false);
  }

  @Override
  public void onViewCreated(View view, Bundle savedInstanceState) {
    Button goToSettingsButton = view.findViewById(R.id.go_to_settings_button);
    View exposureNotificationsToggle = view.findViewById(R.id.exposure_notifications_toggle);

    goToSettingsButton.setOnClickListener(
        v -> exposureNotificationPermissionHelper.optInAndStartExposureTracing(view));
    exposureNotificationsToggle.setOnClickListener(v -> launchAbout());

    RecyclerView exposuresList = view.findViewById(R.id.exposures_list);
    adapter = new ExposureAdapter(exposureEntity -> {
      ExposureBottomSheetFragment sheet = ExposureBottomSheetFragment.newInstance(exposureEntity);
      sheet.show(getChildFragmentManager(), ExposureBottomSheetFragment.TAG);
    });
    exposuresList.setItemAnimator(null);
    exposuresList.setLayoutManager(new LinearLayoutManager(requireContext(),
        LinearLayoutManager.VERTICAL, false));
    exposuresList.setAdapter(adapter);

    exposureViewModel =
        new ViewModelProvider(this, getDefaultViewModelProviderFactory())
            .get(ExposureViewModel.class);
    exposureViewModel
        .getAllLiveData()
        .observe(getViewLifecycleOwner(), this::refreshUiForExposureEntities);
    tokenViewModel =
        new ViewModelProvider(this, getDefaultViewModelProviderFactory())
            .get(TokenViewModel.class);
  }

  @Override
  public void onResume() {
    super.onResume();
    refreshUi();
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    exposureNotificationPermissionHelper
        .onResolutionComplete(requestCode, resultCode, requireView());
  }

  /**
   * Update UI state after Exposure Notifications client state changes
   */
  private void refreshUi() {
    ExposureNotificationClientWrapper.get(requireContext()).isEnabled()
        .continueWith((isEnabled) -> {
          Boolean currentlyEnabled = isEnabled.getResult();

          refreshUiForEnabled(currentlyEnabled);

          if (currentlyEnabled) {
            // if we're seeing it enabled then permission has been granted
            noteOnboardingCompleted();
          }

          if (tokenViewModel != null) {
            FluentFuture.from(tokenViewModel.getAllAsync())
                .transformAsync(this::checkForRespondedTokensAsync,
                    AppExecutors.getBackgroundExecutor());
          }
          return null;
        });
  }

  private ListenableFuture<List<Void>> checkForRespondedTokensAsync(
      List<TokenEntity> tokenEntities) {
    List<ListenableFuture<Void>> futures = new ArrayList<>();
    for (TokenEntity tokenEntity : tokenEntities) {
      if (tokenEntity.isResponded()) {
        futures.add(FluentFuture.from(
            TaskToFutureAdapter.getFutureWithTimeout(
                ExposureNotificationClientWrapper.get(requireContext())
                    .getExposureInformation(tokenEntity.getToken()),
                DEFAULT_API_TIMEOUT.toMillis(),
                TimeUnit.MILLISECONDS,
                AppExecutors.getScheduledExecutor()))
            .transformAsync((exposureInformations) -> {
              List<ExposureEntity> exposureEntities = new ArrayList<>();
              for (ExposureInformation exposureInformation : exposureInformations) {
                exposureEntities.add(
                    ExposureEntity.create(
                        exposureInformation.getDateMillisSinceEpoch(),
                        tokenEntity.getLastUpdatedTimestampMs()));
              }
              return exposureViewModel.upsertAsync(exposureEntities);
            }, AppExecutors.getLightweightExecutor())
            .transformAsync((v) -> tokenViewModel.deleteByTokensAsync(tokenEntity.getToken()),
                AppExecutors.getLightweightExecutor()));
      }
    }
    return Futures.allAsList(futures);
  }

  /**
   * Update UI to match Exposure Notifications client has become enabled/not-enabled.
   *
   * @param currentlyEnabled True if Exposure Notifications is enabled
   */
  private void refreshUiForEnabled(Boolean currentlyEnabled) {
    View rootView = getView();
    if (rootView == null) {
      return;
    }

    ViewSwitcher settingsBannerSwitcher = rootView.findViewById(R.id.settings_banner_switcher);
    TextView exposureNotificationStatus = rootView.findViewById(R.id.exposure_notifications_status);
    TextView infoStatus = rootView.findViewById(R.id.info_status);

    settingsBannerSwitcher.setDisplayedChild(currentlyEnabled ? 1 : 0);

    if (currentlyEnabled) {
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

  /**
   * Record in SharedPreferences that the user has completed the Onboarding flow.
   */
  private void noteOnboardingCompleted() {
    ExposureNotificationSharedPreferences sharedPrefs = new ExposureNotificationSharedPreferences(
        requireContext());
    sharedPrefs.setOnboardedState(true);
  }

  /**
   * Open the Exposure Notifications about screen.
   */
  private void launchAbout() {
    startActivity(new Intent(requireContext(), ExposureAboutActivity.class));
  }

}
