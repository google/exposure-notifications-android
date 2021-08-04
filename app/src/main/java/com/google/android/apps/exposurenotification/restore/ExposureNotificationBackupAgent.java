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

package com.google.android.apps.exposurenotification.restore;

import android.annotation.SuppressLint;
import android.app.backup.BackupAgentHelper;
import android.app.backup.SharedPreferencesBackupHelper;
import android.content.Context;
import android.content.SharedPreferences;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import dagger.hilt.EntryPoint;
import dagger.hilt.EntryPoints;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;

/**
 * Simple {@link BackupAgentHelper} that backs up an file and handles a device restore event.
 *
 */
public class ExposureNotificationBackupAgent extends BackupAgentHelper {

  @EntryPoint
  @InstallIn(SingletonComponent.class)
  public interface ExposureNotificationBackupAgentEntryPoint {
    ExposureNotificationSharedPreferences getExposureNotificationSharedPreferences();
  }

  public static final String PREFS = "enx_backup_preferences";
  public static final String PREFS_BACKUP_KEY = "enx_backup_prefs_key";

  @Override
  public void onCreate() {
    createEmptySharedPreferenceFile();
    SharedPreferencesBackupHelper helper =
        new SharedPreferencesBackupHelper(this, PREFS);
    addHelper(PREFS_BACKUP_KEY, helper);
  }

  @SuppressLint("ApplySharedPref")
  private void createEmptySharedPreferenceFile() {
    SharedPreferences sharedPreferences = getSharedPreferences(
        ExposureNotificationBackupAgent.PREFS, Context.MODE_PRIVATE);
    sharedPreferences.edit().commit();
  }

  @Override
  public void onRestoreFinished() {
    super.onRestoreFinished();

    ExposureNotificationBackupAgentEntryPoint backupAgentProviderInterface = EntryPoints
        .get(getApplicationContext(), ExposureNotificationBackupAgentEntryPoint.class);

    backupAgentProviderInterface.getExposureNotificationSharedPreferences()
        .setHasPendingRestoreNotificationState(true);
  }
}
