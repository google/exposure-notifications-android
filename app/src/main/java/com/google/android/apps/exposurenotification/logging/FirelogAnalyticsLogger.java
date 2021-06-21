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
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.concurrent.futures.CallbackToFutureAdapter;
import com.google.android.apps.exposurenotification.BuildConfig;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.BuildUtils;
import com.google.android.apps.exposurenotification.common.BuildUtils.Type;
import com.google.android.apps.exposurenotification.common.Qualifiers.BackgroundExecutor;
import com.google.android.apps.exposurenotification.common.Qualifiers.ScheduledExecutor;
import com.google.android.apps.exposurenotification.common.TaskToFutureAdapter;
import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.android.apps.exposurenotification.nearby.ExposureNotificationClientWrapper;
import com.google.android.apps.exposurenotification.nearby.PackageConfigurationHelper;
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
import dagger.Lazy;
import dagger.hilt.android.qualifiers.ApplicationContext;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeoutException;
import javax.inject.Inject;
import org.threeten.bp.Duration;
import org.threeten.bp.Instant;

/**
 * Analytics logger which logs through Firelog transport and logcat
 */
public class FirelogAnalyticsLogger implements AnalyticsLogger {
  private static final Duration GET_PACKAGE_CONFIGURATION_TIMEOUT = Duration.ofSeconds(120);

  /*
   * ExposureNotificationClientWrapper injects (Firelog)AnalyticsLogger in its constructor.
   * Injecting ExposureNotificationClientWrapper in this constructor would thus cause a dependency
   * cycle. We instead use a Lazy<ExposureNotificationClientWrapper>, which enables us to inject
   * ExposureNotificationClientWrapper later when it is required by a method instead.
   * Lazy<...> provides the same instance every time its .get() method is called and because
   * ExposureNotificationClientWrapper is @Singleton its instance is shared among all clients.
   */
  private final Lazy<ExposureNotificationClientWrapper> exposureNotificationClientWrapper;

  private final String healthAuthorityCode;
  private final String tag;
  private final ExposureNotificationSharedPreferences preferences;
  private final Transport<EnxLogExtension> transport;
  private final Clock clock;
  private final AnalyticsLoggingRepository repository;
  private final ListeningExecutorService backgroundExecutor;
  private final ScheduledExecutorService scheduledExecutor;
  private final WorkerStatusRepository workerStatusRepository;

