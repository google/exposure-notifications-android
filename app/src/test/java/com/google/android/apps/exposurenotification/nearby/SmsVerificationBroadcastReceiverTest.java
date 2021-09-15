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

import static com.google.android.apps.exposurenotification.nearby.ExposureNotificationClientWrapper.ACTION_VERIFICATION_LINK;
import static com.google.android.apps.exposurenotification.nearby.SmsVerificationWorker.SMS_RECEIVED_WORKER_TAG;
import static com.google.common.truth.Truth.assertThat;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.work.Configuration;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.impl.utils.SynchronousExecutor;
import androidx.work.testing.WorkManagerTestInitHelper;
import com.google.android.apps.exposurenotification.testsupport.ExposureNotificationRules;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowApplication.Wrapper;

@RunWith(AndroidJUnit4.class)
@HiltAndroidTest
@Config(application = HiltTestApplication.class)
public class SmsVerificationBroadcastReceiverTest {

  @Rule
  public ExposureNotificationRules rules = ExposureNotificationRules.forTest(this).withMocks()
      .build();

  private static final Uri SAMPLE_DEEPLINK = Uri.parse("https://us-goo.en.express/v?c=62221765");

  private final Context context = ApplicationProvider.getApplicationContext();
  private Intent smsVerificationIntent;

  WorkManager workManager;

  @Before
  public void setup() {
    rules.hilt().inject();

    Configuration config = new Configuration.Builder()
        .setExecutor(new SynchronousExecutor())
        .build();
    WorkManagerTestInitHelper.initializeTestWorkManager(
        context, config);
    workManager = WorkManager.getInstance(context);

    smsVerificationIntent = new Intent(context, SmsVerificationBroadcastReceiver.class);
    smsVerificationIntent.setAction(ACTION_VERIFICATION_LINK);
    smsVerificationIntent.setData(SAMPLE_DEEPLINK);
    smsVerificationIntent.setPackage(context.getPackageName());
  }

  @Test
  public void androidManifest_isBroadcastReceiverRegistered() {
    SmsVerificationBroadcastReceiver receiver =
        getReceiverOfClass(SmsVerificationBroadcastReceiver.class);

    assertThat(receiver).isNotNull();
  }

  @Test
  public void application_getReceiversForIntent_returnsSmsVerificationBroadcastReceiver() {
    ShadowApplication shadowApplication =
        shadowOf((Application)ApplicationProvider.getApplicationContext());
    List<BroadcastReceiver> receiversForIntent =
        shadowApplication.getReceiversForIntent(smsVerificationIntent);

    assertThat(receiversForIntent.get(0)).isInstanceOf(SmsVerificationBroadcastReceiver.class);
  }

  @Test
  public void onReceive_intentActionDoesNotMatch_workerNotEnqueued() throws Exception {
    SmsVerificationBroadcastReceiver receiver =
        getReceiverOfClass(SmsVerificationBroadcastReceiver.class);
    Intent intent = new Intent();
    intent.setAction(ACTION_VERIFICATION_LINK + "_no_match");
    intent.setPackage(context.getPackageName());

    receiver.onReceive(context, intent);

    assertThatSmsReceivedWorkerNotFired();
  }

  @Test
  public void onReceive_intentActionMatchesAndIntentHasNoData_workerNotEnqueued() throws Exception {
    SmsVerificationBroadcastReceiver receiver =
        getReceiverOfClass(SmsVerificationBroadcastReceiver.class);
    Intent intent = new Intent();
    intent.setAction(ACTION_VERIFICATION_LINK);
    intent.setPackage(context.getPackageName());

    receiver.onReceive(context, intent);

    assertThatSmsReceivedWorkerNotFired();
  }


  @Test
  public void onReceive_intentActionMatchesAndIntentDataIsNull_workerNotEnqueued()
      throws Exception {
    SmsVerificationBroadcastReceiver receiver =
        getReceiverOfClass(SmsVerificationBroadcastReceiver.class);
    Intent intent = new Intent();
    intent.setAction(ACTION_VERIFICATION_LINK);
    intent.setPackage(context.getPackageName());
    intent.setData(null);

    receiver.onReceive(context, intent);

    assertThatSmsReceivedWorkerNotFired();
  }

  @Test
  public void onReceive_intentActionMatchesAndIntentHasData_workerEnqueued() throws Exception {
    SmsVerificationBroadcastReceiver receiver =
        getReceiverOfClass(SmsVerificationBroadcastReceiver.class);

    receiver.onReceive(context, smsVerificationIntent);
    List<WorkInfo> workInfos = workManager.getWorkInfosByTag(SMS_RECEIVED_WORKER_TAG).get();

    assertThat(workInfos).hasSize(1);
  }

  private <T> T getReceiverOfClass(Class<T> receiverClass) {
    ShadowApplication app = shadowOf((Application) context);
    List<Wrapper> receivers = app.getRegisteredReceivers();
    for (Wrapper wrapper : receivers) {
      if (receiverClass.isInstance(wrapper.getBroadcastReceiver())) {
        return receiverClass.cast(wrapper.getBroadcastReceiver());
      }
    }

    return null;
  }

  private void assertThatSmsReceivedWorkerNotFired() throws Exception {
    List<WorkInfo> workInfos = workManager.getWorkInfosByTag(SMS_RECEIVED_WORKER_TAG).get();
    assertThat(workInfos).isEmpty();
  }
}
