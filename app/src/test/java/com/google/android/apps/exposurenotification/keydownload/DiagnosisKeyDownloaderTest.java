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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.android.volley.VolleyError;
import com.google.android.apps.exposurenotification.common.ExecutorsModule;
import com.google.android.apps.exposurenotification.common.Qualifiers.BackgroundExecutor;
import com.google.android.apps.exposurenotification.common.Qualifiers.LightweightExecutor;
import com.google.android.apps.exposurenotification.common.Qualifiers.ScheduledExecutor;
import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.android.apps.exposurenotification.common.time.RealTimeModule;
import com.google.android.apps.exposurenotification.keydownload.Qualifiers.HomeDownloadUriPair;
import com.google.android.apps.exposurenotification.keydownload.Qualifiers.TravellerDownloadUriPairs;
import com.google.android.apps.exposurenotification.network.RealRequestQueueModule;
import com.google.android.apps.exposurenotification.network.RequestQueueWrapper;
import com.google.android.apps.exposurenotification.storage.CountryRepository;
import com.google.android.apps.exposurenotification.storage.DbModule;
import com.google.android.apps.exposurenotification.storage.DownloadServerEntity;
import com.google.android.apps.exposurenotification.storage.DownloadServerRepository;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationDatabase;
import com.google.android.apps.exposurenotification.testsupport.ExposureNotificationRules;
import com.google.android.apps.exposurenotification.testsupport.FakeClock;
import com.google.android.apps.exposurenotification.testsupport.FakeRequestQueue;
import com.google.android.apps.exposurenotification.testsupport.InMemoryDb;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.testing.TestingExecutors;
import dagger.hilt.android.testing.BindValue;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import dagger.hilt.android.testing.UninstallModules;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Inject;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

@HiltAndroidTest
@RunWith(AndroidJUnit4.class)
@Config(application = HiltTestApplication.class)
@UninstallModules({
    ExecutorsModule.class,
    RealRequestQueueModule.class,
    RealTimeModule.class,
    DownloadUrisModule.class,
    DbModule.class})
public class DiagnosisKeyDownloaderTest {

  private static Joiner NEWLINE_JOINER = Joiner.on("\n");
  private static final AtomicInteger UNIQUE_INT = new AtomicInteger(1);

  // Having uninstalled some modules above (@UninstallModules), we need to provide everything they
  // would have, even if the code under test here doesn't use them.
  @BindValue
  @BackgroundExecutor
  static final ExecutorService BACKGROUND_EXEC = MoreExecutors.newDirectExecutorService();
  @BindValue
  @LightweightExecutor
  static final ExecutorService LIGHTWEIGHT_EXEC = MoreExecutors.newDirectExecutorService();
  @BindValue
  @BackgroundExecutor
  static final ListeningExecutorService BACKGROUND_LISTENING_EXEC =
      MoreExecutors.newDirectExecutorService();
  @BindValue
  @LightweightExecutor
  static final ListeningExecutorService LIGHTWEIGHT_LISTENING_EXEC =
      MoreExecutors.newDirectExecutorService();
  @BindValue
  @ScheduledExecutor
  static final ScheduledExecutorService SCHEDULED_EXEC =
      TestingExecutors.sameThreadScheduledExecutor();

  @BindValue
  @HomeDownloadUriPair
  static final DownloadUriPair HOME_URIS = newDownloadUriPair("US");
  @BindValue
  @TravellerDownloadUriPairs
  static final Map<String, List<DownloadUriPair>> TRAVEL_URIS =
      ImmutableMap.of(
          "MX", ImmutableList.of(newDownloadUriPair("MX")),
          "CA", ImmutableList.of(newDownloadUriPair("CA")));

  @BindValue
  ExposureNotificationDatabase db = InMemoryDb.create();
  @BindValue
  RequestQueueWrapper queue = new FakeRequestQueue();
  @BindValue
  Clock clock = new FakeClock();

  @Rule
  public ExposureNotificationRules rules = ExposureNotificationRules.forTest(this).build();

  // We need to manipulate the countries the user has visited to provoke roaming downloads.
  @Inject
  CountryRepository countryRepository;
  // Also need to access the last successful download.
  @Inject
  DownloadServerRepository downloadServerRepo;

