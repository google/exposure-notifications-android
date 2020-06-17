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
import androidx.work.ListenableWorker;
import androidx.work.WorkerParameters;
import com.google.android.apps.exposurenotification.common.AppExecutors;
import com.google.android.apps.exposurenotification.common.TaskToFutureAdapter;
import com.google.android.apps.exposurenotification.storage.ExposureEntity;
import com.google.android.apps.exposurenotification.storage.ExposureRepository;
import com.google.android.apps.exposurenotification.storage.TokenRepository;
import com.google.android.gms.nearby.exposurenotification.ExposureInformation;
import com.google.android.gms.nearby.exposurenotification.ExposureNotificationClient;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.threeten.bp.Duration;

/**
 * Performs work for {@value
 * com.google.android.gms.nearby.exposurenotification.ExposureNotificationClient#ACTION_EXPOSURE_STATE_UPDATED}
 * broadcast from exposure notification API.
 */
public class StateUpdatedWorker extends ListenableWorker {

  private static final String TAG = "StateUpdatedWorker";

  public static final String ACTION_LAUNCH_FROM_EXPOSURE_NOTIFICATION =
      "com.google.android.apps.exposurenotification.ACTION_LAUNCH_FROM_EXPOSURE_NOTIFICATION";
  private static final Duration GET_SUMMARY_TIMEOUT = Duration.ofSeconds(30);
  private static final Duration GET_EXPOSURE_INFORMATION_TIMEOUT = Duration.ofSeconds(30);

  private final Context context;
  private final TokenRepository tokenRepository;
  private final ExposureRepository exposureRepository;

  public StateUpdatedWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
    super(context, workerParams);
    this.context = context;
    this.tokenRepository = new TokenRepository(context);
    this.exposureRepository = new ExposureRepository(context);
  }

  @NonNull
  @Override
  public ListenableFuture<Result> startWork() {
    final String token = getInputData().getString(ExposureNotificationClient.EXTRA_TOKEN);
    if (token == null) {
      return Futures.immediateFuture(Result.failure());
    } else {
      return FluentFuture.from(
              TaskToFutureAdapter.getFutureWithTimeout(
                  ExposureNotificationClientWrapper.get(context).getExposureSummary(token),
                  GET_SUMMARY_TIMEOUT.toMillis(),
                  TimeUnit.MILLISECONDS,
                  AppExecutors.getScheduledExecutor()))
          .transformAsync(
              (exposureSummary) -> {
                Log.d(TAG, "EN summary received: " + exposureSummary);
                if (exposureSummary.getMatchedKeyCount() > 0) {
                  return hasMatches(token);
                } else {
                  return noMatches(token);
                }
              },
              AppExecutors.getBackgroundExecutor())
          .transform((v) -> Result.success(), AppExecutors.getLightweightExecutor())
          .catching(Exception.class, x -> {
            Log.e(TAG, "Failure to update app state (tokens, etc) from exposure summary.", x);
            return Result.failure();
            }, AppExecutors.getLightweightExecutor());
    }
  }


  public ListenableFuture<Void> hasMatches(String token) {
    return FluentFuture.from(
        TaskToFutureAdapter.getFutureWithTimeout(
            ExposureNotificationClientWrapper.get(context).getExposureInformation(token),
            GET_EXPOSURE_INFORMATION_TIMEOUT.toMillis(),
            TimeUnit.MILLISECONDS,
            AppExecutors.getScheduledExecutor()))
        .transformAsync(
            (exposureInformations) -> {
              List<ExposureEntity> exposureEntities = new ArrayList<>();
              for (ExposureInformation exposureInformation : exposureInformations) {
                exposureEntities.add(
                    ExposureEntity.create(
                        exposureInformation.getDateMillisSinceEpoch(),
                        System.currentTimeMillis()));
              }
              return exposureRepository.upsertAsync(exposureEntities);
            },
            AppExecutors.getBackgroundExecutor())
        .transformAsync(
            (v) -> tokenRepository.deleteByTokensAsync(token),
            AppExecutors.getBackgroundExecutor());
  }

  public ListenableFuture<Void> noMatches(String token) {
    // No matches so we show no notification and just delete the token.
    return tokenRepository.deleteByTokensAsync(token);
  }

}
