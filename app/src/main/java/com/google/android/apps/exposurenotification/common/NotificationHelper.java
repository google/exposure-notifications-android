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

import android.Manifest.permission;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.text.TextUtils;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationCompat.Builder;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.BuildUtils.Type;
import com.google.android.apps.exposurenotification.common.logging.Logger;
import com.google.common.base.Optional;
import java.util.Objects;

/**
 * Helper class for managing notifications in the app.
 */
public final class NotificationHelper {

  private static final Logger logger = Logger.getLogger("NotificationHelper");

  @VisibleForTesting
  static final String EXPOSURE_NOTIFICATION_CHANNEL_ID =
      "ApolloExposureNotificationCallback.EXPOSURE_NOTIFICATION_CHANNEL_ID";

  private static final int POSSIBLE_EXPOSURE_NOTIFICATION_ID = 0;
  private static final int REACTIVATE_APPLICATION_NOTIFICATION_ID = 1;

  public enum NotificationPermissionRequestState {
    /**
     * Notification permission granted by user already or Android pre-granted the permission
     * because the app was already installed before upgrade to Android T.
     */
    GRANTED,

    /**
     * The user has already declined the notification permission request in the past. We cannot
     * request for notification permission via the system permission dialog. We should take the
     * user to the app settings to update the notification permission preference.
     */
    DENIED,

    /**
     * The user has not declined the notification permission request in the past. We can still
     * request for notification permission via the system permission dialog.
     */
    NOT_GRANTED_BUT_CAN_REQUEST,

    /**
     * Notification permission is not available on this api.
     * {@link permission#POST_NOTIFICATIONS} was added Android T.
     */
    NOT_APPLICABLE
  }

  /**
   * Shows a notification based on Strings resources.
   */
  public void showNotification(Context context, @StringRes int titleResource,
      @StringRes int messageResource, Intent contentIntent, Intent deleteIntent) {
    String titleString = context.getString(titleResource);
    String messageString = context.getString(messageResource);
    showNotification(context, titleString, messageString, contentIntent, deleteIntent);
  }

