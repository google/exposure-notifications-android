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
import androidx.hilt.Assisted;
import androidx.hilt.work.WorkerInject;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ListenableWorker;
import androidx.work.NetworkType;
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
 */
public final class UploadCoverTrafficWorker extends ListenableWorker {

  private static final Logger logger = Logger.getLogger("UploadCoverTrafficWrk");
  private static final TimeUnit REPEAT_INTERVAL_UNITS = TimeUnit.HOURS;
  private static final int KEY_SIZE_BYTES = 16;
  private static final int FAKE_INTERVAL_NUM = 2650847; // Only size matters here, not the value.
  // The upper bound of the range for the randomly generated sleep time (in milliseconds) to mimic
  // delay in user interaction between submitting the verification code and submitting the keys.
  private static final int MIMIC_USER_DELAY_SLEEP_MILLIS_BOUND = 3000;

  @VisibleForTesting
  static final String WORKER_NAME = "UploadCoverTrafficWorker";
  @VisibleForTesting
  static final int REPEAT_INTERVAL = 4;
  @VisibleForTesting
  static final double EXECUTION_PROBABILITY = 1.0d / 12.0d;
  @VisibleForTesting
  static final double USER_REPORT_RPC_EXECUTION_PROBABILITY = 1.0d / 6.0d;

  private final UploadController uploadController;
  private final ExecutorService backgroundExecutor;
  private final ExecutorService lightweightExecutor;
  private final ListeningScheduledExecutorService scheduledExecutor;
  private final SecureRandom secureRandom;
  private final WorkerStartupManager workerStartupManager;

  /**
   * @param appContext   The application {@link Context}
   * @param workerParams Parameters to setup the internal state of this worker
   */
  @WorkerInject
  public UploadCoverTrafficWorker(
      @Assisted @NonNull Context appContext,
      @Assisted @NonNull WorkerParameters workerParams,
      UploadController uploadController,
      @BackgroundExecutor ExecutorService backgroundExecutor,
      @LightweightExecutor ExecutorService lightweightExecutor,
      @ScheduledExecutor ListeningScheduledExecutorService scheduledExecutor,
      SecureRandom secureRandom,
      WorkerStartupManager workerStartupManager) {
    super(appContext, workerParams);
    this.uploadController = uploadController;
    this.backgroundExecutor = backgroundExecutor;
    this.lightweightExecutor = lightweightExecutor;
    this.scheduledExecutor = scheduledExecutor;
    this.secureRandom = secureRandom;
    this.workerStartupManager = workerStartupManager;
  }

  @NonNull
  @Override
  public ListenableFuture<Result> startWork() {
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
                return Futures.immediateFuture(null);
              }
              return FluentFuture.from(maybeRequestCode())
                  .transformAsync(
                      unused -> uploadController.submitCode(fakeCodeRequest()),
                      backgroundExecutor)
                  .transformAsync(
                      upload -> scheduledExecutor
                          .schedule(() -> uploadController.submitKeysForCert(fakeCertRequest()),
                              Duration.ofMillis(
                                  secureRandom.nextInt(MIMIC_USER_DELAY_SLEEP_MILLIS_BOUND + 1))
                                  .toMillis(),
                              TimeUnit.MILLISECONDS),
                      backgroundExecutor)
                  .transformAsync(
                      upload -> uploadController.upload(fakeKeyUpload()),
                      backgroundExecutor);
            },
            lightweightExecutor)
        // Report success or failure.
        .transform(unused -> Result.success(), lightweightExecutor)
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
   * @return true the execution should happen and false otherwise.
   */
  private boolean shouldExecute(double executionProbability) {
    return secureRandom.nextDouble() < executionProbability;
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
}
