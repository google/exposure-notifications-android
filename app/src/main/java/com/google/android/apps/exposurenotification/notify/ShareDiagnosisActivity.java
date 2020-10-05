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

package com.google.android.apps.exposurenotification.notify;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.os.Bundle;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.home.ExposureNotificationViewModel;
import com.google.android.apps.exposurenotification.notify.ShareDiagnosisViewModel.Step;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity;
import com.google.android.apps.exposurenotification.utils.RequestCodes;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import dagger.hilt.android.AndroidEntryPoint;

/**
 * Activity for adding and viewing a positive diagnosis.
 *
 * <p>
 * <ul>
 *   <li>Flow for adding a new positive diagnosis starting with {@link
 *       ShareDiagnosisBeginFragment}
 *   <li>Flow for viewing a previously diagnosis starting with {@link ShareDiagnosisViewFragment}
 * </ul>
 * <p>
 */
@AndroidEntryPoint
public class ShareDiagnosisActivity extends AppCompatActivity {

  private static final String TAG = "ShareDiagnosisActivity";

  static final String SHARE_EXPOSURE_FRAGMENT_TAG =
      "ShareExposureActivity.POSITIVE_TEST_FRAGMENT_TAG";
  static final String ACTIVITY_START_INTENT = "ShareDiagnosisActivity.ACTIVITY_START_INTENT";

  private static final String EXTRA_VIEW_POSITIVE_DIAGNOSIS_ID =
      "ShareExposureActivity.EXTRA_VIEW_POSITIVE_DIAGNOSIS_ID";
  private static final String EXTRA_STEP =
      "ShareExposureActivity.EXTRA_STEP";

  /**
   * Creates an intent for adding a positive diagnosis flow.
   */
  public static Intent newIntentForAddFlow(Context context) {
    return new Intent(context, ShareDiagnosisActivity.class);
  }

  private ShareDiagnosisViewModel viewModel;

  private ExposureNotificationViewModel exposureNotificationViewModel;

  /**
   * Creates an intent for viewing a positive diagnosis flow.
   *
   * @param entity the {@link DiagnosisEntity} to view
   */
  public static Intent newIntentForViewFlow(Context context, DiagnosisEntity entity) {
    Intent intent = new Intent(context, ShareDiagnosisActivity.class);
    intent.putExtra(EXTRA_VIEW_POSITIVE_DIAGNOSIS_ID, entity.getId());
    intent.putExtra(EXTRA_STEP,
        ShareDiagnosisFlowHelper.getMaxStepForDiagnosisEntity(entity).name());
    return intent;
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_share_diagnosis);

    exposureNotificationViewModel =
        new ViewModelProvider(this).get(ExposureNotificationViewModel.class);

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

    viewModel = new ViewModelProvider(this).get(ShareDiagnosisViewModel.class);

    if (viewModel.isCloseOpen()) {
      ShareDiagnosisActivity.showCloseWarningAlertDialog(this, viewModel);
    }

    if (savedInstanceState == null) {
      if (getIntent().hasExtra(EXTRA_VIEW_POSITIVE_DIAGNOSIS_ID)) {
        viewModel
            .setCurrentDiagnosisId(getIntent().getLongExtra(EXTRA_VIEW_POSITIVE_DIAGNOSIS_ID, -1));
        viewModel
            .nextStepIrreversible(Step.valueOf(getIntent().getStringExtra(EXTRA_STEP)));
      } else {
        viewModel.nextStepIrreversible(Step.BEGIN);
      }
    }

    // This line can be removed once the fix to flickering issue
    // https://issuetracker.google.com/166155034 is in the androidx.fragment release.
    FragmentManager.enableNewStateManager(false);

    viewModel.getCurrentStepLiveData()
        .observe(this, step -> {
          Bundle fragmentArguments = new Bundle();
          fragmentArguments.putParcelable(ACTIVITY_START_INTENT, getIntent());
          Fragment fragment = fragmentForStep(step);
          fragment.setArguments(fragmentArguments);

          getSupportFragmentManager()
            .beginTransaction()
            .replace(
                R.id.share_exposure_fragment,
                fragment,
                SHARE_EXPOSURE_FRAGMENT_TAG)
            .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
            .commit();
        });
  }

  @NonNull
  private static Fragment fragmentForStep(Step step) {
    switch (step) {
      case CODE:
        return new ShareDiagnosisCodeFragment();
      case ONSET:
        return new ShareDiagnosisOnsetDateFragment();
      case REVIEW:
        return new ShareDiagnosisReviewFragment();
      case SHARED:
        return new ShareDiagnosisSharedFragment();
      case NOT_SHARED:
        return new ShareDiagnosisNotSharedFragment();
      case TRAVEL_STATUS:
        return new ShareDiagnosisTravelStatusFragment();
      case VIEW:
        return new ShareDiagnosisViewFragment();
      case BEGIN:
      default:
        // We "shouldn't" get here, but start at the beginning if we somehow do.
        return new ShareDiagnosisBeginFragment();
    }
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
    onResolutionComplete(requestCode, resultCode);
  }

  @Override
  public void onTitleChanged(CharSequence title, int color) {
    super.onTitleChanged(title, color);
    // Fire a TYPE_WINDOW_STATE_CHANGED event so that the accessibility service will be notified
    // of window title change.
    getWindow().getDecorView().sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
  }

  @Override
  public void onBackPressed() {
    // Only use android back stack if backStepIfPossible() is false, otherwise the ViewModel will
    // handle it.
    if (!viewModel.backStepIfPossible()) {
      super.onBackPressed();
    }
  }

  /**
   * Shows an alert dialog warning of closing. Closes the activity as the positive action.
   *
   * @param activity the activity to finish if the user chooses to close
   */
  public static void showCloseWarningAlertDialog(Activity activity,
      ShareDiagnosisViewModel shareDiagnosisViewModel) {
    shareDiagnosisViewModel.setCloseOpen(true);
    new MaterialAlertDialogBuilder(activity)
        .setTitle(R.string.share_close_title)
        .setMessage(R.string.share_close_detail)
        .setPositiveButton(R.string.btn_resume_later, (d, w) -> activity.finish())
        .setNegativeButton(R.string.btn_cancel,
            (d, w) -> {
              shareDiagnosisViewModel.setCloseOpen(false);
              d.dismiss();
            })
        .setOnCancelListener((d) -> shareDiagnosisViewModel.setCloseOpen(false))
        .show();
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
}
