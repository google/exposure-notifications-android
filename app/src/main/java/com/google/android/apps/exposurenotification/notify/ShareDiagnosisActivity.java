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
import android.os.Bundle;
import android.view.accessibility.AccessibilityEvent;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.databinding.ActivityShareDiagnosisBinding;
import com.google.android.apps.exposurenotification.edgecases.VerificationFlowEdgeCaseFragment;
import com.google.android.apps.exposurenotification.home.ExposureNotificationViewModel;
import com.google.android.apps.exposurenotification.notify.ShareDiagnosisViewModel.Step;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity;
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

  static final String SHARE_DIAGNOSIS_FRAGMENT_TAG =
      "ShareDiagnosisActivity.POSITIVE_TEST_FRAGMENT_TAG";
  static final String ACTIVITY_START_INTENT = "ShareDiagnosisActivity.ACTIVITY_START_INTENT";

  // Parameters that can be accepted as Intent extras to land in the View flow.
  private static final String EXTRA_DIAGNOSIS_ID = "ShareDiagnosisActivity.EXTRA_DIAGNOSIS_ID";
  private static final String EXTRA_STEP = "ShareDiagnosisActivity.EXTRA_STEP";

  private static final int VIEWFLIPPER_EN_ENABLED = 0;
  private static final int VIEWFLIPPER_EN_DISABLED = 1;

  private ActivityShareDiagnosisBinding binding;
  private ShareDiagnosisViewModel viewModel;
  private ExposureNotificationViewModel exposureNotificationViewModel;

  /**
   * Creates an intent for adding a diagnosis flow.
   */
  public static Intent newIntentForAddFlow(Context context) {
    return new Intent(context, ShareDiagnosisActivity.class);
  }

  /**
   * Creates an intent for viewing a diagnosis flow.
   *
   * @param entity the {@link DiagnosisEntity} to view
   */
  public static Intent newIntentForViewFlow(Context context, DiagnosisEntity entity) {
    Intent intent = new Intent(context, ShareDiagnosisActivity.class);
    intent.putExtra(EXTRA_DIAGNOSIS_ID, entity.getId());
    intent.putExtra(EXTRA_STEP,
        ShareDiagnosisFlowHelper.getMaxStepForDiagnosisEntity(entity, context).name());
    return intent;
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    binding = ActivityShareDiagnosisBinding.inflate(getLayoutInflater());
    setContentView(binding.getRoot());

    exposureNotificationViewModel =
        new ViewModelProvider(this).get(ExposureNotificationViewModel.class);

    viewModel = new ViewModelProvider(this).get(ShareDiagnosisViewModel.class);

    if (viewModel.isCloseOpen()) {
      ShareDiagnosisActivity.showCloseWarningAlertDialog(this, viewModel);
    }

    if (savedInstanceState == null) {
      // This Activity can be created to share a new diagnosis or view an already existing one.
      // Check for Intent extras to decide between the two options. Also, ensure that if there are
      // Intent extras, they are valid and come from a trusted source.
      String sourceComponent = this.getComponentName().getClassName();
      String extraStepName =
          getIntent() != null ? getIntent().getStringExtra(EXTRA_STEP) : null;
      long diagnosisId =
          getIntent() != null ? getIntent().getLongExtra(EXTRA_DIAGNOSIS_ID, -1) : -1;
      if (diagnosisId > -1
          && ShareDiagnosisViewModel.getStepNames().contains(extraStepName)
          && sourceComponent.equals(ShareDiagnosisActivity.class.getName())) {
        viewModel.setCurrentDiagnosisId(diagnosisId);
        viewModel.nextStepIrreversible(Step.valueOf(extraStepName));
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
                SHARE_DIAGNOSIS_FRAGMENT_TAG)
            .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
            .commit();
        });

    /*
     * Make sure to prevent the user from continuing at any step in the diagnosis sharing flow
     * if the EN is disabled.
     */
    exposureNotificationViewModel
        .getEnEnabledLiveData()
        .observe(
            this,
            isEnabled -> {
              if (isEnabled) {
                binding.enEnabledFlipper.setDisplayedChild(VIEWFLIPPER_EN_ENABLED);
              } else {
                binding.enEnabledFlipper.setDisplayedChild(VIEWFLIPPER_EN_DISABLED);
              }
            });

    /*
     * Attach the edge-case logic as a fragment
     */
    FragmentManager fragmentManager = getSupportFragmentManager();
    if (fragmentManager.findFragmentById(R.id.edge_case_fragment) == null) {
      Fragment verificationEdgeCaseFragment = VerificationFlowEdgeCaseFragment
          .newInstance(/* handleApiErrorLiveEvents= */ true, /* handleResolutions= */ true);
      fragmentManager.beginTransaction()
          .replace(R.id.edge_case_fragment, verificationEdgeCaseFragment)
          .commit();
    }
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

}
