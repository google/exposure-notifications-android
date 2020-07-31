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

package com.google.android.apps.exposurenotification.network;

import android.content.Context;
import android.util.Log;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences.NetworkMode;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * A facade to network operations to download Diagnosis Keys from the keyserver.
 *
 * <p>This facade uses shared preferences to switch between using a live server and a local faked
 * implementation.
 */
public class DownloadController {

  private static final String TAG = "DownloadController";

  private final DiagnosisKeyDownloader diagnosisKeyDownloader;
  private final FakeDiagnosisKeyDownloader fakeDiagnosisKeyDownloader;
  private final ExposureNotificationSharedPreferences preferences;

  public DownloadController(Context context) {
    diagnosisKeyDownloader = new DiagnosisKeyDownloader(context.getApplicationContext());
    fakeDiagnosisKeyDownloader = new FakeDiagnosisKeyDownloader(context.getApplicationContext());
    preferences = new ExposureNotificationSharedPreferences(context.getApplicationContext());
  }

  public DownloadController(
      DiagnosisKeyDownloader downloader,
      FakeDiagnosisKeyDownloader fakeDownloader,
      ExposureNotificationSharedPreferences prefs) {
    diagnosisKeyDownloader = downloader;
    fakeDiagnosisKeyDownloader = fakeDownloader;
    preferences = prefs;
  }

  public ListenableFuture<ImmutableList<KeyFileBatch>> download() {
    NetworkMode mode = preferences.getKeySharingNetworkMode(NetworkMode.DISABLED);
    switch (mode) {
      case DISABLED:
        Log.d(TAG, "Server disabled. Using fake FakeDiagnosisKeyDownloader");
        return fakeDiagnosisKeyDownloader.download();
      case LIVE:
        Log.d(TAG, "Server enabled. Using real DiagnosisKeyDownloader");
        return diagnosisKeyDownloader.download();
      default:
        throw new IllegalArgumentException("Unsupported network mode: " + mode);
    }
  }
}
