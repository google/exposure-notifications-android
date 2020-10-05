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
import androidx.annotation.Nullable;
import com.android.volley.VolleyError;
import com.google.android.apps.exposurenotification.BuildConfig;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.android.apps.exposurenotification.proto.ApiCall;
import com.google.android.apps.exposurenotification.proto.ApiCall.ApiCallType;
import com.google.android.apps.exposurenotification.proto.EnxLogExtension;
import com.google.android.apps.exposurenotification.proto.RpcCall;
import com.google.android.apps.exposurenotification.proto.RpcCall.RpcCallResult;
import com.google.android.apps.exposurenotification.proto.RpcCall.RpcCallType;
import com.google.android.apps.exposurenotification.proto.UiInteraction;
import com.google.android.apps.exposurenotification.proto.UiInteraction.EventType;
import com.google.android.apps.exposurenotification.proto.WorkManagerTask;
import com.google.android.apps.exposurenotification.proto.WorkManagerTask.Status;
import com.google.android.apps.exposurenotification.proto.WorkManagerTask.WorkerTask;
import com.google.android.apps.exposurenotification.network.VolleyUtils;
import com.google.android.apps.exposurenotification.storage.AnalyticsLoggingEntity;
import com.google.android.apps.exposurenotification.storage.AnalyticsLoggingRepository;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.datatransport.Event;
import com.google.android.datatransport.Transport;
import com.google.android.gms.common.api.ApiException;
import com.google.common.io.BaseEncoding;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.threeten.bp.Duration;
import org.threeten.bp.Instant;

/** Analytics logger which logs through Firelog transport and logcat */
public class FirelogAnalyticsLogger implements AnalyticsLogger {

  private final String healthAuthorityCode;
  private final String tag;
  private final ExposureNotificationSharedPreferences preferences;
  private final Transport<EnxLogExtension> transport;
  private final Clock clock;
  private final AnalyticsLoggingRepository repository;

  @SuppressWarnings("RestrictedApi")
  public FirelogAnalyticsLogger(Context context, ExposureNotificationSharedPreferences preferences,
      Transport<EnxLogExtension> transport, AnalyticsLoggingRepository repository, Clock clock) {
    healthAuthorityCode = context.getResources().getString(R.string.enx_regionIdentifier);
    tag = "ENX." + healthAuthorityCode;
    this.transport = transport;
    this.preferences = preferences;
    this.clock = clock;
    this.repository = repository;
    Log.i(tag, "Using firelog analytics logger.");

    preferences.getAndResetAnalyticsLoggingLastTimestamp();
    preferences.setAnalyticsStateListener(analyticsEnabled -> {
      if (analyticsEnabled) {
        Log.i(tag, "Firelog analytics logging enabled");
        preferences.getAndResetAnalyticsLoggingLastTimestamp();
      } else {
        Log.i(tag, "Firelog analytics logging disabled");
        repository.eraseEventsBatch();
      }
    });
  }

  @Override
  public void logUiInteraction(EventType event) {
    EnxLogExtension logEvent = EnxLogExtension.newBuilder()
        .addUiInteraction(UiInteraction.newBuilder().setEventType(event).build()).build();
    logEventIfEnabled(logEvent);
    Log.i(tag, event.toString());
  }

  @Override
  public void logApiCallFailure(ApiCallType apiCallType, Exception exception) {
    int statusCode = -2;
    if (exception instanceof ApiException) {
      statusCode = ((ApiException) exception).getStatusCode();
    }
    EnxLogExtension logEvent = EnxLogExtension.newBuilder().addApiCall(
        ApiCall.newBuilder().setApiCallType(apiCallType).setStatusCode(statusCode).build()).build();
    logEventIfEnabled(logEvent);
    Log.e(tag, apiCallType + " failed.", exception);
  }

  @Override
  public void logApiCallSuccess(ApiCallType apiCallType) {
    EnxLogExtension logEvent = EnxLogExtension.newBuilder().addApiCall(
        ApiCall.newBuilder().setApiCallType(apiCallType).setStatusCode(0).build()).build();
    logEventIfEnabled(logEvent);

    Log.i(tag, apiCallType + " succeeded.");
  }

  @Override
  public void logWorkManagerTaskSuccess(WorkerTask workerTask) {
    EnxLogExtension logEvent = EnxLogExtension.newBuilder().addWorkManagerTask(
        WorkManagerTask.newBuilder().setWorkerTask(workerTask).setStatus(Status.STATUS_SUCCESS)
            .build()).build();
    logEventIfEnabled(logEvent);

    Log.i(tag, workerTask + " finished with status: SUCCESS");
  }

