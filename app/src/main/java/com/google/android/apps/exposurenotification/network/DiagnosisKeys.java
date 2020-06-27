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
 * A facade to network operations to upload Diagnosis Keys (i.e. Temporary Exposure Keys covering an
 * infectious period for someone with a positive COVID-19 diagnosis) to a server, and download all
 * known Diagnosis Keys.
 *
 * <p>The upload is an RPC, the download is a file fetch.
 *
 * <p>This facade uses shared preferences to switch between using a live test server and internal
 * faked implementations.
 */
public class DiagnosisKeys {
  private static final String TAG = "DiagnosisKeys";

  private final DiagnosisKeyDownloader diagnosisKeyDownloader;
  private final DiagnosisKeyUploader diagnosisKeyUploader;
  private final FakeDiagnosisKeyDownloader fakeDiagnosisKeyDownloader;
  private final FakeDiagnosisKeyUploader fakeDiagnosisKeyUploader;

  private final ExposureNotificationSharedPreferences preferences;

  public DiagnosisKeys(Context context) {
    diagnosisKeyDownloader = new DiagnosisKeyDownloader(context.getApplicationContext());
    diagnosisKeyUploader = new DiagnosisKeyUploader(context.getApplicationContext());
    fakeDiagnosisKeyDownloader = new FakeDiagnosisKeyDownloader(context.getApplicationContext());
    fakeDiagnosisKeyUploader = new FakeDiagnosisKeyUploader(context.getApplicationContext());
    preferences = new ExposureNotificationSharedPreferences(context.getApplicationContext());
  }

  /**
   * Upload Diagnosis Keys to server to mark them as tested positive for COVID-19.
   *
   * <p>A Diagnosis key is a Temporary Exposure Key from a user who has tested positive.
   *
   * @param diagnosisKeys List of keys including their interval, period and transmission risk.
   */
  public ListenableFuture<?> upload(ImmutableList<DiagnosisKey> diagnosisKeys) {
    NetworkMode mode = preferences.getNetworkMode(NetworkMode.FAKE);
    switch (mode) {
      case FAKE:
        Log.d(TAG, "Using fake: FakeDiagnosisKeyUploader");
        return fakeDiagnosisKeyUploader.upload(diagnosisKeys);
      case TEST:
        Log.d(TAG, "Using real: DiagnosisKeyUploader");
        return diagnosisKeyUploader.upload(diagnosisKeys);
      default:
        throw new IllegalArgumentException("Unsupported network mode: " + mode);
    }
  }

  public ListenableFuture<ImmutableList<KeyFileBatch>> download() {
    NetworkMode mode = preferences.getNetworkMode(NetworkMode.FAKE);
    switch (mode) {
      case FAKE:
        Log.d(TAG, "Using fake: FakeDiagnosisKeyDownloader");
        return fakeDiagnosisKeyDownloader.download();
      case TEST:
        Log.d(TAG, "Using real: DiagnosisKeyDownloader");
        return diagnosisKeyDownloader.download();
      default:
        throw new IllegalArgumentException("Unsupported network mode: " + mode);
    }
  }
}
