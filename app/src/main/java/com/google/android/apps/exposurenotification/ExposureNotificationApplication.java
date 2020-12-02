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
import androidx.annotation.Nullable;
import androidx.hilt.work.HiltWorkerFactory;
import androidx.work.Configuration;
import androidx.work.Configuration.Builder;
import androidx.work.WorkManager;
import com.google.android.apps.exposurenotification.home.ExposureNotificationActivity;
import com.google.android.apps.exposurenotification.work.WorkScheduler;
import com.google.firebase.FirebaseApp;
import com.jakewharton.threetenabp.AndroidThreeTen;
import dagger.hilt.android.HiltAndroidApp;
import javax.inject.Inject;

/**
 * ExposureNotificationApplication is instantiated whenever the app is running.
 *
 * <p>For UI see {@link ExposureNotificationActivity}
 */
@HiltAndroidApp
public final class ExposureNotificationApplication extends Application implements
    Configuration.Provider {

  private static final String TAG = "ENApplication";
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

  /**
   * Done to ensure firebase services are initialized properly.
   */
  @Keep
  @Inject
  @Nullable
  FirebaseApp firebaseApp;

  @Override
  public void onCreate() {
    super.onCreate();
    AndroidThreeTen.init(this);
    workScheduler.schedule();
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
