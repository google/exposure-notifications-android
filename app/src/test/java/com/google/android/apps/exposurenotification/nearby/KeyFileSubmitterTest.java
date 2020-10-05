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

package com.google.android.apps.exposurenotification.nearby;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.apps.exposurenotification.common.ExecutorsModule;
import com.google.android.apps.exposurenotification.common.Qualifiers.BackgroundExecutor;
import com.google.android.apps.exposurenotification.common.Qualifiers.LightweightExecutor;
import com.google.android.apps.exposurenotification.common.Qualifiers.ScheduledExecutor;
import com.google.android.apps.exposurenotification.keydownload.KeyFile;
import com.google.android.apps.exposurenotification.keydownload.KeyFileConstants;
import com.google.android.apps.exposurenotification.proto.TemporaryExposureKeyExport;
import com.google.android.apps.exposurenotification.storage.DbModule;
import com.google.android.apps.exposurenotification.storage.DownloadServerEntity;
import com.google.android.apps.exposurenotification.storage.DownloadServerRepository;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationDatabase;
import com.google.android.apps.exposurenotification.testsupport.ExposureNotificationRules;
import com.google.android.apps.exposurenotification.testsupport.InMemoryDb;
import com.google.android.gms.tasks.Tasks;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Bytes;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.testing.TestingExecutors;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.android.testing.BindValue;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import dagger.hilt.android.testing.UninstallModules;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.annotation.Config;

@HiltAndroidTest
@RunWith(AndroidJUnit4.class)
@Config(application = HiltTestApplication.class)
@UninstallModules({
    DbModule.class,
    ExecutorsModule.class,
    ExposureNotificationsClientModule.class
})
public class KeyFileSubmitterTest {

  private static final AtomicInteger UNIQUE_INT = new AtomicInteger(1);
  private static final String HEADER_V1 = "EK Export v1";
  private static final int HEADER_LEN = 16;

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

  @Rule
  public ExposureNotificationRules rules =
      ExposureNotificationRules.forTest(this).withMocks().build();

  @BindValue
  ExposureNotificationDatabase db = InMemoryDb.create();
  @BindValue
  @Mock
  ExposureNotificationClientWrapper exposureNotificationClient;

  @Inject
  DownloadServerRepository downloadServerRepo;
  @Inject
  @ApplicationContext
  Context context;

  // The SUT
  @Inject
  DiagnosisKeyFileSubmitter submitter;

  @Before
  public void setUp() {
    rules.hilt().inject();
    when(exposureNotificationClient.provideDiagnosisKeys(any())).thenReturn(Tasks.forResult(null));
  }

  @Test
  public void shouldRememberLastSuccessfulFiles_oneForEachServer() throws Exception {
    // GIVEN
    Uri index1 = Uri.parse("http://example-1.com/index");
    Uri index2 = Uri.parse("http://example-2.com/index");
    Uri server1FileUri1 = Uri.parse("http://example-1.com/file1");
    Uri server1FileUri2 = Uri.parse("http://example-1.com/file2");
    Uri server2FileUri1 = Uri.parse("http://example-2.com/file1");
    Uri server2FileUri2 = Uri.parse("http://example-2.com/file2");
    File server1file1 = createFile();
    File server1file2 = createFile();
    File server2file1 = createFile();
    File server2file2 = createFile();
    KeyFile server1keyFile1 = KeyFile.create(index1, server1FileUri1, false).with(server1file1);
    KeyFile server1keyFile2 = KeyFile.create(index1, server1FileUri2, true).with(server1file2);
    KeyFile server2keyFile1 = KeyFile.create(index2, server2FileUri1, false).with(server2file1);
    KeyFile server2keyFile2 = KeyFile.create(index2, server2FileUri2, true).with(server2file2);

    // WHEN
    submitter.submitFiles(
        ImmutableList.of(server1keyFile1, server1keyFile2, server2keyFile1, server2keyFile2))
        .get();

    // THEN
    assertThat(downloadServerRepo.getMostRecentSuccessfulDownload(index1))
        .isEqualTo(server1FileUri2);
    assertThat(downloadServerRepo.getMostRecentSuccessfulDownload(index2))
        .isEqualTo(server2FileUri2);
  }

  @Test
  public void submitFails_shouldRememberPreviouslySuccessfulFiles() throws Exception {
    // GIVEN
    Uri index = Uri.parse("http://example-1.com/index");
    Uri fileUri1 = Uri.parse("http://example-1.com/file1");
    Uri fileUri2 = Uri.parse("http://example-1.com/file2");
    File file1 = createFile();
    File file2 = createFile();
    KeyFile keyFile1 = KeyFile.create(index, fileUri1, true).with(file1);
    KeyFile keyFile2 = KeyFile.create(index, fileUri2, true).with(file2);
    downloadServerRepo.upsert(DownloadServerEntity.create(index, fileUri1));

    // WHEN
    // First submit should succeed and remember the file.
    submitter.submitFiles(ImmutableList.of(keyFile1)).get();
    assertThat(downloadServerRepo.getMostRecentSuccessfulDownload(index)).isEqualTo(fileUri1);
    // Set up the second submit to fail
    when(exposureNotificationClient.provideDiagnosisKeys(any()))
        .thenReturn(Tasks.forException(new RuntimeException("BOOOOOM!")));
    assertThrows(
        ExecutionException.class,
        () -> submitter.submitFiles(ImmutableList.of(keyFile2)).get());

    // THEN
    // So we should still remember the first file.
    assertThat(downloadServerRepo.getMostRecentSuccessfulDownload(index)).isEqualTo(fileUri1);
  }

  /**
   * Creates a structurally compliant but empty keyfile and writes it to disk.
   */
  private File createFile() throws Exception {
    File outFile =
        new File(
            context.getFilesDir(),
            String.format("test-keyfile-%s.zip", UNIQUE_INT.incrementAndGet()));
    try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(outFile))) {
      ZipEntry signatureEntry = new ZipEntry(KeyFileConstants.SIG_FILENAME);
      ZipEntry exportEntry = new ZipEntry(KeyFileConstants.EXPORT_FILENAME);

      byte[] exportBytes = Bytes.concat(
          Strings.padEnd(HEADER_V1, HEADER_LEN, ' ').getBytes(),
          TemporaryExposureKeyExport.getDefaultInstance().toByteArray());

      out.putNextEntry(signatureEntry);
      out.write("signature".getBytes());

      out.putNextEntry(exportEntry);
      out.write(exportBytes);

      return outFile;
    }
  }
}
