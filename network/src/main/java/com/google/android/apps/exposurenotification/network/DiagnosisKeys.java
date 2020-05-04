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
import com.google.common.util.concurrent.ListenableFuture;
import java.io.File;
import java.util.List;

/**
 * A facade to network operations to upload Diagnosis Keys (i.e. Exposure Keys covering an
 * infectious period for someone with a positive covid-19 test) to a server, and download all known
 * Diagnosis Keys.
 *
 * <p>The upload is an RPC, the download is a file fetch.
 */
public class DiagnosisKeys {
  private static final String TAG = "DiagnosisKeys";

  private final DiagnosisKeyDownloader diagnosisKeyDownloader;
  private final DiagnosisKeyUploader diagnosisKeyUploader;

  public DiagnosisKeys(Context context) {
    diagnosisKeyDownloader = new DiagnosisKeyDownloader(context.getApplicationContext());
    diagnosisKeyUploader = new DiagnosisKeyUploader(context.getApplicationContext());
  }

  /**
   * Upload Diagnosis Keys to server to mark them as tested positive for covid-19.
   *
   * <p>A Diagnosis key is a Temporary Exposure Key from a user who has tested positive.
   *
   * @param diagnosisKeys List of keys, which includes their interval
   */
  public ListenableFuture<Void> upload(List<DiagnosisKey> diagnosisKeys) {
    return diagnosisKeyUploader.upload(diagnosisKeys);
  }

  public ListenableFuture<List<File>> download() {
    return diagnosisKeyDownloader.download();
  }
}
