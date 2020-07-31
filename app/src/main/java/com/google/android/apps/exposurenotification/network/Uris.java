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
import com.google.common.base.Optional;
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
import org.threeten.bp.Duration;
import org.threeten.bp.Instant;

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
  private static final Pattern DOWNLOAD_FILENAME_PATTERN =
      Pattern.compile("exposureKeyExport-([A-Z]{2})/([0-9]+)-([0-9]+-)?[0-9]+.zip");
  private static final Duration DEFAULT_MAX_DOWNLOAD_AGE = Duration.ofHours(24);

  private final ExposureNotificationSharedPreferences prefs;
  private final Uri baseDownloadUri;
  private final Uri uploadUri;
  private final Uri verificationUriStep1;
  private final Uri verificationUriStep2;
  private final RequestQueueWrapper queue;

  public Uris(Context context) {
    this(context,
        RequestQueueWrapper.wrapping(RequestQueueSingleton.get(context)),
        new ExposureNotificationSharedPreferences(context));
  }

  public Uris(
      Context context, RequestQueueWrapper queue, ExposureNotificationSharedPreferences prefs) {
    this.prefs = prefs;
    this.queue = queue;

    // These three string resources must be set by gradle.properties or overriden by preferences.
    baseDownloadUri =
        Uri.parse(
            prefs.getDownloadServerAddress(
                context.getString(R.string.key_server_download_base_uri)));
    uploadUri =
        Uri.parse(prefs.getUploadServerAddress(context.getString(R.string.key_server_upload_uri)));
    verificationUriStep1 =
        Uri.parse(
            prefs.getVerificationServerAddress1(
                context.getString(R.string.verification_server_uri_step_1)));
    verificationUriStep2 =
        Uri.parse(
            prefs.getVerificationServerAddress1(
                context.getString(R.string.verification_server_uri_step_2)));
  }

  /**
   * Gets the URI of the diagnosis verification service's first step for the given country/region
   * code.
   *
   * @param region a code for the "home" country/region of the user, whose public health authority
   *               owns the verification server they would consult for diagnosis verification
   */
  Uri getVerificationUri1(String region) {
    if (hasDefaultUris()) {
      throw new IllegalStateException(
          "Attempting to use servers while compiled with default URIs!");
    }
    Log.d(TAG, "Verification server step 1 URI for region/country: [" + region + "] is ["
        + verificationUriStep1 + "]");
    return verificationUriStep1;
  }

  /**
   * Gets the URI of the diagnosis verification service's second step for the given country/region
   * code.
   *
   * @param region a code for the "home" country/region of the user, whose public health authority
   *               owns the verification server they would consult for diagnosis verification
   */
  Uri getVerificationUri2(String region) {
    if (hasDefaultUris()) {
      throw new IllegalStateException(
          "Attempting to use servers while compiled with default URIs!");
    }
    Log.d(TAG, "Verification server step 2 URI for region/country: [" + region + "] is ["
        + verificationUriStep2 + "]");
    return verificationUriStep2;
  }

  /**
   * Gets the URIs of upload services for the given counthy codes.
   *
   * <p>Not necessarily exactly one per country.
   */
  ListenableFuture<ImmutableList<Uri>> getUploadUris(List<String> regions) {
    // Check if the app has been built with default URIs first.
    if (hasDefaultUris()) {
      return Futures.immediateFailedFuture(
          new IllegalStateException("Attempting to use servers while compiled with default URIs!"));
    }
    Log.d(TAG, "Key server URIs for regions/countries: " + regions + " are ["
        + ImmutableList.of(uploadUri) + "]");
    // In this test instance we use one server for all regions.
    // An alternative implementation could have a "home" server and a "roaming" server, or some
    // other scheme.
    return Futures.immediateFuture(ImmutableList.of(uploadUri));
  }

  /**
   * Gets batches of URIs from which to download key files for the given country codes.
   */
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
              Log.d(TAG, "Index content is " + indexContent);
              List<String> indexEntries = WHITESPACE_SPLITTER.splitToList(indexContent);
              Log.d(TAG, "Index file has " + indexEntries.size() + " lines.");
              Map<Long, List<Uri>> batches = new HashMap<>();

              // Parse out each line of the index file and split them into batches as indicated by
              // the leading timestamp in the filename, e.g. "1589490000" for
              // "exposureKeyExport-US/1589490000-1589510000-00002.zip"
              for (String indexEntry : indexEntries) {
                Optional<Long> batchNumOrSkip = maybeGetBatchNum(indexEntry, regionCode);
                if (!batchNumOrSkip.isPresent()) {
                  continue;
                }
                long batchNum = batchNumOrSkip.get();

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

  /**
   * Parses the given filename to get its batch number, or empty() if we should skip this file.
   *
   * <p>Skips files that are too old, or from the wrong country/region.
   *
   * @throws RuntimeException      for failures to parse.
   * @throws NumberFormatException for numeric failures to parse.
   */
  private Optional<Long> maybeGetBatchNum(String filename, String regionCode) {
    Matcher m = DOWNLOAD_FILENAME_PATTERN.matcher(filename);
    if (!m.matches() || m.group(1) == null || m.group(2) == null) {
      throw new RuntimeException(
          "Failed to parse country/region code and batch num from File [" + filename + "].");
    }

    String regionCodeFromFilename = m.group(1);
    // Allow NumberFormatExceptions from parseLong() to bubble up.
    long batchNum = Long.parseLong(m.group(2));
    Log.d(
        TAG,
        String.format(
            "Country/region code %s and batch number %s from indexEntry %s",
            regionCodeFromFilename, batchNum, filename));
    if (!regionCode.equals(regionCodeFromFilename)) {
      Log.d(
          TAG,
          String.format(
              "Region code %s from filename %s unequal to desired region %s."
                  + " Skipping this file.",
              regionCodeFromFilename, filename, regionCode));
      return Optional.absent();
    }

    Instant fileCTime = Instant.ofEpochSecond(batchNum);
    Instant oldestCTimeDesired =
        Instant.now().minus(prefs.getMaxDownloadAge(DEFAULT_MAX_DOWNLOAD_AGE));
    if (fileCTime.isBefore(oldestCTimeDesired)) {
      Log.d(
          TAG,
          String.format(
              "Skipping file created at/near %s, it looks older than %s",
              fileCTime, oldestCTimeDesired));
      return Optional.absent();
    }

    return Optional.of(batchNum);
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
          Uri indexUri = baseDownloadUri.buildUpon().appendEncodedPath(path).build();
          Log.d(TAG, "Getting index file from " + indexUri);
          StringRequest request =
              new StringRequest(indexUri.toString(), responseListener, errorListener);
          request.setShouldCache(false);
          queue.add(request);
          return request;
        });
  }

  public boolean hasDefaultUris() {
    return DEFAULT_URI_PATTERN.matcher(baseDownloadUri.toString()).matches()
        || DEFAULT_URI_PATTERN.matcher(uploadUri.toString()).matches()
        || DEFAULT_URI_PATTERN.matcher(verificationUriStep1.toString()).matches()
        || DEFAULT_URI_PATTERN.matcher(verificationUriStep2.toString()).matches();
  }
}
