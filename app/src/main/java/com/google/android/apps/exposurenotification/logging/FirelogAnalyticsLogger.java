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
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.concurrent.futures.CallbackToFutureAdapter;
import com.google.android.apps.exposurenotification.BuildConfig;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.Qualifiers.BackgroundExecutor;
import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.android.apps.exposurenotification.network.VolleyUtils;
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
import com.google.android.apps.exposurenotification.storage.AnalyticsLoggingEntity;
import com.google.android.apps.exposurenotification.storage.AnalyticsLoggingRepository;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.apps.exposurenotification.storage.WorkerStatusRepository;
import com.google.android.datatransport.Event;
import com.google.android.datatransport.Transport;
import com.google.android.gms.common.api.ApiException;
import com.google.common.base.Optional;
import com.google.common.io.BaseEncoding;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.protobuf.InvalidProtocolBufferException;
import dagger.hilt.android.qualifiers.ApplicationContext;
import java.util.List;
import java.util.concurrent.TimeoutException;
import javax.inject.Inject;
import org.threeten.bp.Duration;
import org.threeten.bp.Instant;

/**
 * Analytics logger which logs through Firelog transport and logcat
 */
public class FirelogAnalyticsLogger implements AnalyticsLogger {

  private final String healthAuthorityCode;
  private final String tag;
  private final ExposureNotificationSharedPreferences preferences;
  private final Transport<EnxLogExtension> transport;
  private final Clock clock;
  private final AnalyticsLoggingRepository repository;
  private final ListeningExecutorService backgroundExecutor;
  private final WorkerStatusRepository workerStatusRepository;

  @Inject
  @SuppressWarnings("RestrictedApi")
  public FirelogAnalyticsLogger(
      @ApplicationContext Context context,
      ExposureNotificationSharedPreferences preferences,
      Transport<EnxLogExtension> transport,
      AnalyticsLoggingRepository repository,
      Clock clock,
      WorkerStatusRepository workerStatusRepository,
      @BackgroundExecutor ListeningExecutorService backgroundExecutor) {
    healthAuthorityCode = context.getResources().getString(R.string.enx_regionIdentifier);
    tag = "ENX." + healthAuthorityCode;
    this.transport = transport;
    this.preferences = preferences;
    this.clock = clock;
    this.repository = repository;
    this.backgroundExecutor = backgroundExecutor;
    this.workerStatusRepository = workerStatusRepository;
    Log.i(tag, "Using firelog analytics logger.");

    preferences.setAnalyticsStateListener(analyticsEnabled -> {
      if (analyticsEnabled) {
        Log.i(tag, "Firelog analytics logging enabled");
      } else {
        Log.i(tag, "Firelog analytics logging disabled");
        repository.eraseEventsBatch();
      }
    });
  }

  @Override
  @UiThread
  public void logUiInteraction(EventType event) {
    EnxLogExtension logEvent = EnxLogExtension.newBuilder()
        .addUiInteraction(UiInteraction.newBuilder().setEventType(event).build()).build();
    Log.i(tag, event.toString());

    FluentFuture.from(backgroundExecutor.submit(() -> logEventIfEnabled(logEvent)))
        .catching(Exception.class, e -> {
          Log.w(tag, "Error recording UI logs", e);
          return null;
        }, backgroundExecutor);
  }

  @Override
  @WorkerThread
  public void logApiCallFailure(ApiCallType apiCallType, Exception exception) {
    EnxLogExtension logEvent = getApiFailureLogEvent(apiCallType, exception);
    logEventIfEnabled(logEvent);
    Log.e(tag, apiCallType + " failed.", exception);
  }

  @Override
  @WorkerThread
  public void logApiCallSuccess(ApiCallType apiCallType) {
    EnxLogExtension logEvent = getApiSuccessLogEvent(apiCallType);
    logEventIfEnabled(logEvent);

    Log.i(tag, apiCallType + " succeeded.");
  }

  @Override
  @AnyThread
  public ListenableFuture<?> logApiCallFailureAsync(
      ApiCallType apiCallType, Exception exception) {
    EnxLogExtension logEvent = getApiFailureLogEvent(apiCallType, exception);
    Log.e(tag, apiCallType + " failed.", exception);
    return backgroundExecutor.submit(() -> logEventIfEnabled(logEvent));
  }

  @Override
  @AnyThread
  public ListenableFuture<?> logApiCallSuccessAsync(ApiCallType apiCallType) {
    EnxLogExtension logEvent = getApiSuccessLogEvent(apiCallType);
    Log.i(tag, apiCallType + " succeeded.");
    return backgroundExecutor.submit(() -> logEventIfEnabled(logEvent));
  }

