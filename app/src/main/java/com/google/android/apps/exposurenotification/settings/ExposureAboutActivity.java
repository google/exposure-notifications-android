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

package com.google.android.apps.exposurenotification.settings;

import static com.google.android.gms.nearby.exposurenotification.ExposureNotificationClient.ACTION_EXPOSURE_NOTIFICATION_SETTINGS;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.LinearLayout;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.databinding.ActivityExposureAboutBinding;
import com.google.android.apps.exposurenotification.edgecases.AboutEdgeCaseFragment;
import com.google.android.apps.exposurenotification.home.ExposureNotificationViewModel;
import com.google.android.apps.exposurenotification.home.ExposureNotificationViewModel.ExposureNotificationState;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import dagger.hilt.android.AndroidEntryPoint;

/**
 * Activity for information about exposure notifications in general.
 */
@AndroidEntryPoint
public class ExposureAboutActivity extends AppCompatActivity {

  private static final String TAG = "ExposureAboutActivity";

  private static final String STATE_TURN_OFF_OPEN = "STATE_TURN_OFF_OPEN";

  private ActivityExposureAboutBinding binding;
  private ExposureNotificationViewModel exposureNotificationViewModel;

  boolean isTurnOffOpen = false;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    binding = ActivityExposureAboutBinding.inflate(getLayoutInflater());
    setContentView(binding.getRoot());

    exposureNotificationViewModel =
        new ViewModelProvider(this).get(ExposureNotificationViewModel.class);

    if (savedInstanceState != null) {
      isTurnOffOpen = savedInstanceState.getBoolean(STATE_TURN_OFF_OPEN, false);
    }
    if (isTurnOffOpen) {
      showTurnOffDialog();
    }

    exposureNotificationViewModel
        .getStateLiveData()
        .observe(this, this::refreshUiTextVisibilityForState);
    exposureNotificationViewModel
        .getEnEnabledLiveData()
        .observe(this, this::refreshUiSwitchForIsEnabled);
    exposureNotificationViewModel
        .getApiErrorLiveEvent()
        .observe(
            this,
            unused -> maybeShowSnackbar(getString(R.string.generic_error_message)));

    exposureNotificationViewModel.registerResolutionForActivityResult(this);

    binding.exposureAboutDetail.setText(
        getString(R.string.exposure_about_detail, getString(R.string.exposure_about_agency)));

    binding.home.setContentDescription(getString(R.string.navigate_up));
    binding.home.setOnClickListener((v) -> onBackPressed());

    binding.exposureAboutSettingsButton.setOnClickListener(v -> settingsAction());

    IntentFilter intentFilter = new IntentFilter();
    intentFilter.addAction(LocationManager.PROVIDERS_CHANGED_ACTION);
    intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
    registerReceiver(refreshBroadcastReceiver, intentFilter);

    /*
     * Attach the edge-case logic as a fragment
     */
    FragmentManager fragmentManager = getSupportFragmentManager();
    if (fragmentManager.findFragmentById(R.id.edge_case_fragment) == null) {
      Fragment aboutEdgeCaseFragment = AboutEdgeCaseFragment
          .newInstance(/* handleApiErrorLiveEvents= */ false, /* handleResolutions= */ false);
      fragmentManager.beginTransaction()
          .replace(R.id.edge_case_fragment, aboutEdgeCaseFragment)
          .commit();
    }

  }

  private final BroadcastReceiver refreshBroadcastReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      refreshUi();
    }
  };

  @Override
  public void onDestroy() {
    super.onDestroy();
    unregisterReceiver(refreshBroadcastReceiver);
  }

  @Override
  public void onResume() {
    super.onResume();
    refreshUi();
  }

  private final OnCheckedChangeListener enSwitchChangeListener =
      new OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
          buttonView.setOnCheckedChangeListener(null);
          // Set the toggle back. It will only toggle to correct state if operation succeeds.
          buttonView.setChecked(!isChecked);
          buttonView.setOnCheckedChangeListener(enSwitchChangeListener);
          if (isChecked) {
            exposureNotificationViewModel.startExposureNotifications();
          } else {
            showTurnOffDialog();
          }
        }
      };

  private void settingsAction() {
    Intent intent = new Intent(ACTION_EXPOSURE_NOTIFICATION_SETTINGS);
    startActivity(intent);
  }

  /**
   * Update UI state after Exposure Notifications client state changes
   */
  private void refreshUi() {
    exposureNotificationViewModel.refreshState();
  }

  /**
   * Make explanatory text (in)visible depending on the Exposure Notifications state.
   * @param state the {@link ExposureNotificationState} of the API
   */
  private void refreshUiTextVisibilityForState(ExposureNotificationState state) {
    LinearLayout exposureAboutDetailLayout = binding.exposureAboutDetailLayout;
    if (state == ExposureNotificationState.ENABLED || state == ExposureNotificationState.DISABLED) {
      exposureAboutDetailLayout.setVisibility(View.VISIBLE);
    } else {
      exposureAboutDetailLayout.setVisibility(View.GONE);
    }
  }

  /**
   * Make sure the on/off switch reflects the live-data
   */
  private void refreshUiSwitchForIsEnabled(boolean isEnabled) {
    // Set OnCheckedChangeListener to null while changing the switch state to avoid unwanted calls
    // to enSwitchChangeListener.
    binding.exposureNotificationToggle.setOnCheckedChangeListener(null);
    binding.exposureNotificationToggle.setChecked(isEnabled);
    binding.exposureNotificationToggle.setOnCheckedChangeListener(enSwitchChangeListener);
  }

  private void maybeShowSnackbar(String message) {
    View rootView = findViewById(android.R.id.content);
    if (rootView != null) {
      Snackbar.make(rootView, message, Snackbar.LENGTH_LONG).show();
    }
  }

  @Override
  public void onSaveInstanceState(@NonNull Bundle bundle) {
    super.onSaveInstanceState(bundle);
    bundle.putBoolean(STATE_TURN_OFF_OPEN, isTurnOffOpen);
  }

  private void showTurnOffDialog() {
    isTurnOffOpen = true;
    new MaterialAlertDialogBuilder(ExposureAboutActivity.this)
        .setTitle(R.string.exposure_turn_off_title)
        .setMessage(R.string.exposure_turn_off_detail)
        .setCancelable(true)
        .setNegativeButton(R.string.btn_cancel, (dialog, i) -> {
          isTurnOffOpen = false;
          dialog.cancel();
        })
        .setPositiveButton(R.string.btn_turn_off,
            (dialog, i) -> {
              isTurnOffOpen = false;
              exposureNotificationViewModel
                  .stopExposureNotifications();
            })
        .setOnCancelListener(d -> isTurnOffOpen = false)
        .show();
  }

}
