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

package com.google.android.apps.exposurenotification.logging;

import android.content.Context;
import androidx.annotation.AnyThread;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.logging.Logger;
import com.google.android.apps.exposurenotification.network.VolleyUtils;
import com.google.android.apps.exposurenotification.proto.ApiCall.ApiCallType;
import com.google.android.apps.exposurenotification.proto.RpcCall.RpcCallResult;
import com.google.android.apps.exposurenotification.proto.RpcCall.RpcCallType;
import com.google.android.apps.exposurenotification.proto.UiInteraction.EventType;
import com.google.android.apps.exposurenotification.proto.WorkManagerTask.Status;
import com.google.android.apps.exposurenotification.proto.WorkManagerTask.WorkerTask;
import com.google.android.apps.exposurenotification.storage.AnalyticsLoggingEntity;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.nearby.exposurenotification.ExposureNotificationStatusCodes;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.hilt.android.qualifiers.ApplicationContext;
import java.util.concurrent.TimeoutException;
import javax.inject.Inject;

/**
 * Analytics logger which logs to Logcat only
 */
public class LogcatAnalyticsLogger implements AnalyticsLogger {

  private static final Logger logger = Logger.getLogger("LogcatAnalyticsLogger");
  private final String healthAuthorityCode;

  @Inject
  public LogcatAnalyticsLogger(@ApplicationContext Context context) {
    Context appContext = context.getApplicationContext();
    healthAuthorityCode = appContext.getResources().getString(R.string.enx_regionIdentifier);
    logger.i("Using logcat analytics logger.");
  }

  @Override
  @UiThread
  public void logUiInteraction(EventType event) {
    if (event == EventType.LOW_STORAGE_WARNING_SHOWN) {
      logger.e(event.toString());
    } else {
      logger.i(event.toString());
    }
  }

  @Override
  @AnyThread
  public void logWorkManagerTaskStarted(WorkerTask workerTask) {
    logger.i(workerTask + " started.");
  }

  @Override
  @AnyThread
  public void logApiCallFailure(ApiCallType apiCallType, Exception exception) {
    if (exception instanceof ApiException) {
      if (((ApiException) exception).getStatusCode()
            == ExposureNotificationStatusCodes.RESOLUTION_REQUIRED) {
        logger.i(apiCallType + " requires resolution");
        return;
      }
    }
    logger.e(apiCallType + " failed.", exception);
  }

  @Override
  @AnyThread
  public void logApiCallSuccess(ApiCallType apiCallType) {
    logger.i(apiCallType + " succeeded.");
  }

  @Override
  @AnyThread
  public ListenableFuture<?> logApiCallFailureAsync(
      ApiCallType apiCallType, Exception exception) {
    logApiCallFailure(apiCallType, exception);
    return Futures.immediateVoidFuture();
  }

  @Override
  @AnyThread
  public ListenableFuture<?> logApiCallSuccessAsync(ApiCallType apiCallType) {
    logApiCallSuccess(apiCallType);
    return Futures.immediateVoidFuture();
  }

  @Override
  @AnyThread
  public void logRpcCallSuccess(RpcCallType rpcCallType, int payloadSize) {
    logger.i(rpcCallType + " succeeded with payload size: " + payloadSize);
  }

  @Override
  @AnyThread
  public void logRpcCallFailure(RpcCallType rpcCallType, Throwable error) {
    RpcCallResult rpcCallResult = VolleyUtils.getLoggableResult(error);
    int httpStatus = VolleyUtils.getHttpStatus(error);
    String errorCode = VolleyUtils.getErrorCode(error);
    String errorMessage = VolleyUtils.getErrorMessage(error);

    logger.e(rpcCallType + " failed. "
        + " Result:[" + rpcCallResult + "]"
        + " HTTP status:[" + httpStatus + "]"
        + " Server error:[" + errorCode + ":" + errorMessage + "]");
  }

  @Override
  @AnyThread
  public ListenableFuture<?> logRpcCallSuccessAsync(RpcCallType rpcCallType, int payloadSize) {
    logRpcCallSuccess(rpcCallType, payloadSize);
    return Futures.immediateVoidFuture();
  }

  @Override
  @AnyThread
  public ListenableFuture<?> logRpcCallFailureAsync(RpcCallType rpcCallType, Throwable error) {
    logRpcCallFailure(rpcCallType, error);
    return Futures.immediateVoidFuture();
  }

  @Override
  @AnyThread
  public void logWorkManagerTaskSuccess(WorkerTask workerTask) {
    logger.i(workerTask + " finished with status: " + Status.STATUS_SUCCESS);
  }

  @Override
  @AnyThread
  public void logWorkManagerTaskFailure(WorkerTask workerTask, Throwable t) {
    Status status = Status.STATUS_FAIL;
    if (t instanceof TimeoutException) {
      status = Status.STATUS_TIMEOUT;
    }
    logger.e(workerTask + " failed with status: " + status);
  }

  @Override
  @AnyThread
  public void logWorkManagerTaskAbandoned(WorkerTask workerTask) {
    logger.e(workerTask + " finished with status: " + Status.STATUS_ABANDONED);
  }

  @Override
  @AnyThread
  public ListenableFuture<Void> sendLoggingBatchIfConsented(boolean isENEnabled) {
    // No action as logcat logger doesn't send anything off device
    logger.i("LogcatAnalytics logger - no batch upload operation specified");
    return Futures.immediateVoidFuture();
  }
}
