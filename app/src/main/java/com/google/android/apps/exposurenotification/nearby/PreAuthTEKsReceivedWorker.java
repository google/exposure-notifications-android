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
import android.text.TextUtils;
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
import com.google.android.apps.exposurenotification.common.Qualifiers.BackgroundExecutor;
import com.google.android.apps.exposurenotification.common.Qualifiers.LightweightExecutor;
import com.google.android.apps.exposurenotification.common.SecureRandomUtil;
import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.android.apps.exposurenotification.keyupload.Upload;
import com.google.android.apps.exposurenotification.keyupload.UploadController;
import com.google.android.apps.exposurenotification.keyupload.UploadController.NoInternetException;
import com.google.android.apps.exposurenotification.keyupload.UploadController.UploadException;
import com.google.android.apps.exposurenotification.keyupload.UploadError;
import com.google.android.apps.exposurenotification.network.DiagnosisKey;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity.Shared;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity.TestResult;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity.TravelStatus;
import com.google.android.apps.exposurenotification.storage.DiagnosisRepository;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.gms.nearby.exposurenotification.TemporaryExposureKey;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;
import java.security.SecureRandom;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Performs work for {@link com.google.android.gms.nearby.exposurenotification.ExposureNotificationClient#ACTION_PRE_AUTHORIZE_RELEASE_PHONE_UNLOCKED}
 * broadcast from the Exposure Notifications API.
 */
@HiltWorker
public class PreAuthTEKsReceivedWorker extends ListenableWorker {

  private static final long WORK_REQUEST_BACK_OFF_DELAY_MINUTES = 30;
  private static final long WORK_REQUEST_INITIAL_DELAY_SECONDS = 10;

  static final String KEYS_BYTES = "PreAuthTEKsReceivedWorker.KEYS_BYTES";
  @VisibleForTesting static final String TEKS_RECEIVED_WORKER_TAG =
      "PreAuthTEKsReceivedWorker.TEKS_RECEIVED_WORKER_TAG";

  private final DiagnosisRepository diagnosisRepository;
  private final UploadController uploadController;
  private final ExecutorService backgroundExecutor;
  private final ExecutorService lightweightExecutor;
  private final SecureRandom secureRandom;
  private final Clock clock;
  private final ExposureNotificationSharedPreferences exposureNotificationSharedPreferences;

  @AssistedInject
  public PreAuthTEKsReceivedWorker(
      @Assisted @NonNull Context context,
      @Assisted @NonNull WorkerParameters workerParams,
      ExposureNotificationSharedPreferences exposureNotificationSharedPreferences,
      DiagnosisRepository diagnosisRepository,
      UploadController uploadController,
      @BackgroundExecutor ExecutorService backgroundExecutor,
      @LightweightExecutor ExecutorService lightweightExecutor,
      SecureRandom secureRandom,
      Clock clock) {
    super(context, workerParams);
    this.exposureNotificationSharedPreferences = exposureNotificationSharedPreferences;
    this.diagnosisRepository = diagnosisRepository;
    this.uploadController = uploadController;
    this.backgroundExecutor = backgroundExecutor;
    this.lightweightExecutor = lightweightExecutor;
    this.secureRandom = secureRandom;
    this.clock = clock;
  }

  @NonNull
  @Override
  public ListenableFuture<Result> startWork() {
    byte[] keysBytes = getInputData().getByteArray(KEYS_BYTES);
    if (keysBytes == null || keysBytes.length == 0) {
      return Futures.immediateFuture(Result.failure());
    }

    Optional<List<DiagnosisKey>> optionalDiagnosisKeys =
        TemporaryExposureKeyHelper.maybeBytesToDiagnosisKeys(keysBytes);
    if (!optionalDiagnosisKeys.isPresent()) {
      return Futures.immediateFuture(Result.failure());
    }
    List<DiagnosisKey> diagnosisKeys = optionalDiagnosisKeys.get();

    return FluentFuture.from(diagnosisRepository.maybeGetLastPreAuthDiagnosisAsync())
        .transformAsync(
            // Construct an Upload from the latest available pre-auth diagnosis.
            optionalDiagnosis -> {
              if (!optionalDiagnosis.isPresent()) {
                return Futures.immediateFailedFuture(new NoPreAuthDiagnosisException());
              }
              DiagnosisEntity diagnosis = optionalDiagnosis.get();
              Upload upload =
                  Upload.newBuilder(
                          diagnosisKeys,
                          diagnosis.getVerificationCode(),
                          SecureRandomUtil.newHmacKey(secureRandom))
                      .setLongTermToken(diagnosis.getLongTermToken())
                      .setSymptomOnset(diagnosis.getOnsetDate())
                      .setCertificate(diagnosis.getCertificate())
                      .setHasTraveled(TravelStatus.TRAVELED.equals(diagnosis.getTravelStatus()))
                      .build();
              return Futures.immediateFuture(upload);
            },
            lightweightExecutor)
        .transformAsync(
            upload -> {
              // We normally do not have a certificate yet, but in some cases like resuming a past
              // failed upload, we have one already. Get one if we need one.
              if (TextUtils.isEmpty(upload.certificate())) {
                return uploadController.submitKeysForCert(upload);
              }
              return Futures.immediateFuture(upload);
            },
            backgroundExecutor)
        .transformAsync(this::addRevisionTokenToUpload, lightweightExecutor)
        .transformAsync(
            // Finally, upload the keys.
            uploadController::upload, backgroundExecutor)
        .transformAsync(
            upload -> {
              // Store in the preferences that keys have been successfully uploaded and the
              // associated report type.
              exposureNotificationSharedPreferences.setPrivateAnalyticsLastSubmittedKeysTime(
                  clock.now());

              TestResult testResult = null;
              try {
                testResult = TestResult.of(upload.testType());
              } catch (IllegalArgumentException | NullPointerException e) {
                // Do nothing: testResult is already null, which is the right behavior
              }
              exposureNotificationSharedPreferences.setPrivateAnalyticsLastReportType(testResult);
              return saveDiagnosis(
                  diagnosis ->
                      diagnosis.toBuilder()
                          .setCertificate(upload.certificate())
                          .setRevisionToken(upload.revisionToken())
                          .setSharedStatus(Shared.SHARED)
                          .build());
            },
            backgroundExecutor)
        .transformAsync(diagnosisId -> {
          if (!diagnosisId.isPresent()) {
            // We should never get here but if we somehow do, throw an exception.
            return Futures.immediateFailedFuture(new NoPreAuthDiagnosisException());
          }
          // Return the diagnosis ID again to signify the successful work completion.
          return Futures.immediateFuture(diagnosisId.get());
        }, lightweightExecutor)
        .transform(diagnosisId -> Result.success(), lightweightExecutor)
        .catching(
            // If no pre-auth diagnosis has been found, attempt to do the work a bit later. The
            // pre-auth diagnosis might be missing because this worker was fired off (in response to
            // receiving TEKs from the EN module) too quickly after the request to release TEKs.
            NoPreAuthDiagnosisException.class,
            ex -> Result.retry(),
            lightweightExecutor
        )
        .catching(
            // If the upload failed because there was no internet connection, do the retry.
            NoInternetException.class, ex -> Result.retry(), lightweightExecutor)
        .catching(
            // If the upload failed with a server error that might be fixed upon retrying, do the
            // retry.
            UploadException.class,
            ex -> {
              if (UploadError.RATE_LIMITED.equals(ex.getUploadError())
                  || UploadError.SERVER_ERROR.equals(ex.getUploadError())) {
                return Result.retry();
              }
              return Result.failure();
            },
            lightweightExecutor)
        .catching(
            Exception.class,
            // As this is the background upload, there's nothing we can do in case of other
            // exceptions. So, fail silently.
            ex -> Result.failure(),
            lightweightExecutor);
  }

  /**
   * Adds a revision token to the provided {@link Upload} object and returns that updated object.
   */
  private ListenableFuture<Upload> addRevisionTokenToUpload(Upload upload) {
    return FluentFuture.from(diagnosisRepository.getMostRecentRevisionTokenAsync())
        .transform(
            revisionToken -> upload.toBuilder().setRevisionToken(revisionToken).build(),
            backgroundExecutor);
  }

  /**
   * Updates the existing pre-auth diagnosis with a provided mutator (and does nothing if such
   * diagnosis does not exist).
   */
  private ListenableFuture<Optional<Long>> saveDiagnosis(
      Function<DiagnosisEntity, DiagnosisEntity> mutator) {
    return FluentFuture.from(diagnosisRepository.maybeGetLastPreAuthDiagnosisAsync())
        // Apply the given mutation
        .transform(
            optionalDiagnosis -> {
              if (optionalDiagnosis.isPresent()) {
                return Optional.of(diagnosisRepository
                    .createOrMutateById(optionalDiagnosis.get().getId(), mutator));
              }
              // We should never get here as this method is never called if there's no pre-auth
              // diagnosis available.
              return Optional.absent();
            },
            backgroundExecutor);
  }

  /**
   * An {@link Exception} thrown to signify that there is no pre-auth diagnosis available.
   */
  public static class NoPreAuthDiagnosisException extends Exception {}

  static void runOnce(WorkManager workManager, List<TemporaryExposureKey> inputTEKs) {
    Data inputData = new Data.Builder()
        .putByteArray(KEYS_BYTES, TemporaryExposureKeyHelper.keysToTEKExportBytes(inputTEKs))
        .build();
    // Enqueue the work.
    workManager.enqueue(
        new OneTimeWorkRequest.Builder(PreAuthTEKsReceivedWorker.class)
            .addTag(TEKS_RECEIVED_WORKER_TAG)
            .setConstraints(
                new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WORK_REQUEST_BACK_OFF_DELAY_MINUTES,
                TimeUnit.MINUTES)
            .setInputData(inputData)
            .setInitialDelay(WORK_REQUEST_INITIAL_DELAY_SECONDS, TimeUnit.SECONDS)
            .build());
  }

}
