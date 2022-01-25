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

package com.google.android.apps.exposurenotification.common;

import android.content.Context;
import androidx.annotation.AnyThread;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.work.WorkManager;
import com.google.android.apps.exposurenotification.common.Qualifiers.BackgroundExecutor;
import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.android.apps.exposurenotification.nearby.ExposureInfoCleanupWorker;
import com.google.android.apps.exposurenotification.nearby.ExposureInformationHelper;
import com.google.android.apps.exposurenotification.restore.RestoreNotificationWorker;
import com.google.android.apps.exposurenotification.storage.AnalyticsLoggingRepository;
import com.google.android.apps.exposurenotification.storage.CountryRepository;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity;
import com.google.android.apps.exposurenotification.storage.DiagnosisRepository;
import com.google.android.apps.exposurenotification.storage.DownloadServerRepository;
import com.google.android.apps.exposurenotification.storage.ExposureCheckEntity;
import com.google.android.apps.exposurenotification.storage.ExposureCheckRepository;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.apps.exposurenotification.storage.ExposureRepository;
import com.google.android.apps.exposurenotification.storage.VerificationCodeRequestRepository;
import com.google.android.apps.exposurenotification.storage.WorkerStatusRepository;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.hilt.android.qualifiers.ApplicationContext;
import java.util.concurrent.ExecutorService;
import javax.inject.Inject;
import org.threeten.bp.Duration;

/**
 * Helper class for various cleanup work in the app.
 *
 * <p>Cleanup work examples:
 * <ul>
 *   <li>cleaning up outdated data (data which expires after a certain threshold)</li>
 *   <li>cleaning up obsolete storage after the EN turndown</li>
 *   <li>cancelling the Backup & Restore work and notifications after the EN turndown</li>
 * </ul>
 */
public class CleanupHelper {

  // Used to determine the threshold beyond which we mark the exposure checks as expired.
  // Currently, all exposure checks captured earlier than two weeks ago are deemed expired.
  @VisibleForTesting
  static final Duration EXPOSURE_CHECK_MAX_AGE = Duration.ofDays(14);
  // Used to determine the threshold beyond which we mark the requests for a verification code
  // as expired. Currently, all verification code requests made earlier than thirty days ago are
  // deemed expired.
  @VisibleForTesting static final Duration VERIFICATION_CODE_REQUEST_MAX_AGE = Duration.ofDays(30);

  private final ExposureNotificationSharedPreferences exposureNotificationSharedPreferences;
  private final AnalyticsLoggingRepository analyticsLoggingRepository;
  private final CountryRepository countryRepository;
  private final DiagnosisRepository diagnosisRepository;
  private final DownloadServerRepository downloadServerRepository;
  private final ExposureRepository exposureRepository;
  private final ExposureCheckRepository exposureCheckRepository;
  private final VerificationCodeRequestRepository verificationCodeRequestRepository;
  private final WorkerStatusRepository workerStatusRepository;
  private final ExposureInformationHelper exposureInformationHelper;
  private final WorkManager workManager;
  private final Context context;
  private final NotificationHelper notificationHelper;
  private final Clock clock;
  private final ExecutorService backgroundExecutor;

  @Inject
  public CleanupHelper(
      @ApplicationContext Context context,
      ExposureNotificationSharedPreferences exposureNotificationSharedPreferences,
      AnalyticsLoggingRepository analyticsLoggingRepository,
      CountryRepository countryRepository,
      DiagnosisRepository diagnosisRepository,
      DownloadServerRepository downloadServerRepository,
      ExposureRepository exposureRepository,
      ExposureCheckRepository exposureCheckRepository,
      VerificationCodeRequestRepository verificationCodeRequestRepository,
      WorkerStatusRepository workerStatusRepository,
      ExposureInformationHelper exposureInformationHelper,
      WorkManager workManager,
      NotificationHelper notificationHelper,
      Clock clock,
      @BackgroundExecutor ExecutorService backgroundExecutor) {
    this.exposureNotificationSharedPreferences = exposureNotificationSharedPreferences;
    this.analyticsLoggingRepository = analyticsLoggingRepository;
    this.countryRepository = countryRepository;
    this.diagnosisRepository = diagnosisRepository;
    this.downloadServerRepository = downloadServerRepository;
    this.exposureCheckRepository = exposureCheckRepository;
    this.exposureRepository = exposureRepository;
    this.verificationCodeRequestRepository = verificationCodeRequestRepository;
    this.workerStatusRepository = workerStatusRepository;
    this.exposureInformationHelper = exposureInformationHelper;
    this.workManager = workManager;
    this.context = context;
    this.notificationHelper = notificationHelper;
    this.clock = clock;
    this.backgroundExecutor = backgroundExecutor;
  }

