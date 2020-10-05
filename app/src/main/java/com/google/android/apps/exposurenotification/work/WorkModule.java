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

package com.google.android.apps.exposurenotification.work;

import android.content.Context;
import androidx.work.WorkManager;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.Qualifiers.BackgroundExecutor;
import com.google.android.apps.exposurenotification.common.Qualifiers.LightweightExecutor;
import com.google.android.apps.exposurenotification.common.Qualifiers.ScheduledExecutor;
import com.google.android.apps.exposurenotification.nearby.ExposureNotificationClientWrapper;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.common.util.concurrent.ListeningExecutorService;
import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.components.ApplicationComponent;
import dagger.hilt.android.qualifiers.ApplicationContext;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import org.threeten.bp.Duration;

@Module
@InstallIn(ApplicationComponent.class)
public class WorkModule {

  @Provides
  public WorkScheduler provideWorkScheduler(
      @ApplicationContext Context context,
      WorkManager workManager,
      @LightweightExecutor ListeningExecutorService lightweightExecutor) {
    Duration tekPublishInterval =
        Duration.ofHours(context.getResources().getInteger(R.integer.enx_tekPublishInterval));
    return new WorkScheduler(workManager, lightweightExecutor, tekPublishInterval);
  }

  @Provides
  public WorkManager provideWorkManager(@ApplicationContext Context context) {
    return WorkManager.getInstance(context);
  }

}