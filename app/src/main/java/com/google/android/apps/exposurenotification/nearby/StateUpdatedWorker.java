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

package com.google.android.apps.exposurenotification.nearby;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.hilt.Assisted;
import androidx.hilt.work.WorkerInject;
import androidx.work.ListenableWorker;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkerParameters;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.NotificationHelper;
import com.google.android.apps.exposurenotification.common.Qualifiers.BackgroundExecutor;
import com.google.android.apps.exposurenotification.common.Qualifiers.LightweightExecutor;
import com.google.android.apps.exposurenotification.common.Qualifiers.ScheduledExecutor;
import com.google.android.apps.exposurenotification.common.TaskToFutureAdapter;
import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.android.apps.exposurenotification.logging.AnalyticsLogger;
import com.google.android.apps.exposurenotification.proto.WorkManagerTask.WorkerTask;
import com.google.android.apps.exposurenotification.riskcalculation.DailySummaryRiskCalculator;
import com.google.android.apps.exposurenotification.riskcalculation.ExposureClassification;
import com.google.android.apps.exposurenotification.riskcalculation.RevocationDetector;
import com.google.android.apps.exposurenotification.storage.ExposureEntity;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences.BadgeStatus;
import com.google.android.apps.exposurenotification.storage.ExposureRepository;
import com.google.android.gms.nearby.exposurenotification.DailySummariesConfig;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import org.threeten.bp.Duration;

/**
 * Performs work for {@value com.google.android.gms.nearby.exposurenotification.ExposureNotificationClient#ACTION_EXPOSURE_STATE_UPDATED}
 * broadcast from exposure notification API.
 */
public class StateUpdatedWorker extends ListenableWorker {

  private static final String TAG = "StateUpdatedWorker";

  private static final Duration GET_DAILY_SUMMARIES_TIMEOUT = Duration.ofSeconds(120);

  private final Context context;
  private final ExposureRepository exposureRepository;
  private final ExposureNotificationClientWrapper exposureNotificationClientWrapper;
  private final RevocationDetector revocationDetector;
  private final DailySummariesConfig dailySummariesConfig;
  private final DailySummaryRiskCalculator dailySummaryRiskCalculator;
  private final NotificationHelper notificationHelper;
  private final ExecutorService backgroundExecutor;
  private final ExecutorService lightweightExecutor;
  private final ScheduledExecutorService scheduledExecutor;
  private final AnalyticsLogger logger;
  private final Clock clock;

  ExposureNotificationSharedPreferences exposureNotificationSharedPreferences;

  @WorkerInject
  public StateUpdatedWorker(
      @Assisted @NonNull Context context,
      @Assisted @NonNull WorkerParameters workerParams,
      ExposureRepository exposureRepository,
      ExposureNotificationClientWrapper exposureNotificationClientWrapper,
      ExposureNotificationSharedPreferences exposureNotificationSharedPreferences,
      RevocationDetector revocationDetector,
      DailySummariesConfig dailySummariesConfig,
      DailySummaryRiskCalculator dailySummaryRiskCalculator,
      NotificationHelper notificationHelper,
      @BackgroundExecutor ExecutorService backgroundExecutor,
      @LightweightExecutor ExecutorService lightweightExecutor,
      @ScheduledExecutor ScheduledExecutorService scheduledExecutor,
      AnalyticsLogger logger,
      Clock clock) {
    super(context, workerParams);
    this.context = context;
    this.exposureRepository = exposureRepository;
    this.exposureNotificationClientWrapper = exposureNotificationClientWrapper;
    this.exposureNotificationSharedPreferences = exposureNotificationSharedPreferences;
    this.revocationDetector = revocationDetector;
    this.dailySummariesConfig = dailySummariesConfig;
    this.dailySummaryRiskCalculator = dailySummaryRiskCalculator;
    this.notificationHelper = notificationHelper;
    this.backgroundExecutor = backgroundExecutor;
    this.lightweightExecutor = lightweightExecutor;
    this.scheduledExecutor = scheduledExecutor;
    this.logger = logger;
    this.clock = clock;
  }

  static void runOnce(WorkManager workManager) {
    workManager.enqueue(new OneTimeWorkRequest.Builder(StateUpdatedWorker.class).build());
  }

  @NonNull
  @Override
  public ListenableFuture<Result> startWork() {
    return FluentFuture.from(
        TaskToFutureAdapter.getFutureWithTimeout(
            exposureNotificationClientWrapper.getDailySummaries(dailySummariesConfig),
            GET_DAILY_SUMMARIES_TIMEOUT,
            scheduledExecutor))
        .transform(
            (dailySummaries) -> {
              logger.logWorkManagerTaskStarted(WorkerTask.TASK_STATE_UPDATED);
              retrievePreviousExposuresAndCheckForExposureUpdate(context, dailySummaries);
              logger.logWorkManagerTaskSuccess(WorkerTask.TASK_STATE_UPDATED);
              return Result.success();
            },
            backgroundExecutor)
        .catching(
            Exception.class,
            x -> {
              Log.e(TAG, "Failure to update app state (tokens, etc) from exposure summary.", x);
              logger.logWorkManagerTaskFailure(WorkerTask.TASK_STATE_UPDATED, x);
              return Result.failure();
            }, backgroundExecutor);
  }

  private void retrievePreviousExposuresAndCheckForExposureUpdate(Context context,
      List<DailySummaryWrapper> dailySummaries) {
    List<ExposureEntity> currentExposureEntities =
        revocationDetector.dailySummaryToExposureEntity(dailySummaries);

    ExposureClassification currentClassification =
        dailySummaryRiskCalculator.classifyExposure(dailySummaries);

    ExposureClassification previousClassification =
        exposureNotificationSharedPreferences.getExposureClassification();

    checkForExposureUpdate(context, currentExposureEntities, currentClassification,
        previousClassification);
  }

