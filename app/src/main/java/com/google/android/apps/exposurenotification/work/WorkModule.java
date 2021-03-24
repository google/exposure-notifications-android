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
import com.google.android.apps.exposurenotification.common.Qualifiers.LightweightExecutor;
import com.google.android.libraries.privateanalytics.PrivateAnalyticsEnabledProvider;
import com.google.android.libraries.privateanalytics.PrivateAnalyticsRemoteConfig;
import com.google.common.util.concurrent.ListeningExecutorService;
import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;
import org.threeten.bp.Duration;

@Module
@InstallIn(SingletonComponent.class)
public class WorkModule {

  @Provides
  public WorkScheduler provideWorkScheduler(
      @ApplicationContext Context context,
      WorkManager workManager,
      @LightweightExecutor ListeningExecutorService lightweightExecutor,
      PrivateAnalyticsEnabledProvider privateAnalyticsEnabledProvider,
      PrivateAnalyticsRemoteConfig privateAnalyticsRemoteConfig) {
    Duration tekPublishInterval =
        Duration.ofHours(context.getResources().getInteger(R.integer.enx_tekPublishInterval));
    return new WorkScheduler(workManager, lightweightExecutor, tekPublishInterval,
        privateAnalyticsEnabledProvider, privateAnalyticsRemoteConfig);
  }

  @Provides
  public WorkManager provideWorkManager(@ApplicationContext Context context) {
    return WorkManager.getInstance(context);
  }

}
