/*
 * Copyright 2021 Google LLC
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

import static com.google.android.apps.exposurenotification.nearby.SmsVerificationWorker.DEEP_LINK_URI_STRING;
import static com.google.android.gms.nearby.exposurenotification.ExposureNotificationStatusCodes.FAILED_KEY_RELEASE_NOT_PREAUTHORIZED;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import androidx.core.app.NotificationCompat;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.work.Configuration;
import androidx.work.Data;
import androidx.work.ListenableWorker.Result;
import androidx.work.WorkManager;
import androidx.work.WorkerParameters;
import androidx.work.impl.utils.SynchronousExecutor;
import androidx.work.testing.WorkManagerTestInitHelper;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.CleanupHelper;
import com.google.android.apps.exposurenotification.common.IntentUtil;
import com.google.android.apps.exposurenotification.common.NotificationHelper;
import com.google.android.apps.exposurenotification.keyupload.Upload;
import com.google.android.apps.exposurenotification.keyupload.UploadController;
import com.google.android.apps.exposurenotification.keyupload.UploadController.NoInternetException;
import com.google.android.apps.exposurenotification.storage.DbModule;
import com.google.android.apps.exposurenotification.storage.DiagnosisRepository;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationDatabase;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.apps.exposurenotification.testsupport.ExposureNotificationRules;
import com.google.android.apps.exposurenotification.testsupport.FakeClock;
import com.google.android.apps.exposurenotification.testsupport.FakeShadowResources;
import com.google.android.apps.exposurenotification.testsupport.InMemoryDb;
import com.google.android.apps.exposurenotification.work.WorkerStartupManager;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.testing.TestingExecutors;
import dagger.hilt.android.testing.BindValue;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import dagger.hilt.android.testing.UninstallModules;
import java.security.SecureRandom;
import javax.inject.Inject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowNotificationManager;

/**
 * This unit test verifies the behavior of smsVerificationWorker, which is fired upon receiving
 * SMSVerification broadcasts from the EN module.
 */
@RunWith(AndroidJUnit4.class)
@HiltAndroidTest
@Config(application = HiltTestApplication.class, shadows = {FakeShadowResources.class},
    minSdk = 21, maxSdk = 30)
@UninstallModules({DbModule.class})
public class SmsVerificationWorkerTest {

  private static final Uri SAMPLE_DEEPLINK = Uri.parse("https://us-goo.en.express/v?c=62221765");
  private static final Task<Void> TASK_FOR_RESULT_VOID = Tasks.forResult(null);

  private final Context context = ApplicationProvider.getApplicationContext();
  private final FakeShadowResources resources =
      (FakeShadowResources) shadowOf(context.getResources());

  @Rule
  public ExposureNotificationRules rules = ExposureNotificationRules.forTest(this)
      .withMocks()
      .build();

  @BindValue
  ExposureNotificationDatabase db = InMemoryDb.create();

  @Inject
  ExposureNotificationSharedPreferences exposureNotificationSharedPreferences;
  @Inject
  NotificationHelper notificationHelper;
  @Inject
  PackageConfigurationHelper packageConfigurationHelper;
  @Inject
  DiagnosisRepository diagnosisRepository;

  @Mock
  WorkerParameters workerParameters;
  @Mock
  ExposureNotificationClientWrapper exposureNotificationClientWrapper;
  @Mock
  UploadController uploadController;
  @Mock
  SecureRandom secureRandom;
  @Mock
  CleanupHelper cleanupHelper;

  FakeClock clock = new FakeClock();
  SmsVerificationWorker smsVerificationWorker;
  ShadowNotificationManager notificationManager;
  WorkManager workManager;

  @Before
  public void setUp() {
    rules.hilt().inject();

    // Initialize WorkManager for testing.
    Context context = ApplicationProvider.getApplicationContext();
    Configuration config = new Configuration.Builder()
        .setExecutor(new SynchronousExecutor())
        .build();
    WorkManagerTestInitHelper.initializeTestWorkManager(
        context, config);
    workManager = WorkManager.getInstance(context);

    notificationManager = shadowOf(
        (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE));

    // Set up all mocked controller operations to succeed by default.
    when(uploadController.submitCode(any()))
        .thenReturn(Futures.immediateFuture(Upload.newBuilder("dummy-code", "dummy-key").build()));
    when(exposureNotificationClientWrapper.isEnabled()).thenReturn(Tasks.forResult(true));
    when(exposureNotificationClientWrapper.requestPreAuthorizedTemporaryExposureKeyRelease())
        .thenReturn(TASK_FOR_RESULT_VOID);

    // Instantiate the actual object under test
    smsVerificationWorker = spy(new SmsVerificationWorker(context, workerParameters,
        exposureNotificationClientWrapper, exposureNotificationSharedPreferences,
        diagnosisRepository, uploadController, notificationHelper,
        MoreExecutors.newDirectExecutorService(), MoreExecutors.newDirectExecutorService(),
        TestingExecutors.sameThreadScheduledExecutor(), secureRandom,
        new WorkerStartupManager(
            exposureNotificationClientWrapper,
            MoreExecutors.newDirectExecutorService(),
            TestingExecutors.sameThreadScheduledExecutor(),
            packageConfigurationHelper,
            cleanupHelper
        ), clock));
  }

