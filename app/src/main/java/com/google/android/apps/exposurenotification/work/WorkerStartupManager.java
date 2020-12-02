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

import android.util.Log;
import com.google.android.apps.exposurenotification.common.Qualifiers.BackgroundExecutor;
import com.google.android.apps.exposurenotification.common.Qualifiers.ScheduledExecutor;
import com.google.android.apps.exposurenotification.common.TaskToFutureAdapter;
import com.google.android.apps.exposurenotification.nearby.ExposureNotificationClientWrapper;
import com.google.android.apps.exposurenotification.nearby.PackageConfigurationHelper;
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

  private static final String TAG = "WorkerStartupManager";

  private static final Duration IS_ENABLED_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration GET_PACKAGE_CONFIGURATION_TIMEOUT = Duration.ofSeconds(10);

  private final ExposureNotificationClientWrapper exposureNotificationClientWrapper;
  private final ExecutorService backgroundExecutor;
  private final ScheduledExecutorService scheduledExecutor;
  private final PackageConfigurationHelper packageConfigurationHelper;

  @Inject
  public WorkerStartupManager(
      ExposureNotificationClientWrapper exposureNotificationClientWrapper,
      @BackgroundExecutor ExecutorService backgroundExecutor,
      @ScheduledExecutor ScheduledExecutorService scheduledExecutor,
      PackageConfigurationHelper packageConfigurationHelper) {
    this.exposureNotificationClientWrapper = exposureNotificationClientWrapper;
    this.backgroundExecutor = backgroundExecutor;
    this.scheduledExecutor = scheduledExecutor;
    this.packageConfigurationHelper = packageConfigurationHelper;
  }

  /**
   * Checks if the API isEnabled. If so, performs some startup tasks then returns true once done,
   * otherwise immediately returns false.
   */
  public ListenableFuture<Boolean> getIsEnabledWithStartupTasks() {
    return FluentFuture.from(TaskToFutureAdapter.getFutureWithTimeout(
        exposureNotificationClientWrapper.isEnabled(),
        IS_ENABLED_TIMEOUT,
        scheduledExecutor))
        .transformAsync(isEnabled -> {
          if (isEnabled) {
            return maybeUpdateAnalyticsState().transform(v -> true, backgroundExecutor);
          } else {
            return Futures.immediateFuture(false);
          }
        }, backgroundExecutor);
  }

  private FluentFuture<Void> maybeUpdateAnalyticsState() {
    return FluentFuture.from(TaskToFutureAdapter.getFutureWithTimeout(
        exposureNotificationClientWrapper.getPackageConfiguration(),
        GET_PACKAGE_CONFIGURATION_TIMEOUT,
        scheduledExecutor))
        .transformAsync(packageConfiguration -> {
          packageConfigurationHelper.maybeUpdateAnalyticsState(packageConfiguration);
          return Futures.immediateVoidFuture();
        }, backgroundExecutor)
        .catchingAsync(Exception.class, t -> {
          Log.e(TAG, "Unable to update app analytics state", t);
          return Futures.immediateVoidFuture();
        }, backgroundExecutor);
  }

}
