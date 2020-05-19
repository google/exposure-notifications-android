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
import android.net.Uri;
import android.util.Log;
import androidx.concurrent.futures.CallbackToFutureAdapter;
import com.android.volley.Response.ErrorListener;
import com.android.volley.Response.Listener;
import com.android.volley.toolbox.StringRequest;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.AppExecutors;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Encapsulates logic for resolving URIs for uploading and downloading Diagnosis Keys.
 *
 * <p>For uploads this is relatively simple: likely just a lookup. For downloads it's less simple:
 * for each applicable country/region we fetch an index file which itself lists all the diagnosis
 * key files to fetch. We return the URIs for those files.
 *
 * <p>This example code focuses on demonstrating the server API and helping you test end to end
 * operation of your app along with a server and Google Play Services Exposure Notifications API
 * while in development. It simply downloads all files available for the user's applicable regions
 * every time it is called. This does not address the different batching or interval strategies that
 * a production app might implement.
 */
public class Uris {
  private static final String TAG = "Uris";
  private static final Splitter WHITESPACE_SPLITTER =
      Splitter.onPattern("\\s+").trimResults().omitEmptyStrings();
  // Path for index files; to be appended to the base URI with %s replaced by an ISO-Alpha-2 country
  // code
  private static final String INDEX_FILE_FORMAT = "exposureKeyExport-%s/index.txt";
  // For any of the server uploads and downloads to work, the app must be built with non-default
  // URIs set in gradle.properties. This pattern helps us check.
  private static final Pattern DEFAULT_URI_PATTERN = Pattern.compile(".*example\\.com.*");
  private static final Pattern BATCH_NUM_PATTERN =
      Pattern.compile("exposureKeyExport-[A-Z]{2}/([0-9]+)-[0-9]+.zip");

  private final Context context;
  private final ExposureNotificationSharedPreferences prefs;
  private final Uri baseDownloadUri;
  private final Uri uploadUri;

  public Uris(Context context) {
    this.context = context;
    this.prefs = new ExposureNotificationSharedPreferences(context);
    // These two string resources must be set by gradle.properties.
    baseDownloadUri = Uri.parse(context.getString(R.string.key_server_download_base_uri));
    uploadUri = Uri.parse(context.getString(R.string.key_server_upload_uri));
  }

  /**
   * Gets the URIs of upload services for the given counthy codes.
   *
   * <p>Not necessarily exactly one per country.
   */
  ListenableFuture<ImmutableList<Uri>> getUploadUris(List<String> regionsIsoAlpha2) {
    // Check if the app has been built with default URIs first.
    if (hasDefaultUris()) {
      Log.w(TAG, "Attempting to use servers while compiled with default URIs!");
      return Futures.immediateFuture(ImmutableList.of());
    }
    // In this test instance we use one server for all regions.
    // An alternative implementation could have a "home" server and a "roaming" server, or some
    // other scheme.
    return Futures.immediateFuture(ImmutableList.of(uploadUri));
  }

  /** Gets batches of URIs from which to download key files for the given country codes. */
  ListenableFuture<ImmutableList<KeyFileBatch>> getDownloadFileUris(List<String> regionsIsoAlpha2) {
    // Check if the app has been built with default URIs first.
    if (hasDefaultUris()) {
      Log.w(TAG, "Attempting to use servers while compiled with default URIs!");
      return Futures.immediateFuture(ImmutableList.of());
    }

    Log.d(TAG, "Getting download URIs for " + regionsIsoAlpha2.size() + " regions");
    List<ListenableFuture<ImmutableList<KeyFileBatch>>> perRegionBatches = new ArrayList<>();
    for (String region : regionsIsoAlpha2) {
      perRegionBatches.add(regionBatches(region));
    }
    return FluentFuture.from(Futures.allAsList(perRegionBatches))
        .transform(
            batches -> {
              List<KeyFileBatch> flattenedList = new ArrayList<>();
              for (List<KeyFileBatch> list : batches) {
                flattenedList.addAll(list);
              }
              return ImmutableList.copyOf(flattenedList);
            },
            AppExecutors.getLightweightExecutor());
  }

  private ListenableFuture<ImmutableList<KeyFileBatch>> regionBatches(String regionCode) {
    return FluentFuture.from(index(regionCode))
        .transform(
            indexContent -> {
              List<String> indexEntries = WHITESPACE_SPLITTER.splitToList(indexContent);
              Log.d(TAG, "Index file has " + indexEntries.size() + " lines.");
              Map<Long, List<Uri>> batches = new HashMap<>();

              // Parse out each line of the index file and split them into batches as indicated by
              // the leading timestamp in the filename, e.g. "1589490000" for
              // "exposureKeyExport-US/1589490000-00002.zip"
              for (String indexEntry : indexEntries) {
                Matcher m = BATCH_NUM_PATTERN.matcher(indexEntry);
                if (!m.matches() || m.group(1) == null) {
                  throw new RuntimeException(
                      "Failed to parse batch num from File [" + indexEntry + "].");
                }
                Long batchNum = Long.valueOf(m.group(1));
                Log.d(
                    TAG, String.format("Batch number %s from indexEntry %s", batchNum, indexEntry));
                if (!batches.containsKey(batchNum)) {
                  batches.put(batchNum, new ArrayList<>());
                }
                batches
                    .get(batchNum)
                    .add(baseDownloadUri.buildUpon().appendEncodedPath(indexEntry).build());
              }

              ImmutableList.Builder<KeyFileBatch> builder = ImmutableList.builder();
              for (Map.Entry<Long, List<Uri>> batch : batches.entrySet()) {
                builder.add(KeyFileBatch.ofUris(regionCode, batch.getKey(), batch.getValue()));
              }
              Log.d(TAG, String.format("Batches: %s", builder.build()));
              return builder.build();
            },
            AppExecutors.getBackgroundExecutor());
  }

  private ListenableFuture<String> index(String countryCode) {
    return CallbackToFutureAdapter.getFuture(
        completer -> {
          Listener<String> responseListener = completer::set;

          ErrorListener errorListener =
              err -> {
                Log.e(TAG, "Error getting keyfile index.");
                completer.setCancelled();
              };

          String path = String.format(INDEX_FILE_FORMAT, countryCode);
          Uri indexUri = baseDownloadUri.buildUpon().appendPath(path).build();
          StringRequest request =
              new StringRequest(indexUri.toString(), responseListener, errorListener);
          RequestQueueSingleton.get(context).add(request);
          return request;
        });
  }

  public boolean hasDefaultUris() {
    return DEFAULT_URI_PATTERN.matcher(baseDownloadUri.toString()).matches()
        || DEFAULT_URI_PATTERN.matcher(uploadUri.toString()).matches();
  }
}
