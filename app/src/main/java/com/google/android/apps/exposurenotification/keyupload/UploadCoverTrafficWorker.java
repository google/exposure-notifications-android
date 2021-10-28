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

package com.google.android.apps.exposurenotification.keyupload;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.hilt.work.HiltWorker;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.ListenableWorker;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.Operation;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkerParameters;
import com.google.android.apps.exposurenotification.common.Qualifiers.BackgroundExecutor;
import com.google.android.apps.exposurenotification.common.Qualifiers.LightweightExecutor;
import com.google.android.apps.exposurenotification.common.Qualifiers.ScheduledExecutor;
import com.google.android.apps.exposurenotification.common.SecureRandomUtil;
import com.google.android.apps.exposurenotification.common.StringUtils;
import com.google.android.apps.exposurenotification.common.logging.Logger;
import com.google.android.apps.exposurenotification.network.DiagnosisKey;
import com.google.android.apps.exposurenotification.work.WorkerStartupManager;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;
import java.security.SecureRandom;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.threeten.bp.Duration;
import org.threeten.bp.LocalDate;

/**
 * A worker that somewhat randomly executes fake requests to the Verification and Key servers.
 *
 * <p>Somewhat random operation is achieved by skipping some executions of the worker at random.
 * <p>Currently, this worker is executed with a probability of once every 2 days.
 * <p>Out of 4 RPC calls made by this worker when the worker gets executed, one RPC call to request
 * a verification code is made with an additional probability (which is once over each 6 actual
 * non-skipped executions of the worker).
 * <p>Finally, this worker also mimics the delay between submitting the verification code and
 * submitting the keys: a short delay (up to 10s) in case of the user interaction and a long delay
 * (up to 24 hours) in case of the background upload flow triggered by the pre-auth flow.
 */
@HiltWorker
public final class UploadCoverTrafficWorker extends ListenableWorker {

  private static final Logger logger = Logger.getLogger("UploadCoverTrafficWrk");
  private static final TimeUnit REPEAT_INTERVAL_UNITS = TimeUnit.HOURS;
  private static final int KEY_SIZE_BYTES = 16;
  private static final int FAKE_INTERVAL_NUM = 2650847; // Only size matters here, not the value.
  // The upper bound of the range for the randomly generated sleep time (in milliseconds) to mimic
  // a short delay between submitting the code and submitting the keys.
  private static final Duration MIMIC_USER_DELAY_SLEEP_MAX = Duration.ofMillis(10000L);
  // The upper bound of the range for the randomly generated sleep time (in hours) to mimic a long
  // delay between submitting the code and submitting the keys.
  private static final Duration KEYS_UPLOAD_DELAY_MAX = Duration.ofHours(25L);

  @VisibleForTesting
  static final String WORKER_NAME = "UploadCoverTrafficWorker";
  @VisibleForTesting
  static final int REPEAT_INTERVAL = 4;
  @VisibleForTesting
  static final double EXECUTION_PROBABILITY = 1.0d / 12.0d;
  @VisibleForTesting
  static final double USER_REPORT_RPC_EXECUTION_PROBABILITY = 1.0d / 6.0d;
  @VisibleForTesting
  static final double SHORT_DELAY_KEYS_UPLOAD_PROBABILITY = 4.0d / 5.0d;
  // The threshold for the longer delay between the code and keys submission. Delays above this
  // threshold should skip the keys submission.
  @VisibleForTesting
  static final Duration KEYS_UPLOAD_DELAY_THRESHOLD = Duration.ofHours(24L);
  static final String IS_DELAYED_EXECUTION = "UploadCoverTrafficWorker.IS_DELAYED_EXECUTION";

  private final UploadController uploadController;
  private final ExecutorService backgroundExecutor;
  private final ExecutorService lightweightExecutor;
  private final ListeningScheduledExecutorService scheduledExecutor;
  private final SecureRandom secureRandom;
  private final WorkerStartupManager workerStartupManager;
  private final WorkManager workManager;

  /**
   * @param appContext   The application {@link Context}
   * @param workerParams Parameters to setup the internal state of this worker
   */
  @AssistedInject
  public UploadCoverTrafficWorker(
      @Assisted @NonNull Context appContext,
      @Assisted @NonNull WorkerParameters workerParams,
      UploadController uploadController,
      @BackgroundExecutor ExecutorService backgroundExecutor,
      @LightweightExecutor ExecutorService lightweightExecutor,
      @ScheduledExecutor ListeningScheduledExecutorService scheduledExecutor,
      SecureRandom secureRandom,
      WorkerStartupManager workerStartupManager,
      WorkManager workManager) {
    super(appContext, workerParams);
    this.uploadController = uploadController;
    this.backgroundExecutor = backgroundExecutor;
    this.lightweightExecutor = lightweightExecutor;
    this.scheduledExecutor = scheduledExecutor;
    this.secureRandom = secureRandom;
    this.workerStartupManager = workerStartupManager;
    this.workManager = workManager;
  }

