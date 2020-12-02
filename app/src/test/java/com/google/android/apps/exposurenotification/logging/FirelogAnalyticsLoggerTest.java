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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.support.test.espresso.core.internal.deps.guava.collect.ImmutableList;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.android.volley.NetworkResponse;
import com.android.volley.VolleyError;
import com.google.android.apps.exposurenotification.BuildConfig;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.ExecutorsModule;
import com.google.android.apps.exposurenotification.common.Qualifiers.BackgroundExecutor;
import com.google.android.apps.exposurenotification.common.Qualifiers.LightweightExecutor;
import com.google.android.apps.exposurenotification.common.Qualifiers.ScheduledExecutor;
import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.android.apps.exposurenotification.common.time.RealTimeModule;
import com.google.android.apps.exposurenotification.proto.ApiCall;
import com.google.android.apps.exposurenotification.proto.ApiCall.ApiCallType;
import com.google.android.apps.exposurenotification.proto.EnxLogExtension;
import com.google.android.apps.exposurenotification.proto.RpcCall;
import com.google.android.apps.exposurenotification.proto.RpcCall.RpcCallResult;
import com.google.android.apps.exposurenotification.proto.RpcCall.RpcCallType;
import com.google.android.apps.exposurenotification.proto.UiInteraction;
import com.google.android.apps.exposurenotification.proto.UiInteraction.EventType;
import com.google.android.apps.exposurenotification.proto.WorkManagerTask;
import com.google.android.apps.exposurenotification.proto.WorkManagerTask.WorkerTask;
import com.google.android.apps.exposurenotification.storage.AnalyticsLoggingEntity;
import com.google.android.apps.exposurenotification.storage.AnalyticsLoggingRepository;
import com.google.android.apps.exposurenotification.storage.DbModule;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationDatabase;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.apps.exposurenotification.storage.WorkerStatusRepository;
import com.google.android.apps.exposurenotification.testsupport.ExposureNotificationRules;
import com.google.android.apps.exposurenotification.testsupport.FakeClock;
import com.google.android.apps.exposurenotification.testsupport.InMemoryDb;
import com.google.android.datatransport.Event;
import com.google.android.datatransport.Transport;
import com.google.android.datatransport.TransportScheduleCallback;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Status;
import com.google.common.base.Optional;
import com.google.common.io.BaseEncoding;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.testing.TestingExecutors;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.android.testing.BindValue;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import dagger.hilt.android.testing.UninstallModules;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeoutException;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.robolectric.annotation.Config;
import org.threeten.bp.Duration;
import org.threeten.bp.Instant;

@RunWith(AndroidJUnit4.class)
@HiltAndroidTest
@Config(application = HiltTestApplication.class)
@UninstallModules(
    {RealTimeModule.class, DbModule.class, TransportModule.class, ExecutorsModule.class})
public class FirelogAnalyticsLoggerTest {

  private static final BaseEncoding BASE64 = BaseEncoding.base64();
  @BindValue
  @BackgroundExecutor
  static final ExecutorService BACKGROUND_EXEC = MoreExecutors.newDirectExecutorService();
  @BindValue
  @LightweightExecutor
  static final ExecutorService LIGHTWEIGHT_EXEC = MoreExecutors.newDirectExecutorService();
  @BindValue
  @BackgroundExecutor
  static final ListeningExecutorService BACKGROUND_LISTENING_EXEC =
      MoreExecutors.newDirectExecutorService();
  @BindValue
  @LightweightExecutor
  static final ListeningExecutorService LIGHTWEIGHT_LISTENING_EXEC =
      MoreExecutors.newDirectExecutorService();
  @BindValue
  @ScheduledExecutor
  static final ScheduledExecutorService SCHEDULED_EXEC =
      TestingExecutors.sameThreadScheduledExecutor();

  @Rule
  public ExposureNotificationRules rules =
      ExposureNotificationRules.forTest(this).withMocks().build();

  @BindValue
  @Mock
  Transport<EnxLogExtension> transport;
  @BindValue
  ExposureNotificationDatabase db = InMemoryDb.create();
  @BindValue
  Clock clock = new FakeClock();

