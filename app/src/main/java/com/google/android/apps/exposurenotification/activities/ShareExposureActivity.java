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

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.storage.PositiveDiagnosisEntity;
import java.util.Objects;

/**
 * Activity for adding and viewing a positive diagnosis.
 *
 * <p><ul>
 * <li> Flow for adding a new positive diagnosis starting with {@link ShareExposureBeginFragment}
 * <li> Flow for viewing a previously diagnosis starting with {@link ShareExposureViewFragment}
 * </ul><p>
 */
public class ShareExposureActivity extends AppCompatActivity {

  static final String SAVED_INSTANCE_STATE_FRAGMENT_KEY =
      "ShareExposureActivity.SAVED_INSTANCE_STATE_FRAGMENT_KEY";
  static final String SHARE_EXPOSURE_FRAGMENT_TAG =
      "ShareExposureActivity.POSITIVE_TEST_FRAGMENT_TAG";

  private static final String EXTRA_VIEW_POSITIVE_DIAGNOSIS_ID =
      "ShareExposureActivity.EXTRA_VIEW_POSITIVE_DIAGNOSIS_ID";

  /**
   * Creates an intent for adding a positive diagnosis flow.
   */
  public static Intent newIntentForAddFlow(Context context) {
    return new Intent(context, ShareExposureActivity.class);
  }

  /**
   * Creates an intent for viewing a positive diagnosis flow.
   *
   * @param entity the {@link PositiveDiagnosisEntity} to view
   */
  public static Intent newIntentForViewFlow(Context context, PositiveDiagnosisEntity entity) {
    Intent intent = new Intent(context, ShareExposureActivity.class);
    intent.putExtra(EXTRA_VIEW_POSITIVE_DIAGNOSIS_ID, entity.getId());
    return intent;
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_share_exposure);

    if (savedInstanceState != null) {
      // If this is a configuration change such as rotation, restore the fragment that was
      // previously saved in onSaveInstanceState
      FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
      fragmentTransaction.replace(
          R.id.share_exposure_fragment,
          Objects.requireNonNull(getSupportFragmentManager()
              .getFragment(savedInstanceState, SAVED_INSTANCE_STATE_FRAGMENT_KEY)),
          SHARE_EXPOSURE_FRAGMENT_TAG);
      fragmentTransaction.commit();
    } else {
      // This is a fresh launch.
      if (getIntent().hasExtra(EXTRA_VIEW_POSITIVE_DIAGNOSIS_ID)) {
        // Has extra so start view flow.
        FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
        fragmentTransaction
            .replace(R.id.share_exposure_fragment, ShareExposureViewFragment.newInstance(
                getIntent().getLongExtra(EXTRA_VIEW_POSITIVE_DIAGNOSIS_ID, -1)),
                SHARE_EXPOSURE_FRAGMENT_TAG);
        fragmentTransaction.commit();
      } else {
        // Start the add flow.
        FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
        fragmentTransaction
            .replace(R.id.share_exposure_fragment, new ShareExposureBeginFragment(),
                SHARE_EXPOSURE_FRAGMENT_TAG);
        fragmentTransaction.commit();
      }
    }
  }

  /**
   * Save the {@link Fragment} across rotations or other configuration changes.
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
                getSupportFragmentManager().findFragmentByTag(SHARE_EXPOSURE_FRAGMENT_TAG)));
  }

  @Override
  public void onRequestPermissionsResult(
      int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    // Propagate to the fragments.
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.share_exposure_fragment);
    if (fragment != null) {
      fragment.onActivityResult(requestCode, resultCode, data);
    }
  }

}
