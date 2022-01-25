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

package com.google.android.apps.exposurenotification.nearby;

import static com.google.android.apps.exposurenotification.nearby.ExposureNotificationClientWrapper.GET_STATUS_TIMEOUT;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.ExistingWorkPolicy;
import androidx.work.ListenableWorker;
import androidx.work.OneTimeWorkRequest;
import androidx.work.Operation;
import androidx.work.WorkManager;
import androidx.work.WorkerParameters;
import com.google.android.apps.exposurenotification.common.Qualifiers.BackgroundExecutor;
import com.google.android.apps.exposurenotification.common.Qualifiers.ScheduledExecutor;
import com.google.android.apps.exposurenotification.common.TaskToFutureAdapter;
import com.google.android.gms.nearby.exposurenotification.ExposureNotificationStatus;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.threeten.bp.Duration;

/**
 * Performs work to clean up the possible exposure information.
 */
public class ExposureInfoCleanupWorker extends ListenableWorker {

  private static final String WORKER_NAME = "ExposureInfoCleanupWorker";

  public static final String EXPOSURE_INFO_CLEANUP_WORKER_TAG = "EXPOSURE_INFO_CLEANUP_WORKER_TAG";

  private final ExposureNotificationClientWrapper exposureNotificationClientWrapper;
  private final ExposureInformationHelper exposureInformationHelper;
  private final ExecutorService backgroundExecutor;
  private final ScheduledExecutorService scheduledExecutor;

  @AssistedInject
  public ExposureInfoCleanupWorker(
      @Assisted @NonNull Context context,
      @Assisted @NonNull WorkerParameters workerParams,
      ExposureNotificationClientWrapper exposureNotificationClientWrapper,
      ExposureInformationHelper exposureInformationHelper,
      @BackgroundExecutor ExecutorService backgroundExecutor,
      @ScheduledExecutor ScheduledExecutorService scheduledExecutor) {
    super(context, workerParams);
    this.exposureNotificationClientWrapper = exposureNotificationClientWrapper;
    this.exposureInformationHelper = exposureInformationHelper;
    this.backgroundExecutor = backgroundExecutor;
    this.scheduledExecutor = scheduledExecutor;
  }

  @NonNull
  @Override
  public ListenableFuture<Result> startWork() {
    // Since this worker gets executed with a delay (to delete exposure information only after the
    // exposure expires), in rare cases EN might be turned back on when this worker is finally
    // executed. To avoid accidentally deleting valid exposures, check if EN is still in a turndown.
    return FluentFuture.from(TaskToFutureAdapter.getFutureWithTimeout(
        exposureNotificationClientWrapper.getStatus(),
        GET_STATUS_TIMEOUT,
        scheduledExecutor))
        .transformAsync(statuses -> {
          if (statuses.contains(ExposureNotificationStatus.EN_NOT_SUPPORT)
              || statuses.contains(ExposureNotificationStatus.NOT_IN_ALLOWLIST)) {
            // EN is still turned down. Delete the exposure info.
            exposureInformationHelper.deleteExposures();
          }
          return Futures.immediateVoidFuture();
        }, backgroundExecutor)
        .transform(unused -> Result.success(), backgroundExecutor)
        .catching(Exception.class, t -> Result.failure(), backgroundExecutor);
  }

  /**
   * Runs this worker once with a given initial delay to wipe out the possible exposure information
   * after this information expires.
   *
   * @param workManager               workManager that enqueues a request to execute this worker.
   * @param delayUntilExposureExpires delay after which the possible exposure information expires
   *                                  and so, needs to be deleted.
   */
  public static Operation runOnceWithDelay(WorkManager workManager,
      Duration delayUntilExposureExpires) {
    OneTimeWorkRequest oneTimeWorkRequest =
        new OneTimeWorkRequest.Builder(ExposureInfoCleanupWorker.class)
            .addTag(EXPOSURE_INFO_CLEANUP_WORKER_TAG)
            .setInitialDelay(delayUntilExposureExpires.getSeconds(), TimeUnit.SECONDS)
            .build();
    return workManager.enqueueUniqueWork(
        WORKER_NAME, ExistingWorkPolicy.KEEP, oneTimeWorkRequest);
  }

}
