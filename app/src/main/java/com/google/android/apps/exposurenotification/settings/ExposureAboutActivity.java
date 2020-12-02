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

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender.SendIntentException;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ViewSwitcher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.StorageManagementHelper;
import com.google.android.apps.exposurenotification.home.ExposureNotificationViewModel;
import com.google.android.apps.exposurenotification.home.ExposureNotificationViewModel.ExposureNotificationState;
import com.google.android.apps.exposurenotification.proto.UiInteraction.EventType;
import com.google.android.apps.exposurenotification.utils.RequestCodes;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.switchmaterial.SwitchMaterial;
import dagger.hilt.android.AndroidEntryPoint;

/**
 * Activity for information about exposure notifications in general.
 */
@AndroidEntryPoint
public class ExposureAboutActivity extends AppCompatActivity {

  private static final String TAG = "ExposureAboutActivity";

  private static final String STATE_TURN_OFF_OPEN = "STATE_TURN_OFF_OPEN";

  private ExposureNotificationViewModel exposureNotificationViewModel;

  boolean isTurnOffOpen = false;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.activity_exposure_about);

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
        .observe(this, this::refreshUiForState);
    exposureNotificationViewModel
        .getApiErrorLiveEvent()
        .observe(
            this,
            unused -> maybeShowSnackbar(getString(R.string.generic_error_message)));
    exposureNotificationViewModel
        .getResolutionRequiredLiveEvent()
        .observe(
            this,
            apiException -> {
              try {
                Log.d(TAG, "startResolutionForResult");
                apiException
                    .getStatus()
                    .startResolutionForResult(
                        this, RequestCodes.REQUEST_CODE_START_EXPOSURE_NOTIFICATION);
              } catch (SendIntentException e) {
                Log.w(TAG, "Error calling startResolutionForResult", apiException);
              }
            });

    TextView exposureAboutDetail = findViewById(R.id.exposure_about_detail);
    exposureAboutDetail.setText(
        getString(R.string.exposure_about_detail, getString(R.string.exposure_about_agency)));

    View upButton = findViewById(android.R.id.home);
    upButton.setContentDescription(getString(R.string.navigate_up));
    upButton.setOnClickListener((v) -> onBackPressed());

    Button settingsButton = findViewById(R.id.exposure_about_settings_button);
    settingsButton.setOnClickListener(v -> settingsAction());

    Button errorSettingsButton = findViewById(R.id.exposure_about_device_settings);
    errorSettingsButton.setOnClickListener(v -> settingsAction());

    Button manageStorageButton = findViewById(R.id.exposure_about_manage_storage);
    manageStorageButton
        .setOnClickListener(v -> StorageManagementHelper.launchStorageManagement(this));

    IntentFilter intentFilter = new IntentFilter();
    intentFilter.addAction(LocationManager.PROVIDERS_CHANGED_ACTION);
    intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
    registerReceiver(refreshBroadcastReceiver, intentFilter);
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
   * Update UI to match Exposure Notifications state.
   *
   * @param state the {@link ExposureNotificationState} of the API
   */
  private void refreshUiForState(ExposureNotificationState state) {
    SwitchMaterial exposureNotificationToggle = findViewById(R.id.exposure_notification_toggle);
    /*
     * Set OnCheckedChangeListener to null to while changing switch state to avoid unwanted calls
     * to enSwitchChangeListener.
     */
    exposureNotificationToggle.setOnCheckedChangeListener(null);
    exposureNotificationToggle
        .setChecked(exposureNotificationViewModel.getEnEnabledLiveData().getValue());
    exposureNotificationToggle.setOnCheckedChangeListener(enSwitchChangeListener);

    ViewSwitcher errorSwitcher = findViewById(R.id.exposure_about_errors);
    View errorDivider = findViewById(R.id.exposure_about_error_divider);
    TextView errorBleLocText = findViewById(R.id.error_loc_ble_text);
    LinearLayout exposureAboutDetailLayout = findViewById(R.id.exposure_about_detail_layout);

    switch (state) {
      case ENABLED:
        errorSwitcher.setVisibility(View.GONE);
        errorDivider.setVisibility(View.GONE);
        exposureAboutDetailLayout.setVisibility(View.VISIBLE);
        break;
      case PAUSED_LOCATION_BLE:
        errorSwitcher.setDisplayedChild(0);
        errorDivider.setVisibility(View.VISIBLE);
        errorSwitcher.setVisibility(View.VISIBLE);
        errorBleLocText.setText(R.string.location_ble_off_warning);
        exposureAboutDetailLayout.setVisibility(View.GONE);
        exposureNotificationViewModel.logUiInteraction(EventType.LOCATION_PERMISSION_WARNING_SHOWN);
        exposureNotificationViewModel.logUiInteraction(EventType.BLUETOOTH_DISABLED_WARNING_SHOWN);
        break;
      case PAUSED_BLE:
        errorSwitcher.setDisplayedChild(0);
        errorDivider.setVisibility(View.VISIBLE);
        errorSwitcher.setVisibility(View.VISIBLE);
        errorBleLocText.setText(R.string.ble_off_warning);
        exposureAboutDetailLayout.setVisibility(View.GONE);
        exposureNotificationViewModel.logUiInteraction(EventType.BLUETOOTH_DISABLED_WARNING_SHOWN);
        break;
      case PAUSED_LOCATION:
        errorSwitcher.setDisplayedChild(0);
        errorDivider.setVisibility(View.VISIBLE);
        errorSwitcher.setVisibility(View.VISIBLE);
        errorBleLocText.setText(R.string.location_off_warning);
        exposureAboutDetailLayout.setVisibility(View.GONE);
        exposureNotificationViewModel.logUiInteraction(EventType.LOCATION_PERMISSION_WARNING_SHOWN);
        break;
      case STORAGE_LOW:
        errorSwitcher.setDisplayedChild(1);
        errorDivider.setVisibility(View.VISIBLE);
        errorSwitcher.setVisibility(View.VISIBLE);
        exposureAboutDetailLayout.setVisibility(View.GONE);
        Button manageStorageButton = findViewById(R.id.exposure_about_manage_storage);
        manageStorageButton.setVisibility(
            StorageManagementHelper.isStorageManagementAvailable(this)
                ? Button.VISIBLE : Button.GONE);
        exposureNotificationViewModel.logUiInteraction(EventType.LOW_STORAGE_WARNING_SHOWN);
        break;
      case DISABLED:
      default:
        errorDivider.setVisibility(View.GONE);
        errorSwitcher.setVisibility(View.GONE);
        exposureAboutDetailLayout.setVisibility(View.VISIBLE);
        break;
    }
  }

  private void maybeShowSnackbar(String message) {
    View rootView = findViewById(android.R.id.content);
    if (rootView != null) {
      Snackbar.make(rootView, message, Snackbar.LENGTH_LONG).show();
    }
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    onResolutionComplete(requestCode, resultCode);
  }

  @Override
  public void onSaveInstanceState(@NonNull Bundle bundle) {
    super.onSaveInstanceState(bundle);
    bundle.putBoolean(STATE_TURN_OFF_OPEN, isTurnOffOpen);
  }

  /**
   * Called when opt-in resolution is completed by user.
   *
   * <p>Modeled after {@code Activity#onActivityResult} as that's how the API sends callback to
   * apps.
   */
  public void onResolutionComplete(int requestCode, int resultCode) {
    if (requestCode != RequestCodes.REQUEST_CODE_START_EXPOSURE_NOTIFICATION) {
      return;
    }
    if (resultCode == Activity.RESULT_OK) {
      exposureNotificationViewModel.startResolutionResultOk();
    } else {
      exposureNotificationViewModel.startResolutionResultNotOk();
    }
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
