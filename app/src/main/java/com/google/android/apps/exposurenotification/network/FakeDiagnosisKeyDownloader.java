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
import com.google.android.apps.exposurenotification.R;
import com.google.common.collect.ImmutableList;
import com.google.common.io.BaseEncoding;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import org.apache.commons.io.FileUtils;

/**
 * A faked version of to {@link DiagnosisKeyDownloader} that provides a baked-in sample file to the
 * provideDiagnosisKeys() API.
 */
class FakeDiagnosisKeyDownloader {
  private static final SecureRandom RAND = new SecureRandom();
  private static final BaseEncoding BASE32 = BaseEncoding.base32().lowerCase().omitPadding();
  private static final String FILE_PATTERN = "/diag_keys/sample_diagnosis_key_file_%s.zip";

  private final Context context;

  FakeDiagnosisKeyDownloader(Context context) {
    this.context = context;
  }

  /**
   * Downloads all available files of Diagnosis Keys for the currently applicable regions and
   * returns a future with a list of all the files. (Well, a real implementation would do so; this
   * fake implementation just returns a built-in static file.)
   *
   * <p>The caller would then provide these files of Diagnosis Keys to Google Play Services'
   * provideDiagnosisKeys() method.
   */
  ListenableFuture<ImmutableList<KeyFileBatch>> download() {
    // Here we copy a sample keyfile from app resources to the local filesystem, whereas a full
    // implementation would get the file(s) from a server instead.
    File keyFile = new File(context.getFilesDir(), String.format(FILE_PATTERN, uniq()));
    try (InputStream in = context.getResources().openRawResource(R.raw.sample_diagnosis_key_file)) {
      FileUtils.copyInputStreamToFile(in, keyFile);
    } catch (IOException e) {
      // TODO: Better exception.
      throw new RuntimeException(e);
    }
    return Futures.immediateFuture(ImmutableList.of(KeyFileBatch.ofFiles("US", 1, keyFile)));
  }

  private static String uniq() {
    byte[] bytes = new byte[4];
    RAND.nextBytes(bytes);
    return BASE32.encode(bytes);
  }
}