  @Override
  public void logWorkManagerTaskFailure(WorkerTask workerTask, Throwable t) {
    Status status = Status.STATUS_FAIL;
    if (t instanceof TimeoutException) {
      status = Status.STATUS_TIMEOUT;
    }
    EnxLogExtension logEvent = EnxLogExtension.newBuilder().addWorkManagerTask(
        WorkManagerTask.newBuilder().setWorkerTask(workerTask).setStatus(status)
            .build()).build();
    logEventIfEnabled(logEvent);
    Log.e(tag, workerTask + " failed with status: " + status);
  }

  @Override
  public void logRpcCallSuccess(RpcCallType rpcCallType, int payloadSize) {
    EnxLogExtension logEvent = EnxLogExtension.newBuilder().addRpcCall(
        RpcCall.newBuilder().setRpcCallType(rpcCallType).setPayloadSize(payloadSize)
            .setRpcCallResult(RpcCallResult.RESULT_SUCCESS)
            .build()).build();
    logEventIfEnabled(logEvent);
    Log.i(tag, rpcCallType + " succeeded with payload size: " + payloadSize);
  }

  @Override
  public void logRpcCallFailure(RpcCallType rpcCallType, @Nullable VolleyError error) {
    RpcCallResult rpcCallResult = RpcCallResult.RESULT_FAILED_UNKNOWN;
    int httpStatus = VolleyUtils.getHttpStatus(error);
    String errorCode = VolleyUtils.getErrorCode(error);
    String errorMessage = VolleyUtils.getErrorMessage(error);
    if (httpStatus / 100 == 5) {
      rpcCallResult = RpcCallResult.RESULT_FAILED_GENERIC_5XX;
    } else if (httpStatus / 100 == 4) {
      rpcCallResult = RpcCallResult.RESULT_FAILED_GENERIC_4XX;
    }

    EnxLogExtension logEvent = EnxLogExtension.newBuilder().addRpcCall(
        RpcCall.newBuilder().setRpcCallType(rpcCallType).setRpcCallResult(rpcCallResult).build())
        .build();
    logEventIfEnabled(logEvent);
    Log.e(tag, rpcCallType + " failed with server error " + httpStatus + ":" + errorCode + ":["
        + errorMessage + "]");
  }

  @Override
  public void sendLoggingBatchIfEnabled() {
    if (!preferences.getAppAnalyticsState()) {
      return;
    }
    Instant currentTime = clock.now();
    Instant lastTimestamp;

    lastTimestamp = preferences.getAnalyticsLoggingLastTimestamp();
    if (Duration.between(lastTimestamp, currentTime).toHours() < 4) {
      Log.i(tag, "Skipped firelog upload - less than 4 hours");
      return;
    }

    // get time since last upload in hours properly rounded
    int hoursSinceLastTimestamp = (int) Duration.between(lastTimestamp, currentTime)
        .plus(Duration.ofMinutes(30)).toHours();

    preferences.getAndResetAnalyticsLoggingLastTimestamp();
    List<AnalyticsLoggingEntity> batch = repository.getAndEraseEventsBatch();
    EnxLogExtension.Builder enxLogExtensionBuilder = EnxLogExtension.newBuilder();

    for (AnalyticsLoggingEntity logEvent : batch) {
      try {
        enxLogExtensionBuilder.mergeFrom(BaseEncoding.base64().decode(logEvent.getEventProto()));
      } catch (InvalidProtocolBufferException e) {
        Log.e(tag, "Error reading from AnalyticsLoggingRepository: " + e);
        continue;
      }
    }

    EnxLogExtension logEvent = enxLogExtensionBuilder
        .setBuildId(BuildConfig.VERSION_CODE)
        .setHoursSinceLastBatch(hoursSinceLastTimestamp)
        .setRegionIdentifier(healthAuthorityCode)
        .build();

    transport.send(Event.ofData(logEvent));
    Log.i(tag, "Analytics log batch sent to Firelog.");
  }

  private void logEventIfEnabled(EnxLogExtension logEvent) {
    if (preferences.getAppAnalyticsState()) {
      repository.recordEvent(logEvent);
      Log.i(tag, "App analytics enabled. Sending log event.");
    } else {
      Log.i(tag, "App analytics disabled. Not sending log event.");
    }
  }
}
