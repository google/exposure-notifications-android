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

import static com.google.android.apps.exposurenotification.notify.ShareDiagnosisViewModel.EMPTY_DIAGNOSIS;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.IntentUtil;
import com.google.android.apps.exposurenotification.common.PairLiveData;
import com.google.android.apps.exposurenotification.databinding.FragmentShareDiagnosisBinding;
import com.google.android.apps.exposurenotification.edgecases.VerificationFlowEdgeCaseFragment;
import com.google.android.apps.exposurenotification.home.BaseFragment;
import com.google.android.apps.exposurenotification.notify.ShareDiagnosisViewModel.Step;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity.Shared;
import dagger.hilt.android.AndroidEntryPoint;
import java.util.Objects;

/**
 * Main fragment that handles the Share Diagnosis flow.
 *
 * <p>
 * <ul>
 *   <li>Flow for adding a new positive diagnosis starting with {@link ShareDiagnosisBeginFragment}
 *   <li>Flow for viewing a previously diagnosis starting with {@link ShareDiagnosisViewFragment}
 * </ul>
 * <p>
 */
@AndroidEntryPoint
public class ShareDiagnosisFragment extends BaseFragment {

  // Parameters that can be accepted as fragment arguments to land in the View flow.
  public static final String EXTRA_DIAGNOSIS_ID = "ShareDiagnosisFragment.EXTRA_DIAGNOSIS_ID";
  public static final String EXTRA_STEP = "ShareDiagnosisFragment.EXTRA_STEP";

  static final String NOTIFY_OTHERS_FRAGMENT_TAG =
      "ShareDiagnosisFragment.NOTIFY_OTHERS_FRAGMENT_TAG";

  private static final String SAVED_INSTANCE_STATE_FRAGMENT_KEY =
      "ShareDiagnosisFragment.SAVED_INSTANCE_STATE_FRAGMENT_KEY";

  private static final int VIEWFLIPPER_EN_ENABLED = 0;
  private static final int VIEWFLIPPER_EN_DISABLED = 1;

  private ShareDiagnosisViewModel shareDiagnosisViewModel;
  private FragmentShareDiagnosisBinding binding;
  // Both the shared status of the diagnosis and isEnabled state of the EN service determine if we
  // need to display an edge case when viewing individual diagnoses.
  private PairLiveData<DiagnosisEntity, Boolean> displayEdgeCaseForDiagnosisLiveData;
  private boolean restoredFromSavedInstanceState = false;
  private boolean showCloseWarningDialog = false;

  /**
   * Creates a {@link ShareDiagnosisFragment} instance.
   */
  public static ShareDiagnosisFragment newInstance() {
    return new ShareDiagnosisFragment();
  }

  /**
   * Creates a {@link ShareDiagnosisFragment} to open the Share Diagnosis flow for viewing
   * an already existing diagnosis.
   */
  public static ShareDiagnosisFragment newInstance(Context context, DiagnosisEntity entity) {
    Bundle fragmentArguments = new Bundle();
    fragmentArguments.putLong(EXTRA_DIAGNOSIS_ID, entity.getId());
    fragmentArguments.putString(EXTRA_STEP,
        ShareDiagnosisFlowHelper.getMaxStepForDiagnosisEntity(entity, context).name());

    ShareDiagnosisFragment shareDiagnosisFragment = ShareDiagnosisFragment.newInstance();
    shareDiagnosisFragment.setArguments(fragmentArguments);

    return shareDiagnosisFragment;
  }

