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

import android.content.Context;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.hilt.work.HiltWorker;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ListenableWorker;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkerParameters;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.IntentUtil;
import com.google.android.apps.exposurenotification.common.NotificationHelper;
import com.google.android.apps.exposurenotification.common.Qualifiers.BackgroundExecutor;
import com.google.android.apps.exposurenotification.common.Qualifiers.LightweightExecutor;
import com.google.android.apps.exposurenotification.common.Qualifiers.ScheduledExecutor;
import com.google.android.apps.exposurenotification.common.SecureRandomUtil;
import com.google.android.apps.exposurenotification.common.TaskToFutureAdapter;
import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.android.apps.exposurenotification.keyupload.Upload;
import com.google.android.apps.exposurenotification.keyupload.UploadController;
import com.google.android.apps.exposurenotification.keyupload.UploadController.NoInternetException;
import com.google.android.apps.exposurenotification.keyupload.UploadController.UploadException;
import com.google.android.apps.exposurenotification.keyupload.UploadError;
import com.google.android.apps.exposurenotification.notify.ShareDiagnosisFlowHelper;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity.HasSymptoms;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity.Shared;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity.TestResult;
import com.google.android.apps.exposurenotification.storage.DiagnosisRepository;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.apps.exposurenotification.work.WorkerStartupManager;
import com.google.android.apps.exposurenotification.work.WorkerStartupManager.IsEnabledWithStartupTasksException;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.nearby.exposurenotification.TemporaryExposureKey;
import com.google.common.base.Optional;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;
import java.security.SecureRandom;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.threeten.bp.Duration;

/**
 * Performs work for {@link ExposureNotificationClientWrapper#ACTION_VERIFICATION_LINK}
 * broadcast from the Exposure Notifications API.
 */
@HiltWorker
public class SmsVerificationWorker extends ListenableWorker {

  private static final Duration REQUEST_PRE_AUTH_TEKS_RELEASE_API_TIMEOUT = Duration.ofSeconds(10);
  private static final long WORK_REQUEST_BACK_OFF_DELAY_MINUTES = 30;

  static final String DEEP_LINK_URI_STRING = "SmsReceivedWorker.DEEP_LINK_URI_STRING";
  static final String SMS_RECEIVED_WORKER_TAG = "SMS_RECEIVED_WORKER_TAG";

  private final ExposureNotificationClientWrapper exposureNotificationClientWrapper;
  private final DiagnosisRepository diagnosisRepository;
  private final UploadController uploadController;
  private final NotificationHelper notificationHelper;
  private final ExecutorService backgroundExecutor;
  private final ExecutorService lightweightExecutor;
  private final ListeningScheduledExecutorService scheduledExecutor;
  private final SecureRandom secureRandom;
  private final WorkerStartupManager workerStartupManager;
  private final Clock clock;
  private final ExposureNotificationSharedPreferences exposureNotificationSharedPreferences;

  @AssistedInject
  public SmsVerificationWorker(
      @Assisted @NonNull Context context,
      @Assisted @NonNull WorkerParameters workerParams,
      ExposureNotificationClientWrapper exposureNotificationClientWrapper,
      ExposureNotificationSharedPreferences exposureNotificationSharedPreferences,
      DiagnosisRepository diagnosisRepository,
      UploadController uploadController,
      NotificationHelper notificationHelper,
      @BackgroundExecutor ExecutorService backgroundExecutor,
      @LightweightExecutor ExecutorService lightweightExecutor,
      @ScheduledExecutor ListeningScheduledExecutorService scheduledExecutor,
      SecureRandom secureRandom,
      WorkerStartupManager workerStartupManager,
      Clock clock) {
    super(context, workerParams);
    this.exposureNotificationClientWrapper = exposureNotificationClientWrapper;
    this.exposureNotificationSharedPreferences = exposureNotificationSharedPreferences;
    this.diagnosisRepository = diagnosisRepository;
    this.uploadController = uploadController;
    this.notificationHelper = notificationHelper;
    this.backgroundExecutor = backgroundExecutor;
    this.lightweightExecutor = lightweightExecutor;
    this.scheduledExecutor = scheduledExecutor;
    this.secureRandom = secureRandom;
    this.workerStartupManager = workerStartupManager;
    this.clock = clock;
  }

