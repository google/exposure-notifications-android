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

package com.google.android.apps.exposurenotification.nearby;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationCompat.Builder;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.activities.ExposureNotificationActivity;

/**
 * Performs work for {@value com.google.android.gms.nearby.exposurenotification.ExposureNotificationClient#ACTION_EXPOSURE_STATE_UPDATED}
 * broadcast from exposure notification API.
 */
public class StateUpdatedWorker extends Worker {

  private static final String EXPOSURE_NOTIFICATION_CHANNEL_ID =
      "ApolloExposureNotificationCallback.EXPOSURE_NOTIFICATION_CHANNEL_ID";

  public static final String ACTION_LAUNCH_FROM_EXPOSURE_NOTIFICATION =
      "com.google.android.apps.exposurenotification.ACTION_LAUNCH_FROM_EXPOSURE_NOTIFICATION";

  private final Context context;

  public StateUpdatedWorker(
      @NonNull Context context, @NonNull WorkerParameters workerParams) {
    super(context, workerParams);
    this.context = context;
  }

  private void createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      NotificationChannel channel =
          new NotificationChannel(EXPOSURE_NOTIFICATION_CHANNEL_ID,
              context.getString(R.string.notification_channel_name),
              NotificationManager.IMPORTANCE_HIGH);
      channel.setDescription(context.getString(R.string.notification_channel_description));
      NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
      notificationManager.createNotificationChannel(channel);
    }
  }

  @NonNull
  @Override
  public Result doWork() {
    createNotificationChannel();
    Intent intent = new Intent(getApplicationContext(), ExposureNotificationActivity.class);
    // TODO: avoid the explicit class setting when nearby moves into the main app.
    intent.setPackage(context.getPackageName());
    intent.setAction(ACTION_LAUNCH_FROM_EXPOSURE_NOTIFICATION);
    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
    PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);
    NotificationCompat.Builder builder =
        new Builder(context, EXPOSURE_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(context.getString(R.string.notification_message))
            .setStyle(new NotificationCompat.BigTextStyle()
                .bigText(context.getString(R.string.notification_message)))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true);
    NotificationManagerCompat notificationManager = NotificationManagerCompat
        .from(context);
    notificationManager.notify(0, builder.build());
    return Result.success();
  }
}
