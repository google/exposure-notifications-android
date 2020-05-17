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

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ViewSwitcher;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.activities.utils.ExposureNotificationPermissionHelper;
import com.google.android.apps.exposurenotification.adapter.NotifyAdapter;
import com.google.android.apps.exposurenotification.nearby.ExposureNotificationClientWrapper;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.apps.exposurenotification.storage.PositiveDiagnosisViewModel;

/**
 * Fragment for Notify tab on home screen
 */
public class NotifyFragment extends Fragment {

  private static final String TAG = "NotifyFragment";

  private final ExposureNotificationPermissionHelper exposureNotificationPermissionHelper;

  public NotifyFragment() {
    exposureNotificationPermissionHelper =
        new ExposureNotificationPermissionHelper(this, this::refreshUi);
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_notify, parent, false);
  }

  @Override
  public void onViewCreated(View view, Bundle savedInstanceState) {
    Button shareButton = view.findViewById(R.id.fragment_notify_share_button);
    shareButton.setOnClickListener(
        v -> startActivity(ShareExposureActivity.newIntentForAddFlow(requireContext())));

    NotifyAdapter notifyViewAdapter = new NotifyAdapter(positiveDiagnosisEntity -> startActivity(
        ShareExposureActivity.newIntentForViewFlow(requireContext(), positiveDiagnosisEntity)));
    final LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
    RecyclerView recyclerView = view.findViewById(R.id.notify_recycler_view);
    recyclerView.setLayoutManager(layoutManager);
    recyclerView.setAdapter(notifyViewAdapter);

    final ViewSwitcher switcher = requireView()
        .findViewById(R.id.fragment_notify_diagnosis_switcher);

    PositiveDiagnosisViewModel positiveDiagnosisViewModel =
        new ViewModelProvider(this, getDefaultViewModelProviderFactory())
            .get(PositiveDiagnosisViewModel.class);
    positiveDiagnosisViewModel
        .getAllLiveData()
        .observe(getViewLifecycleOwner(), l -> {
              switcher.setDisplayedChild(l.isEmpty() ? 0 : 1);
              notifyViewAdapter.setPositiveDiagnosisEntities(l);
            }
        );

    Button goToSettingsButton = view.findViewById(R.id.go_to_settings_button);
    goToSettingsButton.setOnClickListener(
        v -> exposureNotificationPermissionHelper.optInAndStartExposureTracing(view));
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
    ExposureNotificationClientWrapper exposureNotificationClientWrapper =
        ExposureNotificationClientWrapper.get(requireContext());

    exposureNotificationClientWrapper.isEnabled()
        .addOnSuccessListener(this::refreshUiForEnabled)
        .addOnFailureListener((cause) -> refreshUiForEnabled(false));
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
    if (currentlyEnabled) {
      // if we're seeing it enabled then permission has been granted
      ExposureNotificationSharedPreferences sharedPrefs = new ExposureNotificationSharedPreferences(
          requireContext());
      sharedPrefs.setOnboardedState(true);
    }
    rootView.findViewById(R.id.settings_banner_section)
        .setVisibility(currentlyEnabled ? View.GONE : View.VISIBLE);
    rootView.findViewById(R.id.notify_share_section)
        .setVisibility(currentlyEnabled ? View.VISIBLE : View.GONE);
    rootView.findViewById(R.id.diagnosis_history_container)
        .setVisibility(currentlyEnabled ? View.VISIBLE : View.GONE);
  }

}