  @NonNull
  @Override
  public ListenableFuture<Result> startWork() {
    String deepLinkUriString = getInputData().getString(DEEP_LINK_URI_STRING);
    if (deepLinkUriString == null) {
      return Futures.immediateFuture(Result.success());
    }

    Uri deepLinkUri = Uri.parse(deepLinkUriString);
    if (deepLinkUri == null || Uri.EMPTY.equals(deepLinkUri)) {
      return Futures.immediateFuture(Result.success());
    }

    if (!ShareDiagnosisFlowHelper.isSmsInterceptEnabled(getApplicationContext())) {
      return Futures.immediateFuture(Result.success());
    }

    Optional<String> optionalCode = IntentUtil.maybeGetCodeFromDeepLinkUri(deepLinkUri);
    return FluentFuture.from(workerStartupManager.getIsEnabledWithStartupTasks())
        .transformAsync(
            isEnabled -> {
              if (!isEnabled) {
                // If the API is not enabled, don't attempt to do the background upload. Instead
                // show the notification that intents into the sharing flow, where user will be able
                // to enable the API if needed.
                return Futures.immediateFailedFuture(new TEKsNotReleasedException());
              }
              return FluentFuture.from(requestPreAuthorizedTemporaryExposureKeyRelease())
                  .catchingAsync(
                      ApiException.class,
                      // If there's an ApiException from the GMSCore upon request to release keys,
                      // this means that keys cannot be released and automatic upload is not
                      // possible. So, display the notification instead.
                      ex -> Futures.immediateFailedFuture(new TEKsNotReleasedException()),
                      lightweightExecutor);
            },
            lightweightExecutor)
        .transformAsync(unused -> submitCode(optionalCode), lightweightExecutor)
        .transform(unused -> Result.success(), lightweightExecutor)
        .catching(
            VerificationServerRetryException.class, ex -> Result.retry(), lightweightExecutor)
        .catching(
            VerificationCodeExistsException.class, ex -> Result.success(), lightweightExecutor)
        .catching(
            TEKsNotReleasedException.class,
            ex -> {
              // Could not release TEKs, so show the notification instead.
              notificationHelper.showNotification(
                  getApplicationContext(),
                  R.string.notify_others_notification_title,
                  R.string.enx_testVerificationNotificationBody,
                  IntentUtil.getNotificationContentIntentSmsVerification(
                      getApplicationContext(), deepLinkUri),
                  IntentUtil.getNotificationDeleteIntentSmsVerification(getApplicationContext()));
              return Result.success();
            },
            lightweightExecutor)
        .catching(IsEnabledWithStartupTasksException.class, e -> Result.failure(),
            lightweightExecutor)
        .catching(Throwable.class, t -> Result.failure(), lightweightExecutor);
  }

