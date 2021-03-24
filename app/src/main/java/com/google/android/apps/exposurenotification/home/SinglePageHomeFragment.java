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


import static com.google.android.apps.exposurenotification.home.ExposureNotificationActivity.ACTION_LAUNCH_FROM_EXPOSURE_NOTIFICATION;
import static com.google.android.apps.exposurenotification.home.ExposureNotificationActivity.HOME_FRAGMENT_TAG;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.google.android.apps.exposurenotification.BuildConfig;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.StringUtils;
import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.android.apps.exposurenotification.databinding.FragmentHomeSinglePageBinding;
import com.google.android.apps.exposurenotification.edgecases.HomePageEdgeCaseFragment;
import com.google.android.apps.exposurenotification.exposure.ExposureChecksDialogFragment;
import com.google.android.apps.exposurenotification.exposure.ExposureHomeViewModel;
import com.google.android.apps.exposurenotification.exposure.PossibleExposureActivity;
import com.google.android.apps.exposurenotification.home.ExposureNotificationViewModel.ExposureNotificationState;
import com.google.android.apps.exposurenotification.notify.DiagnosisEntityAdapter;
import com.google.android.apps.exposurenotification.notify.NotifyHomeViewModel;
import com.google.android.apps.exposurenotification.notify.ShareDiagnosisActivity;
import com.google.android.apps.exposurenotification.proto.UiInteraction.EventType;
import com.google.android.apps.exposurenotification.riskcalculation.ExposureClassification;
import com.google.android.apps.exposurenotification.settings.SettingsActivity;
import com.google.android.apps.exposurenotification.storage.ExposureCheckEntity;
import com.google.android.apps.exposurenotification.utils.UrlUtils;
import dagger.hilt.android.AndroidEntryPoint;
import javax.inject.Inject;

/**
 * Main screen of the application in the re-designed UX flow.
 *
 * <p>SinglePageHomeFragment combines all the tabs in the {@link HomeFragment} into one screen.
 */
@AndroidEntryPoint
public class SinglePageHomeFragment extends Fragment {

  private static final String TAG = "SinglePageHomeFragment";

  private static final int ACTIVE_AND_CHECKING = 0;
  private static final int NOT_CHECKING_FOR_EXPOSURE = 1;
  private static final int POSSIBLE_EXPOSURE = 2;

  private FragmentHomeSinglePageBinding binding;
  private ExposureNotificationViewModel exposureNotificationViewModel;
  private ExposureHomeViewModel exposureHomeViewModel;
  private NotifyHomeViewModel notifyHomeViewModel;
  private FragmentManager childFragmentManager;
  private Animation pulseSmall;
  private Animation pulseMedium;
  private Animation pulseLarge;

  @Inject
  Clock clock;