  @Override
  @WorkerThread
  public void logWorkManagerTaskStarted(WorkerTask workerTask) {
    int hoursSinceLastRun = getHoursSinceLastRunAndUpdateLastRunTimestamp(workerTask);
    EnxLogExtension logEvent = EnxLogExtension.newBuilder().addWorkManagerTask(
        WorkManagerTask.newBuilder().setWorkerTask(workerTask)
            .setStatus(Status.STATUS_STARTED)
            .setHoursSinceLastRun(hoursSinceLastRun)
            .build()).build();
    logEventIfEnabled(logEvent);
    Log.i(tag, workerTask + " started");
  }

  private int getHoursSinceLastRunAndUpdateLastRunTimestamp(WorkerTask workerTask) {
    Optional<Instant> lastRunTimestamp =
        workerStatusRepository.getLastRunTimestamp(workerTask, Status.STATUS_STARTED.toString());
    int hoursSinceLastRun = 0;
    if (lastRunTimestamp.isPresent()) {
      // Add 30 minutes to ensure we round to the nearest hour correctly.
      Duration durationSinceLastRun =
          Duration.between(lastRunTimestamp.get(), clock.now()).plusMinutes(30);
      hoursSinceLastRun = (int) durationSinceLastRun.toHours();
    }
    // Update last run timestamp in the db, to current time
    workerStatusRepository.upsert(workerTask, Status.STATUS_STARTED.toString(), clock.now());
    return  hoursSinceLastRun;
  }

  @Override
  @WorkerThread
  public void logWorkManagerTaskSuccess(WorkerTask workerTask) {
    EnxLogExtension logEvent = EnxLogExtension.newBuilder().addWorkManagerTask(
        WorkManagerTask.newBuilder().setWorkerTask(workerTask).setStatus(Status.STATUS_SUCCESS)
            .build()).build();
    logEventIfEnabled(logEvent);

    Log.i(tag, workerTask + " finished with status: SUCCESS");
  }

  @Override
  @WorkerThread
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
  @WorkerThread
  public void logWorkManagerTaskAbandoned(WorkerTask workerTask) {
    Status status = Status.STATUS_ABANDONED;
    EnxLogExtension logEvent = EnxLogExtension.newBuilder().addWorkManagerTask(
        WorkManagerTask.newBuilder().setWorkerTask(workerTask).setStatus(status)
            .build()).build();
    logEventIfEnabled(logEvent);
    Log.e(tag, workerTask + " finished with status: " + status);
  }

  @Override
  @WorkerThread
  public void logRpcCallSuccess(RpcCallType rpcCallType, int payloadSize) {
    EnxLogExtension logEvent = getRpcSuccessLogEvent(rpcCallType, payloadSize);
    logEventIfEnabled(logEvent);
    Log.i(tag, rpcCallType + " succeeded with payload size: " + payloadSize);
  }

  @Override
  @WorkerThread
  public void logRpcCallFailure(RpcCallType rpcCallType, Throwable error) {
    EnxLogExtension logEvent = getRpcFailureLogEvent(rpcCallType, error);
    logEventIfEnabled(logEvent);
  }

  @Override
  @AnyThread
  public ListenableFuture<?> logRpcCallSuccessAsync(RpcCallType rpcCallType, int payloadSize) {
    EnxLogExtension logEvent = getRpcSuccessLogEvent(rpcCallType, payloadSize);
    Log.i(tag, rpcCallType + " succeeded with payload size: " + payloadSize);
    return backgroundExecutor.submit(() -> logEventIfEnabled(logEvent));
  }

  @Override
  @AnyThread
  public ListenableFuture<?> logRpcCallFailureAsync(RpcCallType rpcCallType, Throwable error) {
    EnxLogExtension logEvent = getRpcFailureLogEvent(rpcCallType, error);
    return backgroundExecutor.submit(() -> logEventIfEnabled(logEvent));
  }

