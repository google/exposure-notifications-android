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
import androidx.concurrent.futures.CallbackToFutureAdapter;
import com.android.volley.Response.ErrorListener;
import com.android.volley.Response.Listener;
import com.google.android.apps.exposurenotification.common.Qualifiers.BackgroundExecutor;
import com.google.android.apps.exposurenotification.common.Qualifiers.LightweightExecutor;
import com.google.android.apps.exposurenotification.common.logging.Logger;
import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.android.apps.exposurenotification.network.RequestQueueWrapper;
import com.google.android.apps.exposurenotification.network.RespondableStringRequest;
import com.google.android.apps.exposurenotification.storage.DownloadServerRepository;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import javax.inject.Inject;

/**
 * An agent responsible for consulting keyfile server locations it is given, understanding the index
 * files that the services provide, and resolving their content to batches of keyfile URLs that a
 * {@link DiagnosisKeyDownloader} can retrieve.
 */
class KeyFileUriResolver {

  private static final Logger logger = Logger.getLogger("KeyFileUriResolver");
  private static final Splitter WHITESPACE_SPLITTER =
      Splitter.onPattern("\\s+").trimResults().omitEmptyStrings();

  private final DownloadServerRepository downloadServerRepo;
  private final RequestQueueWrapper queue;
  private final Clock clock;
  private final ExecutorService backgroundExecutor;
  private final ExecutorService lightweightExecutor;

  @Inject
  KeyFileUriResolver(
      DownloadServerRepository downloadServerRepo,
      RequestQueueWrapper queue,
      Clock clock,
      @BackgroundExecutor ExecutorService backgroundExecutor,
      @LightweightExecutor ExecutorService lightweightExecutor) {
    this.downloadServerRepo = downloadServerRepo;
    this.queue = queue;
    this.clock = clock;
    this.backgroundExecutor = backgroundExecutor;
    this.lightweightExecutor = lightweightExecutor;
  }

  /**
   * Gets URIs from which to download key files for the given {@link DownloadUriPair}s.
   */
  ListenableFuture<ImmutableList<KeyFile>> resolve(List<DownloadUriPair> downloadUriPairs) {
    logger.d("Getting download URIs for " + downloadUriPairs.size() + " servers.");

    List<ListenableFuture<ImmutableList<KeyFile>>> keyfiles = new ArrayList<>();
    for (DownloadUriPair uriPair : downloadUriPairs) {
      keyfiles.add(keyFilesFor(uriPair));
    }

    return FluentFuture.from(Futures.allAsList(keyfiles))
        .transform(
            uriLists -> {
              List<KeyFile> flattenedList = new ArrayList<>();
              for (List<KeyFile> list : uriLists) {
                flattenedList.addAll(list);
              }
              return ImmutableList.copyOf(flattenedList);
            }, lightweightExecutor);
  }

  private ListenableFuture<ImmutableList<KeyFile>> keyFilesFor(DownloadUriPair uriPair) {
    return FluentFuture.from(indexFileFrom(uriPair))
        .transform(
            indexContent -> {
              logger.d("Index content is " + indexContent);
              List<String> indexEntries = WHITESPACE_SPLITTER.splitToList(indexContent);
              logger.d("Index file has " + indexEntries.size() + " lines.");

              // Parse out each line of the index file
              List<Uri> fileUris = new ArrayList<>();
              for (String indexEntry : indexEntries) {
                Uri fileUri = uriPair.fileBaseUri().buildUpon()
                    .appendEncodedPath(indexEntry)
                    .build();
                fileUris.add(fileUri);
              }

              // If we have a "most recently downloaded" for this DownloadUriPair, and it's found in
              // the list of files we got, skip past (and exclude) that one.
              Uri lastSuccessfulDownload =
                  downloadServerRepo.getMostRecentSuccessfulDownload(uriPair.indexUri());
              if (lastSuccessfulDownload != null
                  && fileUris.contains(lastSuccessfulDownload)) {
                fileUris =
                    fileUris.subList(fileUris.indexOf(lastSuccessfulDownload) + 1, fileUris.size());
              }

              // Now we have the (sub?)set of files we want. Build them into a list of KeyFiles.
              ImmutableList.Builder<KeyFile> builder = ImmutableList.builder();
              for (Uri fileUri : fileUris) {
                boolean isMostRecent = fileUri.equals(Iterables.getLast(fileUris));
                builder.add(KeyFile.create(uriPair.indexUri(), fileUri, isMostRecent));
              }
              logger.d(String.format(
                  "Uris for server [%s]: [%s]", uriPair.indexUri(), builder.build()));
              return builder.build();
            },
            backgroundExecutor);
  }

  private ListenableFuture<String> indexFileFrom(DownloadUriPair uriPair) {
    return CallbackToFutureAdapter.getFuture(
        completer -> {
          Listener<String> responseListener =
              resp -> {
                logger.d("Response was " + resp);
                completer.set(resp);
              };

          ErrorListener errorListener =
              err -> {
                logger.e("Error getting keyfile index.");
                completer.setException(err);
              };

          logger.d("Getting index file from " + uriPair.indexUri());
          RespondableStringRequest request =
              new RespondableStringRequest(
                  uriPair.indexUri().toString(), responseListener, errorListener, clock);
          request.setShouldCache(false);
          queue.add(request);
          return request;
        });
  }
}