  /**
   * Handle dailySummary update logic:
   * <ul>
   * <li>- Update UI's classification and date components
   * <li>- Badge UI components as "new"
   * <li>- Recognize changes that trigger notifications
   * <li>- Detect revocations
   * </ul>
   */
  public void checkForExposureUpdate(Context context,
      List<ExposureEntity> currentExposureEntities, ExposureClassification currentClassification,
      ExposureClassification previousClassification) {

    boolean isClassificationRevoked = false;

    Log.d(TAG, "Current ExposureClassification: " + currentClassification);

    Log.d(TAG, "Previous ExposureClassification: " + previousClassification);

    /*
     * We assume a change of classification if either the classification index (and resources)
     * change (because of changes in the underlying dailySummaries)
     * OR if the classification name changes (when the health authority changes their definitions).
     * This is also the information used to decide on the "new" badges
     */
    boolean newExposureClassification =
        (previousClassification.getClassificationIndex()
            != currentClassification.getClassificationIndex())
            || (!previousClassification.getClassificationName()
            .equals(currentClassification.getClassificationName()));

    boolean newExposureDate =
        (previousClassification.getClassificationDate()
            != currentClassification.getClassificationDate());

    /*
     * If either of these change, we almost always notify the user with the notification message of
     * the current exposure classification. The only exceptions are state
     * changes from "some exposure classification" to "no exposure". In this case we only notify
     * if we believe the state-change was caused by a key revocation.
     * If this transition occurs without a revocation, it is usually caused by exposure windows
     * expiring after 14 days. We don't want to notify the user in this case.
     *
     * To give the UI a chance to update to changed exposure classifications, we only set a
     * "notifyUser" flag here and postpone the actual notification to the very end of this method.
     */
    int notificationTitleResource = 0;
    int notificationMessageResource = 0;

    if (newExposureClassification || newExposureDate) {
      if (previousClassification.getClassificationIndex()
          != ExposureClassification.NO_EXPOSURE_CLASSIFICATION_INDEX
          && currentClassification.getClassificationIndex()
          == ExposureClassification.NO_EXPOSURE_CLASSIFICATION_INDEX) {

        // Check for the revocation edge case by looking up previous exposures in room
        List<ExposureEntity> previousExposureEntities = exposureRepository.getAllExposureEntities();
        if (revocationDetector.isRevocation(previousExposureEntities, currentExposureEntities)) {
          notificationTitleResource = R.string.exposure_notification_title_revoked;
          notificationMessageResource = R.string.exposure_notification_message_revoked;
          isClassificationRevoked = true;
        }
      }
      // Otherwise just notify using the normal classifications
      else {
        notificationTitleResource = getNotificationTitleResource(currentClassification);
        notificationMessageResource = getNotificationMessageResource(currentClassification);
      }

    }

    /*
     * Write the new state to SharedPrefs to detect changes on the next call.
     * Persist information on what was updated / if there was a revocation for the UI
     */
    exposureNotificationSharedPreferences.setExposureClassification(currentClassification);
    exposureNotificationSharedPreferences
        .setIsExposureClassificationRevoked(isClassificationRevoked);
    if (newExposureClassification) {
      exposureNotificationSharedPreferences
          .setIsExposureClassificationNewAsync(BadgeStatus.NEW);
    }
    if (newExposureDate) {
      exposureNotificationSharedPreferences
          .setIsExposureClassificationDateNewAsync(BadgeStatus.NEW);
    }

    /*
     * Notify the user
     */
    if (notificationTitleResource != 0 || notificationMessageResource != 0) {
      notificationHelper.showPossibleExposureNotification(
          context, notificationTitleResource, notificationMessageResource);
      exposureNotificationSharedPreferences
          .setExposureNotificationLastShownClassification(clock.now(),
              currentClassification.getClassificationIndex());
      Log.d(TAG, "Notifying user: "
          + context.getResources().getString(notificationTitleResource) + " - "
          + context.getResources().getString(notificationMessageResource));
    } else {
      Log.d(TAG, "No new exposure information, not notifying user");
    }

    /*
     * Write a the scores of the DailySummaries to disk for later revocation detection
     */
    exposureRepository.clearInsertExposureEntities(currentExposureEntities);
  }

  /**
   * Helper to provide the string resources for the notification title
   */
  private int getNotificationTitleResource(ExposureClassification exposureClassification) {
    switch (exposureClassification.getClassificationIndex()) {
      case 1:
        return R.string.exposure_notification_title_1;
      case 2:
        return R.string.exposure_notification_title_2;
      case 3:
        return R.string.exposure_notification_title_3;
      case 4:
        return R.string.exposure_notification_title_4;
      default:
        throw new IllegalArgumentException("Classification index must be between 1 and 4");
    }
  }

  /**
   * Helper to provide the string resources for the notification message
   */
  private int getNotificationMessageResource(ExposureClassification exposureClassification) {
    switch (exposureClassification.getClassificationIndex()) {
      case 1:
        return R.string.exposure_notification_message_1;
      case 2:
        return R.string.exposure_notification_message_2;
      case 3:
        return R.string.exposure_notification_message_3;
      case 4:
        return R.string.exposure_notification_message_4;
      default:
        throw new IllegalArgumentException("Classification index must be between 1 and 4");
    }
  }
}