  @NonNull
  @Override
  public ListenableFuture<Result> startWork() {
    boolean isDelayedExecution =
        getInputData() != null && getInputData().getBoolean(IS_DELAYED_EXECUTION, false);
    if (isDelayedExecution) {
      // If this worker has been fired to run once to imitate a longer delay between calls to submit
      // code and to submit keys, then submit the keys now.
      return FluentFuture.from(uploadController.submitKeysForCert(fakeCertRequest()))
          .transformAsync(
              upload -> uploadController.upload(fakeKeyUpload()),
              backgroundExecutor)
          // Report success or failure.
          .transform(unused -> Result.success(), lightweightExecutor)
          .catching(Throwable.class, t -> Result.failure(), lightweightExecutor);
    }

    if (!shouldExecute(EXECUTION_PROBABILITY)) {
      // We skip execution with random probability.
      return Futures.immediateFuture(Result.success());
    }

    return FluentFuture.from(
        // First see if the API is enabled in the first place.
        workerStartupManager.getIsEnabledWithStartupTasks())
        .transformAsync(
            isEnabled -> {
              if (!isEnabled) {
                // If the API is not enabled, skip the upload.
                return Futures.immediateFailedFuture(new FinishWorkerEarlyException());
              }
              return FluentFuture.from(maybeRequestCode())
                  .transformAsync(
                      unused -> uploadController.submitCode(fakeCodeRequest()),
                      backgroundExecutor)
                  .transformAsync(
                      upload -> {
                        if (shouldExecute(SHORT_DELAY_KEYS_UPLOAD_PROBABILITY)) {
                          // Have a short delay between the code and keys submission.
                          return scheduledExecutor.schedule(
                              () -> uploadController.submitKeysForCert(fakeCertRequest()),
                              Duration.ofMillis(
                                      secureRandom.nextInt(
                                          (int) MIMIC_USER_DELAY_SLEEP_MAX.toMillis() + 1))
                                  .toMillis(),
                              TimeUnit.MILLISECONDS);
                        }
                        // Have a long delay between the code and keys submission.
                        long longDelaySecs = getLongDelayInSecs();
                        // Finish early if the long delay calculated above is more than a threshold.
                        if (!triggerOneTimeExecutionAfterLongDelay(longDelaySecs)) {
                          return Futures.immediateFailedFuture(new FinishWorkerEarlyException());
                        }
                        return FluentFuture.from(runOnce(workManager, longDelaySecs).getResult())
                            .transformAsync(
                                unused ->
                                    Futures.immediateFailedFuture(new FinishWorkerEarlyException()),
                                lightweightExecutor);
                      },
                      backgroundExecutor)
                  .transformAsync(
                      upload -> uploadController.upload(fakeKeyUpload()),
                      backgroundExecutor);
            },
            lightweightExecutor)
        // Report success or failure.
        .transform(unused -> Result.success(), lightweightExecutor)
        .catching(FinishWorkerEarlyException.class, ex -> Result.success(), lightweightExecutor)
        .catching(Throwable.class, t -> Result.failure(), lightweightExecutor);
  }

  private ListenableFuture<?> maybeRequestCode() {
    if (!shouldExecute(USER_REPORT_RPC_EXECUTION_PROBABILITY)) {
      // We skip execution of the RPC call to request a verification code with random probability.
      return Futures.immediateVoidFuture();
    }
    return uploadController.requestCode(fakeUserReportRequest());
  }

  private UserReportUpload fakeUserReportRequest() {
    return UserReportUpload.newBuilder("FAKE-PHONE-NUMBER", SecureRandomUtil.newNonce(secureRandom),
        LocalDate.now(), /* tzOffsetMin= */0L)
        .setIsCoverTraffic(true)
        .build();
  }

  private Upload fakeCodeRequest() {
    return Upload.newBuilder("FAKE-VALIDATION-CODE", SecureRandomUtil.newHmacKey(secureRandom))
        .setIsCoverTraffic(true)
        .build();
  }

  private Upload fakeCertRequest() {
    return Upload.newBuilder("FAKE-VALIDATION-CODE", SecureRandomUtil.newHmacKey(secureRandom))
        .setIsCoverTraffic(true)
        .setKeys(fakeKeys())
        .setLongTermToken(StringUtils.randomBase64Data(100))
        .build();
  }

