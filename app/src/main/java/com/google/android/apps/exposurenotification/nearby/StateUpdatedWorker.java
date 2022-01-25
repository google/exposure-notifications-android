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
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.res.ResourcesCompat;
import androidx.hilt.work.HiltWorker;
import androidx.work.ListenableWorker;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkerParameters;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.IntentUtil;
import com.google.android.apps.exposurenotification.common.NotificationHelper;
import com.google.android.apps.exposurenotification.common.Qualifiers.BackgroundExecutor;
import com.google.android.apps.exposurenotification.common.Qualifiers.ScheduledExecutor;
import com.google.android.apps.exposurenotification.common.TaskToFutureAdapter;
import com.google.android.apps.exposurenotification.common.logging.Logger;
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
import com.google.android.gms.nearby.exposurenotification.ExposureNotificationStatus;
import com.google.common.base.Optional;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import org.threeten.bp.Duration;
import org.threeten.bp.Instant;

/**
 * Performs work for {@value com.google.android.gms.nearby.exposurenotification.ExposureNotificationClient#ACTION_EXPOSURE_STATE_UPDATED}
 * broadcast from exposure notification API.
 */
@HiltWorker
public class StateUpdatedWorker extends ListenableWorker {

  private static final Logger logcat = Logger.getLogger("StateUpdatedWorker");

  private static final Duration GET_DAILY_SUMMARIES_TIMEOUT = Duration.ofSeconds(120);
  @VisibleForTesting static final Duration BLE_LOC_OFF_UNTIL_NOTIFICATION_THRESHOLD =
      Duration.ofHours(24);

  private final Context context;
  private final ExposureRepository exposureRepository;
  private final ExposureNotificationClientWrapper exposureNotificationClientWrapper;
  private final RevocationDetector revocationDetector;
  private final DailySummariesConfig dailySummariesConfig;
  private final DailySummaryRiskCalculator dailySummaryRiskCalculator;
  private final NotificationHelper notificationHelper;
  private final ExecutorService backgroundExecutor;
  private final ScheduledExecutorService scheduledExecutor;
  private final AnalyticsLogger logger;
  private final Clock clock;

  ExposureNotificationSharedPreferences exposureNotificationSharedPreferences;

