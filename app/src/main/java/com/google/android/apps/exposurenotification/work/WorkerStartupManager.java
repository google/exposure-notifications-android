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

package com.google.android.apps.exposurenotification.work;

import static com.google.android.apps.exposurenotification.nearby.ExposureNotificationClientWrapper.GET_STATUS_TIMEOUT;

import com.google.android.apps.exposurenotification.common.CleanupHelper;
import com.google.android.apps.exposurenotification.common.Qualifiers.BackgroundExecutor;
import com.google.android.apps.exposurenotification.common.Qualifiers.ScheduledExecutor;
import com.google.android.apps.exposurenotification.common.TaskToFutureAdapter;
import com.google.android.apps.exposurenotification.common.logging.Logger;
import com.google.android.apps.exposurenotification.nearby.ExposureNotificationClientWrapper;
import com.google.android.apps.exposurenotification.nearby.PackageConfigurationHelper;
import com.google.android.gms.nearby.exposurenotification.ExposureNotificationStatus;
import com.google.android.gms.nearby.exposurenotification.PackageConfiguration;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import org.threeten.bp.Duration;

/**
 * Helper methods that should be used by all periodic workers to check the API is enabled and
 * perform some routine tasks.
 *
 * <pre>{@code
 *    public ListenableFuture<Result> startWork() {
 *       return FluentFuture.from(workerStartupManager.getIsEnabledWithStartupTasks())
 *           .transformAsync(
 *               (isEnabled) -> {
 *                 // Only continue if it is enabled.
 *                 if (isEnabled) {
 *                   return continueWorkFuture();
 *                 } else {
 *                   // Stop here because things aren't enabled. Will still return successful though.
 *                   return Futures.immediateFailedFuture(new NotEnabledException());
 *                 }
 *               }, backgroundExecutor)
 *           ... continue the work flow
 *           .catching(
 *                 NotEnabledException.class,
 *                 x -> {
 *                   // Not enabled. Return as success.
 *                   return Result.success();
 *                 },
 *                 backgroundExecutor);
 *     }
 * }</pre>
 */
public class WorkerStartupManager {

  private static final Logger logger = Logger.getLogger("WorkerStartupManager");

  private static final Duration IS_ENABLED_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration GET_PACKAGE_CONFIGURATION_TIMEOUT = Duration.ofSeconds(10);

  private final ExposureNotificationClientWrapper exposureNotificationClientWrapper;
  private final PackageConfigurationHelper packageConfigurationHelper;
  private final CleanupHelper cleanupHelper;
  private final ExecutorService backgroundExecutor;
  private final ScheduledExecutorService scheduledExecutor;

  @Inject
  public WorkerStartupManager(
      ExposureNotificationClientWrapper exposureNotificationClientWrapper,
      @BackgroundExecutor ExecutorService backgroundExecutor,
      @ScheduledExecutor ScheduledExecutorService scheduledExecutor,
      PackageConfigurationHelper packageConfigurationHelper,
      CleanupHelper cleanupHelper) {
    this.exposureNotificationClientWrapper = exposureNotificationClientWrapper;
    this.backgroundExecutor = backgroundExecutor;
    this.scheduledExecutor = scheduledExecutor;
    this.packageConfigurationHelper = packageConfigurationHelper;
    this.cleanupHelper = cleanupHelper;
  }

