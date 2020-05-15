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
import com.google.android.apps.exposurenotification.common.AppExecutors;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Encapsulates logic for resolving URIs for uploading and downloading Diagnosis Keys. */
public class Uris {
  private static final String TAG = "Uris";
  private static final Splitter WHITESPACE_SPLITTER =
      Splitter.onPattern("\\s+").trimResults().omitEmptyStrings();
  // Path for index files; to be appended to the base URI with %s replaced by an ISO-Alpha-2 country
  // code
  private static final String INDEX_FILE_FORMAT = "exposureKeyExport-%s/index.txt";
  // For any of the server uploads and downloads to work the app must be built with non-default URIs
  // set by gradle.properties. This pattern helps us check.
  private static final Pattern DEFAULT_URI_PATTERN = Pattern.compile(".*example\\.com.*");

  private final Context context;
  private final Uri baseDownloadUri;
  private final Uri uploadUri;

  public Uris(Context context) {
    this.context = context;
    // These two string resources must be set by gradle.properties.
    baseDownloadUri = Uri.parse(context.getString(R.string.key_server_download_base_uri));
    uploadUri = Uri.parse(context.getString(R.string.key_server_upload_uri));
  }

  ListenableFuture<List<Uri>> getUploadUris(List<String> regionsIsoAlpha2) {
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

  ListenableFuture<List<Uri>> getDownloadFileUris(List<String> regionsIsoAlpha2) {
    // Check if the app has been built with default URIs first.
    if (hasDefaultUris()) {
      Log.w(TAG, "Attempting to use servers while compiled with default URIs!");
      return Futures.immediateFuture(ImmutableList.of());
    }

    Log.d(TAG, "Getting download URIs for " + regionsIsoAlpha2.size() + " regions");
    List<ListenableFuture<List<Uri>>> perRegionFiles = new ArrayList<>();
    for (String region : regionsIsoAlpha2) {
      perRegionFiles.add(regionFiles(region));
    }
    return FluentFuture.from(Futures.allAsList(perRegionFiles))
        .transform(
            uriLists -> {
              List<Uri> flattenedList = new ArrayList<>();
              for (List<Uri> list : uriLists) {
                flattenedList.addAll(list);
              }
              return flattenedList;
            },
            AppExecutors.getLightweightExecutor());
  }

  private ListenableFuture<List<Uri>> regionFiles(String regionCode) {
    return FluentFuture.from(index(regionCode))
        .transform(
            indexContent -> {
              List<String> lines = WHITESPACE_SPLITTER.splitToList(indexContent);
              Log.d(TAG, "Index file has " + lines.size() + " lines.");
              List<Uri> uris = new ArrayList<>();
              for (String l : lines) {
                Uri file = baseDownloadUri.buildUpon().appendPath(l).build();
                uris.add(file);
              }
              return uris;
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
