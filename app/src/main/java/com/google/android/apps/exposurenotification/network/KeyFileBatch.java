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

import android.net.Uri;
import androidx.annotation.NonNull;
import com.google.auto.value.AutoValue;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.Collection;
import java.util.List;

/**
 * A carrier for {@link Uri}s and {@link File}s associated with a given region (server) and batch.
 *
 * <p>This construct is used throughout the diagnosis key download process, so at some stages it has
 * only URIs, and at later stages it also has the downloaded files.
 */
@AutoValue
public abstract class KeyFileBatch {

  /**
   * The country/region code which indicates an "area of authority" for sharing diagnosis keys.
   *
   * <p>It tell us what server URIs should be used for this batch of files.
   */
  public abstract String region();

  /**
   * An identifier of a batch of diagnosis  key files.
   *
   * <p>Batch numbers must increase over time; that is to say that for batch numbers X and Y, if
   * X < Y then X was available for download before Y. This is useful to skip past batches by
   * storing one "most recent processed batch" datum.
   */
  public abstract long batchNum();

  /**
   * A list of files that exist on the local filesystem, ready for processing.
   *
   * <p>Should always be either empty or contain all files for a given batch (never an incomplete
   * batch). May be empty if the files have not yet been downloaded.
   */
  public abstract ImmutableList<File> files();

  /**
   * A list of network URIs at which we expect to find a batch of diagnosis key files.
   *
   * <p>Should always be either empty or contain all URIs for a given batch (not an incomplete
   * batch).
   */
  public abstract ImmutableList<Uri> uris();

  public static KeyFileBatch ofFiles(String region, long batchNum, Collection<File> files) {
    return new AutoValue_KeyFileBatch(
        region, batchNum, ImmutableList.copyOf(files), ImmutableList.of());
  }

  public static KeyFileBatch ofFiles(String region, long batchNum, File... files) {
    return ofFiles(region, batchNum, ImmutableList.copyOf(files));
  }

  public static KeyFileBatch ofUris(String region, long batchNum, Collection<Uri> uris) {
    return new AutoValue_KeyFileBatch(
        region, batchNum, ImmutableList.of(), ImmutableList.copyOf(uris));
  }

  /**
   * Creates a new {@link KeyFileBatch} with the given {@link File}s, replacing any {@link File}s
   * that this {@link KeyFileBatch} may have. Leaves this {@link KeyFileBatch} unchanged (@AutoValue
   * objects are immutable).
   */
  public KeyFileBatch copyWith(List<File> files) {
    return new AutoValue_KeyFileBatch(region(), batchNum(), ImmutableList.copyOf(files), uris());
  }

  @NonNull
  public String toString() {
    return MoreObjects.toStringHelper(KeyFileBatch.class)
        .add("region", region())
        .add("batchNum", batchNum())
        .add("uris", uris())
        .add("files", "{" + files().size() + " files}")
        .toString();
  }
}
