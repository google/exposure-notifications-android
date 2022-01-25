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

package com.google.android.apps.exposurenotification.storage;

import static com.google.common.truth.Truth.assertThat;

import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.apps.exposurenotification.testsupport.ExposureNotificationRules;
import com.google.android.apps.exposurenotification.testsupport.InMemoryDb;
import dagger.hilt.android.testing.BindValue;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import dagger.hilt.android.testing.UninstallModules;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

/**
 * Tests for operations in {@link DownloadServerRepository}, which serves to also test {@link
 * DownloadServerDao} which it wraps.
 */
@RunWith(AndroidJUnit4.class)
@HiltAndroidTest
@Config(application = HiltTestApplication.class)
@UninstallModules({DbModule.class})
public class DownloadServerRepositoryTest {

  @Rule
  public ExposureNotificationRules rules = ExposureNotificationRules.forTest(this).build();

  @BindValue
  ExposureNotificationDatabase db = InMemoryDb.create();

  @Inject
  DownloadServerRepository downloadServerRepo;

  @Before
  public void setUp() {
    rules.hilt().inject();
  }

  @Test
  public void getMostRecentDownload_emptyDb_returnsNull() {
    assertThat(downloadServerRepo.getMostRecentSuccessfulDownload(Uri.parse("anything"))).isNull();
  }

  @Test
  public void saveOneServer_shouldReturnMostRecentDownload() {
    Uri index = Uri.parse("example.com/index");
    Uri file = Uri.parse("example.com/file");
    DownloadServerEntity record = DownloadServerEntity.create(index, file);
    downloadServerRepo.upsert(record);

    assertThat(downloadServerRepo.getMostRecentSuccessfulDownload(index)).isEqualTo(file);
  }

  @Test
  public void saveOneServer_querySomeOtherServer_shouldReturnNull() {
    Uri index = Uri.parse("example.com/index");
    Uri file = Uri.parse("example.com/file");
    DownloadServerEntity record = DownloadServerEntity.create(index, file);
    downloadServerRepo.upsert(record);

    assertThat(downloadServerRepo.getMostRecentSuccessfulDownload(Uri.parse("other.com"))).isNull();
  }

  @Test
  public void saveMultipleServers_queryOneOfThem_shouldReturnCorrectFileUri() {
    Uri index1 = Uri.parse("example-1.com/index");
    Uri file1 = Uri.parse("example-1.com/file");
    Uri index2 = Uri.parse("example-2.com/index");
    Uri file2 = Uri.parse("example-2.com/file");
    Uri index3 = Uri.parse("example-3.com/index");
    Uri file3 = Uri.parse("example-3.com/file");
    DownloadServerEntity record1 = DownloadServerEntity.create(index1, file1);
    DownloadServerEntity record2 = DownloadServerEntity.create(index2, file2);
    DownloadServerEntity record3 = DownloadServerEntity.create(index3, file3);
    downloadServerRepo.upsert(record1);
    downloadServerRepo.upsert(record2);
    downloadServerRepo.upsert(record3);

    assertThat(downloadServerRepo.getMostRecentSuccessfulDownload(index2)).isEqualTo(file2);
  }

  @Test
  public void saveSameServerMultipleTimes_shouldReturnLastUri() {
    Uri index = Uri.parse("example.com/index");
    Uri file1 = Uri.parse("example.com/file1");
    Uri file2 = Uri.parse("example.com/file2");
    Uri file3 = Uri.parse("example.com/file3");
    DownloadServerEntity record1 = DownloadServerEntity.create(index, file1);
    DownloadServerEntity record2 = DownloadServerEntity.create(index, file2);
    DownloadServerEntity record3 = DownloadServerEntity.create(index, file3);
    downloadServerRepo.upsert(record1);
    downloadServerRepo.upsert(record2);
    downloadServerRepo.upsert(record3);

    assertThat(downloadServerRepo.getMostRecentSuccessfulDownload(index)).isEqualTo(file3);
  }

  @Test
  public void saveMultipleServers_clearDownloadServerEntitiesAsync_clearsAllData()
      throws Exception {
    Uri index1 = Uri.parse("example-1.com/index");
    Uri file1 = Uri.parse("example-1.com/file");
    Uri index2 = Uri.parse("example-2.com/index");
    Uri file2 = Uri.parse("example-2.com/file");
    Uri index3 = Uri.parse("example-3.com/index");
    Uri file3 = Uri.parse("example-3.com/file");
    DownloadServerEntity record1 = DownloadServerEntity.create(index1, file1);
    DownloadServerEntity record2 = DownloadServerEntity.create(index2, file2);
    DownloadServerEntity record3 = DownloadServerEntity.create(index3, file3);
    downloadServerRepo.upsert(record1);
    downloadServerRepo.upsert(record2);
    downloadServerRepo.upsert(record3);

    downloadServerRepo.deleteDownloadServerEntitiesAsync().get();

    assertThat(downloadServerRepo.getMostRecentSuccessfulDownload(index1)).isNull();
    assertThat(downloadServerRepo.getMostRecentSuccessfulDownload(index2)).isNull();
    assertThat(downloadServerRepo.getMostRecentSuccessfulDownload(index3)).isNull();
  }

  @Test
  public void saveSameServerMultipleTimes_clearDownloadServerEntitiesAsync_clearsAllData()
      throws Exception {
    Uri index = Uri.parse("example.com/index");
    Uri file1 = Uri.parse("example.com/file1");
    Uri file2 = Uri.parse("example.com/file2");
    Uri file3 = Uri.parse("example.com/file3");
    DownloadServerEntity record1 = DownloadServerEntity.create(index, file1);
    DownloadServerEntity record2 = DownloadServerEntity.create(index, file2);
    DownloadServerEntity record3 = DownloadServerEntity.create(index, file3);
    downloadServerRepo.upsert(record1);
    downloadServerRepo.upsert(record2);
    downloadServerRepo.upsert(record3);

    downloadServerRepo.deleteDownloadServerEntitiesAsync().get();

    assertThat(downloadServerRepo.getMostRecentSuccessfulDownload(index)).isNull();
  }

}