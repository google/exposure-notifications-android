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

import static com.google.android.apps.exposurenotification.nearby.ExposureNotificationClientWrapper.GMSCORE_PACKAGE_NAME;
import static com.google.android.apps.exposurenotification.slices.PossibleExposureSliceProvider.POSSIBLE_EXPOSURE_SLICE_URI;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.StrictMode;
import android.os.StrictMode.ThreadPolicy;
import androidx.slice.SliceManager;
import com.google.android.apps.exposurenotification.common.logging.Logger;
import com.google.android.gms.common.GoogleSignatureVerifier;
import java.util.Arrays;
import java.util.List;

/**
 * Responsible for permission-related tasks on slices.
 * Provides implementations to dynamically grant access to viewing a slice and making sure that
 * sensitive content is only returned to privileged callers (Google-signed GMScore).
 */
public class SlicePermissionManagerV3Impl implements SlicePermissionManager {
  private static final Logger logger = Logger.getLogger("SlicePermissionManager");

  private final Context context;

  SlicePermissionManagerV3Impl(Context context) {
    this.context = context;
  }

  /**
   * Grant a package access to bind or pin this slice.
   * If pinSlice() is called before auto-granting happens via a slice's constructor, we get a
   * SecurityException. Thus we additionally grant access manually at slice/application startup
   * by calling this method.
   */
  public void grantSlicePermission() {
    // There is a very rare bug on Android Pi devices, where the Android framework mis-routes the
    // call and tries to grant permission to a slice that the app does not own. To avoid this case
    // crashing the App, we catch the raised Exception here.
    try {
      SliceManager.getInstance(context)
          .grantSlicePermission(GMSCORE_PACKAGE_NAME, POSSIBLE_EXPOSURE_SLICE_URI);
    } catch (SecurityException ignored) {}
  }

  /**
   * Checks if caller has access to this slice content. This method is independent of AndroidOS's
   * permission checking mechanism. Access is considered allowed if
   * - The calling Uid resolves to the GMScore package name AND
   * - The GMScore package is signed by Google
   *
   * @return false if access is disallowed, true otherwise
   */
  public boolean callingUidHasAccess()  {
    PackageManager packageManager = context.getPackageManager();
    String[] packageNamesArray = packageManager.getPackagesForUid(Binder.getCallingUid());
    if (packageNamesArray == null) {
      logger.e("Access not allowed - could not get package name from uid");
      return false;
    }
    List<String> packageNames = Arrays.asList(packageNamesArray);
    if (!packageNames.contains(GMSCORE_PACKAGE_NAME)) {
      logger.e("Access not allowed for app:" + packageNames);
      return false;
    }

    ThreadPolicy oldPolicy = StrictMode.getThreadPolicy();
    ThreadPolicy newPolicy =
        new ThreadPolicy.Builder(oldPolicy).permitCustomSlowCalls().permitDiskWrites().build();
    StrictMode.setThreadPolicy(newPolicy);
    try {
      for (String packageName : packageNames) {
        if (GoogleSignatureVerifier.getInstance(context).isPackageGoogleSigned(packageName)) {
          return true;
        }
      }
    } finally {
      StrictMode.setThreadPolicy(oldPolicy);
    }
    logger.e("Access not allowed for non-Google app:" + packageNames);
    return false;
  }

}
