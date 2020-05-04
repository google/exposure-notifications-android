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
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * Activity for sharing a new confirmed diagnosis with others
 */
public class ShareExposureActivity extends AppCompatActivity {

  private static final String TAG = "ShareExposureActivity";

  private static final String SAVED_INSTANCE_STATE_FRAGMENT_KEY =
      "ShareExposureActivity.SAVED_INSTANCE_STATE_FRAGMENT_KEY";

  static final String SHARE_EXPOSURE_FRAGMENT_TAG = "ShareExposureActivity.HOME_FRAGMENT_TAG";

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.activity_share_exposure);

    if (savedInstanceState != null) {
      // If this is a configuration change such as rotation, restore the ShareFragment that was
      // previously saved in onSaveInstanceState
      FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
      fragmentTransaction.replace(
          R.id.share_exposure_fragment,
          Objects.requireNonNull(getSupportFragmentManager()
              .getFragment(savedInstanceState, SAVED_INSTANCE_STATE_FRAGMENT_KEY)),
          SHARE_EXPOSURE_FRAGMENT_TAG);
      fragmentTransaction.commit();
    } else {
      // This is a fresh launch, so create a new ShareFragment and display it.
      FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
      fragmentTransaction.replace(R.id.share_exposure_fragment, new ShareExposureStartFragment(),
          SHARE_EXPOSURE_FRAGMENT_TAG);
      fragmentTransaction.commit();
    }
  }

  @Override
  public void onResume() {
    super.onResume();
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    Fragment fragment = getSupportFragmentManager().findFragmentByTag(SHARE_EXPOSURE_FRAGMENT_TAG);
    if (fragment != null) {
      fragment.onActivityResult(requestCode, resultCode, data);
    }
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
                getSupportFragmentManager().findFragmentByTag(SHARE_EXPOSURE_FRAGMENT_TAG)));
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
