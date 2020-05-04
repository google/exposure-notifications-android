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
import com.google.android.apps.exposurenotification.network.DiagnosisKeys;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * Performs work for {@value com.google.android.gms.nearby.exposurenotification.ExposureNotificationClient#ACTION_EXPOSURE_STATE_UPDATED}
 * broadcast from exposure notification API.
 */
public class RequestDiagnosisKeysWorker extends ListenableWorker {
  private static final String TAG = "RequestKeysWorker";

  private final DiagnosisKeys diagnosisKeys;
  private final DiagnosisKeyFileSubmitter submitter;

  public RequestDiagnosisKeysWorker(@NonNull Context context,
      @NonNull WorkerParameters workerParams) {
    super(context, workerParams);
    diagnosisKeys = new DiagnosisKeys(context);
    submitter = new DiagnosisKeyFileSubmitter(context);
  }

  @NonNull
  @Override
  public ListenableFuture<Result> startWork() {
    Log.d(TAG, "Starting worker downloading diagnosis key files, parsing them, and submitting "
        + "them to the API for exposure detection.");
    return FluentFuture.from(diagnosisKeys.download())
        .transformAsync(submitter::parseFiles, AppExecutors.getBackgroundExecutor())
        .transform(done -> Result.success(), AppExecutors.getLightweightExecutor())
        .catching(Exception.class, x -> Result.failure(), AppExecutors.getLightweightExecutor());
  }

}