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

package com.google.android.apps.exposurenotification.slices;

import static com.google.android.apps.exposurenotification.nearby.ExposureNotificationClientWrapper.EN_MODULE_PERMISSION;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PorterDuff.Mode;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.IconCompat;
import androidx.slice.Slice;
import androidx.slice.SliceProvider;
import com.google.android.apps.exposurenotification.BuildConfig;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.IntentUtil;
import com.google.android.apps.exposurenotification.common.StringUtils;
import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.android.apps.exposurenotification.nearby.ExposureInformationHelper;
import com.google.android.apps.exposurenotification.notify.ShareDiagnosisFlowHelper;
import com.google.android.apps.exposurenotification.riskcalculation.ExposureClassification;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import dagger.hilt.EntryPoint;
import dagger.hilt.EntryPoints;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;

/**
 Enables Android settings / nearby module to embed up-to-date info in their settings via slices
 */
public class PossibleExposureSliceProvider extends SliceProvider {
  private static final int POSSIBLE_EXPOSURE_ACTIVITY_REQUEST_CODE = 118999;

  static final Uri POSSIBLE_EXPOSURE_SLICE_URI = new Uri.Builder()
      .scheme(ContentResolver.SCHEME_CONTENT)
      .authority(BuildConfig.APPLICATION_ID + ".slice")
      .appendPath("possible_exposure")
      .build();

  private SlicePermissionManager slicePermissionManager;

  /**
   * Constructor for slice autoGrantPermission functionality based on permissions.
   * Manual package-name based grantSlicePermissions in onCreateSliceProvider crash for pre API 28.
   */
  public PossibleExposureSliceProvider() {
    super(/* autoGrantPermissions= */ EN_MODULE_PERMISSION);
  }

  /**
   * Interface for Dependency Injection As of now Hilt does only support manual entry points for
   * content providers
   */
  @EntryPoint
  @InstallIn(SingletonComponent.class)
  public interface PossibleExposureSliceProviderInterface {
    SlicePermissionManager getSlicePermissionManager();
    ExposureNotificationSharedPreferences getExposureNotificationSharedPreferences();
    Clock getClock();
    ExposureInformationHelper getExposureInformationHelper();
  }

  /**
   * Initializes the SliceProvider and fetch dependencies.
   */
  @Override
  public boolean onCreateSliceProvider() {
    Context context = getContext();
    if (context == null) {
      return false;
    }

    PossibleExposureSliceProviderInterface possibleExposureSliceProviderInterface =
        EntryPoints.get(context, PossibleExposureSliceProviderInterface.class);
    slicePermissionManager = possibleExposureSliceProviderInterface.getSlicePermissionManager();

    return true;
  }

  /*
   * Called when settings requests the possible exposure slice.
   * Pulls the relevant exposure information from shared preferences and if there is an exposure,
   * returns a Slice to the caller. Returns null if there currently is no exposure.
   */
  @Override
  public Slice onBindSlice(Uri sliceUri) {
    Context context = getContext();
    if (context == null) {
      return null;
    }

    // If a different slice URI was requested, we return no slice
    if (!POSSIBLE_EXPOSURE_SLICE_URI.getPath().equals(sliceUri.getPath())) {
      return null;
    }

    // Make sure we only return the slice to a Google-signed GMSCore
    if (!slicePermissionManager.callingUidHasAccess()) {
      return null;
    }

    // Get clock, ExposureNotificationSharedPreferences, and ExposureInformationHelper via DI
    PossibleExposureSliceProviderInterface possibleExposureSliceProviderInterface =
        EntryPoints.get(context, PossibleExposureSliceProviderInterface.class);

    Clock clock = possibleExposureSliceProviderInterface.getClock();

    ExposureNotificationSharedPreferences exposureNotificationSharedPreferences =
        possibleExposureSliceProviderInterface.getExposureNotificationSharedPreferences();

    ExposureInformationHelper exposureInformationHelper =
        possibleExposureSliceProviderInterface.getExposureInformationHelper();

    // Manually fetch current exposureClassification and revocation data
    ExposureClassification exposureClassification =
        exposureNotificationSharedPreferences.getExposureClassification();
    boolean isRevoked = exposureNotificationSharedPreferences.getIsExposureClassificationRevoked();

    ListSliceBuilderWrapper listBuilder =
        ListSliceBuilderWrapper.createListSliceBuilderWrapper(getContext(), sliceUri);

    // Create a possible exposure slice if there actually is an exposure (or a revocation) and this
    // exposure (or a revocation) are active.
    if ((exposureClassification.getClassificationIndex()
        != ExposureClassification.NO_EXPOSURE_CLASSIFICATION_INDEX
        || isRevoked)
        && exposureInformationHelper.isActiveExposurePresent()) {
      createPossibleExposureSlice(listBuilder, context, exposureClassification, isRevoked, clock);
    }

    // Manually fetch SMS notice information
    boolean isInAppSmsNoticeSeen = exposureNotificationSharedPreferences.isInAppSmsNoticeSeen();
    boolean isPlaySmsNoticeSeen = exposureNotificationSharedPreferences.isPlaySmsNoticeSeen();

    // Create a SMS notice slice if SMS intercept is enabled and we've not shown a notice in-app
    // or during play onboarding
    if (!isInAppSmsNoticeSeen && !isPlaySmsNoticeSeen &&
        ShareDiagnosisFlowHelper.isSmsInterceptEnabled(context)) {
      createSmsNoticeSlice(listBuilder, context);
    }

    // Check if we added any rows until here, otherwise return null
    if (!listBuilder.isEmpty()) {
      return listBuilder.build();
    } else {
      return null;
    }
  }

