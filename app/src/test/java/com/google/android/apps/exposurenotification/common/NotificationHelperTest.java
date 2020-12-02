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

package com.google.android.apps.exposurenotification.common;

import static com.google.android.apps.exposurenotification.common.NotificationHelper.EXPOSURE_NOTIFICATION_CHANNEL_ID;
import static org.robolectric.Shadows.shadowOf;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.apps.exposurenotification.R;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowNotificationManager;

import static com.google.common.truth.Truth.assertThat;

@RunWith(AndroidJUnit4.class)
@HiltAndroidTest
@Config(application = HiltTestApplication.class)
public class NotificationHelperTest {

  private final Context context = ApplicationProvider.getApplicationContext();
  private final ShadowNotificationManager notificationManager =
      shadowOf((NotificationManager) context
          .getSystemService(Context.NOTIFICATION_SERVICE));
  private final NotificationHelper notificationHelper = new NotificationHelper();

  @Test
  public void showPossibleExposureNotification_verifyNotificationInfo() {
    notificationHelper.showPossibleExposureNotification(context, R.string.exposure_notification_title_1,
        R.string.exposure_notification_message_1);

    assertThat(notificationManager.getAllNotifications()).hasSize(1);
    Notification notification = notificationManager.getNotification(0);
    assertThat(shadowOf(notification).getContentTitle())
        .isEqualTo(context.getString(R.string.exposure_notification_title_1));
    assertThat(shadowOf(notification).getContentText())
        .isEqualTo(context.getString(R.string.exposure_notification_message_1));
    assertThat(notification.getSmallIcon().getResId())
        .isEqualTo(R.drawable.ic_exposure_notification);
    assertThat(notification.priority).isEqualTo(NotificationCompat.PRIORITY_MAX);
    assertThat(notification.flags & Notification.FLAG_AUTO_CANCEL)
        .isEqualTo(Notification.FLAG_AUTO_CANCEL);
    assertThat(notification.flags & Notification.FLAG_ONLY_ALERT_ONCE)
        .isEqualTo(Notification.FLAG_ONLY_ALERT_ONCE);
    assertThat(notification.visibility).isEqualTo(NotificationCompat.VISIBILITY_SECRET);
  }

  @Test
  @Config(minSdk = Build.VERSION_CODES.O)
  public void showPossibleExposureNotification_createChannel() {
    notificationHelper.showPossibleExposureNotification(context, R.string.exposure_notification_title_1,
        R.string.exposure_notification_message_1);

    assertThat(notificationManager.getNotificationChannels()).hasSize(1);
    NotificationChannel channel = (NotificationChannel) notificationManager
        .getNotificationChannels().get(0);
    assertThat(channel.getId())
        .isEqualTo(EXPOSURE_NOTIFICATION_CHANNEL_ID);
    assertThat(channel.getName()).isEqualTo(context.getString(R.string.notification_channel_name));
    assertThat(channel.getImportance()).isEqualTo(NotificationManager.IMPORTANCE_HIGH);
    assertThat(channel.getDescription())
        .isEqualTo(context.getString(R.string.notification_channel_description));
  }
}