  @After
  public void tearDown() {
    db.close();
  }

  @Test
  public void startWork_nullDeepLinkUriString_doesNothing() throws Exception {
    // GIVEN
    Data inputData = new Data.Builder()
        .putString(DEEP_LINK_URI_STRING, null)
        .build();
    when(workerParameters.getInputData()).thenReturn(inputData);

    // WHEN
    Result result = smsVerificationWorker.startWork().get();

    // THEN
    verifyResultSuccessAndNoWorkDone(result);
  }

  @Test
  public void startWork_emptyDeepLinkUriString_doesNothing() throws Exception {
    // GIVEN
    Data inputData = new Data.Builder()
        .putString(DEEP_LINK_URI_STRING, "")
        .build();
    when(workerParameters.getInputData()).thenReturn(inputData);

    // WHEN
    Result result = smsVerificationWorker.startWork().get();

    // THEN
    verifyResultSuccessAndNoWorkDone(result);
  }

  @Test
  public void startWork_enableTextMessageVerificationFalse_doesNothing() throws Exception {
    // GIVEN
    resources.addFakeResource(R.bool.enx_enableTextMessageVerification, false);
    Data inputData = new Data.Builder()
        .putString(DEEP_LINK_URI_STRING, SAMPLE_DEEPLINK.toString())
        .build();
    when(workerParameters.getInputData()).thenReturn(inputData);

    // WHEN
    Result result = smsVerificationWorker.startWork().get();

    // THEN
    verifyResultSuccessAndNoWorkDone(result);
  }

  @Test
  public void startWork_testVerificationNotificationBodyEmpty_doesNothing() throws Exception {
    // GIVEN
    resources.addFakeResource(R.string.enx_testVerificationNotificationBody, "");
    Data inputData = new Data.Builder()
        .putString(DEEP_LINK_URI_STRING, SAMPLE_DEEPLINK.toString())
        .build();
    when(workerParameters.getInputData()).thenReturn(inputData);

    // WHEN
    Result result = smsVerificationWorker.startWork().get();

    // THEN
    verifyResultSuccessAndNoWorkDone(result);
  }

  @Test
  public void startWork_noInternetConnection_resultRetry() throws Exception {
    // GIVEN
    Data inputData = new Data.Builder()
        .putString(DEEP_LINK_URI_STRING, SAMPLE_DEEPLINK.toString())
        .build();
    when(workerParameters.getInputData()).thenReturn(inputData);
    // SMS Verification enabled
    resources.addFakeResource(R.bool.enx_enableTextMessageVerification, true);
    resources.addFakeResource(R.string.enx_testVerificationNotificationBody, "non-empty");
    // ...but no internet connection.
    when(uploadController.submitCode(any()))
        .thenReturn(Futures.immediateFailedFuture(new NoInternetException()));

    // WHEN
    Result result = smsVerificationWorker.startWork().get();

    // THEN
    assertThat(result).isEqualTo(Result.retry());
  }

  @Test
  public void startWork_isEnabledThrowsException_doesNoWork() throws Exception {
    // GIVEN
    Data inputData = new Data.Builder()
        .putString(DEEP_LINK_URI_STRING, SAMPLE_DEEPLINK.toString())
        .build();
    when(workerParameters.getInputData()).thenReturn(inputData);
    // SMS Verification enabled
    resources.addFakeResource(R.bool.enx_enableTextMessageVerification, true);
    resources.addFakeResource(R.string.enx_testVerificationNotificationBody, "non-empty");
    // ...but isEnabled API call fails
    when(exposureNotificationClientWrapper.isEnabled())
        .thenReturn(Tasks.forException(new ApiException(Status.RESULT_INTERNAL_ERROR)));

    // WHEN
    Result result = smsVerificationWorker.startWork().get();

    // THEN
    assertThat(result).isEqualTo(Result.failure());
    verify(exposureNotificationClientWrapper).isEnabled();
    verify(exposureNotificationClientWrapper, never())
        .requestPreAuthorizedTemporaryExposureKeyRelease();
    verify(uploadController, never()).submitCode(any());
    assertThat(notificationManager.getAllNotifications()).isEmpty();
  }

