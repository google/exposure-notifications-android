/*
 * Copyright 2021 Google LLC
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

package com.google.android.apps.exposurenotification.migrate;

import static com.google.android.apps.exposurenotification.migrate.Migration.EN_SHARED_PREFS_FILE;
import static com.google.android.apps.exposurenotification.migrate.Migration.WORK_MANAGER_DIR;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.work.Operation;
import androidx.work.WorkManager;
import com.google.android.apps.exposurenotification.migrate.Migration.MigrationFailedException;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.apps.exposurenotification.testsupport.ExposureNotificationRules;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import java.io.File;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.annotation.Config;

@RunWith(AndroidJUnit4.class)
@HiltAndroidTest
@Config(application = HiltTestApplication.class, minSdk = 21, maxSdk = 30)
public class MigrationTest {

  @Rule
  public ExposureNotificationRules rules = ExposureNotificationRules.forTest(this).withMocks()
      .build();

  // Spy on the Context object as we want to stub some of its methods.
  private final Context context = spy(ApplicationProvider.getApplicationContext());

  private final String APP_DIR_NAME = context.getPackageName();
  private final String CACHE_DIR_NAME = "cache";
  private final String DB_DIR_NAME ="databases";
  private final Map<String, File> MOCK_APP_STORAGE_FILES = ImmutableMap.of(
      APP_DIR_NAME, setupMockFile(APP_DIR_NAME),
      CACHE_DIR_NAME, setupMockFile(CACHE_DIR_NAME),
      DB_DIR_NAME, setupMockFile(DB_DIR_NAME),
      WORK_MANAGER_DIR, setupMockFile(WORK_MANAGER_DIR),
      EN_SHARED_PREFS_FILE, setupMockFile(EN_SHARED_PREFS_FILE)
  );
  private final String[] APP_DIR_CHILD_NAMES = {
      WORK_MANAGER_DIR, EN_SHARED_PREFS_FILE, DB_DIR_NAME, CACHE_DIR_NAME
  };
  private final String[] DEFAULT_DIR_CHILD_NAMES = {};

  @Inject
  ExposureNotificationSharedPreferences exposureNotificationSharedPreferences;

  @Mock
  WorkManager workManager;

  // SUT.
  Migration migration;

  @Before
  public void setUp() {
    rules.hilt().inject();

    // Spy on the migration object as we need to stub some of its methods.
    migration = spy(new Migration(
        MoreExecutors.newDirectExecutorService(),
        exposureNotificationSharedPreferences,
        workManager
    ));

    // Stub methods as needed.
    Operation successOperation = mock(Operation.class);
    when(successOperation.getResult()).thenReturn(Futures.immediateFuture(Operation.SUCCESS));
    when(workManager.cancelAllWork()).thenReturn(successOperation);
    setupAppStorageFromMocks();
  }

  @Test
  public void isMigrationRunOrNotNeeded_default_isFalse() {
    assertThat(migration.isMigrationRunOrNotNeeded()).isFalse();
  }

  @Test
  public void isMigrationRunOrNotNeeded_markMigrationAsRunOrNotNeeded_isTrue() {
    migration.markMigrationAsRunOrNotNeeded();

    assertThat(migration.isMigrationRunOrNotNeeded()).isTrue();
  }

  @Test
  public void migrate_wmOperationFailed_hasFailed() {
    Operation failedOperation = mock(Operation.class);
    when(failedOperation.getResult()).thenReturn(Futures.immediateFailedFuture(
        new NullPointerException()));
    when(workManager.cancelAllWork()).thenReturn(failedOperation);

    ThrowingRunnable execute = () -> migration.migrate(context).get();

    Exception thrownException = assertThrows(ExecutionException.class, execute);
    assertThat(thrownException.getCause()).isInstanceOf(NullPointerException.class);
    verify(workManager).cancelAllWork();
    // We should never delete WorkManager directory and EN Shared Preferences file when migrating.
    verify(MOCK_APP_STORAGE_FILES.get(WORK_MANAGER_DIR), never()).delete();
    verify(MOCK_APP_STORAGE_FILES.get(EN_SHARED_PREFS_FILE), never()).delete();
  }

  @Test
  public void migrate_deletionFailed_hasFailed() {
    // Fail any of the deletions.
    when(MOCK_APP_STORAGE_FILES.get(CACHE_DIR_NAME).delete()).thenReturn(false);

    ThrowingRunnable execute = () -> migration.migrate(context).get();

    Exception thrownException = assertThrows(ExecutionException.class, execute);
    assertThat(thrownException.getCause()).isInstanceOf(MigrationFailedException.class);
    verify(workManager).cancelAllWork();
    // We should never delete WorkManager directory and EN Shared Preferences file when migrating.
    verify(MOCK_APP_STORAGE_FILES.get(WORK_MANAGER_DIR), never()).delete();
    verify(MOCK_APP_STORAGE_FILES.get(EN_SHARED_PREFS_FILE), never()).delete();
  }

  @Test
  public void migrate_deletionSuccessful_isSuccessful() throws Exception {
    migration.migrate(context).get();

    verify(workManager).cancelAllWork();
    // We should never delete WorkManager directory and EN Shared Preferences file when migrating.
    verify(MOCK_APP_STORAGE_FILES.get(WORK_MANAGER_DIR), never()).delete();
    verify(MOCK_APP_STORAGE_FILES.get(EN_SHARED_PREFS_FILE), never()).delete();
  }

  private File setupMockFile(String name) {
    File mockFile = mock(File.class);

    when(mockFile.canRead()).thenReturn(true);
    when(mockFile.canWrite()).thenReturn(true);
    when(mockFile.getName()).thenReturn(name);
    when(mockFile.getPath()).thenReturn(name);

    if (name.equals(APP_DIR_NAME)) {
      when(mockFile.getAbsolutePath()).thenReturn("/" + name);
    } else {
      when(mockFile.getAbsolutePath()).thenReturn("/" + APP_DIR_NAME + "/" + name);
    }

    // Set up successful file deletion by default.
    when(mockFile.delete()).thenReturn(true);

    return mockFile;
  }

  private void setupAppStorageFromMocks() {
    File appDir = MOCK_APP_STORAGE_FILES.get(APP_DIR_NAME);
    File cacheDir = MOCK_APP_STORAGE_FILES.get(CACHE_DIR_NAME);
    File databaseDir = MOCK_APP_STORAGE_FILES.get(DB_DIR_NAME);
    File wmDir = MOCK_APP_STORAGE_FILES.get(WORK_MANAGER_DIR);
    File enSharedPrefs = MOCK_APP_STORAGE_FILES.get(EN_SHARED_PREFS_FILE);

    // Mark each pathname as file or directory.
    when(appDir.isDirectory()).thenReturn(true);
    when(cacheDir.isDirectory()).thenReturn(true);
    when(databaseDir.isDirectory()).thenReturn(true);
    when(wmDir.isDirectory()).thenReturn(true);
    when(enSharedPrefs.isDirectory()).thenReturn(false);

    // Set up appDir directory.
    ApplicationInfo mApplicationInfo = new ApplicationInfo();
    mApplicationInfo.dataDir = appDir.getName();
    when(context.getApplicationInfo()).thenReturn(mApplicationInfo);
    when(appDir.exists()).thenReturn(true);
    when(appDir.list()).thenReturn(APP_DIR_CHILD_NAMES);

    // Set up other directories.
    when(cacheDir.list()).thenReturn(DEFAULT_DIR_CHILD_NAMES);
    when(databaseDir.list()).thenReturn(DEFAULT_DIR_CHILD_NAMES);
    when(wmDir.list()).thenReturn(DEFAULT_DIR_CHILD_NAMES);

    // Now ensure that the correct File objects are created in the SUT.
    doReturn(appDir).when(migration).getFileFromStringPathname(APP_DIR_NAME);
    doReturn(cacheDir).when(migration).getFileFromParentAndChild(appDir, CACHE_DIR_NAME);
    doReturn(databaseDir).when(migration).getFileFromParentAndChild(appDir, DB_DIR_NAME);
    doReturn(wmDir).when(migration).getFileFromParentAndChild(appDir, WORK_MANAGER_DIR);
    doReturn(enSharedPrefs).when(migration)
        .getFileFromParentAndChild(appDir, EN_SHARED_PREFS_FILE);
  }

}