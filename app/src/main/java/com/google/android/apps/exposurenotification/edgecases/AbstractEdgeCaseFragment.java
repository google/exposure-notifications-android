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

package com.google.android.apps.exposurenotification.edgecases;

import static com.google.android.gms.nearby.exposurenotification.ExposureNotificationClient.ACTION_EXPOSURE_NOTIFICATION_SETTINGS;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import androidx.annotation.NonNull;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.SnackbarUtil;
import com.google.android.apps.exposurenotification.common.StorageManagementHelper;
import com.google.android.apps.exposurenotification.common.logging.Logger;
import com.google.android.apps.exposurenotification.home.BaseFragment;
import com.google.android.apps.exposurenotification.home.ExposureNotificationViewModel.ExposureNotificationState;
import com.google.android.apps.exposurenotification.proto.UiInteraction.EventType;
import com.google.android.apps.exposurenotification.utils.UrlUtils;

/**
 * Abstract superclass fragment that encapsulates all lifecycle and event-management logic required
 * for edge-case handling.
 */
public abstract class AbstractEdgeCaseFragment extends BaseFragment {

  private static final Logger logger = Logger.getLogger("MainEdgeCaseFragment");

  // Provide arguments to the fragment via static "newInstance" constructor pattern
  private static final String KEY_HANDLE_API_ERROR_LIVE_EVENTS = "handleApiErrorLiveEvents";
  private static final String KEY_HANDLE_RESOLUTIONS = "handleResolutions";
  private static final String NO_ARG_CONSTRUCTOR_WARNING =
      "Constructing EdgeCaseFragment with non-argument constructor might lead to"
          + " unexpected behavior, consider using  EdgeCaseFragment.newInstance(...) instead.";

  // Both true by default, see constructor documentation
  public boolean isHandleApiErrorLiveEvents() {
    if (getArguments() == null) {
      logger.w(NO_ARG_CONSTRUCTOR_WARNING);
      return true;
    }
    return getArguments().getBoolean(KEY_HANDLE_API_ERROR_LIVE_EVENTS, true);
  }

  public boolean isHandleResolutions() {
    if (getArguments() == null) {
      logger.w(NO_ARG_CONSTRUCTOR_WARNING);
      return true;
    }
    return getArguments().getBoolean(KEY_HANDLE_RESOLUTIONS, true);
  }

  /**
   * @param handleApiErrorLiveEvents If true this fragment observes and handles
   *     exposureNotificationViewModel.getApiErrorLiveEvent().
   *     In many cases the edge-case fragment is the only component that makes calls to EN_module,
   *     and thus the only one that needs to handle ApiErrors.
   *     If the embedding activity/fragment wants to observe getApiErrorLiveEvent() itself,
   *     this can be set to false.
   *
   * @param handleResolutions If true this fragment observes and handles
   *     exposureNotificationViewModel.getResolutionRequiredLiveEvent() (e.g. when users pressed
   *     the "turn on EN" button)
   *     <p>In most cases, this edge-case fragment is the only component in another
   *     activity/fragment that needs to handle resolutions. To avoid dependencies on additional
   *     resolution-handling code in activities/fragments that embed this fragment (and make it
   *     harder for developers to forget to include this logic), we handle resolutions here by
   *     default.
   *     <p>In cases where the embedding activity/fragment wants to handle resolutions itself and
   *     the edge-case fragment is not always present (e.g. Onboarding), automatic resolution
   *     handling can be turned off by setting this to false.
   */
  protected static AbstractEdgeCaseFragment newInstance(
      AbstractEdgeCaseFragment fragment,
      boolean handleApiErrorLiveEvents,
      boolean handleResolutions) {
    Bundle args = new Bundle();
    args.putBoolean(KEY_HANDLE_API_ERROR_LIVE_EVENTS, handleApiErrorLiveEvents);
    args.putBoolean(KEY_HANDLE_RESOLUTIONS, handleResolutions);
    fragment.setArguments(args);
    return fragment;
  }

