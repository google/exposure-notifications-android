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
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import androidx.annotation.VisibleForTesting;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.Qualifiers.BackgroundExecutor;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.ExecutorService;
import javax.inject.Inject;

/** Helper class to manage the V1 -> ENX migration-related operations. */
public final class MigrationManager {

  private final Migration migration;
  private final ExecutorService backgroundExecutor;

  @Inject
  public MigrationManager(
      Migration migration,
      @BackgroundExecutor ExecutorService backgroundExecutor) {
    this.migration = migration;
    this.backgroundExecutor = backgroundExecutor;
  }

  /**
   * Runs migration but only if the conditions to run migration are satisfied.
   *
   * <p>Check {@link MigrationManager#shouldMigrate(Context context)} method below for more
   * information on what conditions must be satisfied for migration to run.
   */
  public ListenableFuture<?> maybeMigrate(Context context) {
    if (!shouldMigrate(context)) {
      migration.markMigrationAsRunOrNotNeeded();
      return Futures.immediateVoidFuture();
    }
    return FluentFuture.from(migration.migrate(context))
        .transform(
            unused -> {
              migration.markMigrationAsRunOrNotNeeded();
              return null;
            },
            backgroundExecutor)
        .catchingAsync(
            Exception.class,
            ex -> {
              migration.markMigrationAsRunOrNotNeeded();
              return Futures.immediateFailedFuture(ex);
            },
            backgroundExecutor);
  }

  /**
   * Checks if we should run migration for the current app.
   *
   * <p>We should run migration if all of the following hold:
   *
   * <ul>
   *   <li>Migration is enabled via a config flag.
   *   <li>This is the app update (which together with the item above should trigger migration from
   *       the previously V1 app to the ENX app).
   *   <li>Migration has not been run yet.
   * </ul>
   *
   * @return true if we should run the migration and false otherwise.
   */
  @VisibleForTesting
  boolean shouldMigrate(Context context) {
    return isMigrationEnabled(context.getResources())
        && !migration.isMigrationRunOrNotNeeded()
        && !isFirstInstall(context.getPackageManager(), context.getPackageName());
  }

  /**
   * Checks if this is the first app install.
   *
   * @param packageManager package manager
   * @param packageName name of this application's package.
   * @return true if this is the first app install and false otherwise (or if this information
   *     failed to be determined).
   */
  @VisibleForTesting
  boolean isFirstInstall(PackageManager packageManager, String packageName) {
    try {
      PackageInfo packageInfo = packageManager.getPackageInfo(packageName, 0);
      long firstInstallTime = packageInfo.firstInstallTime;
      long lastUpdateTime = packageInfo.lastUpdateTime;
      return firstInstallTime == lastUpdateTime;
    } catch (NameNotFoundException e) {
      // Ignore as nothing much we can do here. Continue with the app startup.
    }
    return false;
  }

  /**
   * Checks if migration is enabled.
   *
   * @param resources application's resources
   * @return true if migration is enabled via a config flag and false otherwise.
   */
  public static boolean isMigrationEnabled(Resources resources) {
    return resources.getBoolean(R.bool.enx_enableV1toENXMigration);
  }

}