  @Inject
  AnalyticsLoggingRepository repository;
  @Inject
  WorkerStatusRepository workerStatusRepo;
  @Inject
  ExposureNotificationSharedPreferences preferences;
  @Inject
  @ApplicationContext
  Context context;

  @Inject
  FirelogAnalyticsLogger logger;


  @Before
  public void setUp() {
    rules.hilt().inject();
    // Logging is enabled for most of these tests. Can override in specific tests.
    preferences.setAppAnalyticsState(true);
  }

  @Test
  public void logApiSuccess_shouldWriteDbRecord_withApiCallTypeAndStatusZero() throws Exception {
    // WHEN
    logger.logApiCallSuccess(ApiCallType.CALL_IS_ENABLED);

    // THEN
    assertThat(storedLogs())
        .containsExactly(EnxLogExtension.newBuilder()
            .addApiCall(ApiCall.newBuilder()
                .setApiCallType(ApiCallType.CALL_IS_ENABLED)
                .setStatusCode(0))
            .build());
  }

  @Test
  public void logApiFail_shouldWriteDbRecord_withApiCallType_andStatusFromException()
      throws Exception {
    // WHEN
    ApiException e = new ApiException(Status.RESULT_INTERNAL_ERROR);
    logger.logApiCallFailure(ApiCallType.CALL_IS_ENABLED, e);

    // THEN
    assertThat(storedLogs())
        .containsExactly(EnxLogExtension.newBuilder()
            .addApiCall(ApiCall.newBuilder()
                .setApiCallType(ApiCallType.CALL_IS_ENABLED)
                .setStatusCode(Status.RESULT_INTERNAL_ERROR.getStatusCode()))
            .build());
  }

  @Test
  public void logRpcSuccess_shouldWriteDbRecord_withRpcCallTypeAndPayloadSize() throws Exception {
    // WHEN
    logger.logRpcCallSuccess(RpcCallType.RPC_TYPE_KEYS_DOWNLOAD, 123);

    // THEN
    assertThat(storedLogs())
        .containsExactly(EnxLogExtension.newBuilder()
            .addRpcCall(RpcCall.newBuilder()
                .setRpcCallResult(RpcCallResult.RESULT_SUCCESS)
                .setRpcCallType(RpcCallType.RPC_TYPE_KEYS_DOWNLOAD)
                .setPayloadSize(123))
            .build());
  }

  @Test
  public void logRpcFail_shouldWriteDbRecord_withRpcCallType_andGeneric4xxHttpStatus()
      throws Exception {
    // WHEN
    VolleyError e = volleyErrorOf(456);
    logger.logRpcCallFailure(RpcCallType.RPC_TYPE_KEYS_UPLOAD, e);

    // THEN
    assertThat(storedLogs())
        .containsExactly(EnxLogExtension.newBuilder()
            .addRpcCall(RpcCall.newBuilder()
                .setRpcCallType(RpcCallType.RPC_TYPE_KEYS_UPLOAD)
                .setRpcCallResult(RpcCallResult.RESULT_FAILED_GENERIC_4XX))
            .build());
  }

  @Test
  public void logRpcFail_shouldWriteDbRecord_withRpcCallType_andGeneric5xxHttpStatus()
      throws Exception {
    // WHEN
    VolleyError e = volleyErrorOf(567);
    logger.logRpcCallFailure(RpcCallType.RPC_TYPE_KEYS_UPLOAD, e);

    // THEN
    assertThat(storedLogs())
        .containsExactly(EnxLogExtension.newBuilder()
            .addRpcCall(RpcCall.newBuilder()
                .setRpcCallType(RpcCallType.RPC_TYPE_KEYS_UPLOAD)
                .setRpcCallResult(RpcCallResult.RESULT_FAILED_GENERIC_5XX))
            .build());
  }

  @Test
  public void logWorkManagerStarted_shouldWriteDbRecord_withWorkerAndStartedStatus()
      throws Exception {
    // WHEN
    logger.logWorkManagerTaskStarted(WorkerTask.TASK_PROVIDE_DIAGNOSIS_KEYS);

    // THEN
    assertThat(storedLogs())
        .containsExactly(EnxLogExtension.newBuilder()
            .addWorkManagerTask(WorkManagerTask.newBuilder()
                .setWorkerTask(WorkerTask.TASK_PROVIDE_DIAGNOSIS_KEYS)
                .setHoursSinceLastRun(0)
                .setStatus(WorkManagerTask.Status.STATUS_STARTED))
            .build());
  }

