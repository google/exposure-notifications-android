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
import static com.google.android.apps.exposurenotification.nearby.ExposureNotificationClientWrapper.ACTION_WAKE_UP;
import static com.google.android.apps.exposurenotification.nearby.PreAuthTEKsReceivedWorker.TEKS_RECEIVED_WORKER_TAG;
import static com.google.android.gms.nearby.exposurenotification.ExposureNotificationClient.ACTION_EXPOSURE_NOT_FOUND;
import static com.google.android.gms.nearby.exposurenotification.ExposureNotificationClient.ACTION_EXPOSURE_STATE_UPDATED;
import static com.google.android.gms.nearby.exposurenotification.ExposureNotificationClient.ACTION_PRE_AUTHORIZE_RELEASE_PHONE_UNLOCKED;
import static com.google.common.truth.Truth.assertThat;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.google.common.collect.ImmutableList;
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
public class ExposureNotificationBroadcastReceiverTest {

  @Rule
  public ExposureNotificationRules rules = ExposureNotificationRules.forTest(this).withMocks()
      .build();

  private final Context context = ApplicationProvider.getApplicationContext();

  WorkManager workManager;
  Intent actionExposureNotFound;
  Intent actionExposureStateUpdated;
  Intent actionWakeUp;
  Intent actionPreAuthReleasePhoneUnlocked;
  List<Intent> allIntents;

  @Before
  public void setup() {
    rules.hilt().inject();

    Configuration config = new Configuration.Builder()
        .setExecutor(new SynchronousExecutor())
        .build();
    WorkManagerTestInitHelper.initializeTestWorkManager(
        context, config);
    workManager = WorkManager.getInstance(context);

    // Set up all intents we want to test.
    actionExposureNotFound = new Intent(context, ExposureNotificationBroadcastReceiver.class);
    actionExposureNotFound.setAction(ACTION_EXPOSURE_NOT_FOUND);
    actionExposureNotFound.setPackage(context.getPackageName());

    actionExposureStateUpdated = new Intent(context, ExposureNotificationBroadcastReceiver.class);
    actionExposureStateUpdated.setAction(ACTION_EXPOSURE_STATE_UPDATED);
    actionExposureStateUpdated.setPackage(context.getPackageName());

    actionWakeUp = new Intent(context, ExposureNotificationBroadcastReceiver.class);
    actionWakeUp.setAction(ACTION_WAKE_UP);
    actionWakeUp.setPackage(context.getPackageName());

    actionPreAuthReleasePhoneUnlocked = new Intent(context,
        ExposureNotificationBroadcastReceiver.class);
    actionPreAuthReleasePhoneUnlocked.setAction(ACTION_PRE_AUTHORIZE_RELEASE_PHONE_UNLOCKED);
    actionPreAuthReleasePhoneUnlocked.setPackage(context.getPackageName());

    allIntents = ImmutableList.of(actionExposureNotFound, actionExposureStateUpdated,
        actionWakeUp, actionPreAuthReleasePhoneUnlocked);
  }

  @Test
  public void androidManifest_isBroadcastReceiverRegistered() {
    ExposureNotificationBroadcastReceiver receiver =
        getReceiverOfClass(ExposureNotificationBroadcastReceiver.class);

    assertThat(receiver).isNotNull();
  }

  @Test
  public void application_getReceiversForIntent_returnsSmsVerificationBroadcastReceiver() {
    ShadowApplication shadowApplication =
        shadowOf((Application)ApplicationProvider.getApplicationContext());
    for (Intent intent : allIntents) {
      List<BroadcastReceiver> receiversForIntent =
          shadowApplication.getReceiversForIntent(intent);
      assertThat(receiversForIntent.get(0))
          .isInstanceOf(ExposureNotificationBroadcastReceiver.class);
    }
  }

  @Test
  public void onReceive_actionPreAuthReleasePhoneUnlockedNoExtra_workerNotEnqueued()
      throws Exception {
    ExposureNotificationBroadcastReceiver receiver =
        getReceiverOfClass(ExposureNotificationBroadcastReceiver.class);

    receiver.onReceive(context, actionPreAuthReleasePhoneUnlocked);
    List<WorkInfo> workInfos = workManager.getWorkInfosByTag(TEKS_RECEIVED_WORKER_TAG).get();

    assertThat(workInfos).isEmpty();
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

}
