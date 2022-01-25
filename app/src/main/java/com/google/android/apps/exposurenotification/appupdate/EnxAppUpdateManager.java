/*
 * Copyright 2021 Google LLC
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

package com.google.android.apps.exposurenotification.appupdate;

import static com.google.android.apps.exposurenotification.utils.RequestCodes.REQUEST_CODE_APP_UPDATE;

import android.content.IntentSender.SendIntentException;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContract;
import androidx.concurrent.futures.CallbackToFutureAdapter;
import com.google.android.apps.exposurenotification.common.Qualifiers.BackgroundExecutor;
import com.google.android.apps.exposurenotification.common.Qualifiers.ScheduledExecutor;
import com.google.android.play.core.appupdate.AppUpdateInfo;
import com.google.android.play.core.appupdate.AppUpdateManager;
import com.google.android.play.core.install.model.AppUpdateType;
import com.google.android.play.core.install.model.UpdateAvailability;
import com.google.android.play.core.tasks.Task;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import org.threeten.bp.Duration;

/** Helper class for managing app updates. */
public final class EnxAppUpdateManager {

  private static final Duration PLAY_STORE_UPDATE_API_TIMEOUT = Duration.ofSeconds(30);

  private final AppUpdateManager appUpdateManager;
  private final ExecutorService backgroundExecutor;
  private final ScheduledExecutorService scheduledExecutor;

  @Inject
  public EnxAppUpdateManager(
      AppUpdateManager appUpdateManager,
      @BackgroundExecutor ExecutorService backgroundExecutor,
      @ScheduledExecutor ScheduledExecutorService scheduledExecutor) {
    this.appUpdateManager = appUpdateManager;
    this.backgroundExecutor = backgroundExecutor;
    this.scheduledExecutor = scheduledExecutor;
  }

  /**
   * Returns the current {@link AppUpdateInfo} object (wrapped into the Task), which contains
   * information about the availability and progress of an app update.
   */
  public Task<AppUpdateInfo> getAppUpdateInfo() {
    return appUpdateManager.getAppUpdateInfo();
  }

  /**
   * Returns the current {@link AppUpdateInfo} object (wrapped into the FluentFuture), which
   * contains information about the availability and progress of an app update.
   */
  public FluentFuture<AppUpdateInfo> getAppUpdateInfoFuture() {
    return FluentFuture.<AppUpdateInfo>from(
        CallbackToFutureAdapter.getFuture(
            completer -> {
              appUpdateManager.getAppUpdateInfo().addOnCompleteListener(
                  backgroundExecutor,
                  completedTask -> {
                    try {
                      if (completedTask.getException() != null) {
                        completer.setException(completedTask.getException());
                      } else if (completedTask.isSuccessful()) {
                        completer.set(completedTask.getResult());
                      }
                    } catch (Exception ex) {
                      completer.setException(ex);
                    }
                  });
              return "PlayCoreTask";
            }))
        .withTimeout(PLAY_STORE_UPDATE_API_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS,
            scheduledExecutor);
  }

  /**
   * Checks whether there is an app update available and whether the immediate app update is
   * allowed.
   *
   * @param appUpdateInfo object containing information about the availability and progress of an
   *     app update.
   */
  public boolean isImmediateAppUpdateAvailable(AppUpdateInfo appUpdateInfo) {
    return appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
        && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE);
  }

  /**
   * Checks whether there is a download of the Play in-app update in progress.
   *
   * @param appUpdateInfo object containing information about the availability and progress of an
   *                      app update.
   */
  public boolean isAppUpdateInProgress(AppUpdateInfo appUpdateInfo) {
    return appUpdateInfo.updateAvailability()
        == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS;
  }

  /**
   * Triggers the immediate app update flow, which is a Play owned flow that does the download of
   * an app update and the app restart once the download is completed.
   *
   * <p>Returns true to signal that the in-app update flow has been launched successfully and false
   * otherwise.
   *
   * @param activityResultLauncher launcher for a previously-prepared call to start the process of
   *                               executing an {@link ActivityResultContract}.
   */
  public ListenableFuture<Boolean> triggerImmediateAppUpdateFlow(
      ActivityResultLauncher<IntentSenderRequest> activityResultLauncher) {
    return getAppUpdateInfoFuture()
        .transformAsync(appUpdateInfo -> {
          boolean success = startUpdateFlowForResult(appUpdateInfo, activityResultLauncher);
          return Futures.immediateFuture(success);
        }, backgroundExecutor)
        .catchingAsync(SendIntentException.class,
            ex -> Futures.immediateFailedFuture(new AppUpdateFlowFailedToLaunchException(ex)),
            backgroundExecutor);
  }

  /**
   * Triggers the immediate app update flow, which is a Play owned flow that does the download of an
   * app update and the app restart once the download is completed.
   *
   * <p>Returns true to signal that the in-app update flow has been launched successfully and false
   * otherwise.
   *
   * @param appUpdateInfo          object containing information about the availability and progress
   *                               of an app update.
   * @param activityResultLauncher launcher for a previously-prepared call to start the process of
   *                               executing an {@link ActivityResultContract}.
   */
  public ListenableFuture<Boolean> triggerImmediateAppUpdateFlow(AppUpdateInfo appUpdateInfo,
      ActivityResultLauncher<IntentSenderRequest> activityResultLauncher) {
    try {
      boolean success = startUpdateFlowForResult(appUpdateInfo, activityResultLauncher);
      return Futures.immediateFuture(success);
    } catch (SendIntentException ex) {
      return Futures.immediateFailedFuture(new AppUpdateFlowFailedToLaunchException(ex));
    }
  }

  /**
   * Starts Immediate In-App update flow.
   *
   * @param appUpdateInfo          object containing information about the availability and progress
   *                               of an app update.
   * @param activityResultLauncher launcher for a previously-prepared call to start the process of
   *                               executing an {@link ActivityResultContract}.
   * @return true if Immediate In-App update flow launched and false otherwise.
   * @throws SendIntentException
   */
  private boolean startUpdateFlowForResult(AppUpdateInfo appUpdateInfo,
      ActivityResultLauncher<IntentSenderRequest> activityResultLauncher)
      throws SendIntentException {
    return appUpdateManager.startUpdateFlowForResult(
        appUpdateInfo,
        AppUpdateType.IMMEDIATE,
        (intent, requestCode, fillInIntent, flagsMask, flagsVals, extraFlags, options) ->
            activityResultLauncher.launch(
                new IntentSenderRequest.Builder(intent)
                    .setFillInIntent(fillInIntent)
                    .setFlags(flagsVals, flagsMask)
                    .build()),
        REQUEST_CODE_APP_UPDATE);
  }

  /**
   * An {@link Exception} thrown to indicate that the Immediate In-app Update Flow has failed to
   * launch.
   */
  public static class AppUpdateFlowFailedToLaunchException extends Exception {

    public AppUpdateFlowFailedToLaunchException(Throwable cause) {
      super(cause);
    }
  }

}