  @Test
  public void startWork_validDataAndPreAuthPermissionGiven_triggerUploadAndShowNoNotification()
      throws Exception {
    // GIVEN
    Data inputData = new Data.Builder()
        .putString(DEEP_LINK_URI_STRING, SAMPLE_DEEPLINK.toString())
        .build();
    when(workerParameters.getInputData()).thenReturn(inputData);
    // SMS Verification enabled
    resources.addFakeResource(R.bool.enx_enableTextMessageVerification, true);
    resources.addFakeResource(R.string.enx_testVerificationNotificationBody, "non-empty");

    // WHEN
    Result result = smsVerificationWorker.startWork().get();

    // THEN
    assertThat(result).isEqualTo(Result.success());
    verify(exposureNotificationClientWrapper).requestPreAuthorizedTemporaryExposureKeyRelease();
    verify(uploadController).submitCode(any());
    assertThat(notificationManager.getAllNotifications()).isEmpty();
  }

  @Test
  public void startWork_validDataNoPreAuthPermission_uploadNotTriggeredAndShowNotification()
      throws Exception {
    // GIVEN
    Data inputData = new Data.Builder()
        .putString(DEEP_LINK_URI_STRING, SAMPLE_DEEPLINK.toString())
        .build();
    when(workerParameters.getInputData()).thenReturn(inputData);
    // SMS Verification enabled
    resources.addFakeResource(R.bool.enx_enableTextMessageVerification, true);
    resources.addFakeResource(R.string.enx_testVerificationNotificationBody, "non-empty");
    // ...and permission not given.
    ApiException apiException = new ApiException(new Status(FAILED_KEY_RELEASE_NOT_PREAUTHORIZED));
    when(exposureNotificationClientWrapper.requestPreAuthorizedTemporaryExposureKeyRelease())
        .thenReturn(Tasks.forException(apiException));

    // WHEN
    Result result = smsVerificationWorker.startWork().get();

    // THEN
    assertThat(result).isEqualTo(Result.success());
    verify(exposureNotificationClientWrapper).requestPreAuthorizedTemporaryExposureKeyRelease();
    verify(uploadController, never()).submitCode(any());
    // THEN We launch a notification that contains a deeplinking Intent
    assertThat(notificationManager.getAllNotifications()).hasSize(1);
    Notification notification = notificationManager.getNotification(0);
    assertThat(shadowOf(notification).getContentTitle())
        .isEqualTo(context.getString(R.string.notify_others_notification_title));
    assertThat(shadowOf(notification).getContentText())
        .isEqualTo(context.getString(R.string.enx_testVerificationNotificationBody,
            context.getString(R.string.health_authority_name)));
    if (VERSION.SDK_INT >= VERSION_CODES.M) {
      assertThat(notification.getSmallIcon().getResId())
          .isEqualTo(R.drawable.ic_exposure_notification);
    }
    assertThat(notification.priority).isEqualTo(NotificationCompat.PRIORITY_MAX);
    assertThat(notification.flags & Notification.FLAG_AUTO_CANCEL)
        .isEqualTo(Notification.FLAG_AUTO_CANCEL);
    assertThat(notification.flags & Notification.FLAG_ONLY_ALERT_ONCE)
        .isEqualTo(Notification.FLAG_ONLY_ALERT_ONCE);
    assertThat(notification.visibility).isEqualTo(NotificationCompat.VISIBILITY_SECRET);

    if (VERSION.SDK_INT >= VERSION_CODES.M) {
      assertThat(notification.contentIntent).isEqualTo(
          PendingIntent.getActivity(context, 0,
              IntentUtil.getNotificationContentIntentSmsVerification(context,
                  SAMPLE_DEEPLINK), PendingIntent.FLAG_IMMUTABLE));
    } else {
      assertThat(notification.contentIntent).isEqualTo(
          PendingIntent.getActivity(context, 0,
              IntentUtil.getNotificationContentIntentSmsVerification(context,
                  SAMPLE_DEEPLINK), 0));
    }

  }

  private void verifyResultSuccessAndNoWorkDone(Result result) {
    assertThat(result).isEqualTo(Result.success());
    verify(exposureNotificationClientWrapper, never()).isEnabled();
    verify(exposureNotificationClientWrapper, never())
        .requestPreAuthorizedTemporaryExposureKeyRelease();
    verify(uploadController, never()).submitCode(any());
    assertThat(notificationManager.getAllNotifications()).isEmpty();
  }

}
