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

import android.content.Context;
import android.net.Uri;
import com.google.android.apps.exposurenotification.BuildConfig;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.logging.AnalyticsLogger;
import com.google.android.apps.exposurenotification.logging.FirelogAnalyticsLogger;
import com.google.android.apps.exposurenotification.logging.LogcatAnalyticsLogger;
import com.google.android.apps.exposurenotification.proto.RpcCall.RpcCallType;
import com.google.android.apps.exposurenotification.proto.WorkManagerTask.WorkerTask;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.libraries.privateanalytics.DefaultPrivateAnalyticsDeviceAttestation;
import com.google.android.libraries.privateanalytics.DefaultPrivateAnalyticsRemoteConfig;
import com.google.android.libraries.privateanalytics.PrivateAnalyticsDeviceAttestation;
import com.google.android.libraries.privateanalytics.PrivateAnalyticsEnabledProvider;
import com.google.android.libraries.privateanalytics.PrivateAnalyticsEventListener;
import com.google.android.libraries.privateanalytics.PrivateAnalyticsRemoteConfig;
import com.google.android.libraries.privateanalytics.Qualifiers.PackageName;
import com.google.android.libraries.privateanalytics.Qualifiers.RemoteConfigUri;
import com.google.common.base.Optional;
import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;

@Module
@InstallIn(SingletonComponent.class)
public class PrivateAnalyticsModule {

  @Provides
  public PrivateAnalyticsDeviceAttestation providesDeviceAttestation(
      @ApplicationContext Context context, @PackageName String packageName) {
    return new DefaultPrivateAnalyticsDeviceAttestation(context, packageName);
  }

  @Provides
  public PrivateAnalyticsRemoteConfig providesRemoteConfig(@RemoteConfigUri Uri remoteConfigUri,
      Optional<PrivateAnalyticsEventListener> listener) {
    return new DefaultPrivateAnalyticsRemoteConfig(remoteConfigUri, listener);
  }

  @Provides
  @RemoteConfigUri
  public Uri provideRemoteConfigUri(@ApplicationContext Context context) {
    return Uri.parse(context.getString(R.string.enx_enpaRemoteConfigURL));
  }

  @Provides
  @PackageName
  public String providePackageName(@ApplicationContext Context context) {
    return context.getString(R.string.enx_healthAuthorityID);
  }

  @Provides
  public PrivateAnalyticsEnabledProvider provideIsPrivateAnalyticsEnabled(
      ExposureNotificationSharedPreferences sharedPreferences) {
    return new PrivateAnalyticsEnabledProvider() {
      @Override
      public boolean isSupportedByApp() {
        return BuildConfig.PRIVATE_ANALYTICS_SUPPORTED && DefaultPrivateAnalyticsDeviceAttestation
            .isDeviceAttestationAvailable();
      }

      @Override
      public boolean isEnabledForUser() {
        return sharedPreferences.getPrivateAnalyticState();
      }
    };

  }

  @Provides
  public Optional<PrivateAnalyticsEventListener> providesPrivateAnalyticsEventListener(
      LogcatAnalyticsLogger logcatLogger,
      FirelogAnalyticsLogger firelogLogger) {
    AnalyticsLogger logger;
    if (BuildConfig.LOGSOURCE_ID.isEmpty()) {
      logger = logcatLogger;
    } else {
      logger = firelogLogger;
    }

    return Optional.of(new PrivateAnalyticsEventListener() {
      @Override
      public void onPrivateAnalyticsWorkerTaskStarted() {
        logger.logWorkManagerTaskStarted(WorkerTask.TASK_SUBMIT_PRIVATE_ANALYTICS);
      }

      @Override
      public void onPrivateAnalyticsRemoteConfigCallSuccess(int length) {
        logger.logRpcCallSuccessAsync(RpcCallType.RPC_TYPE_ENPA_REMOTE_CONFIG_FETCH, length);
      }

      @Override
      public void onPrivateAnalyticsRemoteConfigCallFailure(Exception err) {
        logger.logRpcCallFailureAsync(RpcCallType.RPC_TYPE_ENPA_REMOTE_CONFIG_FETCH, err);
      }
    });
  }
}