  @Inject
  DiagnosisKeyDownloader downloader;

  @Before
  public void setUp() {
    rules.hilt().inject();
  }

  @Test
  public void emptyIndices_zeroFilesDownloaded() throws Exception {
    // GIVEN
    // Empty response bodies for any request.
    fakeQueue().addResponse(/* uriRegex= */ ".*", /* httpStatus= */ 200, /* responseBody= */ "");

    // WHEN
    List<KeyFile> files = downloader.download().get();

    // THEN
    assertThat(files).isEmpty();
  }

  @Test
  public void oneFileInHomeIndex_shouldDownloadFileToLocalFilesystem() throws Exception {
    // GIVEN
    // US is the home country.
    List<String> filenames = setupKeyFiles(HOME_URIS, "key-file-content");
    setupIndexFile(HOME_URIS, filenames);

    // WHEN
    List<KeyFile> files = downloader.download().get();

    // THEN
    assertThat(files).hasSize(1);
    assertThat(files.get(0).file().exists()).isTrue();
  }

  @Test
  public void multipleFilesInHomeIndex_shouldDownloadAllFiles() throws Exception {
    // GIVEN
    // Set up three keyfiles in the home server
    List<String> filenames = setupKeyFiles(
        HOME_URIS, "key-file-content-1", "key-file-content-2", "key-file-content-3");
    setupIndexFile(HOME_URIS, filenames);

    // WHEN
    List<KeyFile> files = downloader.download().get();

    // THEN
    assertThat(contentsOfAllFilesIn(files))
        .containsExactly("key-file-content-1", "key-file-content-2", "key-file-content-3");
  }

  @Test
  public void multipleFilesInHomeIndex_haveAlreadyDownloadedSome_shouldDownloadTheRestOfTheFiles()
      throws Exception {
    // GIVEN
    // Set up five keyfiles in the home server
    List<String> filenames = setupKeyFiles(HOME_URIS, "key-file-content-1", "key-file-content-2",
        "key-file-content-3", "key-file-content-4", "key-file-content-5");
    setupIndexFile(HOME_URIS, filenames);
    // Pretend we already downloaded up to file #3.
    downloadServerRepo.upsert(DownloadServerEntity.create(
        HOME_URIS.indexUri(),
        HOME_URIS.fileBaseUri().buildUpon().appendEncodedPath(filenames.get(2)).build()));

    // WHEN
    List<KeyFile> files = downloader.download().get();

    // THEN
    assertThat(contentsOfAllFilesIn(files))
        .containsExactly("key-file-content-4", "key-file-content-5");
  }

  @Test
  public void multipleFilesInHomeIndex_haveAlreadyDownloadedThemAll_shouldDownloadNothing()
      throws Exception {
    // GIVEN
    // Set up five keyfiles in the home server
    List<String> filenames = setupKeyFiles(HOME_URIS, "key-file-content-1", "key-file-content-2",
        "key-file-content-3", "key-file-content-4", "key-file-content-5");
    setupIndexFile(HOME_URIS, filenames);
    // Pretend we already downloaded up to file #5.
    downloadServerRepo.upsert(DownloadServerEntity.create(
        HOME_URIS.indexUri(),
        HOME_URIS.fileBaseUri().buildUpon().appendEncodedPath(filenames.get(4)).build()));

    // WHEN
    List<KeyFile> files = downloader.download().get();

    // THEN
    assertThat(files).isEmpty();
  }

  @Test
  public void filesAvailableInRoamingServers_userHasNotTravelled_shouldNotDownloadRoamingFiles()
      throws Exception {
    // GIVEN
    // Set up the home region
    List<String> homeFilenames = setupKeyFiles(HOME_URIS, "home-key-file-content");
    setupIndexFile(HOME_URIS, homeFilenames);
    // Also set up one of the roaming servers
    DownloadUriPair mexicoUris = TRAVEL_URIS.get("MX").get(0);
    List<String> mexicoFilenames = setupKeyFiles(mexicoUris, "mexico-key-file-content");
    setupIndexFile(mexicoUris, mexicoFilenames);
    // But no files in the second roaming server
    DownloadUriPair canadaUris = TRAVEL_URIS.get("CA").get(0);
    setupIndexFile(canadaUris, ImmutableList.of());
    // Finally, Set the user to have not left their home region (doesn't matter what the region is,
    // if there's only one, we figure they haven't travelled).
    countryRepository.markCountrySeen("US");

    // WHEN
    List<KeyFile> files = downloader.download().get();

    // THEN
    assertThat(contentsOfAllFilesIn(files)).containsExactly("home-key-file-content");
  }