  @Test
  public void logWorkManagerStarted_shouldRecordHoursSinceLastRun_roundedToTheNearestHour()
      throws Exception {
    // GIVEN
    workerStatusRepo.upsert(
        WorkerTask.TASK_PROVIDE_DIAGNOSIS_KEYS,
        WorkManagerTask.Status.STATUS_STARTED.toString(),
        clock.now());
    // Advance by a little more than 1.5 hours
    ((FakeClock) clock).advanceBy(Duration.ofHours(1).plusMinutes(31));

    // WHEN
    logger.logWorkManagerTaskStarted(WorkerTask.TASK_PROVIDE_DIAGNOSIS_KEYS);

    // THEN
    assertThat(storedLogs()).hasSize(1);
    assertThat(storedLogs().get(0).getWorkManagerTaskList()).hasSize(1);
    // 1.5+ hours should round up to 2.
    assertThat(storedLogs().get(0).getWorkManagerTask(0).getHoursSinceLastRun()).isEqualTo(2);
  }

  @Test
  public void logWorkManagerSuccess_shouldWriteDbRecord_withWorkerAndSuccessStatus()
      throws Exception {
    // WHEN
    logger.logWorkManagerTaskSuccess(WorkerTask.TASK_STATE_UPDATED);

    // THEN
    assertThat(storedLogs())
        .containsExactly(EnxLogExtension.newBuilder()
            .addWorkManagerTask(WorkManagerTask.newBuilder()
                .setWorkerTask(WorkerTask.TASK_STATE_UPDATED)
                .setStatus(WorkManagerTask.Status.STATUS_SUCCESS))
            .build());
  }

  @Test
  public void logWorkManagerFail_shouldWriteDbRecord_withWorkerAndFailStatus() throws Exception {
    // WHEN
    logger.logWorkManagerTaskFailure(WorkerTask.TASK_STATE_UPDATED, new RuntimeException());

    // THEN
    assertThat(storedLogs())
        .containsExactly(EnxLogExtension.newBuilder()
            .addWorkManagerTask(WorkManagerTask.newBuilder()
                .setWorkerTask(WorkerTask.TASK_STATE_UPDATED)
                .setStatus(WorkManagerTask.Status.STATUS_FAIL))
            .build());
  }

  @Test
  public void logWorkManagerTimeout_shouldWriteDbRecord_withWorkerAndTimeoutStatus()
      throws Exception {
    // WHEN
    logger.logWorkManagerTaskFailure(WorkerTask.TASK_STATE_UPDATED, new TimeoutException());

    // THEN
    assertThat(storedLogs())
        .containsExactly(EnxLogExtension.newBuilder()
            .addWorkManagerTask(WorkManagerTask.newBuilder()
                .setWorkerTask(WorkerTask.TASK_STATE_UPDATED)
                .setStatus(WorkManagerTask.Status.STATUS_TIMEOUT))
            .build());
  }

  @Test
  public void logWorkManagerTaskAbandoned_shouldWriteDbRecord_withWorkerAndAbandonedStatus()
      throws Exception {
    // WHEN
    logger.logWorkManagerTaskAbandoned(WorkerTask.TASK_STATE_UPDATED);

    // THEN
    assertThat(storedLogs())
        .containsExactly(EnxLogExtension.newBuilder()
            .addWorkManagerTask(WorkManagerTask.newBuilder()
                .setWorkerTask(WorkerTask.TASK_STATE_UPDATED)
                .setStatus(WorkManagerTask.Status.STATUS_ABANDONED))
            .build());
  }

  @Test
  public void logUiInteraction_shouldWriteDbRecord_withEventType()
      throws Exception {
    // WHEN
    logger.logUiInteraction(EventType.SHARE_APP_CLICKED);

    // THEN
    assertThat(storedLogs())
        .containsExactly(EnxLogExtension.newBuilder()
            .addUiInteraction(UiInteraction.newBuilder()
                .setEventType(EventType.SHARE_APP_CLICKED))
            .build());
  }

