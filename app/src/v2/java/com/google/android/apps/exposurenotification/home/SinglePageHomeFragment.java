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

package com.google.android.apps.exposurenotification.home;


import static android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO;
import static android.view.View.IMPORTANT_FOR_ACCESSIBILITY_YES;
import static com.google.android.apps.exposurenotification.home.BaseActivity.MAIN_FRAGMENT_TAG;
import static com.google.android.apps.exposurenotification.notify.ShareDiagnosisViewModel.EMPTY_DIAGNOSIS;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission;
import androidx.annotation.NonNull;
import androidx.biometric.BiometricPrompt;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.apps.exposurenotification.BuildConfig;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.AuthenticationUtils;
import com.google.android.apps.exposurenotification.common.BiometricUtil;
import com.google.android.apps.exposurenotification.common.BiometricUtil.BiometricAuthenticationCallback;
import com.google.android.apps.exposurenotification.common.KeyboardHelper;
import com.google.android.apps.exposurenotification.common.QuadrupleLiveData;
import com.google.android.apps.exposurenotification.common.StringUtils;
import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.android.apps.exposurenotification.databinding.FragmentHomeSinglePageBinding;
import com.google.android.apps.exposurenotification.edgecases.SingleHomePageEdgeCaseFragment;
import com.google.android.apps.exposurenotification.exposure.ExposureChecksFragment;
import com.google.android.apps.exposurenotification.exposure.ExposureHomeViewModel;
import com.google.android.apps.exposurenotification.exposure.PossibleExposureFragment;
import com.google.android.apps.exposurenotification.home.ExposureNotificationViewModel.ExposureNotificationState;
import com.google.android.apps.exposurenotification.nearby.EnStateUtil;
import com.google.android.apps.exposurenotification.notify.ShareDiagnosisFlowHelper;
import com.google.android.apps.exposurenotification.notify.ShareDiagnosisFragment;
import com.google.android.apps.exposurenotification.notify.SharingHistoryFragment;
import com.google.android.apps.exposurenotification.proto.UiInteraction.EventType;
import com.google.android.apps.exposurenotification.riskcalculation.ExposureClassification;
import com.google.android.apps.exposurenotification.settings.SettingsFragment;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity;
import com.google.android.apps.exposurenotification.storage.ExposureCheckEntity;
import com.google.android.apps.exposurenotification.utils.UrlUtils;
import com.google.common.base.Optional;
import dagger.hilt.android.AndroidEntryPoint;
import javax.inject.Inject;

/**
 * Home screen of the app.
 */
@AndroidEntryPoint
public class SinglePageHomeFragment extends BaseFragment {

  // For children of the top EN Status ViewFlipper
  private static final int EN_ACTIVE = 0;
  private static final int EN_INACTIVE = 1;
  // For children of the main UI ViewFlipper (i.e. UI that comes above the card sections)
  private static final int ACTIVE_AND_CHECKING = 0;
  private static final int EN_IS_OFF = 1;
  private static final int POSSIBLE_EXPOSURE = 2;
  private static final int POSITIVE_DIAGNOSIS = 3;
  private static final int ALLOW_NOTIFICATIONS = 4;
  // For children of the exposure cards ViewFlipper (in the "Check your exposure status" section)
  private static final int NO_EXPOSURE = 0;
  private static final int EXPOSURE = 1;
  private static final String SAVED_INSTANCE_STATE_IS_BIOMETRIC_PROMPT_SHOWING_KEY =
      "is_biometric_prompt_showing";

  private FragmentHomeSinglePageBinding binding;
  private ExposureHomeViewModel exposureHomeViewModel;
  private Animation pulseSmall;
  private Animation pulseMedium;
  private Animation pulseLarge;
  private boolean biometricInProgress;

  private ActivityResultLauncher<String> requestNotificationPermissionLauncher;

  @Inject
  Clock clock;

