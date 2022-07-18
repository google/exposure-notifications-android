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

package com.google.android.apps.exposurenotification;

import android.app.Application;
import android.util.Log;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.hilt.work.HiltWorkerFactory;
import androidx.lifecycle.ProcessLifecycleOwner;
import androidx.work.Configuration;
import androidx.work.Configuration.Builder;
import androidx.work.WorkManager;
import com.google.android.apps.exposurenotification.common.NotificationHelper;
import com.google.android.apps.exposurenotification.common.Qualifiers.BackgroundExecutor;
import com.google.android.apps.exposurenotification.common.ThemeUtils;
import com.google.android.apps.exposurenotification.logging.ApplicationObserver;
import com.google.android.apps.exposurenotification.migrate.Migration.MigrationRuntimeException;
import com.google.android.apps.exposurenotification.migrate.MigrationManager;
import com.google.android.apps.exposurenotification.slices.SlicePermissionManager;
import com.google.android.apps.exposurenotification.work.WorkScheduler;
import com.google.android.material.color.DynamicColors;
import com.google.common.base.Optional;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.firebase.FirebaseApp;
import com.jakewharton.threetenabp.AndroidThreeTen;
import dagger.hilt.android.HiltAndroidApp;
import java.util.concurrent.ExecutorService;
import javax.inject.Inject;

/**
 * ExposureNotificationApplication is instantiated whenever the app is running.
 */
@HiltAndroidApp
public final class ExposureNotificationApplication extends Application implements
    Configuration.Provider {

  @Inject
  HiltWorkerFactory workerFactory;

  /**
   * Force inject WorkManager to ensure it is initialized when the app was force-stopped and it
   * re-schedule all workers when woken up by Google Play Services.
   */
  @Keep
  @Inject
  WorkManager workManager;

  @Inject
  WorkScheduler workScheduler;

  @Inject
  ApplicationObserver applicationObserver;

  @Inject
  Optional<SlicePermissionManager> slicePermissionManager;

  @Inject
  @BackgroundExecutor
  ExecutorService backgroundExecutor;

  /**
   * Done to ensure firebase services are initialized properly.
   */
  @Keep
  @Inject
  @Nullable
  FirebaseApp firebaseApp;

  @Inject
  MigrationManager migrationManager;

  @Inject
  NotificationHelper notificationHelper;

  @Override
  public void onCreate() {
    super.onCreate();

    // Apply dynamic colors to all activities if device is running Android S+.
    if (ThemeUtils.supportsMaterialYou()) {
      DynamicColors.applyToActivitiesIfAvailable(this);
    }

    // Grant slice runtime permission
    if (slicePermissionManager.isPresent()) {
      backgroundExecutor.execute(() -> slicePermissionManager.get().grantSlicePermission());
    }

    notificationHelper.createNotificationChannel(this);

    AndroidThreeTen.init(this);

    Futures.addCallback(
        migrationManager.maybeMigrate(this),
        new FutureCallback<Object>() {
          @Override
          public void onSuccess(@Nullable Object result) {
            workScheduler.schedule();
          }

          @Override
          public void onFailure(@NonNull Throwable t) {
            throw new MigrationRuntimeException(t);
          }
        },
        backgroundExecutor);

    // Add ProcessLifecycleObserver that logs APP_OPENED if the app is in foreground
    applicationObserver.observeLifecycle(ProcessLifecycleOwner.get());
  }

  @Override
  public Configuration getWorkManagerConfiguration() {
    Builder builder = new Builder().setWorkerFactory(workerFactory);

    // Enable debug logging for debug builds.
    if (BuildConfig.DEBUG) {
      builder.setMinimumLoggingLevel(Log.DEBUG);
    }
    return builder.build();
  }

}
