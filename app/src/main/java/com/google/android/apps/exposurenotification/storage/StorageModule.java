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

import android.content.Context;
import com.google.android.apps.exposurenotification.common.time.Clock;
import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;
import java.security.SecureRandom;

@Module
@InstallIn(SingletonComponent.class)
public class StorageModule {

  @Provides
  public ExposureNotificationSharedPreferences provideExposureNotificationSharedPreferences(
      @ApplicationContext Context context, Clock clock, SecureRandom secureRandom) {
    return new ExposureNotificationSharedPreferences(context, clock, secureRandom);
  }
}
