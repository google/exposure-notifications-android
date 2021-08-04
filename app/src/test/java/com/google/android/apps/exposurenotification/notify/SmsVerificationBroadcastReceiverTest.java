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

package com.google.android.apps.exposurenotification.notify;

import static com.google.android.apps.exposurenotification.nearby.ExposureNotificationClientWrapper.ACTION_VERIFICATION_LINK;
import static com.google.common.truth.Truth.assertThat;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import androidx.core.app.NotificationCompat;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.BuildUtils;
import com.google.android.apps.exposurenotification.common.BuildUtils.Type;
import com.google.android.apps.exposurenotification.common.IntentUtil;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.apps.exposurenotification.testsupport.ExposureNotificationRules;
import com.google.android.apps.exposurenotification.testsupport.FakeShadowResources;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import java.util.List;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowApplication.Wrapper;
import org.robolectric.shadows.ShadowNotificationManager;

@RunWith(AndroidJUnit4.class)
@HiltAndroidTest
@Config(application = HiltTestApplication.class, shadows = {FakeShadowResources.class})
public class SmsVerificationBroadcastReceiverTest {

  @Rule
  public ExposureNotificationRules rules = ExposureNotificationRules.forTest(this).withMocks()
      .build();

  private static final Uri SAMPLE_DEEPLINK = Uri.parse("https://us-goo.en.express/v?c=62221765");

  private final Context context = ApplicationProvider.getApplicationContext();
  private Intent smsVerificationIntent;

  private final ShadowNotificationManager notificationManager =
      shadowOf((NotificationManager) context
          .getSystemService(Context.NOTIFICATION_SERVICE));

  private final FakeShadowResources resources =
      (FakeShadowResources) shadowOf(context.getResources());

  @Inject
  ExposureNotificationSharedPreferences exposureNotificationSharedPreferences;

  @Before
  public void setup() {
    rules.hilt().inject();

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

  /**
   * Robolectric does does not invoke broadcast receivers with context.sendBroadcast, if they
   * contain "data" attributes.
   * To get around this limitation, we call onReceive directly. This lets us test the receivers
   * receive logic, but as a side effect also bypasses system checks on
   * - The "permission" attribute of broadcast receivers
   * - If the intent matches the manifests "intent-filter"
   */
  @Test
  public void onReceive_intentActionDoesNotMatch_showNoNotification() {
    SmsVerificationBroadcastReceiver receiver =
        getReceiverOfClass(SmsVerificationBroadcastReceiver.class);
    Intent intent = new Intent();
    intent.setAction(ACTION_VERIFICATION_LINK + "_no_match");
    intent.setPackage(context.getPackageName());

    receiver.onReceive(context, intent);

    assertThat(notificationManager.getAllNotifications()).isEmpty();
  }

  @Test
  public void onReceive_intentActionMatchesNoData_showNoNotification() {
    SmsVerificationBroadcastReceiver receiver =
        getReceiverOfClass(SmsVerificationBroadcastReceiver.class);
    Intent intent = new Intent();
    intent.setAction(ACTION_VERIFICATION_LINK);
    intent.setPackage(context.getPackageName());

    receiver.onReceive(context, intent);

    assertThat(notificationManager.getAllNotifications()).isEmpty();
  }

  @Test
  public void onReceive_enableTextMessageVerificationFalse_showNoNotification() {
    resources.addFakeResource(R.bool.enx_enableTextMessageVerification, false);
    SmsVerificationBroadcastReceiver receiver =
        getReceiverOfClass(SmsVerificationBroadcastReceiver.class);
    Intent intent = new Intent();
    intent.setAction(ACTION_VERIFICATION_LINK);
    intent.setPackage(context.getPackageName());

    receiver.onReceive(context, intent);

    assertThat(notificationManager.getAllNotifications()).isEmpty();
  }

  @Test
  public void onReceive_testVerificationNotificationBodyEmpty_showNoNotification() {
    resources.addFakeResource(R.string.enx_testVerificationNotificationBody, "");
    SmsVerificationBroadcastReceiver receiver =
        getReceiverOfClass(SmsVerificationBroadcastReceiver.class);
    Intent intent = new Intent();
    intent.setAction(ACTION_VERIFICATION_LINK);
    intent.setPackage(context.getPackageName());

    receiver.onReceive(context, intent);

    assertThat(notificationManager.getAllNotifications()).isEmpty();
  }

  @Test
  public void onReceive_intentActionMatches_showNotificationWithDeeplinkIntentForV2() {
    // GIVEN A registered SmsVerificationBroadcastReceiver and SMS verification enabled
    resources.addFakeResource(R.bool.enx_enableTextMessageVerification, true);
    resources.addFakeResource(R.string.enx_testVerificationNotificationBody, "non-empty");
    SmsVerificationBroadcastReceiver receiver =
        getReceiverOfClass(SmsVerificationBroadcastReceiver.class);

    // WHEN Receiver is called
    receiver.onReceive(context, smsVerificationIntent);

    if (BuildUtils.getType() == Type.V2) {
      // THEN We launch a notification that contains a deeplinking Intent
      assertThat(notificationManager.getAllNotifications()).hasSize(1);
      Notification notification = notificationManager.getNotification(0);
      assertThat(shadowOf(notification).getContentTitle())
          .isEqualTo(context.getString(R.string.notify_others_notification_title));
      assertThat(shadowOf(notification).getContentText())
          .isEqualTo(context.getString(R.string.enx_testVerificationNotificationBody,
              context.getString(R.string.health_authority_name)));
      assertThat(notification.getSmallIcon().getResId())
          .isEqualTo(R.drawable.ic_exposure_notification);
      assertThat(notification.priority).isEqualTo(NotificationCompat.PRIORITY_MAX);
      assertThat(notification.flags & Notification.FLAG_AUTO_CANCEL)
          .isEqualTo(Notification.FLAG_AUTO_CANCEL);
      assertThat(notification.flags & Notification.FLAG_ONLY_ALERT_ONCE)
          .isEqualTo(Notification.FLAG_ONLY_ALERT_ONCE);
      assertThat(notification.visibility).isEqualTo(NotificationCompat.VISIBILITY_SECRET);
      assertThat(notification.contentIntent).isEqualTo(
          PendingIntent.getActivity(context, 0,
              IntentUtil.getNotificationContentIntentSmsVerification(context,
                  SAMPLE_DEEPLINK), 0));
    } else {
      assertThat(notificationManager.getAllNotifications()).isEmpty();
    }
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
