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
import static com.google.common.truth.Truth.assertThat;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build.VERSION_CODES;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

@RunWith(AndroidJUnit4.class)
@HiltAndroidTest
@Config(application = HiltTestApplication.class)
public class StorageManagementHelperTest {

  Context context = ApplicationProvider.getApplicationContext();
  PackageManager packageManager = context.getPackageManager();
  ResolveInfo info = new ResolveInfo();

  @Before
  public void setUp() {
    info.activityInfo = new ActivityInfo();
    info.activityInfo.name = "activityName";
    info.activityInfo.packageName = "com.example";
  }

  @Test
  @Config(maxSdk = VERSION_CODES.N)
  public void createStorageManagementIntent_intentNotNullSdk24() {
    shadowOf(packageManager)
        .addResolveInfoForIntent(new Intent(ACTION_INTERNAL_STORAGE_SETTINGS), info);

    Intent intent = StorageManagementHelper.createStorageManagementIntent(context);

    assertThat(intent).isNotNull();
    assertThat(intent.getAction()).isEqualTo(ACTION_INTERNAL_STORAGE_SETTINGS);
  }

  @Test
  @Config(minSdk = VERSION_CODES.N_MR1)
  public void createStorageManagementIntent_intentNotNullSdk25() {
    shadowOf(packageManager)
        .addResolveInfoForIntent(new Intent(ACTION_MANAGE_STORAGE), info);

    Intent intent = StorageManagementHelper.createStorageManagementIntent(context);

    assertThat(intent).isNotNull();
    assertThat(intent.getAction()).isEqualTo(ACTION_MANAGE_STORAGE);
  }
}