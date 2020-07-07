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
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ListenableWorker;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;
import androidx.work.WorkerParameters;
import com.google.android.apps.exposurenotification.common.AppExecutors;
import com.google.android.apps.exposurenotification.common.TaskToFutureAdapter;
import com.google.android.apps.exposurenotification.network.DiagnosisKeys;
import com.google.android.apps.exposurenotification.storage.TokenEntity;
import com.google.android.apps.exposurenotification.storage.TokenRepository;
import com.google.common.io.BaseEncoding;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;
import org.threeten.bp.Duration;

/** Performs work to provide diagnosis keys to the exposure notifications API. */
public class ProvideDiagnosisKeysWorker extends ListenableWorker {

  private static final String TAG = "ProvideDiagnosisKeysWkr";

  private static final Duration IS_ENABLED_TIMEOUT = Duration.ofSeconds(10);
  public static final Duration JOB_INTERVAL = Duration.ofHours(24);
  public static final Duration JOB_FLEX_INTERVAL = Duration.ofHours(6);
  public static final String WORKER_NAME = "ProvideDiagnosisKeysWorker";
  private static final BaseEncoding BASE64_LOWER = BaseEncoding.base64();
  private static final int RANDOM_TOKEN_BYTE_LENGTH = 32;

  private final DiagnosisKeys diagnosisKeys;
  private final DiagnosisKeyFileSubmitter submitter;
  private final SecureRandom secureRandom;
  private final TokenRepository tokenRepository;

  public ProvideDiagnosisKeysWorker(
      @NonNull Context context, @NonNull WorkerParameters workerParams) {
    super(context, workerParams);
    diagnosisKeys = new DiagnosisKeys(context);
    submitter = new DiagnosisKeyFileSubmitter(context);
    secureRandom = new SecureRandom();
    tokenRepository = new TokenRepository(context);
  }

  private String generateRandomToken() {
    byte[] bytes = new byte[RANDOM_TOKEN_BYTE_LENGTH];
    secureRandom.nextBytes(bytes);
    return BASE64_LOWER.encode(bytes);
  }

  @NonNull
  @Override
  public ListenableFuture<Result> startWork() {
    Log.d(
        TAG,
        "Starting worker downloading diagnosis key files and submitting "
            + "them to the API for exposure detection, then storing the token used.");
    final String token = generateRandomToken();
    return FluentFuture.from(
            TaskToFutureAdapter.getFutureWithTimeout(
                ExposureNotificationClientWrapper.get(getApplicationContext()).isEnabled(),
                IS_ENABLED_TIMEOUT.toMillis(),
                TimeUnit.MILLISECONDS,
                AppExecutors.getScheduledExecutor()))
        .transformAsync(
            (isEnabled) -> {
              // Only continue if it is enabled.
              if (isEnabled) {
                return diagnosisKeys.download();
              } else {
                // Stop here because things aren't enabled. Will still return successful though.
                return Futures.immediateFailedFuture(new NotEnabledException());
              }
            },
            AppExecutors.getBackgroundExecutor())
        .transformAsync(
            (batches) -> submitter.submitFiles(batches, token),
            AppExecutors.getBackgroundExecutor())
        .transformAsync(
            done -> tokenRepository.upsertAsync(TokenEntity.create(token, false)),
            AppExecutors.getBackgroundExecutor())
        .transform(done -> Result.success(), AppExecutors.getBackgroundExecutor())
        .catching(
            NotEnabledException.class,
            x -> {
              // Not enabled. Return as success.
              return Result.success();
            },
            AppExecutors.getBackgroundExecutor())
        .catching(
            Exception.class,
            x -> {
              Log.e(TAG, "Failure to provide diagnosis keys", x);
              return Result.failure();
            },
            AppExecutors.getBackgroundExecutor());
  }

  /**
   * Schedules a job that runs once a day to fetch diagnosis keys from a server and to provide them
   * to the exposure notifications API with flex period.
   *
   * <p>This job will only be run when not low battery and with network connection.
   */
  public static void schedule(Context context) {
    WorkManager workManager = WorkManager.getInstance(context);
    PeriodicWorkRequest workRequest =
        new PeriodicWorkRequest.Builder(
                ProvideDiagnosisKeysWorker.class,
                JOB_INTERVAL.toHours(),
                TimeUnit.HOURS,
                JOB_FLEX_INTERVAL.toHours(),
                TimeUnit.HOURS)
            .setConstraints(
                new Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build())
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.DEFAULT_BACKOFF_DELAY_MILLIS,
                TimeUnit.MILLISECONDS)
            .build();
    workManager.enqueueUniquePeriodicWork(
        WORKER_NAME, ExistingPeriodicWorkPolicy.REPLACE, workRequest);
  }

  private static class NotEnabledException extends Exception {}
}
