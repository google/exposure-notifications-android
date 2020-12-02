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
import android.util.Log;
import androidx.annotation.AnyThread;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import com.android.volley.VolleyError;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.proto.ApiCall.ApiCallType;
import com.google.android.apps.exposurenotification.proto.RpcCall.RpcCallType;
import com.google.android.apps.exposurenotification.proto.UiInteraction.EventType;
import com.google.android.apps.exposurenotification.proto.WorkManagerTask.Status;
import com.google.android.apps.exposurenotification.proto.WorkManagerTask.WorkerTask;
import com.google.android.apps.exposurenotification.network.VolleyUtils;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.hilt.android.qualifiers.ApplicationContext;
import java.util.concurrent.TimeoutException;
import javax.inject.Inject;

/** Analytics logger which logs to Logcat only */
class LogcatAnalyticsLogger implements AnalyticsLogger {

  private final String healthAuthorityCode;
  private final String tag;

  @Inject
  public LogcatAnalyticsLogger(@ApplicationContext Context context) {
    Context appContext = context.getApplicationContext();
    healthAuthorityCode = appContext.getResources().getString(R.string.enx_regionIdentifier);
    tag = "ENX." + healthAuthorityCode;
    Log.i(tag, "Using logcat analytics logger.");
  }

  @Override
  @UiThread
  public void logUiInteraction(EventType event) {
    if (event == EventType.LOW_STORAGE_WARNING_SHOWN) {
      Log.e(tag, event.toString());
    } else {
      Log.i(tag, event.toString());
    }
  }

  @Override
  @AnyThread
  public void logWorkManagerTaskStarted(WorkerTask workerTask) {
    Log.i(tag, workerTask + " started.");
  }

  @Override
  @AnyThread
  public void logApiCallFailure(ApiCallType apiCallType, Exception exception) {
    Log.e(tag, apiCallType + " failed.", exception);
  }

  @Override
  @AnyThread
  public void logApiCallSuccess(ApiCallType apiCallType) {
    Log.i(tag, apiCallType + " succeeded.");
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
    Log.i(tag, rpcCallType + " succeeded with payload size: " + payloadSize);
  }

  @Override
  @AnyThread
  public void logRpcCallFailure(RpcCallType rpcCallType, Throwable error) {
    int httpStatus = VolleyUtils.getHttpStatus(error);
    String errorCode = VolleyUtils.getErrorCode(error);
    String errorMessage = VolleyUtils.getErrorMessage(error);

    Log.e(tag, rpcCallType + " failed with server error " + httpStatus + ":" + errorCode + ":["
        + errorMessage + "]");
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
    Log.i(tag, workerTask + " finished with status: " + Status.STATUS_SUCCESS);
  }

  @Override
  @AnyThread
  public void logWorkManagerTaskFailure(WorkerTask workerTask, Throwable t) {
    Status status = Status.STATUS_FAIL;
    if (t instanceof TimeoutException) {
      status = Status.STATUS_TIMEOUT;
    }
    Log.e(tag, workerTask + " failed with status: " + status);
  }

  @Override
  @AnyThread
  public void logWorkManagerTaskAbandoned(WorkerTask workerTask) {
    Log.e(tag, workerTask + " finished with status: " + Status.STATUS_ABANDONED);
  }

  @Override
  @AnyThread
  public ListenableFuture<Void> sendLoggingBatchIfEnabled() {
    // No action as logcat logger doesn't send anything off device
    Log.i(tag, "LogcatAnalytics logger - no batch upload operation specified");
    return Futures.immediateVoidFuture();
  }
}