  /**
   * Build Sms-notice slice and add it to the provided ListSliceBuilderWrapper.
   */
  private void createSmsNoticeSlice(ListSliceBuilderWrapper listBuilder, Context context) {
    PendingIntent action = createPendingIntent(context, POSSIBLE_EXPOSURE_ACTIVITY_REQUEST_CODE,
        IntentUtil.getSmsNoticeSliceIntent(context));
    IconCompat icon = IconCompat.createWithBitmap(
        getBitmapFromVectorDrawable(getContext(), R.drawable.ic_check_filled));
    String title = context.getString(R.string.sms_intercept_notice_card_title);
    String subtitle = context.getString(R.string.sms_intercept_notice_card_content);

    listBuilder.addRow(action, icon, title, subtitle);
  }

  /**
   * Build possible-exposure slice and add it to the provided ListSliceBuilderWrapper.
   * All information required for its content (exposureClassification, isRevoked, clock) is
   * passed via arguments.
   */
  private void createPossibleExposureSlice(ListSliceBuilderWrapper listBuilder, Context context,
      ExposureClassification exposureClassification, boolean isRevoked, Clock clock) {
    PendingIntent action = createPendingIntent(context, POSSIBLE_EXPOSURE_ACTIVITY_REQUEST_CODE,
        IntentUtil.getPossibleExposureSliceIntent(context));
    IconCompat icon = IconCompat.createWithBitmap(
        getBitmapFromVectorDrawable(getContext(), R.drawable.ic_exposure_card));
    icon.setTint(ContextCompat.getColor(context, R.color.notification_color));
    icon.setTintMode(Mode.SRC_IN);
    String title = context.getString(R.string.exposure_details_status_exposure);
    String subtitle =
        StringUtils.daysFromStartOfExposure(exposureClassification, clock.now(), context);

    listBuilder.addRow(action, icon, title, subtitle);
  }

  private static Bitmap getBitmapFromVectorDrawable(Context context, int drawableId) {
    Drawable drawable = ContextCompat.getDrawable(context, drawableId);
    Bitmap bitmap =
        Bitmap.createBitmap(
            drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bitmap);
    drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
    drawable.draw(canvas);
    return bitmap;
  }

  // Immutable flags are only available on Android 23+. To cover older Android versions, we
  // intentionally omit this flag on pre Android 23.
  @SuppressLint("UnspecifiedImmutableFlag")
  private static PendingIntent createPendingIntent(Context context, int requestCode,
      Intent intent) {
    if (VERSION.SDK_INT >= VERSION_CODES.M) {
      return PendingIntent.getActivity(context, requestCode, intent,
          PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    } else {
      return PendingIntent.getActivity(context, requestCode, intent,
          PendingIntent.FLAG_UPDATE_CURRENT);
    }
  }

}
