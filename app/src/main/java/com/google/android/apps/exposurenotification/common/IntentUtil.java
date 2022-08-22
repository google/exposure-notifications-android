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

package com.google.android.apps.exposurenotification.common;

import static com.google.android.apps.exposurenotification.notify.ShareDiagnosisFragment.EXTRA_DIAGNOSIS_ID;
import static com.google.android.apps.exposurenotification.notify.ShareDiagnosisFragment.EXTRA_STEP;
import static com.google.android.gms.nearby.exposurenotification.ExposureNotificationClient.ACTION_EXPOSURE_NOTIFICATION_SETTINGS;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.net.UrlQuerySanitizer;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.provider.Settings;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.apps.exposurenotification.common.BuildUtils.Type;
import com.google.android.apps.exposurenotification.nearby.ExposureNotificationClientWrapper;
import com.google.android.apps.exposurenotification.notify.ShareDiagnosisViewModel;
import com.google.common.base.Optional;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Utility class for generating and handling intents.
 */
public final class IntentUtil {

  @VisibleForTesting
  static final int VERIFICATION_CODE_MAX_CHARS = 128;
  @VisibleForTesting
  static final String SELF_REPORT_PATH = "report";
  @VisibleForTesting
  static final String PACKAGE_URI_SCHEME = "package:";
  private static final String VERIFICATION_CODE_REGEX =
      String.format(Locale.US, "^[a-zA-Z0-9]{1,%d}$", VERIFICATION_CODE_MAX_CHARS);
  private static final Pattern VERIFICATION_CODE_PATTERN = Pattern.compile(VERIFICATION_CODE_REGEX);
  private static final String ACTION_APP_NOTIFICATION_SETTINGS =
      "android.settings.APP_NOTIFICATION_SETTINGS";
  private static final String EXTRA_APP_PACKAGE = "app_package";
  private static final String EXTRA_APP_UID = "app_uid";

  // V2 & V3
  public static final String EXTRA_NOTIFICATION = "IntentUtil.EXTRA_NOTIFICATION";

  public static final String EXTRA_SMS_VERIFICATION =
      "IntentUtil.ACTION_SMS_VERIFICATION";

  public static final String EXTRA_DEEP_LINK =
      "IntentUtil.EXTRA_DEEP_LINK";

  // V3 Only
  public static final String ACTION_LAUNCH_HOME =
      "com.google.android.exposurenotification.ACTION_LAUNCH_HOME";

  public static final String ACTION_ONBOARDING =
      "com.google.android.exposurenotification.ACTION_ONBOARDING";

  public static final String ACTION_SHARE_HISTORY =
      "com.google.android.exposurenotification.ACTION_SHARE_HISTORY";

  public static final String ACTION_SHARE_DIAGNOSIS =
      "com.google.android.exposurenotification.ACTION_SHARE_DIAGNOSIS";

  public static final String EXTRA_SLICE = "IntentUtil.EXTRA_SLICE";

  public static final String EXTRA_SMS_NOTICE_SLICE = "IntentUtil.EXTRA_SMS_NOTICE_SLICE";

  protected static final String NOTIFICATION_DISMISSED_ACTION_ID =
      "ApolloExposureNotificationCallback.NOTIFICATION_DISMISSED_ACTION_ID";

  public static Intent getExposureNotificationsSettingsIntent() {
    Intent intent = new Intent(ACTION_EXPOSURE_NOTIFICATION_SETTINGS);
    intent.setPackage(ExposureNotificationClientWrapper.GMSCORE_PACKAGE_NAME);
    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
    return intent;
  }

  public static Intent getPossibleExposureSliceIntent(Context context) {
    Intent intent = getActionLaunchHomeIntent(context);
    intent.putExtra(EXTRA_SLICE, true);
    return intent;
  }

  public static Intent getSmsNoticeSliceIntent(Context context) {
    Intent intent = getActionLaunchHomeIntent(context);
    intent.putExtra(EXTRA_SMS_NOTICE_SLICE, true);
    return intent;
  }

  private static Intent getNotificationContentIntent(Context context) {
    Intent intent;
    if (BuildUtils.getType() == Type.V2) {
      intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
    } else /* BuildUtils.getType() == Type.V3 */ {
      intent = getActionLaunchHomeIntent(context);
    }
    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
    return intent;
  }

