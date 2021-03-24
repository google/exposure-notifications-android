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

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.LocationManager;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.databinding.ActivitySettingsBinding;
import com.google.android.apps.exposurenotification.home.ExposureNotificationViewModel;
import dagger.hilt.android.AndroidEntryPoint;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * Settings Activity for the new UX flow.
 */
@AndroidEntryPoint
public class SettingsActivity extends AppCompatActivity {

  private static final String TAG = "SettingsActivity";
  private static final String SAVED_INSTANCE_STATE_FRAGMENT_KEY =
      "SettingsActivity.SAVED_INSTANCE_STATE_FRAGMENT_KEY";

  public static final String SETTINGS_FRAGMENT_TAG = "SettingsActivity.SETTINGS_FRAGMENT_TAG";

  private ActivitySettingsBinding binding;
  private ExposureNotificationViewModel exposureNotificationViewModel;

  @Nullable
  private BroadcastReceiver refreshStateBroadcastReceiver = null;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    binding = ActivitySettingsBinding.inflate(getLayoutInflater());
    setContentView(binding.getRoot());

    exposureNotificationViewModel =
        new ViewModelProvider(this).get(ExposureNotificationViewModel.class);

    binding.home.setContentDescription(getString(R.string.navigate_up));
    binding.home.setOnClickListener(v -> onBackPressed());

    if (savedInstanceState != null) {
      // Restore the fragment saved in the bundle
      getSupportFragmentManager().beginTransaction()
          .replace(
              R.id.settings_fragment,
              Objects.requireNonNull(
                  getSupportFragmentManager()
                      .getFragment(savedInstanceState, SAVED_INSTANCE_STATE_FRAGMENT_KEY)),
              SETTINGS_FRAGMENT_TAG)
          .commit();
    } else {
      // A new launch
      getSupportFragmentManager().beginTransaction()
          .replace(
              R.id.settings_fragment, SettingsHomeFragment.newInstance(), SETTINGS_FRAGMENT_TAG)
          .commit();
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    refreshState();
    if (refreshStateBroadcastReceiver == null) {
      IntentFilter intentFilter = new IntentFilter();
      intentFilter.addAction(LocationManager.PROVIDERS_CHANGED_ACTION);
      intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);

      refreshStateBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          refreshState();
        }
      };

      registerReceiver(refreshStateBroadcastReceiver, intentFilter);
    }
  }

  @Override
  public void onPause() {
    super.onPause();
    if (refreshStateBroadcastReceiver != null) {
      unregisterReceiver(refreshStateBroadcastReceiver);
      refreshStateBroadcastReceiver = null;
    }
  }

  private void refreshState() {
    exposureNotificationViewModel.refreshState();
  }

  /**
   * Save the {@link SettingsHomeFragment} across rotations or other configuration changes.
   *
   * @param outState passed to onCreate when the app finishes the configuration change.
   */
  @Override
  protected void onSaveInstanceState(@NonNull Bundle outState) {
    super.onSaveInstanceState(outState);
    getSupportFragmentManager()
        .putFragment(
            outState,
            SAVED_INSTANCE_STATE_FRAGMENT_KEY,
            Objects.requireNonNull(
                getSupportFragmentManager().findFragmentByTag(SETTINGS_FRAGMENT_TAG)));
  }
}
