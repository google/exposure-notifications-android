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

package com.google.android.apps.exposurenotification.keydownload;

import android.net.Uri;
import com.google.auto.value.AutoValue;

/**
 * A pair of {@link Uri}s that together tell the app where to download TEK keyfiles.
 *
 * <p>The {@code indexUri} is the full URL to an index file whose content is individual keyfile
 * filenames. Each of those filenames gets appended to {@code fileBaseUri} to form a full URL to
 * download one keyfile.
 */
@AutoValue
public abstract class DownloadUriPair {

  public abstract Uri indexUri();

  public abstract Uri fileBaseUri();

  public static DownloadUriPair create(String indexUri, String fileBaseUri) {
    return new AutoValue_DownloadUriPair(Uri.parse(indexUri), Uri.parse(fileBaseUri));
  }
}