  /**
   * Creates a {@link SinglePageHomeFragment} instance.
   */
  public static SinglePageHomeFragment newInstance() {
    return new SinglePageHomeFragment();
  }

  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
    binding = FragmentHomeSinglePageBinding.inflate(inflater, parent, false);
    return binding.getRoot();
  }

  public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
    getActivity().setTitle(R.string.app_name);

    childFragmentManager = getChildFragmentManager();

    Intent intent = getActivity().getIntent();
    if (savedInstanceState == null
        && intent != null
        && ACTION_LAUNCH_FROM_EXPOSURE_NOTIFICATION.equals(intent.getAction())) {
      launchPossibleExposureActivity();
    }

    exposureNotificationViewModel =
        new ViewModelProvider(requireActivity()).get(ExposureNotificationViewModel.class);
    exposureHomeViewModel =
        new ViewModelProvider(requireActivity()).get(ExposureHomeViewModel.class);
    notifyHomeViewModel =
        new ViewModelProvider(requireActivity()).get(NotifyHomeViewModel.class);

    binding.settingsButton.setOnClickListener(
        v -> startActivity(new Intent(requireContext(), SettingsActivity.class)));

    binding.shareAppLayout.shareAppButton.setOnClickListener(v -> launchShareApp());

    binding.possibleExposureLayout.possibleExposureCard.setOnClickListener(
        v -> startActivity(new Intent(requireContext(), PossibleExposureActivity.class)));

    binding.shareTestResultLayout.notifyOthersButton.setOnClickListener(
        v -> startActivity(ShareDiagnosisActivity.newIntentForAddFlow(requireContext())));

    binding.noRecentExposureLayout.seeRecentChecksButton.setOnClickListener(
        unused -> new ExposureChecksDialogFragment()
            .show(childFragmentManager, ExposureChecksDialogFragment.TAG));

    binding.noRecentExposureLayout.howEnWorkButtonNoExposure.setOnClickListener(
        unused -> UrlUtils.openUrl(
            requireContext(), getString(R.string.how_exposure_notifications_work_actual_link)));

    DiagnosisEntityAdapter notifyViewAdapter =
        new DiagnosisEntityAdapter(
            diagnosis ->
                startActivity(
                    ShareDiagnosisActivity.newIntentForViewFlow(requireContext(), diagnosis)),
            notifyHomeViewModel);
    final LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
    binding.shareTestResultLayout.notifyRecyclerView.setLayoutManager(layoutManager);
    binding.shareTestResultLayout.notifyRecyclerView.setAdapter(notifyViewAdapter);

    exposureNotificationViewModel
        .getStateWithExposureClassificationLiveData()
        .observe(getViewLifecycleOwner(), this::refreshUiForStateAndClassification);

    notifyHomeViewModel
        .getAllDiagnosisEntityLiveData()
        .observe(
            getViewLifecycleOwner(),
            diagnosisEntities -> {
              binding.shareTestResultLayout.diagnosisHistoryContainer.setVisibility(
                  diagnosisEntities.isEmpty() ? View.GONE : View.VISIBLE);
              binding.shareTestResultLayout.viewFlipperDivider.setVisibility(
                  diagnosisEntities.isEmpty() ? View.GONE : View.VISIBLE);
              notifyViewAdapter.setDiagnosisEntities(diagnosisEntities);
            });

    exposureHomeViewModel
        .getExposureChecksLiveData()
        .observe(
            getViewLifecycleOwner(), exposureChecks -> {
              if (!exposureChecks.isEmpty()) {
                ExposureCheckEntity lastCheck = exposureChecks.get(0);
                String lastCheckedDateTime = StringUtils
                    .epochTimestampToRelativeZonedDateTimeString(
                        lastCheck.getCheckTime().toEpochMilli(), clock.now(), clock.zonedNow(),
                        requireContext());
                binding.noRecentExposureLayout.lastChecked
                    .setText(getString(R.string.recent_check_last_checked, lastCheckedDateTime));
                binding.noRecentExposureLayout.lastChecked.setVisibility(View.VISIBLE);
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
      Fragment childFragment = HomePageEdgeCaseFragment
          .newInstance(/* handleApiErrorLiveEvents= */ true, /* handleResolutions= */ false);
      childFragmentManager.beginTransaction()
          .replace(R.id.home_page_edge_case_fragment, childFragment)
          .commit();
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
    // Remove the previous fragment from the stack if it is there.
    fragment
        .getParentFragmentManager()
        .popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);

    FragmentTransaction fragmentTransaction =
        fragment.getParentFragmentManager().beginTransaction();
    fragmentTransaction
        .replace(R.id.home_fragment, SinglePageHomeFragment.newInstance(), HOME_FRAGMENT_TAG);
    fragmentTransaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
    fragmentTransaction.commit();
  }

  /**
   * Trigger checks for changes in the Exposure Notifications state and refresh UI if needed.
   */
  private void refreshUi() {
    exposureNotificationViewModel.refreshState();
  }

  /**
   * Update UI to match Exposure Notifications state and the current exposure risk classification.
   *
   * @param state                  the {@link ExposureNotificationState} of the API.
   * @param exposureClassification the {@link ExposureClassification} as returned by
   *                               DailySummaryRiskCalculator.
   */
  private void refreshUiForStateAndClassification(
      ExposureNotificationState state, ExposureClassification exposureClassification) {
    if (binding == null) {
      return;
    }

    // Indicates a "revoked" state transition.
    boolean isRevoked = exposureHomeViewModel.getIsExposureClassificationRevoked();

    if (exposureClassification.getClassificationIndex()
        == ExposureClassification.NO_EXPOSURE_CLASSIFICATION_INDEX
        && !isRevoked) {
      // No exposure.
      binding.enOffAndExposureDetectedTitle.setVisibility(View.GONE);

      if (state == ExposureNotificationState.ENABLED) {
        binding.exposuresStatusFlipper.setDisplayedChild(ACTIVE_AND_CHECKING);
        binding.enOffLayout.enOffCard.setVisibility(View.GONE);
        binding.shareTestResultLayout.notifyOthersButton.setEnabled(true);
      } else {
        binding.exposuresStatusFlipper.setDisplayedChild(NOT_CHECKING_FOR_EXPOSURE);
        binding.enOffLayout.enOffCard.setVisibility(View.VISIBLE);
        binding.shareTestResultLayout.notifyOthersButton.setEnabled(false);
      }
    } else {
      // We've got an exposure!
      binding.exposuresStatusFlipper.setDisplayedChild(POSSIBLE_EXPOSURE);
      binding.possibleExposureLayout.possibleExposureDate.setText(
          StringUtils.epochDaysTimestampToMediumUTCDateString(
              exposureClassification.getClassificationDate(),
              getResources().getConfiguration().locale)
      );

      if (state == ExposureNotificationState.ENABLED) {
        binding.enOffLayout.enOffCard.setVisibility(View.GONE);
        binding.enOffAndExposureDetectedTitle.setVisibility(View.GONE);
        binding.shareTestResultLayout.notifyOthersButton.setEnabled(true);
      } else {
        binding.enOffLayout.enOffCard.setVisibility(View.VISIBLE);
        binding.enOffAndExposureDetectedTitle.setVisibility(View.VISIBLE);
        binding.shareTestResultLayout.notifyOthersButton.setEnabled(false);
      }

      // Hide the components we don't want to display when there is a possible exposure reported.
      Fragment exposureChecksFragment = childFragmentManager
          .findFragmentByTag(ExposureChecksDialogFragment.TAG);
      if (exposureChecksFragment != null) {
        DialogFragment exposureChecksDialog = (DialogFragment) exposureChecksFragment;
        exposureChecksDialog.dismiss();
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
   * Open the Possible Exposure activity
   */
  private void launchPossibleExposureActivity() {
    startActivity(new Intent(requireContext(), PossibleExposureActivity.class));
  }

}