  private Upload fakeKeyUpload() {
    return Upload.newBuilder("FAKE-VALIDATION-CODE", SecureRandomUtil.newHmacKey(secureRandom))
        .setIsCoverTraffic(true)
        .setKeys(fakeKeys())
        .setRegions(ImmutableList.of("US", "CA"))
        // The size of these random blobs doesn't actually matter much, since we're going to pad out
        // the whole request to a consistent size anyway.
        .setHmacKeyBase64(StringUtils.randomBase64Data(32))
        .setLongTermToken(StringUtils.randomBase64Data(100))
        .setCertificate(StringUtils.randomBase64Data(100))
        .setSymptomOnset(LocalDate.of(2020, 1, 1))
        .build();
  }

  private List<DiagnosisKey> fakeKeys() {
    Builder<DiagnosisKey> keys = ImmutableList.builder();
    // Build up 14 random diagnosis keys.
    for (int i = 0; i < 14; i++) {
      byte[] bytes = new byte[KEY_SIZE_BYTES];
      secureRandom.nextBytes(bytes);
      keys.add(
          DiagnosisKey.newBuilder()
              .setKeyBytes(bytes)
              // Accepting the default rolling period that the DiagnosisKey.Builder comes with.
              .setTransmissionRisk(i % 7)
              .setIntervalNumber(FAKE_INTERVAL_NUM)
              .build());
    }
    return keys.build();
  }

  /**
   * Determines whether the execution (e.g. of a chain of RPC calls or a single RPC call in this
   * worker) should happen depending on the provided probability of execution.
   *
   * <p>The execution should happen if a randomly generated number is less than provided
   * executionProbability.
   *
   * @param executionProbability probability of execution
   * @return true if the execution should happen and false otherwise.
   */
  private boolean shouldExecute(double executionProbability) {
    return secureRandom.nextDouble() < executionProbability;
  }

  /**
   * Determines whether the one-time execution of this worker should happen.
   *
   * <p>We want to skip some one-time executions of this worker to simulate real life scenarios,
   * where during the background submission of the test result, the requests to submit the keys may
   * never get triggered after a request to verify the verification code.
   *
   * <p>The one-time worker execution should happen only if the long delay is less than or equal to
   * (<=) 24 * 60 * 60 seconds.
   *
   * @param longDelaySecs long delay value (in seconds)
   * @return true if the one-time execution should happen and false otherwise
   */
  private boolean triggerOneTimeExecutionAfterLongDelay(long longDelaySecs) {
    long longDelayThresholdSecs = KEYS_UPLOAD_DELAY_THRESHOLD.getSeconds();
    return longDelaySecs <= longDelayThresholdSecs;
  }

  /**
   * Picks the long delay between the code and keys submission from the range between 0s and 25
   * hours.
   *
   * @return the long delay between the code and keys submission, which is a pseudo-random uniformly
   *     distributed value in [0s, 25 * 60 * 60s]
   */
  private long getLongDelayInSecs() {
    return secureRandom.nextInt((int) KEYS_UPLOAD_DELAY_MAX.getSeconds() + 1);
  }

  /**
   * Runs the worker with an initial delay of a few hours to mimic the longer delay between calls to
   * submit the verification code and to submit the keys.
   *
   * <p>This method should be called only after the call to the /verify endpoint.
   *
   * <p>This method triggers calls to the /certificate and /publish endpoints (i.e. to submit keys).
   *
   * @param initialDelaySecs initial delay in seconds.
   */
  private Operation runOnce(WorkManager workManager, long initialDelaySecs) {
    Data inputData = new Data.Builder()
        .putBoolean(IS_DELAYED_EXECUTION, true)
        .build();
    OneTimeWorkRequest oneTimeWorkRequest =
        new OneTimeWorkRequest.Builder(UploadCoverTrafficWorker.class)
            .setConstraints(
                new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(inputData)
            .setInitialDelay(initialDelaySecs, TimeUnit.SECONDS)
            .build();
    return workManager.enqueueUniqueWork(
        WORKER_NAME, ExistingWorkPolicy.KEEP, oneTimeWorkRequest);
  }

  public static Operation schedule(WorkManager workManager) {
    logger.i("Scheduling periodic WorkManager job...");
    // WARNING: You must set ExistingPeriodicWorkPolicy.REPLACE if you want to change the params for
    //          previous app version users.
    PeriodicWorkRequest workRequest =
        new PeriodicWorkRequest.Builder(
            UploadCoverTrafficWorker.class,
            REPEAT_INTERVAL,
            REPEAT_INTERVAL_UNITS)
            .setConstraints(
                new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build();
    return workManager.enqueueUniquePeriodicWork(
        WORKER_NAME, ExistingPeriodicWorkPolicy.KEEP, workRequest);
  }

  /**
   * An {@link Exception} thrown when we want to finish the worker early.
   */
  private static class FinishWorkerEarlyException extends Exception {}
}
