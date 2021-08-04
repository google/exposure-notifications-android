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

package com.google.android.apps.exposurenotification.notify;

import static com.google.android.apps.exposurenotification.nearby.ExposureNotificationClientWrapper.ACTION_VERIFICATION_LINK;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.IntentUtil;
import com.google.android.apps.exposurenotification.common.NotificationHelper;
import dagger.hilt.android.AndroidEntryPoint;
import javax.inject.Inject;

/**
 * Broadcast receiver that accepts SMSVerification broadcasts from EN module and displays a
 * "share your diagnosis" notification to the user. The notification serves as an additional
 * entry-point to the deep-linked sharing flow.
 */
@AndroidEntryPoint(BroadcastReceiver.class)
public class SmsVerificationBroadcastReceiver extends Hilt_SmsVerificationBroadcastReceiver {

  @Inject
  NotificationHelper notificationHelper;

  @Override
  public void onReceive(Context context, Intent intent) {
    super.onReceive(context, intent);

    String action = intent.getAction();
    if (!ACTION_VERIFICATION_LINK.equals(action)) {
      return;
    }

    Uri deeplinkUri = intent.getData();
    if (deeplinkUri == null || Uri.EMPTY.equals(deeplinkUri)) {
      return;
    }

    if (!ShareDiagnosisFlowHelper.isSmsInterceptEnabled(context)) {
      return;
    }

    notificationHelper.showNotification(context,
        R.string.notify_others_notification_title,
        R.string.enx_testVerificationNotificationBody,
        IntentUtil.getNotificationContentIntentSmsVerification(context, deeplinkUri),
        IntentUtil.getNotificationDeleteIntentSmsVerification(context));

  }
}