  @Test
  public void bufferedLogProtos_areBase64Encoded() {
    // WHEN
    logger.logApiCallSuccess(ApiCallType.CALL_IS_ENABLED);

    // THEN
    List<AnalyticsLoggingEntity> records = repository.getEventsBatch();
    assertThat(records).hasSize(1);
    assertThat(records.get(0).getEventProto())
        .isEqualTo(BASE64.encode(EnxLogExtension.newBuilder()
            .addApiCall(ApiCall.newBuilder()
                .setApiCallType(ApiCallType.CALL_IS_ENABLED)
                .setStatusCode(0))
            .build().toByteArray()));
  }

  @Test
  public void submittedLogs_shouldIncludeMetadata() {
    // GIVEN
    logger.logApiCallSuccess(ApiCallType.CALL_IS_ENABLED);

    // WHEN
    // The most recent execution must be longer than 4.5 hours ago, otherwise submission is skipped.
    preferences.resetAnalyticsLoggingLastTimestamp();
    ((FakeClock) clock).advanceBy(Duration.ofHours(4).plusMinutes(31));
    // Now submit.
    logger.sendLoggingBatchIfEnabled();

    // THEN
    ArgumentCaptor<Event<EnxLogExtension>> captor = ArgumentCaptor.forClass(Event.class);
    verify(transport).schedule(captor.capture(), any());
    assertThat(captor.getValue().getPayload().getBuildId()).isEqualTo(BuildConfig.VERSION_CODE);
    // Region ID comes from config resources.
    assertThat(captor.getValue().getPayload().getRegionIdentifier())
        .isEqualTo(context.getResources().getString(R.string.enx_regionIdentifier));
    // Time since last batch gets rounded up from the ~4.5 hours we set the fake clock to.
    assertThat(captor.getValue().getPayload().getHoursSinceLastBatch()).isEqualTo(5);
  }

  @Test
  public void afterSubmission_shouldSetLastSubmittedLogsTimeToNow() {
    // GIVEN
    logger.logApiCallSuccess(ApiCallType.CALL_IS_ENABLED);

    // WHEN
    // The most recent execution must be longer than 4.5 hours ago, otherwise submission is skipped.
    preferences.resetAnalyticsLoggingLastTimestamp();
    ((FakeClock) clock).advanceBy(Duration.ofHours(4).plusMinutes(31));
    // Now submit.
    logger.sendLoggingBatchIfEnabled();

    // THEN
    assertThat(preferences.maybeGetAnalyticsLoggingLastTimestamp())
        .isEqualTo(Optional.of(clock.now()));
  }

  @Test
  public void logEvent_enabledWithUnsetTimestamp_resetsTimestamp() {
    // GIVEN
    assertThat(preferences.maybeGetAnalyticsLoggingLastTimestamp().isPresent()).isFalse();

    // WHEN
    logger.logApiCallSuccess(ApiCallType.CALL_IS_ENABLED);

    // THEN
    assertThat(preferences.maybeGetAnalyticsLoggingLastTimestamp().isPresent()).isTrue();
  }

  @Test
  public void logEvent_enabledWithSetTimestamp_doesNotResetTimestamp() {
    // GIVEN
    ((FakeClock) clock).set(Instant.ofEpochMilli(4));
    preferences.resetAnalyticsLoggingLastTimestamp();
    ((FakeClock) clock).advanceBy(Duration.ofHours(1));

    // WHEN
    logger.logApiCallSuccess(ApiCallType.CALL_IS_ENABLED);

    // THEN
    assertThat(preferences.maybeGetAnalyticsLoggingLastTimestamp().get())
        .isEqualTo(Instant.ofEpochMilli(4));
  }

  @Test
  public void logEvent_disabledWithUnsetTimestamp_doesNotSetTimestamp() {
    // GIVEN
    preferences.setAppAnalyticsState(false);
    assertThat(preferences.maybeGetAnalyticsLoggingLastTimestamp().isPresent()).isFalse();

    // WHEN
    logger.logApiCallSuccess(ApiCallType.CALL_IS_ENABLED);

    // THEN
    assertThat(preferences.maybeGetAnalyticsLoggingLastTimestamp().isPresent()).isFalse();
  }