  /**
   * Shows a notification based on Strings.
   */
  public void showNotification(Context context, String titleString,
      String messageString, Intent contentIntent, Intent deleteIntent) {
    createNotificationChannel(context);

    PendingIntent pendingIntent;
    PendingIntent deletePendingIntent;

    if (VERSION.SDK_INT >= VERSION_CODES.M) {
      pendingIntent = PendingIntent.getActivity(context, 0, contentIntent,
              PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
      deletePendingIntent = PendingIntent.getBroadcast(context, 0, deleteIntent,
              PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_CANCEL_CURRENT);
    } else {
      pendingIntent = PendingIntent
          .getActivity(context, 0, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT);
      deletePendingIntent = PendingIntent
          .getBroadcast(context, 0, deleteIntent, PendingIntent.FLAG_CANCEL_CURRENT);
    }

    NotificationCompat.Builder builder = createBuilder(context, titleString, messageString,
        pendingIntent, Optional.of(deletePendingIntent));

    NotificationManagerCompat notificationManager = NotificationManagerCompat
        .from(context);
    notificationManager.notify(POSSIBLE_EXPOSURE_NOTIFICATION_ID, builder.build());
  }

  /**
   * Shows a notification, notifying user to reactivate the exposure notifications app. This
   * notification is disabled for V3 apps
   */
  public void showReActivateENAppNotification(Context context,
      @StringRes int titleResource, @StringRes int messageResource) {

    if (BuildUtils.getType() == Type.V3) {
      return;
    }

    createNotificationChannel(context);

    Intent notificationIntent = IntentUtil.getNotificationContentIntentExposure(context);
    PendingIntent pendingIntent;

    if (VERSION.SDK_INT >= VERSION_CODES.M) {
      pendingIntent = PendingIntent.getActivity(context, 0, notificationIntent,
              PendingIntent.FLAG_IMMUTABLE);
    } else {
      pendingIntent = PendingIntent.getActivity(context, 0,
          notificationIntent, 0);
    }

    NotificationCompat.Builder builder = createBuilder(
        context,
        context.getString(titleResource),
        context.getString(messageResource,
            StringUtils.getHealthAuthorityName(context)),
        pendingIntent,
        Optional.absent());

    NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
    notificationManager.notify(REACTIVATE_APPLICATION_NOTIFICATION_ID, builder.build());
  }

  /**
   * Dismisses notification notifying user to reactivate the exposure notifications app.
   * The reactivate exposure app notification is disabled for V3 apps.
   */
  public void dismissReActivateENNotificationIfShowing(Context context) {
    if (BuildUtils.getType() == Type.V3) {
      return;
    }
    NotificationManagerCompat.from(context).cancel(REACTIVATE_APPLICATION_NOTIFICATION_ID);
  }

  /**
   * Creates the notification channel for O and above.
   */
  public void createNotificationChannel(Context context) {
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

  private static NotificationCompat.Builder createBuilder(
      Context context,
      String titleString,
      String messageString,
      PendingIntent pendingIntent,
      Optional<PendingIntent> deletePendingIntent) {
    NotificationCompat.Builder builder =
        new Builder(context, EXPOSURE_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_exposure_notification)
            .setColor(ContextCompat.getColor(context, R.color.notification_color))
            .setContentTitle(titleString)
            .setContentText(messageString)
            .setStyle(new NotificationCompat.BigTextStyle()
                .bigText(messageString))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            // Do not reveal this notification on a secure lockscreen.
            .setVisibility(NotificationCompat.VISIBILITY_SECRET);
    if (deletePendingIntent.isPresent()) {
      builder.setDeleteIntent(deletePendingIntent.get());
    }
    maybeAppendSubText(context, builder);
    return builder;
  }

  /**
   * Appends a sub text for N+, v3 apps that have the extra v3SubText specified.
   */
  private static void maybeAppendSubText(Context context, NotificationCompat.Builder builder) {
    if (VERSION.SDK_INT >= Build.VERSION_CODES.N && BuildUtils.getType() == Type.V3) {
      String subText = context.getString(R.string.enx_v3NotificationSubText);
      if (!TextUtils.isEmpty(subText)) {
        builder.setSubText(subText);
      }
    }
  }

  /**
   * Requests for notification permission if the permission is available and the permission
   * has not been denied/granted already.
   *
   * Returns true if the permission request was made.
   */
  @SuppressLint("InlinedApi")
  public boolean maybeRequestNotificationPermission(Activity activity,
      ActivityResultLauncher<String> requestNotificationPermissionLauncher) {
    NotificationPermissionRequestState state = getNotificationPermissionState(activity);

    switch (state) {
      case NOT_GRANTED_BUT_CAN_REQUEST:
        requestNotificationPermissionLauncher.launch(permission.POST_NOTIFICATIONS);
        return true;
      case DENIED:
      case GRANTED:
      case NOT_APPLICABLE:
        return false;
    }
    return false;
  }

  /**
   * Returns a {@link NotificationPermissionRequestState} indicating the current notification
   * permission state. See {@link NotificationPermissionRequestState } for more details.
   * */
  public NotificationPermissionRequestState getNotificationPermissionState(Activity activity) {
    if (Build.VERSION.SDK_INT < VERSION_CODES.TIRAMISU) {
      // No notification permission pre Android T.
      return NotificationPermissionRequestState.NOT_APPLICABLE;
    } else {
      if (ContextCompat.checkSelfPermission(activity, permission.POST_NOTIFICATIONS)
          == PackageManager.PERMISSION_GRANTED) {
        return NotificationPermissionRequestState.GRANTED;
      } else if (ActivityCompat.shouldShowRequestPermissionRationale(activity,
          permission.POST_NOTIFICATIONS)) {
        return NotificationPermissionRequestState.DENIED;
      } else {
        /* User has not granted permission but we can request for notification permission. */
        return NotificationPermissionRequestState.NOT_GRANTED_BUT_CAN_REQUEST;
      }
    }
  }

  /**
   * Returns true if the app can post notifications to the user.
   *
   * On Android T+, this method returns true if the user already granted notification
   * permission or if this App is exempted because the user upgraded from a previous version with
   * notification permission.
   *
   * On pre T, this method returns false if the user has turned off notifications in the app's
   * settings or device settings, otherwise it returns true.
   * */
  public boolean areNotificationsEnabled(Context context) {
    NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
    return notificationManager.areNotificationsEnabled();
  }

  /**
   * Launches the app's notification settings screen. If that was unsuccessful, launch the
   * app's details settings screen instead.
   */
  public void launchAppNotificationSettings(Context context) {
    try {
      Intent intent = IntentUtil.getNotificationSettingsIntent(context);
      context.startActivity(intent);
    } catch (ActivityNotFoundException e) {
      // Couldn't launch the app's notification setting, launch the app's details settings instead.
      launchAppDetailsSettings(context);
    }
  }

  private void launchAppDetailsSettings(Context context) {
    try {
      Intent intent = IntentUtil.getAppDetailsSettingsIntent(context);
      context.startActivity(intent);
    } catch (ActivityNotFoundException e) {
      logger.e("Error, settings activity could not be launched", e);
    }
  }
}
