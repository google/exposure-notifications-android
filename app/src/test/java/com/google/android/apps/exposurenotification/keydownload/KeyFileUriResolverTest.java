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

import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.android.apps.exposurenotification.common.time.RealTimeModule;
import com.google.android.apps.exposurenotification.network.RealRequestQueueModule;
import com.google.android.apps.exposurenotification.network.RequestQueueWrapper;
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
import dagger.hilt.android.testing.BindValue;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import dagger.hilt.android.testing.UninstallModules;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

@RunWith(AndroidJUnit4.class)
@HiltAndroidTest
@Config(application = HiltTestApplication.class)
@UninstallModules({
    RealTimeModule.class,
    RealRequestQueueModule.class,
    DbModule.class})
public final class KeyFileUriResolverTest {

  private static Joiner NEWLINE_JOINER = Joiner.on("\n");
  private static final AtomicInteger UNIQUE_INT = new AtomicInteger(1);

  @Rule
  public ExposureNotificationRules rules = ExposureNotificationRules.forTest(this).build();

  @BindValue
  RequestQueueWrapper queue = new FakeRequestQueue();
  @BindValue
  Clock clock = new FakeClock();
  @BindValue
  ExposureNotificationDatabase db = InMemoryDb.create();

  @Inject
  DownloadServerRepository downloadServerRepo;

  // The SUT
  @Inject
  KeyFileUriResolver resolver;

  @Before
  public void setUp() {
    rules.hilt().inject();
  }

  @Test
  public void indexIsEmpty_shouldReturnZeroKeyfiles() throws Exception {
    // GIVEN
    String emptyIndex = "";
    DownloadUriPair uriPair = newDownloadUriPair();
    queue().addResponse(uriPair.indexUri().toString(), 200, emptyIndex);

    // WHEN
    List<KeyFile> keyFiles = resolver.resolve(ImmutableList.of(uriPair)).get();

    // THEN
    assertThat(keyFiles).isEmpty();
  }

  @Test
  public void indexHasOneFile_shouldReturnOneKeyfile() throws Exception {
    // GIVEN
    String keyfile = uniqueFileName();
    DownloadUriPair uriPair = newDownloadUriPair();
    queue().addResponse(uriPair.indexUri().toString(), 200, indexFileFor(keyfile));

    // WHEN
    List<KeyFile> keyFiles = resolver.resolve(ImmutableList.of(uriPair)).get();

    // THEN
    Uri expectedUri = uriPair.fileBaseUri().buildUpon().appendEncodedPath(keyfile).build();
    KeyFile expectedKeyFile = KeyFile.create(uriPair.indexUri(), expectedUri, true);
    assertThat(keyFiles).containsExactly(expectedKeyFile);
  }

  @Test
  public void indexHasManyFiles_lastOneInTheIndexShouldBeDesignatedMostRecent()
      throws Exception {
    // GIVEN
    String keyfile1 = uniqueFileName();
    String keyfile2 = uniqueFileName();
    String keyfile3 = uniqueFileName();
    DownloadUriPair uriPair = newDownloadUriPair();
    queue().addResponse(uriPair.indexUri().toString(), 200, indexFileFor(keyfile1, keyfile2, keyfile3));

    // WHEN
    List<KeyFile> keyFiles = resolver.resolve(ImmutableList.of(uriPair)).get();

    // THEN
    // This test overspecifies a little bit: we don't actually care about the order of the
    // List<KeyFile>, but knowing the order does make this test a bit clearer and easlier to read.
    assertThat(keyFiles.get(0).isMostRecent()).isFalse();
    assertThat(keyFiles.get(1).isMostRecent()).isFalse();
    // Make sure the third file is the one we think it is, and it is "most recent".
    Uri expectedUri = uriPair.fileBaseUri().buildUpon().appendEncodedPath(keyfile3).build();
    assertThat(keyFiles.get(2).uri()).isEqualTo(expectedUri);
    assertThat(keyFiles.get(2).isMostRecent()).isTrue();
  }

  @Test
  public void indexHasManyFiles_noLastDownloadSaved_shouldReturnAllFiles() throws Exception {
    // GIVEN
    String keyfile1 = uniqueFileName();
    String keyfile2 = uniqueFileName();
    String keyfile3 = uniqueFileName();
    DownloadUriPair uriPair = newDownloadUriPair();
    queue().addResponse(
        uriPair.indexUri().toString(), 200, indexFileFor(keyfile1, keyfile2, keyfile3));

    // WHEN
    List<KeyFile> keyFiles = resolver.resolve(ImmutableList.of(uriPair)).get();

    // THEN
    assertThat(keyFiles).hasSize(3);
  }

  @Test
  public void indexHasManyFiles_lastDownloadSavedIsUnrecognised_shouldReturnAllFiles()
      throws Exception {
    // GIVEN
    String keyfile1 = uniqueFileName();
    String keyfile2 = uniqueFileName();
    String keyfile3 = uniqueFileName();
    DownloadUriPair uriPair = newDownloadUriPair();
    queue().addResponse(
        uriPair.indexUri().toString(), 200, indexFileFor(keyfile1, keyfile2, keyfile3));
    // Pretend we have a "last download" that is either way too old, or otherwise doesn't match any
    // file in the index.
    downloadServerRepo.upsert(DownloadServerEntity.create(
        uriPair.indexUri(),
        Uri.parse("http://example.com/file-that-is-not-in-the-index")));

    // WHEN
    List<KeyFile> keyFiles = resolver.resolve(ImmutableList.of(uriPair)).get();

    // THEN
    assertThat(keyFiles).hasSize(3);
  }