  @Test
  public void loggingDisabled_shouldNotRecordLogs() {
    // GIVEN
    preferences.setAppAnalyticsState(false);

    // WHEN
    logger.logApiCallSuccess(ApiCallType.CALL_IS_ENABLED);

    // THEN
    assertThat(repository.getEventsBatch()).isEmpty();
  }

  @Test
  public void loggingDisabled_shouldNotSubmitLogs() {
    // GIVEN
    preferences.setAppAnalyticsState(false);

    // WHEN
    logger.logApiCallSuccess(ApiCallType.CALL_IS_ENABLED);
    preferences.resetAnalyticsLoggingLastTimestamp();
    ((FakeClock) clock).advanceBy(Duration.ofHours(4).plusMinutes(31));
    logger.sendLoggingBatchIfEnabled();

    // THEN
    verify(transport, never()).schedule(any(), any());
  }

  @Test
  public void toggleLoggingOnAndOff_shouldRecordLogsWhileOn_notWhileOff() {
    preferences.setAppAnalyticsState(false);
    logger.logApiCallSuccess(ApiCallType.CALL_IS_ENABLED);
    assertThat(repository.getEventsBatch()).isEmpty();

    preferences.setAppAnalyticsState(true);
    logger.logApiCallSuccess(ApiCallType.CALL_IS_ENABLED);
    assertThat(repository.getEventsBatch()).hasSize(1);

    preferences.setAppAnalyticsState(false);
    logger.logApiCallSuccess(ApiCallType.CALL_IS_ENABLED);
    assertThat(repository.getEventsBatch()).isEmpty();

    preferences.setAppAnalyticsState(true);
    logger.logApiCallSuccess(ApiCallType.CALL_IS_ENABLED);
    assertThat(repository.getEventsBatch()).hasSize(1);
  }

  @Test
  public void shouldLogAndSubmit_endtoEnd() {
    // Log lots of things and collect the expected log submissions for each one.
    List<EnxLogExtension> expectedLogs = new ArrayList<>();

    // Successful API call
    logger.logApiCallSuccess(ApiCallType.CALL_IS_ENABLED);
    expectedLogs.add(EnxLogExtension.newBuilder()
        .addApiCall(ApiCall.newBuilder()
            .setApiCallType(ApiCallType.CALL_IS_ENABLED)
            .setStatusCode(0))
        .build());

    // Failed RPC call
    logger.logRpcCallFailure(RpcCallType.RPC_TYPE_KEYS_DOWNLOAD, volleyErrorOf(404));
    expectedLogs.add(EnxLogExtension.newBuilder()
        .addRpcCall(RpcCall.newBuilder()
            .setRpcCallType(RpcCallType.RPC_TYPE_KEYS_DOWNLOAD)
            .setRpcCallResult(RpcCallResult.RESULT_FAILED_GENERIC_4XX))
        .build());

    // Successful Workmanager job.
    logger.logWorkManagerTaskSuccess(WorkerTask.TASK_STATE_UPDATED);
    expectedLogs.add(EnxLogExtension.newBuilder()
        .addWorkManagerTask(WorkManagerTask.newBuilder()
            .setWorkerTask(WorkerTask.TASK_STATE_UPDATED)
            .setStatus(WorkManagerTask.Status.STATUS_SUCCESS))
        .build());

    // UI interaction
    logger.logUiInteraction(EventType.SHARE_APP_CLICKED);
    expectedLogs.add(EnxLogExtension.newBuilder()
        .addUiInteraction(UiInteraction.newBuilder()
            .setEventType(EventType.SHARE_APP_CLICKED))
        .build());

    // Now submit. Well, first we need the most recent execution to be longer than 4.5 hours ago.
    preferences.resetAnalyticsLoggingLastTimestamp();
    ((FakeClock) clock).advanceBy(Duration.ofHours(4).plusMinutes(31));
    // OK, now submit.
    logger.sendLoggingBatchIfEnabled();

    // Log some more stuff.
    logger.logApiCallSuccess(ApiCallType.CALL_GET_DAILY_SUMMARIES);
    // And pretend the collector job ran a bit early (< 4 hours), so this log isn't expected to be
    // sent.
    ((FakeClock) clock).advanceBy(Duration.ofHours(4).minusMinutes(1));
    logger.sendLoggingBatchIfEnabled();

    // Verify the right logs were sent; that is: the first batch should have been sent, but not that
    // last log.
    ArgumentCaptor<Event<EnxLogExtension>> captor = ArgumentCaptor.forClass(Event.class);
    // Also there should only have been one call to submit logs.
    verify(transport, times(1)).schedule(captor.capture(), any());
    EnxLogExtension expected = mergeAll(expectedLogs).toBuilder()
        .setBuildId(BuildConfig.VERSION_CODE)
        // Time since last batch gets rounded up from the ~4.5 hours we set the fake clock to.
        .setHoursSinceLastBatch(5)
        // Region ID comes from config resources.
        .setRegionIdentifier(context.getResources().getString(R.string.enx_regionIdentifier))
        .build();
    assertThat(captor.getValue()).isEqualTo(Event.ofData(expected));
  }

