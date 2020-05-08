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
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.activities.utils.ExposureNotificationPermissionHelper;
import com.google.android.apps.exposurenotification.activities.utils.ExposureNotificationPermissionHelper.Callback;
import com.google.android.apps.exposurenotification.common.AppExecutors;
import com.google.android.apps.exposurenotification.nearby.ExposureNotificationBroadcastReceiver;
import com.google.android.apps.exposurenotification.nearby.ExposureNotificationClientWrapper;
import com.google.android.apps.exposurenotification.nearby.ProvideDiagnosisKeysWorker;
import com.google.android.apps.exposurenotification.storage.ExposureViewModel;
import com.google.android.apps.exposurenotification.storage.TokenEntity;
import com.google.android.apps.exposurenotification.storage.TokenViewModel;
import com.google.android.gms.nearby.exposurenotification.ExposureNotificationClient;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import java.util.List;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

/**
 * Fragment for Debug tab on home screen
 */
public class DebugFragment extends Fragment {

  private static final String TAG = "DebugFragment";

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
    view.findViewById(R.id.debug_test_exposure_notify_button)
        .setOnClickListener(v -> testExposureNotifyAction());
    view.findViewById(R.id.debug_exposure_reset_button)
        .setOnClickListener(v -> exposureResetAction());
    view.findViewById(R.id.debug_provide_keys_button)
        .setOnClickListener(v -> provideKeysAction());
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
   * Generate test exposure events
   */
  private void testExposureNotifyAction() {
    TokenViewModel tokenViewModel =
        new ViewModelProvider(this, getDefaultViewModelProviderFactory())
            .get(TokenViewModel.class);
    // First inserts/updates the hard coded tokens.
    Futures.addCallback(Futures.allAsList(
        tokenViewModel.upsertTokenEntityAsync(
            TokenEntity.create(ExposureNotificationClientWrapper.FAKE_TOKEN_1, false)),
        tokenViewModel.upsertTokenEntityAsync(
            TokenEntity.create(ExposureNotificationClientWrapper.FAKE_TOKEN_2, false)),
        tokenViewModel.upsertTokenEntityAsync(
            TokenEntity.create(ExposureNotificationClientWrapper.FAKE_TOKEN_3, false))),
        new FutureCallback<List<Void>>() {
          @Override
          public void onSuccess(@NullableDecl List<Void> result) {
            // Now broadcasts them to the worker.
            Intent intent1 = new Intent(requireContext(),
                ExposureNotificationBroadcastReceiver.class);
            intent1.setAction(ExposureNotificationClient.ACTION_EXPOSURE_STATE_UPDATED);
            intent1.putExtra(ExposureNotificationClient.EXTRA_TOKEN,
                ExposureNotificationClientWrapper.FAKE_TOKEN_1);
            requireContext().sendBroadcast(intent1);

            Intent intent2 = new Intent(requireContext(),
                ExposureNotificationBroadcastReceiver.class);
            intent2.setAction(ExposureNotificationClient.ACTION_EXPOSURE_STATE_UPDATED);
            intent2.putExtra(ExposureNotificationClient.EXTRA_TOKEN,
                ExposureNotificationClientWrapper.FAKE_TOKEN_2);
            requireContext().sendBroadcast(intent2);

            Intent intent3 = new Intent(requireContext(),
                ExposureNotificationBroadcastReceiver.class);
            intent3.setAction(ExposureNotificationClient.ACTION_EXPOSURE_STATE_UPDATED);
            intent3.putExtra(ExposureNotificationClient.EXTRA_TOKEN,
                ExposureNotificationClientWrapper.FAKE_TOKEN_3);
            requireContext().sendBroadcast(intent3);
          }

          @Override
          public void onFailure(Throwable t) {
            View rootView = getView();
            if (rootView == null) {
              return;
            }
            Snackbar
                .make(rootView, R.string.generic_error_message, Snackbar.LENGTH_LONG)
                .show();
            Log.w(TAG, "Failed testExposureNotifyAction", t);
          }
        }, AppExecutors.getBackgroundExecutor());
  }

  /**
   * Reset exposure events for testing purposes
   */
  private void exposureResetAction() {
    TokenViewModel tokenViewModel =
        new ViewModelProvider(this, getDefaultViewModelProviderFactory())
            .get(TokenViewModel.class);
    ExposureViewModel exposureViewModel =
        new ViewModelProvider(this, getDefaultViewModelProviderFactory())
            .get(ExposureViewModel.class);

    Futures.addCallback(
        Futures.allAsList(
            tokenViewModel.deleteTokenEntitiesAsync(Lists
                .newArrayList(
                    ExposureNotificationClientWrapper.FAKE_TOKEN_1,
                    ExposureNotificationClientWrapper.FAKE_TOKEN_2,
                    ExposureNotificationClientWrapper.FAKE_TOKEN_3)),
            exposureViewModel.deleteAllExposureEntitiesAsync()),
        new FutureCallback<List<Void>>() {
          @Override
          public void onSuccess(@NullableDecl List<Void> result) {
            View rootView = getView();
            if (rootView == null) {
              return;
            }
            Snackbar
                .make(rootView, R.string.debug_test_exposure_reset_success, Snackbar.LENGTH_LONG)
                .show();
          }

          @Override
          public void onFailure(Throwable t) {
            View rootView = getView();
            if (rootView == null) {
              return;
            }
            Snackbar.make(rootView, R.string.generic_error_message, Snackbar.LENGTH_LONG).show();
            Log.w(TAG, "Failed testExposureResetAction", t);
          }
        }, AppExecutors.getBackgroundExecutor());
  }

  /**
   * Triggers a one off provide keys job.
   */
  private void provideKeysAction() {
    WorkManager workManager = WorkManager.getInstance(requireContext());
    workManager.enqueue(new OneTimeWorkRequest.Builder(ProvideDiagnosisKeysWorker.class).build());
    View rootView = getView();
    if (rootView == null) {
      return;
    }
    Snackbar.make(rootView, R.string.debug_provide_keys_enqueued, Snackbar.LENGTH_LONG).show();
  }

}
