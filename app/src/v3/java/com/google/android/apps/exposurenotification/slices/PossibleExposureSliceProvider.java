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

import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PorterDuff.Mode;
import android.graphics.drawable.Drawable;
import android.net.Uri;
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
  private static final String TAG = "PosExpSliceProvider";

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

    // Make sure we only return the slice to a Google-signed GMSCore
    if (!slicePermissionManager.callingUidHasAccess()) {
      return null;
    }

    // Return slices depending on the requested sliceUri
    if (POSSIBLE_EXPOSURE_SLICE_URI.getPath().equals(sliceUri.getPath())) {
      // createPossibleExposureSlice might return null itself if there are no exposures
      return createPossibleExposureSlice(context, sliceUri);
    } else {
      return null;
    }
  }



  /**
   * Fetches recent exposure information from shared preferences and builds an exposure slice.
   *
   * @return a slice with exposure information filled in if there is an active exposure, null
   * otherwise
   */
  private Slice createPossibleExposureSlice(Context context, Uri sliceUri) {
    // Get clock and ExposureNotificationSharedPreferences via DI
    PossibleExposureSliceProviderInterface possibleExposureSliceProviderInterface =
        EntryPoints.get(context, PossibleExposureSliceProviderInterface.class);

    Clock clock = possibleExposureSliceProviderInterface.getClock();

    ExposureNotificationSharedPreferences exposureNotificationSharedPreferences =
        possibleExposureSliceProviderInterface.getExposureNotificationSharedPreferences();

    // Manually fetch current exposureClassification and revocation data
    ExposureClassification exposureClassification =
        exposureNotificationSharedPreferences.getExposureClassification();
    boolean isRevoked = exposureNotificationSharedPreferences.getIsExposureClassificationRevoked();

    // Only create a slice if there actually is an exposure (or a revocation)
    if (exposureClassification.getClassificationIndex()
        == ExposureClassification.NO_EXPOSURE_CLASSIFICATION_INDEX
        && !isRevoked) {
      return null;
    }

    // Create the actual slice
    PendingIntent action = PendingIntent.getActivity(
        context, POSSIBLE_EXPOSURE_ACTIVITY_REQUEST_CODE,
        IntentUtil.getPossibleExposureSliceIntent(context), 0);
    IconCompat icon = IconCompat.createWithBitmap(
        getBitmapFromVectorDrawable(getContext(), R.drawable.ic_exposure_card));
    icon.setTint(ContextCompat.getColor(context, R.color.notification_color));
    icon.setTintMode(Mode.SRC_IN);
    String title = context.getString(R.string.exposure_details_status_exposure);
    String subtitle =
        StringUtils.daysFromStartOfExposure(exposureClassification, clock.now(), context);

    ListSliceBuilderWrapper listBuilder =
        ListSliceBuilderWrapper.createListSliceBuilderWrapper(getContext(), sliceUri);
    listBuilder.addRow(action, icon, title, subtitle);
    return listBuilder.build();
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
}
