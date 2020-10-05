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

import static android.os.storage.StorageManager.ACTION_MANAGE_STORAGE;
import static android.provider.Settings.ACTION_INTERNAL_STORAGE_SETTINGS;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import androidx.annotation.VisibleForTesting;

/**
 * Helper class for dealing with storage management and it's edge cases for invocation.
 */
public final class StorageManagementHelper {

  /**
   * Check whether storage management is available on this device
   */
  public static boolean isStorageManagementAvailable(Context context) {
    return createStorageManagementIntent(context) != null;
  }

  /**
   * Launch a storage management activity, if available on this device.
   */
  public static void launchStorageManagement(Context context) {
    Intent intent = createStorageManagementIntent(context);

    /* If calls to launchStorageManagement are correctly guarded by isStorageMgmtAvailable calls,
    * this exception is never thrown */
    if (intent == null) {
      throw new UnsupportedOperationException("This device does not support storage management");
    }

    context.startActivity(intent);
  }

  @VisibleForTesting
  static Intent createStorageManagementIntent(Context context) {
    PackageManager packageManager = context.getPackageManager();

    if (VERSION.SDK_INT >= VERSION_CODES.N_MR1) {
      Intent intentManageStorage = new Intent(ACTION_MANAGE_STORAGE);
      if (intentManageStorage.resolveActivity(packageManager) != null) {
        return intentManageStorage;
      }
    }

    Intent intentInternalStorageSettings = new Intent(ACTION_INTERNAL_STORAGE_SETTINGS);
    if (intentInternalStorageSettings.resolveActivity(packageManager) != null) {
        return intentInternalStorageSettings;
    }

    return null;
  }

  private StorageManagementHelper() {}

}