  @Test
  public void indexHasManyFiles_lastDownloadSaved_shouldReturnOnlyFilesAfterLastDownload()
      throws Exception {
    // GIVEN
    String keyfile1 = uniqueFileName();
    String keyfile2 = uniqueFileName();
    String keyfile3 = uniqueFileName();
    String keyfile4 = uniqueFileName();
    String keyfile5 = uniqueFileName();
    DownloadUriPair uriPair = newDownloadUriPair();
    queue().addResponse(
        uriPair.indexUri().toString(),
        200,
        indexFileFor(keyfile1, keyfile2, keyfile3, keyfile4, keyfile5));
    // Pretend we already downloaded up to file3.
    downloadServerRepo.upsert(DownloadServerEntity.create(
        uriPair.indexUri(),
        uriPair.fileBaseUri().buildUpon().appendEncodedPath(keyfile3).build()));

    // WHEN
    List<KeyFile> keyFiles = resolver.resolve(ImmutableList.of(uriPair)).get();

    // THEN
    Uri expectedUri4 = uriPair.fileBaseUri().buildUpon().appendEncodedPath(keyfile4).build();
    KeyFile expectedKeyFile4 =
        KeyFile.create(uriPair.indexUri(), expectedUri4, /* isMostRecent= */ false);
    Uri expectedUri5 = uriPair.fileBaseUri().buildUpon().appendEncodedPath(keyfile5).build();
    KeyFile expectedKeyFile5 =
        KeyFile.create(uriPair.indexUri(), expectedUri5, /* isMostRecent= */ true);
    assertThat(keyFiles).containsExactly(expectedKeyFile4, expectedKeyFile5);
  }

  @Test
  public void indexHasManyFiles_lastDownloadSaved_isStillMostRecentAvailable_shouldReturnEmpty()
      throws Exception {
    // GIVEN
    String keyfile1 = uniqueFileName();
    String keyfile2 = uniqueFileName();
    String keyfile3 = uniqueFileName();
    DownloadUriPair uriPair = newDownloadUriPair();
    queue().addResponse(
        uriPair.indexUri().toString(), 200, indexFileFor(keyfile1, keyfile2, keyfile3));
    // Pretend we already downloaded up to file3.
    downloadServerRepo.upsert(DownloadServerEntity.create(
        uriPair.indexUri(),
        uriPair.fileBaseUri().buildUpon().appendEncodedPath(keyfile3).build()));

    // WHEN
    List<KeyFile> keyFiles = resolver.resolve(ImmutableList.of(uriPair)).get();

    // THEN
    assertThat(keyFiles).isEmpty();
  }

  @Test
  public void shouldResolveMultipleUriPairs_allFilesShouldBeMostRecent() throws Exception {
    DownloadUriPair uriPair1 = newDownloadUriPair();
    DownloadUriPair uriPair2 = newDownloadUriPair();
    DownloadUriPair uriPair3 = newDownloadUriPair();
    String file1 =  uniqueFileName();
    String file2 =  uniqueFileName();
    String file3 =  uniqueFileName();
    queue().addResponse(uriPair1.indexUri().toString(), 200, indexFileFor(file1));
    queue().addResponse(uriPair2.indexUri().toString(), 200, indexFileFor(file2));
    queue().addResponse(uriPair3.indexUri().toString(), 200, indexFileFor(file3));

    // WHEN
    List<KeyFile> keyFiles =
        resolver.resolve(ImmutableList.of(uriPair1, uriPair2, uriPair3)).get();

    // THEN
    Uri expectedUri1 = uriPair1.fileBaseUri().buildUpon().appendEncodedPath(file1).build();
    Uri expectedUri2 = uriPair2.fileBaseUri().buildUpon().appendEncodedPath(file2).build();
    Uri expectedUri3 = uriPair3.fileBaseUri().buildUpon().appendEncodedPath(file3).build();
    KeyFile expectedKeyFile1 = KeyFile.create(uriPair1.indexUri(), expectedUri1, true);
    KeyFile expectedKeyFile2 = KeyFile.create(uriPair2.indexUri(), expectedUri2, true);
    KeyFile expectedKeyFile3 = KeyFile.create(uriPair3.indexUri(), expectedUri3, true);
    assertThat(keyFiles).containsExactly(expectedKeyFile1, expectedKeyFile2, expectedKeyFile3);
  }

  @Test
  public void shouldTolerateExtraWhitespaceInIndexFile() throws Exception {
    // GIVEN
    String file1 = uniqueFileName();
    String file2 = uniqueFileName();
    String indexContent = "\n\n   \n \t " + file1 + "\n\n" + file2 + "  \n\n\n  ";
    DownloadUriPair uriPair = newDownloadUriPair();
    queue().addResponse(uriPair.indexUri().toString(), 200, indexContent);

    // WHEN
    List<KeyFile> keyFiles = resolver.resolve(ImmutableList.of(uriPair)).get();

    // THEN
    assertThat(keyFiles).hasSize(2);
  }

  private static DownloadUriPair newDownloadUriPair() {
    int nextInt = UNIQUE_INT.getAndIncrement();
    return DownloadUriPair.create(
        "http://example.com/" + nextInt + "/index.txt",
        "http://example.com/" + nextInt + "/files/");
  }

  private String uniqueFileName() {
    return "somepath/somefile-" + UNIQUE_INT.incrementAndGet() + ".zip";
  }

  private String indexFileFor(String... files) {
    return NEWLINE_JOINER.join(files);
  }

  private FakeRequestQueue queue() {
    return (FakeRequestQueue) queue;
  }
}