  /**
   * Deletes the outdated data.
   *
   * <p>Currently, the stored data, which might get outdated is:
   * <ul>
   *   <li>exposure checks, which expire after
   *   {@link CleanupHelper#EXPOSURE_CHECK_MAX_AGE} days</li>.
   *   <li>requests for a verification code, which expire after
   *   {@link CleanupHelper#VERIFICATION_CODE_REQUEST_MAX_AGE} days.</li>
   * </ul>
   *
   * <p>This method should be called as frequently as possible to ensure we delete outdated data
   * on time.
   */
  @WorkerThread
  public void deleteOutdatedData() {
    // Delete outdated exposure checks.
    exposureCheckRepository.deleteOutdatedChecksIfAny(clock.now().minus(EXPOSURE_CHECK_MAX_AGE));
    // Delete outdated requests for a verification code.
    verificationCodeRequestRepository.deleteOutdatedRequestsIfAny(
        clock.now().minus(VERIFICATION_CODE_REQUEST_MAX_AGE));
    // Delete outdated possible exposure information.
    if (exposureInformationHelper.isOutdatedExposurePresent()) {
      exposureInformationHelper.deleteExposures();
    }
  }

  /**
   * Resets the outdated data.
   *
   * <p>One example of the outdated data, which needs to be reset, is nonces for self-report
   * requests. If verification codes issued for self-report requests expire, then the nonces for
   * those requests are deemed outdated and need to be reset to a default value.
   */
  @WorkerThread
  public void resetOutdatedData() {
    // Reset nonces for outdated requests for a verification code.
    verificationCodeRequestRepository.resetNonceForExpiredRequestsIfAny(clock.now());
  }

  /**
   * Cleans up some of the app storage, which becomes obsolete after EN has been turned down:
   * <ul>
   *   <li>everything from the database except for {@link DiagnosisEntity} and
   *   {@link ExposureCheckEntity}.</li>
   *   <li>private analytics and app analytics data from the
   *   {@link ExposureNotificationSharedPreferences}.</li>
   *   <li>possible exposure information after it expires.</li>
   * </ul>
   *
   * <p>This method <b>must</b> be called only in case of the EN turndown as it wipes out almost
   * all of the app storage.
   */
  @AnyThread
  public ListenableFuture<Void> deleteObsoleteStorageForTurnDown() {
    // If there is currently an active exposure present, schedule a worker to clean it up after
    // this exposure expires.
    if (exposureInformationHelper.isActiveExposurePresent()) {
      Duration daysUntilExposureExpires = exposureInformationHelper.getDaysUntilExposureExpires();
      ExposureInfoCleanupWorker.runOnceWithDelay(workManager, daysUntilExposureExpires);
    }
    return FluentFuture.from(analyticsLoggingRepository.deleteEventsBatchAsync())
        .transformAsync(
            unused -> countryRepository.deleteCountryEntitiesAsync(),
            backgroundExecutor)
        .transformAsync(
            unused -> diagnosisRepository.deleteAllRevisionTokensAsync(),
            backgroundExecutor)
        .transformAsync(
            unused -> downloadServerRepository.deleteDownloadServerEntitiesAsync(),
            backgroundExecutor)
        .transformAsync(
            unused -> exposureRepository.deleteExposureEntitiesAsync(),
            backgroundExecutor)
        .transformAsync(
            unused -> exposureCheckRepository.deleteExposureCheckEntitiesAsync(),
            backgroundExecutor
        )
        .transformAsync(
            unused -> verificationCodeRequestRepository.deleteVerificationCodeRequestEntities(),
            backgroundExecutor)
        .transformAsync(
            unused -> workerStatusRepository.deleteWorkerStatusEntities(),
            backgroundExecutor)
        .transformAsync(unused -> {
          // Also clean up private analytics data and the last analytics logging timestamp.
          exposureNotificationSharedPreferences.clearPrivateAnalyticsFields();
          exposureNotificationSharedPreferences.clearAnalyticsLoggingLastTimestamp();
          return Futures.immediateVoidFuture();
        }, backgroundExecutor);
  }

  /**
   * Cancels any pending job to notify users to reactivate exposure notification and dismisses
   * restore notification if currently showing.
   */
  @AnyThread
  public ListenableFuture<Void> cancelPendingRestoreNotificationsAndJob() {
    return FluentFuture.from(
        RestoreNotificationWorker.cancelRestoreNotificationWorkIfExisting(workManager).getResult())
        .transformAsync(unused -> {
          notificationHelper.dismissReActivateENNotificationIfShowing(context);
          return Futures.immediateVoidFuture();
        }, backgroundExecutor);
  }

}