  @VisibleForTesting
  ListenableFuture<?> submitCode(Optional<String> optionalCode) {
    if (!optionalCode.isPresent()) {
      // If no code is present, then don't attempt to do the upload and don't show the notification:
      // just let the worker fail silently with generic exception.
      return Futures.immediateFailedFuture(new Exception());
    }

    String code = optionalCode.get();
    return FluentFuture.from(diagnosisRepository.getByVerificationCodeAsync(code))
        .transformAsync(
            diagnosisEntities -> {
              if (!diagnosisEntities.isEmpty()) {
                // This code has been already submitted and verified. Complete the flow immediately.
                return Futures.immediateFailedFuture(new VerificationCodeExistsException());
              }
              Upload.Builder uploadBuilder =
                  Upload.newBuilder(code, SecureRandomUtil.newHmacKey(secureRandom));
              // Submit the verification code to the verification server
              return uploadController.submitCode(uploadBuilder.build());
            },
            lightweightExecutor)
        .transformAsync(
            verifiedUpload -> {
              // If successful, capture the long term token and some diagnosis facts into storage.
              DiagnosisEntity.Builder builder =
                  DiagnosisEntity.newBuilder()
                      .setVerificationCode(code)
                      .setIsCodeFromLink(false)
                      .setIsPreAuth(true)
                      .setSharedStatus(Shared.NOT_ATTEMPTED);
              // The long term token is required.
              builder.setLongTermToken(verifiedUpload.longTermToken());
              // Symptom onset may or may not be provided by the verification server.
              if (verifiedUpload.symptomOnset() != null) {
                builder.setIsServerOnsetDate(true);
                builder.setOnsetDate(verifiedUpload.symptomOnset()).setHasSymptoms(HasSymptoms.YES);
              }
              // Test type is currently always provided by the verification server, but that seems
              // like something that could change. Let's check.
              if (verifiedUpload.testType() != null) {
                builder.setTestResult(TestResult.of(verifiedUpload.testType()));
              }
              // Store in the preferences that a verification code has been successfully uploaded.
              exposureNotificationSharedPreferences.setPrivateAnalyticsLastSubmittedCodeTime(
                  clock.now());
              return diagnosisRepository.upsertAsync(builder.build());
            },
            backgroundExecutor)
        .transform(unusedNewDiagnosisId -> null, lightweightExecutor)
        .catchingAsync(
            // If the upload failed because there was no internet connection, trigger retry by
            // throwing VerificationServerRetryException.
            NoInternetException.class,
            ex -> Futures.immediateFailedFuture(new VerificationServerRetryException()),
            lightweightExecutor)
        .catchingAsync(
            // If the upload failed with a server error that might be fixed upon retrying, trigger
            // retry by throwing VerificationServerRetryException.
            UploadException.class,
            ex -> {
              if (UploadError.RATE_LIMITED.equals(ex.getUploadError())
                  || UploadError.SERVER_ERROR.equals(ex.getUploadError())) {
                return Futures.immediateFailedFuture(new VerificationServerRetryException());
              }
              return Futures.immediateFailedFuture(new TEKsNotReleasedException());
            },
            lightweightExecutor);
  }

  /**
   * Requests to release {@link TemporaryExposureKey}s in the background.
   */
  private ListenableFuture<Void> requestPreAuthorizedTemporaryExposureKeyRelease() {
    return TaskToFutureAdapter.getFutureWithTimeout(
        exposureNotificationClientWrapper.requestPreAuthorizedTemporaryExposureKeyRelease(),
        REQUEST_PRE_AUTH_TEKS_RELEASE_API_TIMEOUT,
        scheduledExecutor);
  }

  /**
   * An {@link Exception} thrown when we failed to retrieve the TEKs from the EN Module.
   */
  private static class TEKsNotReleasedException extends Exception {}

  /**
   * An {@link Exception} thrown when a request to the VerificationServer has failed but we can
   * retry sending it.
   */
  private static class VerificationServerRetryException extends Exception {}

  /**
   * An {@link Exception} thrown when we were trying to submit a code that has been already
   * submitted and verified.
   */
  private static class VerificationCodeExistsException extends Exception {}

  static void runOnce(WorkManager workManager, Uri deepLinkUri) {
    Data inputData = new Data.Builder()
        .putString(DEEP_LINK_URI_STRING, deepLinkUri.toString())
        .build();
    workManager.enqueue(
        new OneTimeWorkRequest.Builder(SmsVerificationWorker.class)
            .addTag(SMS_RECEIVED_WORKER_TAG)
            .setConstraints(
                new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WORK_REQUEST_BACK_OFF_DELAY_MINUTES,
                TimeUnit.MINUTES)
            .setInputData(inputData)
            .build());
  }

}