  @Inject
  @SuppressWarnings("RestrictedApi")
  public FirelogAnalyticsLogger(
      @ApplicationContext Context context,
      ExposureNotificationSharedPreferences preferences,
      Lazy<ExposureNotificationClientWrapper> exposureNotificationClientWrapper,
      Transport<EnxLogExtension> transport,
      AnalyticsLoggingRepository repository,
      Clock clock,
      WorkerStatusRepository workerStatusRepository,
      @BackgroundExecutor ListeningExecutorService backgroundExecutor,
      @ScheduledExecutor ScheduledExecutorService scheduledExecutor) {
    healthAuthorityCode = context.getResources().getString(R.string.enx_regionIdentifier);
    tag = "ENX." + healthAuthorityCode;
    this.transport = transport;
    this.preferences = preferences;
    this.exposureNotificationClientWrapper = exposureNotificationClientWrapper;
    this.clock = clock;
    this.repository = repository;
    this.backgroundExecutor = backgroundExecutor;
    this.scheduledExecutor = scheduledExecutor;
    this.workerStatusRepository = workerStatusRepository;
    Log.i(tag, "Using firelog analytics logger.");

    if (BuildUtils.getType() == Type.V2) {
      preferences.setAnalyticsStateListener(analyticsEnabled -> {
        if (analyticsEnabled) {
          Log.i(tag, "Firelog analytics logging enabled");
        } else {
          Log.i(tag, "Firelog analytics logging disabled");
          repository.eraseEventsBatch();
        }
      });
    }
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

  @WorkerThread
  @VisibleForTesting
  @Nullable
  AnalyticsLoggingEntity findLastStopCallIfExists() {
    AnalyticsLoggingEntity lastAnalyticsLoggingEntity = null;

    List<AnalyticsLoggingEntity> batch = repository.getEventsBatch();
    for (AnalyticsLoggingEntity logEvent : batch) {
      try {
        EnxLogExtension enxLogExtension =
            EnxLogExtension.parseFrom(BaseEncoding.base64().decode(logEvent.getEventProto()));
        /*
         * One log event can have multiple ApiCalls with different attributes, so we check them
         * one-by-one if we find any with the attribute ApiCallType.CALL_STOP
         */
        List<ApiCall> apiCalls = enxLogExtension.getApiCallList();
        for (ApiCall apiCall : apiCalls) {
          if (apiCall.getApiCallType().equals(ApiCallType.CALL_STOP)) {
            lastAnalyticsLoggingEntity = logEvent;
          }
        }
      } catch (InvalidProtocolBufferException e) {
        Log.e(tag, "Error decoding EnxLogExtension: " + e);
        return null;
      }
    }
    if (lastAnalyticsLoggingEntity != null) {
      Log.d(tag, "findLastStopCallIfExists: Found last stop call: " + lastAnalyticsLoggingEntity);
    } else {
      Log.d(tag, "findLastStopCallIfExists: No stop call found");
    }
    return lastAnalyticsLoggingEntity;
  }

  @Override
  @WorkerThread
  public ListenableFuture<?> sendLoggingBatchIfConsented(boolean isENEnabled) {
    final AnalyticsLoggingEntity lastEntryToSend;
    if (!isENEnabled) {
      /* If the API is disabled we check if we have a stop() call in our logs.
       * In this case, we want to submit the logs that were still in the "enabled"
       * window. */
      lastEntryToSend = findLastStopCallIfExists();
      if (lastEntryToSend != null) {
        Log.d(tag, "!isEnabled but stop call found, partially uploading logs.");
      } else {
        // If we don't find a stop() call, we don't submit logs. We can stop here.
        Log.d(tag, "!isEnabled and no stop call found, not uploading logs.");
        return Futures.immediateFailedFuture(new NotEnabledException());
      }
    } else { /* isEnabled */
      // If EN is enabled, we upload all logs. We indicate that by setting lastEntryToSend = null
      Log.d(tag, "isEnabled, fully uploading logs.");
      lastEntryToSend = null;
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

      // If a lastEntryToSend was detected, we stop adding entries to the enxLogExtensionBuilder
      if (lastEntryToSend != null && logEvent.getKey() == lastEntryToSend.getKey()) {
        Log.d(tag, "Stopping to build EnxLogExtension at " + lastEntryToSend);
        break;
      }
    }

    EnxLogExtension logEvent = enxLogExtensionBuilder
        .setBuildId(BuildConfig.VERSION_CODE)
        .setHoursSinceLastBatch(hoursSinceLastTimestamp)
        .setRegionIdentifier(healthAuthorityCode)
        .build();

    // Consent is fetched differently depending on the version
    FluentFuture<Boolean> checkConsentFuture =
        (BuildUtils.getType() == Type.V2)
        // For v2, we get it directly from shared preferences / in-app opt-in
        ? FluentFuture.from(Futures.immediateFuture(preferences.getAppAnalyticsState()))
        // For v3, we get it by querying checkbox via getPackageConfiguration
        : FluentFuture.from(TaskToFutureAdapter.getFutureWithTimeout(
            exposureNotificationClientWrapper.get().getPackageConfiguration(),
            GET_PACKAGE_CONFIGURATION_TIMEOUT,
            scheduledExecutor))
        .transform(PackageConfigurationHelper::getCheckboxConsentFromPackageConfiguration
            , backgroundExecutor);

    return checkConsentFuture
        .transformAsync(
            consent -> {
              if (!consent) {
                // If we don't have checkbox consent
                Log.i(tag, "Skipped firelog upload.");
                repository.eraseEventsBatch();
                return Futures.immediateFailedFuture(new NoConsentException());
              } else {
                return CallbackToFutureAdapter.getFuture(
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
                    });
              }
            }, backgroundExecutor)
        .transform(unused -> {
          // If we have a lastEntryToSend, we only erase logs up until (including this log)
          if (lastEntryToSend != null) {
            Log.d(tag, "ErasingEventsBatchUpToIncludingEvent " + lastEntryToSend);
            repository.eraseEventsBatchUpToIncludingEvent(lastEntryToSend);
          } else {
            repository.eraseEventsBatch();
          }
          Log.i(tag, "Analytics log batch sent to Firelog.");
          return null;
        }, backgroundExecutor)
        .catching(NoConsentException.class, ex -> null, backgroundExecutor);
  }

  private void logEventIfEnabled(EnxLogExtension logEvent) {
    if (BuildUtils.getType() == Type.V2) {
      if (preferences.getAppAnalyticsState()) {
        repository.recordEvent(logEvent);
        if (!preferences.maybeGetAnalyticsLoggingLastTimestamp().isPresent()) {
          preferences.resetAnalyticsLoggingLastTimestamp();
        }
        Log.i(tag, "App analytics enabled via in-app consent. Sending log event.");
      } else {
        Log.d(tag, "App analytics disabled via in-app consent. Not sending log event.");
      }
    } else /* BuildUtils.getType() == Type.V3 */ {
      exposureNotificationClientWrapper.get().getPackageConfiguration()
          .addOnSuccessListener(backgroundExecutor, packageConfiguration -> {
            if (PackageConfigurationHelper
                .getCheckboxConsentFromPackageConfiguration(packageConfiguration)) {
              repository.recordEvent(logEvent);
              if (!preferences.maybeGetAnalyticsLoggingLastTimestamp().isPresent()) {
                preferences.resetAnalyticsLoggingLastTimestamp();
              }
              Log.i(tag, "App analytics enabled via checkbox. Sending log event.");
            } else /* consent not granted via checkbox*/ {
              // Clear recorded events, but only if there were any to avoid unnecessary DB-calls
              repository.eraseEventsBatch();
              Log.d(tag, "App analytics disabled via checkbox. Not sending log event and "
                  + "clearing previous event record.");
            }
          })
          .addOnCanceledListener(() -> Log.i(tag, "Call getPackageConfiguration is canceled"))
          .addOnFailureListener(t -> Log.e(tag, "Error calling getPackageConfiguration", t));
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

  /**
   * An {@link Exception} to use in Future chains when logging consent was not given.
   */
  private static class NoConsentException extends Exception {}
}