  public static Intent getNotificationContentIntentExposure(Context context) {
    Intent intent = getNotificationContentIntent(context);
    intent.putExtra(IntentUtil.EXTRA_NOTIFICATION, true);
    return intent;
  }

  public static Intent getNotificationContentIntentSmsVerification(Context context, Uri uri) {
    Intent intent = getNotificationContentIntent(context);
    intent.putExtra(EXTRA_SMS_VERIFICATION, true);
    intent.putExtra(EXTRA_DEEP_LINK, uri);
    return intent;
  }

  public static Intent getNotificationDeleteIntent(Context context) {
    Intent intent = new Intent(context, ExposureNotificationDismissedReceiver.class);
    intent.setAction(NOTIFICATION_DISMISSED_ACTION_ID);
    return intent;
  }

  public static Intent getNotificationDeleteIntentSmsVerification(Context context) {
    Intent intent = getNotificationDeleteIntent(context);
    intent.putExtra(EXTRA_SMS_VERIFICATION, true);
    return intent;
  }

  public static Intent getNotificationSettingsIntent(Context context) {
    Intent intent = new Intent();
    if (VERSION.SDK_INT >= VERSION_CODES.O) {
      intent.setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
      intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName());
    } else {
      intent.setAction(ACTION_APP_NOTIFICATION_SETTINGS);
      intent.putExtra(EXTRA_APP_PACKAGE, context.getPackageName());
      intent.putExtra(EXTRA_APP_UID, context.getApplicationInfo().uid);
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    return intent;
  }

  public static Intent getAppDetailsSettingsIntent(Context context) {
    Intent intent = new Intent();
    intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
    intent.addCategory(Intent.CATEGORY_DEFAULT);
    intent.setData(Uri.parse(String.format("package:%s", context.getPackageName())));
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    return intent;
  }

  public static Intent getUninstallPackageIntent(Context context) {
    Intent intent = new Intent(Intent.ACTION_UNINSTALL_PACKAGE);
    intent.addCategory(Intent.CATEGORY_DEFAULT);
    intent.setData(Uri.parse(PACKAGE_URI_SCHEME + context.getPackageName()));
    return intent;
  }

  /**
   * Checks if the given Bundle arguments are valid to open the Share Diagnosis flow for an already
   * existing diagnosis.
   *
   * <p>A given bundle is valid to open the Share Diagnosis flow if it has a positive integer value
   * passed for the diagnosis ID and a valid Step name value passed for the share diagnosis flow
   * step.</p>
   *
   * @param args Bundle that holds the arguments.
   * @return true if arguments are valid and false otherwise
   */
  public static boolean isValidBundleToOpenShareDiagnosisFlow(@Nullable Bundle args) {
    if (args == null) {
      return false;
    }

    String stepName = args.getString(EXTRA_STEP, null);
    long diagnosisId = args.getLong(EXTRA_DIAGNOSIS_ID, -1);

    return diagnosisId > -1 && ShareDiagnosisViewModel.getStepNames().contains(stepName);
  }

  /**
   * Parses the verification code from the given Deep Link uri.
   *
   * <p>Basic validation on the code for alpha-numeric charset of between 1 and 128 characters.
   *
   * @param uri uri from the intent of the deep link
   * @return verification code string, absent if not found or not valid
   */
  public static Optional<String> maybeGetCodeFromDeepLinkUri(@Nullable Uri uri) {
    if (uri == null) {
      return Optional.absent();
    }
    @Nullable String cParam = new UrlQuerySanitizer(uri.toString()).getValue("c");
    if (cParam == null) {
      return Optional.absent();
    }
    if (!VERIFICATION_CODE_PATTERN.matcher(cParam).find()) {
      return Optional.absent();
    }
    return Optional.of(cParam);
  }

  /**
   * Checks if the given Deep Link uri is a Self-Report Deep Link uri that should land into the
   * "Get a verification code" screen (if self-report is enabled).
   *
   * @param uri uri from the intent of the deep link
   * @return true if the provided uri is a Self-Report uri and false otherwise.
   */
  public static boolean isSelfReportUri(@Nullable Uri uri) {
    if (uri == null) {
      return false;
    }
    return uri.toString().contains(SELF_REPORT_PATH);
  }

  @VisibleForTesting
  protected static Intent getActionLaunchHomeIntent(Context context) {
    Intent intent = new Intent(ACTION_LAUNCH_HOME);
    intent.setPackage(context.getPackageName());
    return intent;
  }

  private IntentUtil() {
  }
}