  @Test
  public void sendLoggingBatchIfEnabled_batchNotSent_shouldNotErase() throws Exception {
    // GIVEN
    logger.logApiCallSuccess(ApiCallType.CALL_IS_ENABLED);

    // WHEN
    // The most recent execution must be longer than 4.5 hours ago, otherwise submission is skipped.
    preferences.resetAnalyticsLoggingLastTimestamp();
    ((FakeClock) clock).advanceBy(Duration.ofHours(4).plusMinutes(31));
    // Now submit.
    ListenableFuture<Void> sendLoggingBatchFuture = logger.sendLoggingBatchIfEnabled();

    // THEN
    ArgumentCaptor<TransportScheduleCallback> captor = ArgumentCaptor.forClass(
        TransportScheduleCallback.class);
    verify(transport).schedule(any(), captor.capture());
    captor.getValue().onSchedule(new Exception());
    // Verify that the exception has been thrown and, thus, logs have not been deleted.
    assertThrows(Exception.class, sendLoggingBatchFuture::get);
    assertThat(repository.getEventsBatch()).isNotEmpty();
  }

  @Test
  public void sendLoggingBatchIfEnabled_batchSent_shouldErase() throws Exception {
    // GIVEN
    logger.logApiCallSuccess(ApiCallType.CALL_IS_ENABLED);

    // WHEN
    // The most recent execution must be longer than 4.5 hours ago, otherwise submission is skipped.
    preferences.resetAnalyticsLoggingLastTimestamp();
    ((FakeClock) clock).advanceBy(Duration.ofHours(4).plusMinutes(31));
    // Now submit.
    ListenableFuture<Void> sendLoggingBatchFuture = logger.sendLoggingBatchIfEnabled();

    // THEN
    ArgumentCaptor<TransportScheduleCallback> captor = ArgumentCaptor.forClass(
        TransportScheduleCallback.class);
    verify(transport).schedule(any(), captor.capture());
    captor.getValue().onSchedule(null);
    // Verify that the logs have been deleted because the logging batch was sent.
    sendLoggingBatchFuture.get();
    assertThat(repository.getEventsBatch()).isEmpty();
  }

  /**
   * Most of these tests don't care about the storage mechanism, only that the expected log records
   * are stored for later posting to the service. Here we extract the stored log records for use in
   * assertions.
   */
  private List<EnxLogExtension> storedLogs() throws Exception {
    List<EnxLogExtension> logs = new ArrayList<>();
    for (AnalyticsLoggingEntity e : repository.getEventsBatch()) {
      logs.add(EnxLogExtension.parseFrom(BASE64.decode(e.getEventProto())));
    }
    return logs;
  }

  private static VolleyError volleyErrorOf(int httpStatus) {
    NetworkResponse networkResponse = new NetworkResponse(
        httpStatus,
        new byte[]{},
        false, // notModified
        0L, // networkTimeMs
        ImmutableList.of());
    return new VolleyError(networkResponse);
  }

  private static EnxLogExtension mergeAll(Collection<EnxLogExtension> logs) {
    EnxLogExtension.Builder builder = EnxLogExtension.newBuilder();
    for (EnxLogExtension l : logs) {
      builder.mergeFrom(l);
    }
    return builder.build();
  }
}
