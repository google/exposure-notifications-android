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

package com.google.android.apps.exposurenotification.privateanalytics;

import com.google.android.apps.exposurenotification.BuildConfig;
import com.google.android.apps.exposurenotification.common.Qualifiers.LightweightExecutor;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.ExecutorService;
import javax.inject.Inject;

/**
 * A helper class for checking if private analytics is supported and or enabled.
 */
public class PrivateAnalyticsSettingsUtil {

  private final PrivateAnalyticsRemoteConfig privateAnalyticsRemoteConfig;
  private final ExecutorService lightweightExecutor;

  @Inject
  PrivateAnalyticsSettingsUtil(
      PrivateAnalyticsRemoteConfig privateAnalyticsRemoteConfig,
      @LightweightExecutor ExecutorService lightweightExecutor) {
    this.privateAnalyticsRemoteConfig = privateAnalyticsRemoteConfig;
    this.lightweightExecutor = lightweightExecutor;
  }

  public static boolean isPrivateAnalyticsSupported() {
    return BuildConfig.PRIVATE_ANALYTICS_SUPPORTED;
  }

  public ListenableFuture<Boolean> isPrivateAnalyticsSupportAndConfigured() {
    if (!isPrivateAnalyticsSupported()) {
      return Futures.immediateFuture(false);
    }
    return FluentFuture.from(privateAnalyticsRemoteConfig.fetchUpdatedConfigs())
        .transform(RemoteConfigs::enabled, lightweightExecutor);
  }
}
