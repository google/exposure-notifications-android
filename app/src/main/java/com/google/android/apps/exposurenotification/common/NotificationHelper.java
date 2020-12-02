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

import static com.google.android.apps.exposurenotification.home.ExposureNotificationActivity.ACTION_LAUNCH_FROM_EXPOSURE_NOTIFICATION;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationCompat.Builder;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.home.ExposureNotificationActivity;
import java.util.Objects;

/**
 * Helper class for managing notifications in the app.
 */
public final class NotificationHelper {

  @VisibleForTesting
  static final String EXPOSURE_NOTIFICATION_CHANNEL_ID =
      "ApolloExposureNotificationCallback.EXPOSURE_NOTIFICATION_CHANNEL_ID";

  protected static final String NOTIFICATION_DISMISSED_ACTION_ID =
      "ApolloExposureNotificationCallback.NOTIFICATION_DISMISSED_ACTION_ID";

  /**
   * Shows a notification, notifying of a possible exposure.
   */
  public void showPossibleExposureNotification(Context context, @StringRes int titleResource,
      @StringRes int messageResource) {
    createNotificationChannel(context);
    Intent intent = new Intent(context, ExposureNotificationActivity.class);
    intent.setAction(ACTION_LAUNCH_FROM_EXPOSURE_NOTIFICATION);
    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
    PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);

    Intent deleteIntent = new Intent(context, ExposureNotificationDismissedReceiver.class);
    deleteIntent.setAction(NOTIFICATION_DISMISSED_ACTION_ID);
    PendingIntent deletePendingIntent = PendingIntent
        .getBroadcast(context, 0, deleteIntent, PendingIntent.FLAG_CANCEL_CURRENT);

    NotificationCompat.Builder builder =
        new Builder(context, EXPOSURE_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_exposure_notification)
            .setColor(ContextCompat.getColor(context, R.color.notification_color))
            .setContentTitle(context.getString(titleResource))
            .setContentText(context.getString(messageResource))
            .setStyle(new NotificationCompat.BigTextStyle()
                .bigText(context.getString(messageResource)))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setDeleteIntent(deletePendingIntent)
            // Do not reveal this notification on a secure lockscreen.
            .setVisibility(NotificationCompat.VISIBILITY_SECRET);
    NotificationManagerCompat notificationManager = NotificationManagerCompat
        .from(context);
    notificationManager.notify(0, builder.build());
  }

  /**
   * Creates the notification channel for O and above.
   */
  private void createNotificationChannel(Context context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      NotificationChannel channel =
          new NotificationChannel(EXPOSURE_NOTIFICATION_CHANNEL_ID,
              context.getString(R.string.notification_channel_name),
              NotificationManager.IMPORTANCE_HIGH);
      channel.setDescription(context.getString(R.string.notification_channel_description));
      NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
      Objects.requireNonNull(notificationManager).createNotificationChannel(channel);
    }
  }
}
