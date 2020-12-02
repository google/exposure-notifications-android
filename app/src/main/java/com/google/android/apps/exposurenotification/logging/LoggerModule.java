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

package com.google.android.apps.exposurenotification.logging;

import com.google.android.apps.exposurenotification.BuildConfig;
import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.components.ApplicationComponent;
import javax.inject.Singleton;

/**
 * Provides AnalyticLogger depending on build configuration
 */
@Module
@InstallIn(ApplicationComponent.class)
public class LoggerModule {

  /**
   * Get reference to Analytics logger, creating it on the first run
   */
  @Provides
  @Singleton
  public synchronized AnalyticsLogger provideAnalyticsLogger(
      LogcatAnalyticsLogger logcatLogger,
      FirelogAnalyticsLogger firelogLogger) {
    if (BuildConfig.LOGSOURCE_ID.isEmpty()) {
      return logcatLogger;
    } else {
      return firelogLogger;
    }
  }
}