  @Test
  public void roamingFilesAvailable_userHasTravelled_shouldDownloadRoamingFiles()
      throws Exception {
    // GIVEN
    // Set up the home region
    List<String> homeFilenames = setupKeyFiles(HOME_URIS, "home-key-file-content");
    setupIndexFile(HOME_URIS, homeFilenames);
    // Also set up one of the roaming servers
    DownloadUriPair mexicoUris = TRAVEL_URIS.get("MX").get(0);
    List<String> mexicoFilenames = setupKeyFiles(mexicoUris, "mexico-key-file-content");
    setupIndexFile(mexicoUris, mexicoFilenames);
    // Finally, Set the user to have left their home region, so we have two regions for them.
    countryRepository.markCountrySeen("US");  // US is the user's home region.
    countryRepository.markCountrySeen("MX");  // MX is the travelled location.

    // WHEN
    List<KeyFile> files = downloader.download().get();

    // THEN
    assertThat(contentsOfAllFilesIn(files))
        .containsExactly("home-key-file-content", "mexico-key-file-content");
  }

  @Test
  public void roamingFilesAvailable_userHasTravelled_shouldNotDownloadFromCountriesNotVisited()
      throws Exception {
    // GIVEN
    // Set up the home region
    List<String> homeFilenames = setupKeyFiles(HOME_URIS, "home-key-file-content");
    setupIndexFile(HOME_URIS, homeFilenames);
    // Also set up both of the roaming servers
    DownloadUriPair mexicoUris = TRAVEL_URIS.get("MX").get(0);
    List<String> mexicoFilenames = setupKeyFiles(mexicoUris, "mexico-key-file-content");
    setupIndexFile(mexicoUris, mexicoFilenames);
    DownloadUriPair canadaUris = TRAVEL_URIS.get("CA").get(0);
    List<String> canadaFilenames = setupKeyFiles(canadaUris, "canada-key-file-content");
    setupIndexFile(canadaUris, canadaFilenames);

    // Finally, Set the user to have left their home region, and visited one roaming region.
    countryRepository.markCountrySeen("US");  // US is the user's home region.
    countryRepository.markCountrySeen("MX");  // MX is the travelled location.

    // WHEN
    List<KeyFile> files = downloader.download().get();

    // THEN
    assertThat(contentsOfAllFilesIn(files))
        .containsExactly("home-key-file-content", "mexico-key-file-content");
  }

  @Test
  public void roamingFilesAvailable_userHasTravelledToMultipleRegions_shouldDownloadFromAll()
      throws Exception {
    // GIVEN
    // Set up the home region
    List<String> homeFilenames = setupKeyFiles(HOME_URIS, "home-key-file-content");
    setupIndexFile(HOME_URIS, homeFilenames);
    // Also set up both of the roaming servers
    DownloadUriPair mexicoUris = TRAVEL_URIS.get("MX").get(0);
    List<String> mexicoFilenames = setupKeyFiles(mexicoUris, "mexico-key-file-content");
    setupIndexFile(mexicoUris, mexicoFilenames);
    DownloadUriPair canadaUris = TRAVEL_URIS.get("CA").get(0);
    List<String> canadaFilenames = setupKeyFiles(canadaUris, "canada-key-file-content");
    setupIndexFile(canadaUris, canadaFilenames);

    // Finally, Set the user to have left their home region, and visited both roaming regions.
    countryRepository.markCountrySeen("US");  // US is the user's home region.
    countryRepository.markCountrySeen("MX");  // MX is one travelled location.
    countryRepository.markCountrySeen("CA");  // CA is another travelled location.

    // WHEN
    List<KeyFile> files = downloader.download().get();

    // THEN
    assertThat(contentsOfAllFilesIn(files))
        .containsExactly(
            "home-key-file-content", "mexico-key-file-content", "canada-key-file-content");
  }