  @AssistedInject
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
        .transformAsync(
            (dailySummaries) -> {
              logger.logWorkManagerTaskStarted(WorkerTask.TASK_STATE_UPDATED);
              boolean notificationTriggered =
                  retrievePreviousExposuresAndCheckForExposureUpdate(context, dailySummaries);
              logger.logWorkManagerTaskSuccess(WorkerTask.TASK_STATE_UPDATED);
              if (notificationTriggered) {
                // If we triggered an exposure, we skip any edge-case detection steps
                return Futures.immediateFailedFuture(new NotificationShownException());
              } else {
                // Otherwise, we check if we need to show an edge-case notification. To do so,
                // we query getStatus()
                return TaskToFutureAdapter.getFutureWithTimeout(
                    exposureNotificationClientWrapper.getStatus(),
                    GET_DAILY_SUMMARIES_TIMEOUT,
                    scheduledExecutor);
              }
            },
            backgroundExecutor)
        .transform(
            enStatusSet -> {
              maybeShowEdgeCaseNotification(enStatusSet);
              return Result.success();
            }, backgroundExecutor)
        .catching(
            NotificationShownException.class,
            x -> Result.success(), backgroundExecutor)
        .catching(
            Exception.class,
            x -> {
              logcat.e("Failure to update app state (tokens, etc) from exposure summary.", x);
              logger.logWorkManagerTaskFailure(WorkerTask.TASK_STATE_UPDATED, x);
              return Result.failure();
            }, backgroundExecutor);
  }

  private static class NotificationShownException extends Exception {}

  /**
   * Check if there was a new exposure or revocation and whether we need to notify.
   * Returns true if we triggered a exposure/revocation notification.
   */
  @VisibleForTesting
  boolean retrievePreviousExposuresAndCheckForExposureUpdate(Context context,
      List<DailySummaryWrapper> dailySummaries) {
    List<ExposureEntity> currentExposureEntities =
        revocationDetector.dailySummaryToExposureEntity(dailySummaries);

    ExposureClassification currentClassification =
        dailySummaryRiskCalculator.classifyExposure(dailySummaries);

    ExposureClassification previousClassification =
        exposureNotificationSharedPreferences.getExposureClassification();

    return checkForExposureUpdate(context, currentExposureEntities, currentClassification,
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
   *
   * @return true if triggered a exposure/revocation notification
   */
  public boolean checkForExposureUpdate(Context context,
      List<ExposureEntity> currentExposureEntities, ExposureClassification currentClassification,
      ExposureClassification previousClassification) {

    boolean isClassificationRevoked = false;

    logcat.d("Current ExposureClassification: " + currentClassification);

    logcat.d("Previous ExposureClassification: " + previousClassification);

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
    int notificationTitleResource = ResourcesCompat.ID_NULL;
    int notificationMessageResource = ResourcesCompat.ID_NULL;

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
    boolean showNotification = (notificationTitleResource != ResourcesCompat.ID_NULL
        || notificationMessageResource != ResourcesCompat.ID_NULL);
    if (showNotification) {
      notificationHelper.showNotification(
          context, notificationTitleResource, notificationMessageResource,
          IntentUtil.getNotificationContentIntentExposure(context),
          IntentUtil.getNotificationDeleteIntent(context));
      exposureNotificationSharedPreferences
          .setExposureNotificationLastShownClassification(clock.now(),
              currentClassification);
      logcat.d("Notifying user: "
          + context.getResources().getString(notificationTitleResource) + " - "
          + context.getResources().getString(notificationMessageResource));
    } else {
      logcat.d("No new exposure information, not notifying user");
    }

    /*
     * Write a the scores of the DailySummaries to disk for later revocation detection
     */
    exposureRepository.deleteInsertExposureEntities(currentExposureEntities);

    return showNotification;
  }

  /**
   * Helper to provide the string resources for the notification title
   */
  @StringRes
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
  @StringRes
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

  /**
   * Helper to check if we should show a edge-case notification.
   * This must only be called if we have not previously shown an exposure / revocation notification.
   */
  @VisibleForTesting
  void maybeShowEdgeCaseNotification(Set<ExposureNotificationStatus> enStatusSet) {
    // We only a possible edge-case notification once per user. If we did that already, we're done
    if(exposureNotificationSharedPreferences.getBleLocNotificationSeen()) {
      return;
    }

    // We don't execute this logic if the user had an exposure/revocation in the last 14 days
    if(exposureNotificationSharedPreferences.getExposureClassification().getClassificationIndex()
        != ExposureClassification.NO_EXPOSURE_CLASSIFICATION_INDEX
        || exposureNotificationSharedPreferences.getIsExposureClassificationRevoked()) {
      return;
    }

    Optional<Instant> beginTimestampBleLocOff =
        exposureNotificationSharedPreferences.getBeginTimestampBleLocOff();

    // Set the correct beginTimestampBleLocOff
    if (enStatusSet.contains(ExposureNotificationStatus.LOCATION_DISABLED)
        || enStatusSet.contains(ExposureNotificationStatus.BLUETOOTH_DISABLED)
        || enStatusSet.contains(ExposureNotificationStatus.BLUETOOTH_SUPPORT_UNKNOWN)) {
      // We only want to store the time when we started to see loc/ble off, so we only reset the
      // timestamp if we see a change to loc/ble off for the first time
      if (!beginTimestampBleLocOff.isPresent()) {
        beginTimestampBleLocOff = Optional.of(clock.now());
      }
    } else {
      // If Ble/Loc are both enabled, we do not store a timestamp
      beginTimestampBleLocOff = Optional.absent();
    }

    // If we're still in a Ble/Loc off state, notify
    if (beginTimestampBleLocOff.isPresent()
        && Duration.between(beginTimestampBleLocOff.get(), clock.now())
        .compareTo(BLE_LOC_OFF_UNTIL_NOTIFICATION_THRESHOLD) >= 0 /*bigger equal threshold*/) {
      int bleLocNotificationMessageResource = getBleLocMessageResource(enStatusSet);
      if (bleLocNotificationMessageResource != ResourcesCompat.ID_NULL) {
        notificationHelper.showNotification(context,
            R.string.updated_permission_disabled_notification_title,
            getBleLocMessageResource(enStatusSet),
            IntentUtil.getNotificationContentIntentExposure(context),
            IntentUtil.getNotificationDeleteIntent(context));
      }
      exposureNotificationSharedPreferences.setBleLocNotificationSeen(true);
    }

    // Write back the beginTimestampBleLocOff
    exposureNotificationSharedPreferences.setBeginTimestampBleLocOff(beginTimestampBleLocOff);
  }

  /**
   * Return the correct edge-case string resource id depending on the current statusSet
   */
  @StringRes
  private int getBleLocMessageResource(Set<ExposureNotificationStatus> statusSet) {
    if (statusSet.contains(ExposureNotificationStatus.LOCATION_DISABLED)
        && (statusSet.contains(ExposureNotificationStatus.BLUETOOTH_DISABLED)
        || statusSet.contains(ExposureNotificationStatus.BLUETOOTH_SUPPORT_UNKNOWN))) {
      return R.string.updated_bluetooth_location_state_notification;
    } else if (statusSet.contains(ExposureNotificationStatus.BLUETOOTH_DISABLED)
        || statusSet.contains(ExposureNotificationStatus.BLUETOOTH_SUPPORT_UNKNOWN)) {
      return R.string.updated_bluetooth_state_notification;
    } else if (statusSet.contains(ExposureNotificationStatus.LOCATION_DISABLED)) {
      return R.string.updated_location_state_notification;
    } else {
      return ResourcesCompat.ID_NULL;
    }
  }

}
