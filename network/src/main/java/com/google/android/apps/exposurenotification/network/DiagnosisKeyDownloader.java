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
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.File;
import java.util.List;

/**
 * A class to download all the files in a given batch of Diagnosis Key files.
 */
class DiagnosisKeyDownloader {
  private static final String TAG = "DiagnosisKeyDownloader";

  private final Context context;

  DiagnosisKeyDownloader(Context context) {
    this.context = context;
  }

  /**
   * Downloads all the bundles of Diagnosis Keys for the current batch and returns a future with a
   * list of all the files.
   *
   * <p>The caller would then parse these files into Diagnosis Keys and supply them to Google Play
   * Services' providerDiagnosisKeys() method.
   */
  ListenableFuture<List<File>> download() {
    return Futures.immediateFuture(ImmutableList.of());
  }
}