  /**
   * Abstract method that controls the layout of edge-cases (how text and button actions change
   * depending on the current {@link ExposureNotificationState}). Filled in by subclasses.
   *
   * @param rootView      a View layout for a given Fragment
   * @param containerView a View layout of a given Fragment's parent (i.e. of the hosting Activity
   *                      or Fragment)
   * @param state         an ExposureNotificationState object for the current state of EN service
   * @param isInFlight    a flag to indicate if there is an API request in flight
   */
  protected abstract void fillUIContent(View rootView, View containerView,
      ExposureNotificationState state, boolean isInFlight);

  // Helpers to configure button actions and behaviors (from within the subclass)

  protected void configureButtonForStartEn(Button button, boolean isInFlight) {
    // Disable or enable this button depending on whether there is an API change in flight.
    button.setVisibility(Button.VISIBLE);
    button.setEnabled(!isInFlight);

    /*
     * This call requires resolution handling, so you should either construct this fragment with
     * handleResolutions == true or manually handle getResolutionRequiredLiveEvent() in
     * the embedding fragment/activity
     */
    button.setOnClickListener(v -> exposureNotificationViewModel.startExposureNotifications());
  }

  protected void configureButtonForOpenSettings(Button button) {
    button.setVisibility(Button.VISIBLE);
    button.setOnClickListener(unused -> {
      Intent intent = new Intent(ACTION_EXPOSURE_NOTIFICATION_SETTINGS);
      startActivity(intent);
    });
  }

  protected void configureButtonForManageStorage(Button button) {
    button.setVisibility(
        StorageManagementHelper.isStorageManagementAvailable(getContext())
            ? Button.VISIBLE : Button.GONE);
    button.setOnClickListener(
        unused -> StorageManagementHelper.launchStorageManagement(getContext()));
  }

  protected void setContainerVisibility(View containerView, boolean isContainerVisible) {
    containerView.setVisibility(isContainerVisible ? View.VISIBLE : View.GONE);
  }

  protected void logUiInteraction(EventType eventType) {
    exposureNotificationViewModel.logUiInteraction(eventType);
  }

  /**
   * All the life-cycle and viewModel specific interaction is encapsulated in this abstract
   * superclass. This sets up all interaction with the exposureNotificationViewModel.
   */
  @Override
  public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    // Observe changes in the EN state or in the 'in-flight' status of the API request as we need
    // both these pieces of information to refresh the UI.
    exposureNotificationViewModel
        .getStateWithInFlightLiveData()
        .observe(getViewLifecycleOwner(), this::refreshUiForStateAndInFlight);

    // Handle ApiErrors automatically
    if (isHandleApiErrorLiveEvents()) {
      exposureNotificationViewModel
          .getApiErrorLiveEvent()
          .observe(getViewLifecycleOwner(), unused -> SnackbarUtil
              .maybeShowRegularSnackbar(getView(), getString(R.string.generic_error_message)));

      exposureNotificationViewModel
          .getApiUnavailableLiveEvent()
          .observe(
              getViewLifecycleOwner(),
              unused -> {
                View rootView = getView();
                if (rootView != null) {
                  SnackbarUtil.createLargeSnackbar(rootView, R.string.gms_unavailable_error)
                      .setAction(R.string.learn_more, v ->
                          UrlUtils.openUrl(view, getString(R.string.gms_info_link)))
                      .show();
                }
              });
    }

    // Handle Resolutions
    if (isHandleResolutions()) {
      exposureNotificationViewModel.registerResolutionForActivityResult(this);
    }
  }

  /**
   * Update UI to match Exposure Notifications state and beware of the in flight API requests.
   *
   * @param state      the {@link ExposureNotificationState} of the API.
   * @param isInFlight boolean indicating if there is an in-flight API request.
   */
  private void refreshUiForStateAndInFlight(ExposureNotificationState state, boolean isInFlight) {
    View rootView = getView();
    if (rootView == null || state == null) {
      return;
    }

    /*
     * Root view is the LinearLayout at the root of our fragment. If we change its visibility to
     * View.GONE, the container view's (FrameLayout's) padding/margin still apply.
     * Therefore we retrieve the container view (that hosts this fragment) to set the visibility.
     */
    View containerView = (View) rootView.getParent();

    // Delegate the actual content filling to the child classes.
    fillUIContent(rootView, containerView, state, isInFlight);
  }

}
