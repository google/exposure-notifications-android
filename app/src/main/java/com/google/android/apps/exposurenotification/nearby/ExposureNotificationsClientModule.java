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

import android.content.Context;
import com.google.android.apps.exposurenotification.logging.AnalyticsLogger;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.exposurenotification.ExposureNotificationClient;
import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;
import javax.inject.Singleton;

@Module
@InstallIn(SingletonComponent.class)
public class ExposureNotificationsClientModule {

  @Provides
  public ExposureNotificationClient provideExposureNotificationClient(
      @ApplicationContext Context context) {
    return Nearby.getExposureNotificationClient(context);
  }

  @Provides
  @Singleton
  public ExposureNotificationClientWrapper provideExposureNotificationClientWrapper(
      ExposureNotificationClient exposureNotificationClient,
      AnalyticsLogger logger) {
    return new ExposureNotificationClientWrapper(exposureNotificationClient, logger);
  }
}
