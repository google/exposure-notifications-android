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
import android.util.Log;
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
import com.google.android.apps.exposurenotification.common.StringUtils;
import com.google.android.apps.exposurenotification.logging.AnalyticsLogger;
import com.google.android.apps.exposurenotification.network.DiagnosisKey;
import com.google.android.apps.exposurenotification.proto.WorkManagerTask.WorkerTask;
import com.google.android.apps.exposurenotification.work.WorkerStartupManager;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.security.SecureRandom;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;
import org.threeten.bp.LocalDate;

/**
 * A worker that uploads a random number of fake Diagnosis Key uploads each day.
 *
 * <p>Somewhat random operation is achieved two ways: using WorkManager's flex interval to operate
 * within a loose time period, and by skipping some executions at random.
 */
public final class UploadCoverTrafficWorker extends ListenableWorker {

  private static final String TAG = "UploadCoverTrafficWrk";
  @VisibleForTesting
  static final String WORKER_NAME = "UploadCoverTrafficWorker";
  @VisibleForTesting
  static final int REPEAT_INTERVAL = 4;
  private static final TimeUnit REPEAT_INTERVAL_UNITS = TimeUnit.HOURS;
  @VisibleForTesting
  static final double EXECUTION_PROBABILITY = 1.0d / 12.0d;
  private static final int KEY_SIZE_BYTES = 16;
  private static final int FAKE_INTERVAL_NUM = 2650847; // Only size matters here, not the value.

  private final UploadController uploadController;
  private final ExecutorService backgroundExecutor;
  private final ExecutorService lightweightExecutor;
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
      SecureRandom secureRandom,
      WorkerStartupManager workerStartupManager) {
    super(appContext, workerParams);
    this.uploadController = uploadController;
    this.backgroundExecutor = backgroundExecutor;
    this.lightweightExecutor = lightweightExecutor;
    this.secureRandom = secureRandom;
    this.workerStartupManager = workerStartupManager;
  }

  @NonNull
  @Override
  public ListenableFuture<Result> startWork() {
    if (!shouldExecute()) {
      // We skip execution with random probability.
      return Futures.immediateFuture(Result.success());
    }

    // First see if the API is enabled in the first place.
    ListenableFuture<Result> listenableFuture = FluentFuture.from(
        workerStartupManager.getIsEnabledWithStartupTasks())
        .transformAsync(
            isEnabled -> {
              if (!isEnabled) {
                // If the API is not enabled, skip the upload.
                return Futures.immediateFuture(null);
              }
              return FluentFuture.from(uploadController.submitCode(fakeCodeRequest()))
                  .transformAsync(
                      upload -> uploadController.submitKeysForCert(fakeCertRequest()),
                      backgroundExecutor)
                  .transformAsync(
                      upload -> uploadController.upload(fakeKeyUpload()),
                      backgroundExecutor);
            },
            lightweightExecutor)
        // Report success or failure.
        .transform(unused -> Result.success(), lightweightExecutor)
        .catching(Throwable.class, t -> Result.failure(), lightweightExecutor);
    return listenableFuture;
  }

  private static Upload fakeCodeRequest() {
    return Upload.newBuilder("FAKE-VALIDATION-CODE")
        .setIsCoverTraffic(true)
        .build();
  }

  private Upload fakeCertRequest() {
    return Upload.newBuilder("FAKE-VALIDATION-CODE")
        .setIsCoverTraffic(true)
        .setKeys(fakeKeys())
        .setLongTermToken(StringUtils.randomBase64Data(100))
        .build();
  }

  private Upload fakeKeyUpload() {
    return Upload.newBuilder("FAKE-VALIDATION-CODE")
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

  private boolean shouldExecute() {
    return secureRandom.nextDouble() < EXECUTION_PROBABILITY;
  }

  public static Operation schedule(WorkManager workManager) {
    Log.i(TAG, "Scheduling periodic WorkManager job...");
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