  @Override
  @WorkerThread
  public ListenableFuture<Void> sendLoggingBatchIfEnabled() {
    if (!preferences.getAppAnalyticsState()) {
      return Futures.immediateVoidFuture();
    }
    Instant currentTime = clock.now();
    Optional<Instant> lastTimestamp = preferences.maybeGetAnalyticsLoggingLastTimestamp();
    if (lastTimestamp.isPresent()) {
      if (Duration.between(lastTimestamp.get(), currentTime).toHours() < 4) {
        Log.i(tag, "Skipped firelog upload - less than 4 hours");
        return Futures.immediateVoidFuture();
      }
    } else {
      Log.i(tag, "Skipped firelog upload - no last timestamp, resetting");
      preferences.resetAnalyticsLoggingLastTimestamp();
      return Futures.immediateVoidFuture();
    }

    // Get time since last upload in hours properly rounded
    int hoursSinceLastTimestamp =
        (int) Duration.between(lastTimestamp.get(), currentTime).plusMinutes(30).toHours();

    preferences.resetAnalyticsLoggingLastTimestamp();
    List<AnalyticsLoggingEntity> batch = repository.getEventsBatch();
    EnxLogExtension.Builder enxLogExtensionBuilder = EnxLogExtension.newBuilder();

    for (AnalyticsLoggingEntity logEvent : batch) {
      try {
        enxLogExtensionBuilder.mergeFrom(BaseEncoding.base64().decode(logEvent.getEventProto()));
      } catch (InvalidProtocolBufferException e) {
        Log.e(tag, "Error reading from AnalyticsLoggingRepository: " + e);
        return Futures.immediateFailedFuture(e);
      }
    }

    EnxLogExtension logEvent = enxLogExtensionBuilder
        .setBuildId(BuildConfig.VERSION_CODE)
        .setHoursSinceLastBatch(hoursSinceLastTimestamp)
        .setRegionIdentifier(healthAuthorityCode)
        .build();

    return FluentFuture.from(
        CallbackToFutureAdapter.getFuture(
            completer -> {
              transport.schedule(Event.ofData(logEvent), exception -> {
                if (exception != null) {
                  completer.setException(exception);
                  return;
                }
                completer.set(null);
              });
              // This value is used only for debug purposes: it will be used in toString()
              // of returned future or error cases.
              return "AnalyticsLogger#sendLoggingBatchIfEnabled";
            })
    ).transform(unused -> {
      repository.eraseEventsBatch();
      Log.i(tag, "Analytics log batch sent to Firelog.");
      return null;
    }, backgroundExecutor);
  }

  private void logEventIfEnabled(EnxLogExtension logEvent) {
    if (preferences.getAppAnalyticsState()) {
      repository.recordEvent(logEvent);
      if (!preferences.maybeGetAnalyticsLoggingLastTimestamp().isPresent()) {
        preferences.resetAnalyticsLoggingLastTimestamp();
      }
      Log.i(tag, "App analytics enabled. Sending log event.");
    } else {
      Log.d(tag, "App analytics disabled. Not sending log event.");
    }
  }

  private EnxLogExtension getApiSuccessLogEvent(ApiCallType apiCallType) {
    return EnxLogExtension.newBuilder().addApiCall(
        ApiCall.newBuilder().setApiCallType(apiCallType).setStatusCode(0).build()).build();
  }

  private EnxLogExtension getApiFailureLogEvent(ApiCallType apiCallType, Exception exception) {
    int statusCode = -2;
    if (exception instanceof ApiException) {
      statusCode = ((ApiException) exception).getStatusCode();
    }
    return EnxLogExtension.newBuilder().addApiCall(
        ApiCall.newBuilder().setApiCallType(apiCallType).setStatusCode(statusCode).build()).build();
  }

  private EnxLogExtension getRpcSuccessLogEvent(RpcCallType rpcCallType, int payloadSize) {
    return EnxLogExtension.newBuilder().addRpcCall(
        RpcCall.newBuilder().setRpcCallType(rpcCallType).setPayloadSize(payloadSize)
            .setRpcCallResult(RpcCallResult.RESULT_SUCCESS).build())
        .build();
  }

  private EnxLogExtension getRpcFailureLogEvent(RpcCallType rpcCallType, Throwable error) {
    RpcCallResult rpcCallResult = VolleyUtils.getLoggableResult(error);
    int httpStatus = VolleyUtils.getHttpStatus(error);
    String errorCode = VolleyUtils.getErrorCode(error);
    String errorMessage = VolleyUtils.getErrorMessage(error);

    Log.e(tag, rpcCallType + " failed. "
        + " Result:[" + rpcCallResult + "]"
        + " HTTP status:[" + httpStatus + "]"
        + " Server error:[" + errorCode + ":" + errorMessage + "]");

    return EnxLogExtension.newBuilder().addRpcCall(
        RpcCall.newBuilder().setRpcCallType(rpcCallType).setRpcCallResult(rpcCallResult).build())
        .build();
  }
}