  /**
   * Creates a {@link ShareDiagnosisFragment} instance for the Deep Link flow.
   */
  public static ShareDiagnosisFragment newInstance(String codeFromDeepLink) {
    ShareDiagnosisFragment shareDiagnosisFragment = new ShareDiagnosisFragment();
    Bundle args = new Bundle();
    args.putString(EXTRA_CODE_FROM_DEEP_LINK, codeFromDeepLink);
    shareDiagnosisFragment.setArguments(args);
    return shareDiagnosisFragment;
  }

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent,
      @Nullable Bundle savedInstanceState) {
    binding = FragmentShareDiagnosisBinding.inflate(inflater, parent, false);
    return binding.getRoot();
  }

  @CallSuper
  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    shareDiagnosisViewModel = new ViewModelProvider(this).get(ShareDiagnosisViewModel.class);

    if (savedInstanceState != null) {
      // If this is a configuration change such as rotation, restore the fragment that was
      // previously saved in onSaveInstanceState.
      getChildFragmentManager()
          .beginTransaction()
          .replace(
              R.id.notify_others_fragment,
              Objects.requireNonNull(
                  getChildFragmentManager()
                      .getFragment(savedInstanceState, SAVED_INSTANCE_STATE_FRAGMENT_KEY)),
              NOTIFY_OTHERS_FRAGMENT_TAG)
          .commit();
      restoredFromSavedInstanceState = true;
    } else {
      // This fragment can be created (1) from the Sharing History screen, (2) from within the deep
      // link flow, or (3) from the "Share your test result" card. Check for the intent and
      // fragment arguments to decide between these options.
      Bundle fragmentArgs = getArguments();
      boolean isDeepLinkFlow = getArguments() != null
          && getArguments().containsKey(EXTRA_CODE_FROM_DEEP_LINK);

      if (IntentUtil.isValidBundleToOpenShareDiagnosisFlow(fragmentArgs)) {
        // 1. Viewing an already existing flow after coming from the Sharing History screen.
        long diagnosisId = fragmentArgs.getLong(EXTRA_DIAGNOSIS_ID);
        String stepName = fragmentArgs.getString(EXTRA_STEP);
        shareDiagnosisViewModel.setCurrentDiagnosisId(diagnosisId);
        shareDiagnosisViewModel.nextStepIrreversible(Step.valueOf(stepName));
      } else if (isDeepLinkFlow) {
        // 2. Sharing a new diagnosis from within the deep link flow.
        shareDiagnosisViewModel.nextStepIrreversible(Step.BEGIN);
      } else {
        // 3. Starting the flow from the "Share your test result" card.
        exposureNotificationViewModel.getLastNotSharedDiagnosisLiveEvent()
            .observe(this, diagnosis -> {
              if (diagnosis == null || EMPTY_DIAGNOSIS.equals(diagnosis)) {
                // New flow!
                shareDiagnosisViewModel
                    .setCurrentDiagnosisId(ShareDiagnosisViewModel.NO_EXISTING_ID);
                shareDiagnosisViewModel.nextStepIrreversible(Step.BEGIN);
              } else {
                // Resuming an already existing flow automatically.
                shareDiagnosisViewModel.setResumingAndNotConfirmed(true);
                shareDiagnosisViewModel.setCurrentDiagnosisId(diagnosis.getId());
                shareDiagnosisViewModel.setVerifiedCodeForCodeStep(diagnosis.getVerificationCode());
                shareDiagnosisViewModel.nextStepIrreversible(Step.CODE);
              }
            });
        exposureNotificationViewModel.getLastNotSharedDiagnosisIfAny();
      }
    }

    shareDiagnosisViewModel.getCurrentStepLiveData()
        .observe(getViewLifecycleOwner(), step -> {
          // If triggered on configuration changes (e.g. device rotations), do not attempt to
          // replace fragment as the desired fragment has been set already.
          if (!restoredFromSavedInstanceState) {
            getChildFragmentManager()
                .beginTransaction()
                .replace(
                    R.id.notify_others_fragment,
                    fragmentForStep(step),
                    NOTIFY_OTHERS_FRAGMENT_TAG)
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                .commit();
          } else {
            restoredFromSavedInstanceState = false;
          }
        });

    /*
     * Make sure to prevent users from continuing at any step in the diagnosis sharing flow if the
     * EN is disabled as they won't be able to share their keys in that case. But never prevent
     * users from viewing diagnoses they've already shared.
     */
    displayEdgeCaseForDiagnosisLiveData = PairLiveData.of(
        shareDiagnosisViewModel.getCurrentDiagnosisLiveData(),
        exposureNotificationViewModel.getEnEnabledLiveData());
    displayEdgeCaseForDiagnosisLiveData
        .observe(this,
            (currentDiagnosis, isEnabled) -> {
              if (isEnabled || DiagnosisEntityHelper.hasBeenShared(currentDiagnosis)) {
                binding.enEnabledFlipper.setDisplayedChild(VIEWFLIPPER_EN_ENABLED);
              } else {
                binding.enEnabledFlipper.setDisplayedChild(VIEWFLIPPER_EN_DISABLED);
              }
              showCloseWarningDialog = DiagnosisEntityHelper.hasVerified(currentDiagnosis)
                  && currentDiagnosis.getSharedStatus().equals(Shared.NOT_ATTEMPTED);
            });

    /*
     * Attach the edge-case logic as a fragment
     */
    FragmentManager fragmentManager = getChildFragmentManager();
    if (fragmentManager.findFragmentById(R.id.edge_case_fragment) == null) {
      Fragment verificationEdgeCaseFragment = VerificationFlowEdgeCaseFragment
          .newInstance(/* handleApiErrorLiveEvents= */ true, /* handleResolutions= */ true);
      fragmentManager.beginTransaction()
          .replace(R.id.edge_case_fragment, verificationEdgeCaseFragment)
          .commit();
    }
  }

  @Override
  public boolean onBackPressed() {
    if (binding.enEnabledFlipper.getDisplayedChild() == VIEWFLIPPER_EN_DISABLED
        || !shareDiagnosisViewModel.backStepIfPossible()) {
      Fragment shareDiagnosisFragment = getChildFragmentManager()
          .findFragmentById(R.id.notify_others_fragment);
      if (showCloseWarningDialog && shareDiagnosisFragment != null) {
        ((ShareDiagnosisBaseFragment) shareDiagnosisFragment).showCloseWarningAlertDialog();
      } else {
        if (!getParentFragmentManager().popBackStackImmediate()) {
          requireActivity().finish();
        }
      }
    }
    return true;
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    binding = null;
  }

  @Override
  public void onSaveInstanceState(@NonNull Bundle outState) {
    super.onSaveInstanceState(outState);
    getChildFragmentManager()
        .putFragment(
            outState,
            SAVED_INSTANCE_STATE_FRAGMENT_KEY,
            Objects.requireNonNull(
                getChildFragmentManager().findFragmentByTag(NOTIFY_OTHERS_FRAGMENT_TAG)));
  }

  @NonNull
  private static ShareDiagnosisBaseFragment fragmentForStep(Step step) {
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
}
