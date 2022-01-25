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

import android.content.Context;
import androidx.annotation.VisibleForTesting;
import androidx.work.WorkManager;
import com.google.android.apps.exposurenotification.common.Qualifiers.BackgroundExecutor;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.io.File;
import javax.inject.Inject;

/**
 * A helper class that does the migration from a v1 EN app to the ENX app.
 */
public final class Migration {

  @VisibleForTesting
  static final String WORK_MANAGER_DIR = "no_backup";

  @VisibleForTesting
  static final String LIBS_DIR = "lib";

  @VisibleForTesting
  static final String EN_SHARED_PREFS_FILE =
      "ExposureNotificationSharedPreferences.SHARED_PREFERENCES_FILE.xml";

  @VisibleForTesting
  static final String SHARED_PREFS_DIR = "shared_prefs";

  private final ListeningExecutorService backgroundExecutor;
  private final ExposureNotificationSharedPreferences exposureNotificationSharedPreferences;
  private final WorkManager workManager;

  @Inject
  public Migration(
      @BackgroundExecutor ListeningExecutorService backgroundExecutor,
      ExposureNotificationSharedPreferences exposureNotificationSharedPreferences,
      WorkManager workManager) {
    this.backgroundExecutor = backgroundExecutor;
    this.exposureNotificationSharedPreferences = exposureNotificationSharedPreferences;
    this.workManager = workManager;
  }

  /**
   * Checks if migration has been run already or not needed at all.
   */
  public boolean isMigrationRunOrNotNeeded() {
    return exposureNotificationSharedPreferences.isMigrationRunOrNotNeeded();
  }

  /**
   * Marks migration as run or not needed.
   */
  public void markMigrationAsRunOrNotNeeded() {
    exposureNotificationSharedPreferences.markMigrationAsRunOrNotNeeded();
  }

  /**
   * Migrates from V1 to ENX in two steps:
   * <ul>
   *   <li>Cancels the enqueued work requests.
   *   <li>Clears the data from the app storage.
   * </ul>
   *
   * @param context current application context.
   */
  public ListenableFuture<Void> migrate(Context context) {
    return FluentFuture.from(
            // Cancel all of the previously enqueued work.
            workManager.cancelAllWork().getResult())
        .transformAsync(cancellationSuccess -> clearData(context), backgroundExecutor)
        .transformAsync(
            dataCleared ->
                dataCleared
                    ? Futures.immediateVoidFuture()
                    : Futures.immediateFailedFuture(new MigrationFailedException()),
            backgroundExecutor);
  }

  /**
   * Clears all the data currently stored in the local app storage except for the WorkManager
   * data (as we clear up WorkManager requests separately).
   *
   * @return true if the deletion is successful and false otherwise.
   */
  private ListenableFuture<Boolean> clearData(Context context) {
    boolean result = true;
    String appDirName = context.getApplicationInfo().dataDir;
    File appDir = getFileFromStringPathname(appDirName);
    if (appDir.exists()) {
      String[] children = appDir.list();
      for (String child : children) {
        if (!child.equals(WORK_MANAGER_DIR) && !child.equals(LIBS_DIR)) {
          if (!deleteDirRecursively(getFileFromParentAndChild(appDir, child))) {
            result = false;
          }
        }
      }
    }
    return Futures.immediateFuture(result);
  }

  /**
   * Deletes the specified file or directory with all its contents.
   *
   * @return true if the deletion is successful and false otherwise.
   */
  private boolean deleteDirRecursively(File dir) {
    if (dir != null && dir.isDirectory()) {
      String[] children = dir.list();
      for (String child : children) {
        boolean success = deleteDirRecursively(getFileFromParentAndChild(dir, child));
        if (!success) {
          return false;
        }
      }
    }
    // Upon the app startup we may have already created the Shared Preferences file for this
    // ENX app. Make sure we won't delete it when clearing up the V1 app storage.
    if (dir.getName().equals(EN_SHARED_PREFS_FILE)) {
      return true;
    }
    // And as we never delete the Shared Preferences file, don't delete its root directory since
    // deletion will fail (we cannot delete non-empty directory).
    if (dir.getName().equals(SHARED_PREFS_DIR)) {
      return true;
    }
    return dir.delete();
  }

  @VisibleForTesting
  File getFileFromStringPathname(String path) {
    return new File(path);
  }

  @VisibleForTesting
  File getFileFromParentAndChild(File parent, String child) {
    return new File(parent, child);
  }

  /**
   * An {@link Exception} thrown to indicate that the V1 -> ENX migration has failed (because of the
   * issues when clearing up V1 app storage).
   *
   * <p>See more details in {@link Migration#clearData(Context context)}.
   */
  public static class MigrationFailedException extends Exception {}

  /**
   * A {@link RuntimeException} thrown to indicate that the V1 -> ENX migration has failed.
   */
  public static class MigrationRuntimeException extends RuntimeException {

    public MigrationRuntimeException(Throwable cause) {
      super(cause);
    }
  }
}
