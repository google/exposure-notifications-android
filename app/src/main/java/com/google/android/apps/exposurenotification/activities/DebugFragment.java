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

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.activities.utils.ExposureNotificationPermissionHelper;
import com.google.android.apps.exposurenotification.activities.utils.ExposureNotificationPermissionHelper.Callback;
import com.google.android.apps.exposurenotification.common.AppExecutors;
import com.google.android.apps.exposurenotification.nearby.ExposureNotificationBroadcastReceiver;
import com.google.android.apps.exposurenotification.nearby.ExposureNotificationClientWrapper;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.gms.nearby.exposurenotification.ExposureNotificationClient;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.switchmaterial.SwitchMaterial;

/**
 * Fragment for Debug tab on home screen
 */
public class DebugFragment extends Fragment {

  private static final String TAG = "DebugFragment";

  private static final String STATE_DELETE_OPEN = "DebugFragment.STATE_DELETE_OPEN";

  private boolean deleteOpen = false;

  private final OnCheckedChangeListener masterSwitchChangeListener = new OnCheckedChangeListener() {
    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
      buttonView.setOnCheckedChangeListener(null);
      // Set the toggle back. It will only toggle to correct state if operation succeeds.
      buttonView.setChecked(!isChecked);
      if (isChecked) {
        permissionHelper.optInAndStartExposureTracing(requireView());
      } else {
        permissionHelper.optOut(requireView());
      }
    }
  };

  private final Callback permissionHelperCallback = new Callback() {
    @Override
    public void onFailure() {
      View rootView = getView();
      if (rootView == null) {
        return;
      }
      SwitchMaterial masterSwitch = rootView.findViewById(R.id.master_switch);
      masterSwitch.setOnCheckedChangeListener(masterSwitchChangeListener);
    }

    @Override
    public void onOptOutSuccess() {
      refreshUi();
    }

    @Override
    public void onOptInSuccess() {
      refreshUi();
    }
  };

  private final ExposureNotificationPermissionHelper permissionHelper;

  public DebugFragment() {
    permissionHelper = new ExposureNotificationPermissionHelper(this, permissionHelperCallback);
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    permissionHelper.onResolutionComplete(requestCode, resultCode, requireView());
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_debug, parent, false);
  }

  @Override
  public void onViewCreated(View view, Bundle savedInstanceState) {
    view.findViewById(R.id.debug_settings_delete_button).setOnClickListener(v -> deleteAction());
    view.findViewById(R.id.debug_test_exposure_notify_button)
        .setOnClickListener(v -> testExposureNotifyAction());
    view.findViewById(R.id.debug_test_exposure_reset_button)
        .setOnClickListener(v -> testExposureResetAction());

    if (savedInstanceState != null) {
      deleteOpen = savedInstanceState.getBoolean(STATE_DELETE_OPEN, false);
    }
    if (deleteOpen) {
      deleteAction();
    }
  }

  @Override
  public void onSaveInstanceState(Bundle bundle) {
    super.onSaveInstanceState(bundle);
    bundle.putBoolean(STATE_DELETE_OPEN, deleteOpen);
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
    SwitchMaterial masterSwitch = rootView.findViewById(R.id.master_switch);
    masterSwitch.setOnCheckedChangeListener(null);
    masterSwitch.setChecked(currentlyEnabled);
    masterSwitch.setOnCheckedChangeListener(masterSwitchChangeListener);
  }

  /**
   * Shows a dialog to clear both application data and make a call to the Exposure Notifications API
   * to resetAllData().
   */
  private void deleteAction() {
    deleteOpen = true;
    new MaterialAlertDialogBuilder(requireContext())
        .setTitle(R.string.delete_data_confirm_title)
        .setMessage(R.string.delete_data_confirm_message)
        .setPositiveButton(
            R.string.delete_data_delete,
            (d, w) -> {
              deleteOpen = false;
              // Call Exposure Notification API resetAllData()
              ExposureNotificationClientWrapper.get(requireContext())
                  .resetAllData()
                  .addOnSuccessListener(
                      (unused) -> {
                        ActivityManager activityManager = (ActivityManager) requireContext()
                            .getSystemService(Context.ACTIVITY_SERVICE);
                        // Successful, now clear the Application Data.
                        if (activityManager != null && activityManager.clearApplicationUserData()) {
                          // success
                          Log.d(TAG, "Deleted app data.");
                        } else {
                          // failure
                          Log.e(TAG, "Unknown error deleting app data.");
                          View rootView = getView();
                          if (rootView != null) {
                            Snackbar.make(rootView, R.string.delete_data_failure,
                                Snackbar.LENGTH_LONG)
                                .show();
                          }
                        }
                      })
                  .addOnFailureListener(
                      AppExecutors.getLightweightExecutor(),
                      (t) -> {
                        Log.e(TAG, "Error calling resetAllData API", t);
                        View rootView = getView();
                        if (rootView != null) {
                          Snackbar.make(rootView, R.string.delete_data_failure,
                              Snackbar.LENGTH_LONG)
                              .show();
                        }
                      });
            })
        .setNegativeButton(android.R.string.cancel, (d, w) -> deleteOpen = false)
        .setOnDismissListener((d) -> deleteOpen = false)
        .setOnCancelListener((d) -> deleteOpen = false)
        .show();
  }

  /**
   * Generate test exposure events
   */
  private void testExposureNotifyAction() {
    new ExposureNotificationSharedPreferences(requireContext()).setFakeAtRisk(true);
    Intent intent = new Intent(requireContext(), ExposureNotificationBroadcastReceiver.class);
    intent.setAction(ExposureNotificationClient.ACTION_EXPOSURE_STATE_UPDATED);
    requireContext().sendBroadcast(intent);
  }

  /**
   * Reset exposure events for testing purposes
   */
  private void testExposureResetAction() {
    new ExposureNotificationSharedPreferences(requireContext()).setFakeAtRisk(false);
    View rootView = getView();
    if (rootView == null) {
      return;
    }
    Snackbar.make(rootView, R.string.debug_test_exposure_reset_success, Snackbar.LENGTH_LONG)
        .show();
  }

}