  /**
   * Creates a {@link SinglePageHomeFragment} instance.
   */
  public static SinglePageHomeFragment newInstance() {
    return new SinglePageHomeFragment();
  }

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent,
      Bundle savedInstanceState) {
    binding = FragmentHomeSinglePageBinding.inflate(inflater, parent, false);
    return binding.getRoot();
  }

  @Override
  public void onSaveInstanceState(@NonNull Bundle savedInstanceState) {
    super.onSaveInstanceState(savedInstanceState);

    savedInstanceState.putBoolean(SAVED_INSTANCE_STATE_IS_BIOMETRIC_PROMPT_SHOWING_KEY,
        biometricInProgress);
  }

  public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    requestNotificationPermissionLauncher = registerForActivityResult(new RequestPermission(),
        isGranted -> {
          if (isGranted) {
            exposureNotificationViewModel.refreshNotificationsEnabledState(requireContext());
          }
        });

    requireActivity().setTitle(StringUtils.getApplicationTitle(requireContext()));

    FragmentManager childFragmentManager = getChildFragmentManager();

    exposureHomeViewModel = new ViewModelProvider(requireActivity())
        .get(ExposureHomeViewModel.class);

    if (ShareDiagnosisFlowHelper.isSmsInterceptEnabled(getContext())) {
      exposureNotificationViewModel.getShouldShowSmsNoticeLiveData()
          .observe(getViewLifecycleOwner(), shouldShowSmsNotice -> {
            if (shouldShowSmsNotice) {
              exposureNotificationViewModel.markInAppSmsInterceptNoticeSeenAsync();
              new SmsNoticeDialogFragment()
                  .show(getChildFragmentManager(), SmsNoticeDialogFragment.TAG);
            }
          });
    }

    // Set up all the actionable items.
    binding.settingsButton.setOnClickListener(
        v -> transitionToFragmentWithBackStack(SettingsFragment.newInstance()));

    binding.allowNotificationsButton.setOnClickListener(
        v -> maybeRequestForNotificationPermission(requestNotificationPermissionLauncher));

    binding.cardEnableNotifications.allowNotificationsButton.setOnClickListener(
        v -> maybeRequestForNotificationPermission(requestNotificationPermissionLauncher));

    binding.possibleExposureLayout.seeDetailsButton.setOnClickListener(
        v -> transitionToFragmentWithBackStack(PossibleExposureFragment.newInstance()));
    binding.possibleExposureLayout.possibleExposureCardTitle.setVisibility(View.VISIBLE);
    binding.possibleExposureLayout.seeDetailsButton.setVisibility(View.VISIBLE);

    binding.shareTestResultCard.notifyOthersButton.setOnClickListener(
        v -> transitionToFragmentWithBackStack(ShareDiagnosisFragment.newInstance()));
    binding.shareTestResultCard.seeHistoryButton.setOnClickListener(
        v -> authenticateUserAndMaybeShowShareHistory());

    binding.noRecentExposureLayout.seeRecentChecksButton.setOnClickListener(
        v -> transitionToFragmentWithBackStack(ExposureChecksFragment.newInstance()));
    binding.noRecentExposureLayout.noRecentExposureTitle.setVisibility(View.VISIBLE);
    binding.noRecentExposureLayout.seeRecentChecksButton.setVisibility(View.VISIBLE);

    binding.secondaryShareTestResultCard.notifyOthersButton.setOnClickListener(
        v -> transitionToFragmentWithBackStack(ShareDiagnosisFragment.newInstance()));
    binding.secondaryShareTestResultCard.cardShareTestResultRootView.setOnClickListener(
        v -> transitionToFragmentWithBackStack(ShareDiagnosisFragment.newInstance()));
    binding.secondaryShareTestResultCard.seeHistoryButton.setOnClickListener(
        v -> authenticateUserAndMaybeShowShareHistory());

    binding.shareAppLayout.setOnClickListener(v -> launchShareApp());

    binding.learnHowEnWorksLayout.howEnWorksCard.setOnClickListener(
        unused -> UrlUtils.openUrl(
            requireView(), getString(R.string.how_exposure_notifications_work_actual_link)));

    binding.noRecentExposureSecondaryLayout.noRecentExposureCard.setOnClickListener(
        v -> transitionToFragmentWithBackStack(ExposureChecksFragment.newInstance()));

    binding.possibleExposureSecondaryLayout.possibleExposureCard.setOnClickListener(
        v -> transitionToFragmentWithBackStack(PossibleExposureFragment.newInstance()));

    binding.seeTestResultsInTurndownCard.cardSeeTestResultsInTurndownRootView.setOnClickListener(
        v -> authenticateUserAndMaybeShowShareHistory());

    // Set additional top padding for card titles where needed.
    int paddingSmallInPixels = (int) getResources().getDimension(R.dimen.padding_small);
    binding.noRecentExposureLayout.contentLayout.setPadding(
        0, /* top= */paddingSmallInPixels, 0, 0);
    binding.possibleExposureLayout.contentLayout.setPadding(
        0, /* top= */paddingSmallInPixels, 0, 0);

    // Listen to all the LiveDatas necessary for updating the UI.
    QuadrupleLiveData<ExposureNotificationState, ExposureClassification, DiagnosisEntity,
        Optional<Boolean>> stateExposureAndLastNonSharedDiagnosisLiveData = QuadrupleLiveData
        .of(exposureNotificationViewModel.getStateLiveData(),
            exposureHomeViewModel.getExposureClassificationLiveData(),
            exposureNotificationViewModel.getLastNotSharedDiagnosisLiveEvent(),
            exposureNotificationViewModel.getAreNotificationsEnabledLiveData());

    stateExposureAndLastNonSharedDiagnosisLiveData
        .observe(getViewLifecycleOwner(), this::refreshUiForLiveDataValues);

    exposureHomeViewModel
        .getExposureChecksLiveData()
        .observe(getViewLifecycleOwner(), exposureChecks -> {
          if (!exposureChecks.isEmpty()) {
            ExposureCheckEntity lastCheck = exposureChecks.get(0);
            String lastCheckedDateTime = StringUtils
                .epochTimestampToRelativeZonedDateTimeString(
                    lastCheck.getCheckTime().toEpochMilli(), clock.now(), clock.zonedNow(),
                    requireContext());
            binding.noRecentExposureLayout.noRecentExposureBody.setText(
                getString(R.string.no_recent_exposure_card_content, lastCheckedDateTime));
            binding.noRecentExposureSecondaryLayout.noRecentExposureBody.setText(
                getString(R.string.no_recent_exposure_card_content, lastCheckedDateTime));
          } else {
            binding.noRecentExposureLayout.noRecentExposureBody.setText(
                getString(R.string.no_recent_exposure_card_content_no_checks));
            binding.noRecentExposureSecondaryLayout.noRecentExposureBody.setText(
                getString(R.string.no_recent_exposure_card_content_no_checks));
          }
        });

    // Add a pulse animation.
    pulseSmall = AnimationUtils.loadAnimation(requireContext(), R.anim.pulsation_small);
    pulseMedium = AnimationUtils.loadAnimation(requireContext(), R.anim.pulsation_medium);
    pulseLarge = AnimationUtils.loadAnimation(requireContext(), R.anim.pulsation_large);
    startPulseAnimation();

    /*
     * Attach the edge-case logic as a fragment
     */
    if (childFragmentManager.findFragmentById(R.id.edge_case_fragment) == null) {
      Fragment childFragment = SingleHomePageEdgeCaseFragment
          .newInstance(/* handleApiErrorLiveEvents= */ true, /* handleResolutions= */ false);
      childFragmentManager.beginTransaction()
          .replace(R.id.single_page_edge_case_fragment, childFragment)
          .commit();
    }

    exposureHomeViewModel.dismissReactivateENAppNotificationAndPendingJob(requireContext());

    if (savedInstanceState != null &&
        savedInstanceState.getBoolean(SAVED_INSTANCE_STATE_IS_BIOMETRIC_PROMPT_SHOWING_KEY,
            false)) {
      biometricInProgress = true;
      BiometricUtil.createBiometricPrompt(this, authenticationCallback);
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    refreshUi();
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    binding = null;
  }

  /**
   * Helper to transition from one fragment to {@link SinglePageHomeFragment}.
   *
   * @param fragment The fragment to transit from.
   */
  public static void transitionToSinglePageHomeFragment(Fragment fragment) {
    // Clear the back stack.
    fragment
        .getParentFragmentManager()
        .popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);

    fragment.getParentFragmentManager().beginTransaction()
        .replace(R.id.main_fragment, SinglePageHomeFragment.newInstance(), MAIN_FRAGMENT_TAG)
        .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
        .commit();
  }

  /**
   * Trigger checks for changes in the availability of the last non-shared diagnosis to refresh UI
   * as needed.
   */
  private void refreshUi() {
    exposureNotificationViewModel.getLastNotSharedDiagnosisIfAny();
    exposureNotificationViewModel.refreshNotificationsEnabledState(requireContext());
  }

  /**
   * Update UI to match Exposure Notifications state, the current exposure risk classification, the
   * last not shared diagnosis (if any) and if notifications are enabled or not.
   *
   * @param state                  the {@link ExposureNotificationState} of the API.
   * @param exposureClassification the {@link ExposureClassification} as returned by
   *                               DailySummaryRiskCalculator.
   * @param lastNotSharedDiagnosis the last not shared {@link DiagnosisEntity}.
   * @param notificationsEnabled true if notifications are enabled, false otherwise.
   */
  private void refreshUiForLiveDataValues(
      ExposureNotificationState state, ExposureClassification exposureClassification,
      DiagnosisEntity lastNotSharedDiagnosis, Optional<Boolean> notificationsEnabled) {
    if (binding == null) {
      return;
    }

    boolean isEnEnabled = state == ExposureNotificationState.ENABLED;
    boolean shouldDisplayTurndownMessage = EnStateUtil.isEnTurndownForRegion(state)
        && EnStateUtil.isAgencyTurndownMessagePresent(requireContext());
    boolean isTurndown = EnStateUtil.isEnTurndown(state);
    boolean noDiagnosis = EMPTY_DIAGNOSIS.equals(lastNotSharedDiagnosis);
    boolean noExposure = (exposureClassification.getClassificationIndex()
        == ExposureClassification.NO_EXPOSURE_CLASSIFICATION_INDEX
        && !exposureHomeViewModel.getIsExposureClassificationRevoked())
        // Ward off an outdated possible exposure information.
        || !exposureHomeViewModel.isActiveExposurePresent();
    // If we don't know the notification permission state. Assume notifications are enabled.
    // Don't show notification edge case until we're sure notifications are not enabled.
    boolean areNotificationsEnabled = notificationsEnabled.or(/* default = */ true);

    binding.enStatusFlipper.setDisplayedChild(isEnEnabled ? EN_ACTIVE : EN_INACTIVE);

    binding.checkExposureStatusSection.setDisplayedChild(noExposure ? NO_EXPOSURE : EXPOSURE);

    // Users can start (or resume) the "Share diagnosis" flow only for a subset of the EN states.
    binding.shareTestResultCard.notifyOthersButton.setEnabled(
        !exposureNotificationViewModel.isStateBlockingSharingFlow(state));
    binding.secondaryShareTestResultCard.notifyOthersButton.setEnabled(
        !exposureNotificationViewModel.isStateBlockingSharingFlow(state));

    if (noDiagnosis) {
      if (noExposure) {
        // 1. No non-shared diagnoses and no exposure.
        binding.enStatusFlipper.setVisibility(View.GONE);
        if (isEnEnabled) {
          if (areNotificationsEnabled) {
            binding.mainUiFlipper.setDisplayedChild(ACTIVE_AND_CHECKING);
          } else {
            binding.enStatusFlipper.setVisibility(View.VISIBLE);
            binding.mainUiFlipper.setDisplayedChild(ALLOW_NOTIFICATIONS);
          }
          binding.enableNotificationsLayout.setVisibility(View.GONE);
        } else {
          binding.mainUiFlipper.setDisplayedChild(EN_IS_OFF);
          binding.enableNotificationsLayout.setVisibility(
              areNotificationsEnabled ? View.GONE : View.VISIBLE);
        }
      } else {
        // 2. No non-shared diagnoses but we've got an exposure!
        binding.enStatusFlipper.setVisibility(View.VISIBLE);
        binding.mainUiFlipper.setDisplayedChild(POSSIBLE_EXPOSURE);
        binding.enableNotificationsLayout.setVisibility(
            areNotificationsEnabled ? View.GONE : View.VISIBLE);
      }
      if (isTurndown) {
        // EN has been turned down!
        binding.seeTestResultsInTurndown.setVisibility(View.VISIBLE);
        binding.healthAuthorityTurndownContent
            .setVisibility(shouldDisplayTurndownMessage ? View.VISIBLE : View.GONE);
        binding.secondaryShareTestResultLayout.setVisibility(View.GONE);
        binding.haveQuestionsSectionTitle.setVisibility(View.GONE);
        binding.learnHowEnWorks.setVisibility(View.GONE);
        binding.helpProtectCommunitySectionTitle.setVisibility(View.GONE);
        binding.shareAppLayout.setVisibility(View.GONE);
        binding.enableNotificationsLayout.setVisibility(View.GONE);
      } else {
        // EN not turned down.
        binding.seeTestResultsInTurndown.setVisibility(View.GONE);
        binding.healthAuthorityTurndownContent.setVisibility(View.GONE);
        binding.secondaryShareTestResultLayout.setVisibility(View.VISIBLE);
        binding.haveQuestionsSectionTitle.setVisibility(View.VISIBLE);
        binding.learnHowEnWorks.setVisibility(View.VISIBLE);
        binding.helpProtectCommunitySectionTitle.setVisibility(View.VISIBLE);
        binding.shareAppLayout.setVisibility(View.VISIBLE);
      }
      // Hide "Check your exposure status" section.
      binding.checkExposureStatusSectionTitle.setVisibility(View.GONE);
      binding.checkExposureStatusSection.setVisibility(View.GONE);
      /*
       * Hide "Continue" button on the secondary "Share your test result" card and, thus, enable
       * this card for starting new flows. Ensure that TalkBack can see that the card is now
       * actionable.
       */
      binding.secondaryShareTestResultCard.notifyOthersButton.setVisibility(View.GONE);
      binding.secondaryShareTestResultCard.cardShareTestResultRootView.setEnabled(true);
      binding.secondaryShareTestResultCard.cardShareTestResultRootView
          .setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    } else {
      if (noExposure) {
        // 3. We have a non-shared diagnosis but no exposure.
        binding.enStatusFlipper.setVisibility(isEnEnabled ? View.VISIBLE : View.GONE);
        binding.mainUiFlipper.setDisplayedChild(isEnEnabled ? POSITIVE_DIAGNOSIS : EN_IS_OFF);
      } else {
        // 4. We have a non-shared diagnosis and we've got an exposure!
        binding.enStatusFlipper.setVisibility(View.VISIBLE);
        binding.mainUiFlipper.setDisplayedChild(
            isEnEnabled ? POSITIVE_DIAGNOSIS : POSSIBLE_EXPOSURE);
      }
      binding.enableNotificationsLayout.setVisibility(
          areNotificationsEnabled ? View.GONE : View.VISIBLE);
      if (isTurndown) {
        // EN has been turned down!
        binding.seeTestResultsInTurndown.setVisibility(View.VISIBLE);
        binding.healthAuthorityTurndownContent
            .setVisibility(shouldDisplayTurndownMessage ? View.VISIBLE : View.GONE);
        binding.secondaryShareTestResultLayout.setVisibility(View.GONE);
        binding.haveQuestionsSectionTitle.setVisibility(View.GONE);
        binding.checkExposureStatusSectionTitle.setVisibility(View.GONE);
        binding.checkExposureStatusSection.setVisibility(View.GONE);
        binding.enableNotificationsLayout.setVisibility(View.GONE);
      } else {
        // EN not turned down.
        binding.seeTestResultsInTurndown.setVisibility(View.GONE);
        binding.healthAuthorityTurndownContent.setVisibility(View.GONE);
        binding.learnHowEnWorks.setVisibility(View.VISIBLE);
        binding.helpProtectCommunitySectionTitle.setVisibility(View.VISIBLE);
        binding.shareAppLayout.setVisibility(View.VISIBLE);
        // Display secondary "Share your test results" card only if EN is disabled.
        binding.secondaryShareTestResultLayout.setVisibility(
            isEnEnabled ? View.GONE : View.VISIBLE);
        // Display "Have questions" only if EN is disabled.
        binding.haveQuestionsSectionTitle.setVisibility(isEnEnabled ? View.GONE : View.VISIBLE);
        // Display "Check your exposure status" only if EN is enabled.
        binding.checkExposureStatusSectionTitle.setVisibility(
            isEnEnabled ? View.VISIBLE : View.GONE);
        binding.checkExposureStatusSection.setVisibility(isEnEnabled ? View.VISIBLE : View.GONE);
        /*
         * Display "Continue" button on the secondary "Share your test result" card and, thus, disable
         * this card. Also ensure that TalkBack will not confuse users by announcing this card as
         * disabled. Instead TalkBack should focus on the card contents before moving on to read out
         * the "See history" and "Continue" buttons.
         */
        binding.secondaryShareTestResultCard.notifyOthersButton.setVisibility(View.VISIBLE);
        binding.secondaryShareTestResultCard.cardShareTestResultRootView.setEnabled(false);
        binding.secondaryShareTestResultCard.cardShareTestResultRootView
            .setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
      }
    }
  }

  /**
   * Start the pulses.
   */
  private void startPulseAnimation() {
    binding.noExposureCheckmark.pulseSmall.startAnimation(pulseSmall);
    binding.noExposureCheckmark.pulseMedium.startAnimation(pulseMedium);
    binding.noExposureCheckmark.pulseLarge.startAnimation(pulseLarge);
  }

  /**
   * Open a "Share this app" prompt
   */
  private void launchShareApp() {
    Intent sendIntent = new Intent();
    sendIntent.setAction(Intent.ACTION_SEND);
    sendIntent.setType("text/plain");
    sendIntent.putExtra(Intent.EXTRA_TEXT, getContext().getString(R.string.settings_share_message,
        String.format(
            getContext().getString(R.string.share_this_app_link),
            BuildConfig.APPLICATION_ID)));
    startActivity(Intent.createChooser(sendIntent, null));
    exposureNotificationViewModel.logUiInteraction(EventType.SHARE_APP_CLICKED);
  }

  /**
   * This method is called for the user to authenticate when the share history button is clicked.
   */
  private void authenticateUserAndMaybeShowShareHistory() {
    if (!AuthenticationUtils.isAuthenticationAvailable(requireBaseActivity())) {
      transitionToSharingHistoryFragment();
      return;
    }

    biometricInProgress = true;
    BiometricPrompt biometricPrompt = BiometricUtil.createBiometricPrompt(this,
        authenticationCallback);
    BiometricUtil.startBiometricPromptAuthentication(requireContext(), biometricPrompt);
  }

  BiometricAuthenticationCallback authenticationCallback = new BiometricAuthenticationCallback() {
    @Override
    public void onAuthenticationSucceeded() {
      transitionToSharingHistoryFragment();
      biometricInProgress = false;
      KeyboardHelper.maybeHideKeyboard(requireActivity(), binding.getRoot());
    }

    @Override
    public void onAuthenticationError(boolean isBiometricErrorSkippable) {
      if (isBiometricErrorSkippable) {
        transitionToSharingHistoryFragment();
      } else {
        biometricInProgress = false;
      }
      KeyboardHelper.maybeHideKeyboard(requireActivity(), binding.getRoot());
    }

    @Override
    public void onAuthenticationFailed() {
      biometricInProgress = false;
      KeyboardHelper.maybeHideKeyboard(requireActivity(), binding.getRoot());
    }
  };

  private void transitionToSharingHistoryFragment() {
    transitionToFragmentWithBackStack(SharingHistoryFragment.newInstance());
  }
}