  /**
   * Checks if the API isEnabled. If so, performs some startup tasks then returns true once done,
   * otherwise immediately returns false.
   *
   * <p> Also deletes oudated exposure checks and verification code requests and resets nonces for
   * expired verification code requests, if any.
   */
  public ListenableFuture<Boolean> getIsEnabledWithStartupTasks() {
    return FluentFuture.from(TaskToFutureAdapter.getFutureWithTimeout(
        exposureNotificationClientWrapper.isEnabled(),
        IS_ENABLED_TIMEOUT,
        scheduledExecutor))
        .transformAsync(isEnabled -> {
          // Clean up app data, which might have become outdated.
          cleanupOutdatedData();
          if (isEnabled) {
            return maybeUpdatePackageConfigurationState().transform(v -> true, backgroundExecutor);
          } else {
            return maybePerformTurnDown().transform(v -> false, backgroundExecutor);
          }
        }, backgroundExecutor)
        .catchingAsync(Exception.class, e -> {
          if (e instanceof TurndownException) {
            // We do not need to clean up in case of the
            // {@link com.google.android.apps.exposurenotification.work.TurndownException} because
            // this exception is thrown only after the outdated has been already cleaned up.
            return Futures.immediateFailedFuture(new IsEnabledWithStartupTasksException(e));
          }
          // Clean up app data, which might have become outdated.
          cleanupOutdatedData();
          // And check whether we hit a turndown state (because if we did, we need to perform
          // a broader turndown-related cleanup).
          return maybePerformTurnDown().transformAsync(
              unused -> Futures.immediateFailedFuture(new IsEnabledWithStartupTasksException(e)),
              backgroundExecutor);
        }, backgroundExecutor);
  }

  /**
   * Updates the app's package configuration state using the {@link PackageConfiguration} object
   * returned by the EN APIs.
   */
  private FluentFuture<Void> maybeUpdatePackageConfigurationState() {
    return FluentFuture.from(TaskToFutureAdapter.getFutureWithTimeout(
        exposureNotificationClientWrapper.getPackageConfiguration(),
        GET_PACKAGE_CONFIGURATION_TIMEOUT,
        scheduledExecutor))
        .transformAsync(packageConfiguration -> {
          packageConfigurationHelper.maybeUpdateAnalyticsState(packageConfiguration);
          packageConfigurationHelper.maybeUpdateSmsNoticeState(packageConfiguration);
          return Futures.immediateVoidFuture();
        }, backgroundExecutor)
        .catchingAsync(Exception.class, t -> {
          logger.e("Unable to update app analytics state", t);
          return Futures.immediateVoidFuture();
        }, backgroundExecutor);
  }

  /**
   * Cleans up app data, which might have become outdated.
   */
  private void cleanupOutdatedData() {
    cleanupHelper.deleteOutdatedData();
    cleanupHelper.resetOutdatedData();
  }

  /**
   * Checks if EN is in a turndown state and if so, performs a turndown-related work.
   *
   * <p>The turndown states are
   * {@link ExposureNotificationStatus#EN_NOT_SUPPORT} or
   * {@link ExposureNotificationStatus#NOT_IN_ALLOWLIST}.
   */
  private FluentFuture<Void> maybePerformTurnDown() {
    return FluentFuture.from(TaskToFutureAdapter.getFutureWithTimeout(
        exposureNotificationClientWrapper.getStatus(),
        GET_STATUS_TIMEOUT,
        scheduledExecutor))
        .transformAsync(statuses -> {
          if (statuses.contains(ExposureNotificationStatus.EN_NOT_SUPPORT)
              || statuses.contains(ExposureNotificationStatus.NOT_IN_ALLOWLIST)) {
            // EN has been turned down. Delete some of the app data, which won't be needed anymore.
            return performTurnDown();
          }
          return Futures.immediateVoidFuture();
        }, backgroundExecutor)
        .catchingAsync(Exception.class,
            e -> Futures.immediateFailedFuture(new TurndownException(e)), backgroundExecutor);
  }

  /**
   * Performs a turndown-related work (such as deleting obsolete app storage or cancelling pending
   * Backup & Restore work).
   */
  private FluentFuture<Void> performTurnDown() {
    return FluentFuture.from(cleanupHelper.deleteObsoleteStorageForTurnDown())
        .transformAsync(unused -> cleanupHelper.cancelPendingRestoreNotificationsAndJob(),
            backgroundExecutor);
  }

  /**
   * An {@link Exception} thrown if the turndown work has failed.
   */
  public static class TurndownException extends Exception {

    public TurndownException(Throwable cause) {
      super(cause);
    }
  }

  /**
   * An {@link Exception} wrapper for exceptions thrown upon calls to
   * {@link #getIsEnabledWithStartupTasks()}.
   */
  public static class IsEnabledWithStartupTasksException extends Exception {

    public IsEnabledWithStartupTasksException(Throwable cause) {
      super(cause);
    }
  }

}
