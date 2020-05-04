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
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.nearby.StateUpdatedWorker;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences.OnboardingStatus;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * Main Activity for the Exposure Notification Application.
 * <p>
 * This activity uses fragments to show the various screens of the application.
 * <p>
 * Onboarding is handled by {@link OnboardingStartFragment} and {@link
 * OnboardingPermissionFragment}.
 * <p>
 * The main screen of the application is in @{link HomeFragment}.
 * <p>
 * The fragment stack will always contain HomeFragment as the first item, even when showing the
 * onboarding flow.
 */
public final class ExposureNotificationActivity extends AppCompatActivity {

  private static final String TAG = "ExposureNotifnActivity";

  private static final String SAVED_INSTANCE_STATE_FRAGMENT_KEY =
      "ExposureNotificationActivity.SAVED_INSTANCE_STATE_FRAGMENT_KEY";

  static final String HOME_FRAGMENT_TAG = "ExposureNotificationActivity.HOME_FRAGMENT_TAG";

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.activity_exposure_notification);

    if (savedInstanceState != null) {
      // If this is a configuration change such as rotation, restore the HomeFragment that was
      // previously saved in onSaveInstanceState
      FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
      fragmentTransaction.replace(
          R.id.home_fragment,
          Objects.requireNonNull(getSupportFragmentManager()
              .getFragment(savedInstanceState, SAVED_INSTANCE_STATE_FRAGMENT_KEY)),
          HOME_FRAGMENT_TAG);
      fragmentTransaction.commit();
    } else {
      // This is a fresh launch.
      ExposureNotificationSharedPreferences prefs = new ExposureNotificationSharedPreferences(this);
      if (prefs.getOnboardedState() == OnboardingStatus.UNKNOWN) {
        // If the user has not seen the onboarding flow, show it when the app resumes.
        getSupportFragmentManager().beginTransaction()
            .replace(R.id.home_fragment, new OnboardingStartFragment(), HOME_FRAGMENT_TAG)
            .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
            .commitNow();
      } else {
        // Otherwise transition to the home UI.
        FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
        fragmentTransaction.replace(
            R.id.home_fragment, getHomeFragment(getIntent()), HOME_FRAGMENT_TAG);
        fragmentTransaction.commit();
      }
    }
  }

  private HomeFragment getHomeFragment(@Nullable Intent intent) {
    if (intent != null
        && intent.getAction() != null
        && intent.getAction().equals(StateUpdatedWorker.ACTION_LAUNCH_FROM_EXPOSURE_NOTIFICATION)) {
      // If we're started by the exposure notification we should show the Exposures tab, otherwise
      // show the default.
      return new HomeFragment(HomeFragment.TAB_EXPOSURES);
    }
    return new HomeFragment();
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    Fragment fragment = getSupportFragmentManager().findFragmentByTag(HOME_FRAGMENT_TAG);
    if (fragment != null) {
      fragment.onActivityResult(requestCode, resultCode, data);
    }
  }

  @Override
  public void onResume() {
    super.onResume();
  }

  /**
   * Save the {@link HomeFragment} across rotations or other configuration changes.
   *
   * @param outState passed to onCreate when the app finishes the configuration change.
   */
  @Override
  protected void onSaveInstanceState(@NotNull Bundle outState) {
    super.onSaveInstanceState(outState);
    getSupportFragmentManager()
        .putFragment(
            outState,
            SAVED_INSTANCE_STATE_FRAGMENT_KEY,
            Objects.requireNonNull(
                getSupportFragmentManager().findFragmentByTag(HOME_FRAGMENT_TAG)));
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
  }

  @Override
  public void onRequestPermissionsResult(
      int requestCode, @NotNull String[] permissions, @NotNull int[] grantResults) {
    // Propagate to the fragments.
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
  }
}