  @Test
  public void indexFileNotFound_failsWithVolleyError() throws Exception {
    // GIVEN
    // Set up the home index file to 404
    fakeQueue().addResponse(HOME_URIS.indexUri().toString(), 404, "");

    // WHEN
    ThrowingRunnable operation = () -> downloader.download().get();

    // THEN
    ExecutionException thrown = assertThrows(ExecutionException.class, operation);
    assertThat(thrown.getCause()).isInstanceOf(VolleyError.class);
  }

  @Test
  public void keyFileNotFound_failsWithVolleyError() throws Exception {
    // GIVEN
    // Set up the index file as normal.
    String file = uniqueFileName();
    fakeQueue().addResponse(HOME_URIS.indexUri().toString(), 200, indexFileFor(file));
    // But the keyfile 404's
    Uri fileUri = HOME_URIS.fileBaseUri().buildUpon().appendEncodedPath(file).build();
    fakeQueue().addResponse(fileUri.toString(), 404, "");

    // WHEN
    ThrowingRunnable operation = () -> downloader.download().get();

    // THEN
    ExecutionException thrown = assertThrows(ExecutionException.class, operation);
    assertThat(thrown.getCause()).isInstanceOf(VolleyError.class);
    assertThat(((VolleyError) thrown.getCause()).networkResponse.statusCode).isEqualTo(404);
  }

  @Test
  public void server500Error_failsWithVolleyError() throws Exception {
    // GIVEN
    fakeQueue().addResponse(".*", 500, "Network error");

    // WHEN
    ThrowingRunnable operation = () -> downloader.download().get();

    // THEN
    ExecutionException thrown = assertThrows(ExecutionException.class, operation);
    assertThat(thrown.getCause()).isInstanceOf(VolleyError.class);
  }

  /**
   * Sets up some key files with RPC responses in the fake RequestQueue.
   *
   * @return the filenames, so the caller can put them in an index file.
   */
  private List<String> setupKeyFiles(DownloadUriPair uris, String... filesContents) {
    // Create the key files and set up their RPC responses.
    List<String> keyFilenames = new ArrayList<>();
    for (String fileContent : filesContents) {
      String fileName = uniqueFileName();
      keyFilenames.add(fileName);
      Uri homeFileUri = uris.fileBaseUri().buildUpon().appendEncodedPath(fileName).build();
      fakeQueue().addResponse(homeFileUri.toString(), 200, fileContent);
    }
    return keyFilenames;
  }

  private void setupIndexFile(DownloadUriPair uris, List<String> filenames) {
    fakeQueue().addResponse(
        uris.indexUri().toString(), 200, indexFileFor(filenames.toArray(new String[]{})));
  }

  /**
   * Just some syntactical sugar to encapsulate the ugly cast.
   */
  private FakeRequestQueue fakeQueue() {
    return (FakeRequestQueue) queue;
  }

  private static DownloadUriPair newDownloadUriPair(String country) {
    int nextInt = UNIQUE_INT.incrementAndGet();
    return DownloadUriPair.create(
        "http://example.com/" + country + "/" + nextInt + "/index.txt",
        "http://example.com/" + country + "/" + nextInt + "/files/");
  }

  private String uniqueFileName() {
    return "somepath/somefile-" + UNIQUE_INT.incrementAndGet() + ".zip";
  }

  private String indexFileFor(String... files) {
    return NEWLINE_JOINER.join(files);
  }

  /**
   * Because ultimately we only care that we retrieved all expected files (and no others), here we
   * collect just the contents of all the files.
   */
  private static List<String> contentsOfAllFilesIn(List<KeyFile> files) throws Exception {
    List<String> fileContentList = new ArrayList<>();
    for (KeyFile f : files) {
      fileContentList.add(FileUtils.readFileToString(f.file(), StandardCharsets.UTF_8));
    }
    return fileContentList;
  }
}
