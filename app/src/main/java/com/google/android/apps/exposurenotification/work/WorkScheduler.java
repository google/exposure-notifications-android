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

import androidx.work.Operation.State.SUCCESS;
import androidx.work.WorkManager;
import com.google.android.apps.exposurenotification.common.Qualifiers.LightweightExecutor;
import com.google.android.apps.exposurenotification.common.logging.Logger;
import com.google.android.apps.exposurenotification.keyupload.UploadCoverTrafficWorker;
import com.google.android.apps.exposurenotification.logging.FirelogAnalyticsWorker;
import com.google.android.apps.exposurenotification.nearby.ProvideDiagnosisKeysWorker;
import com.google.android.apps.exposurenotification.privateanalytics.SubmitPrivateAnalyticsWorker;
import com.google.android.apps.exposurenotification.roaming.CountryCheckingWorker;
import com.google.android.libraries.privateanalytics.DefaultPrivateAnalyticsDeviceAttestation;
import com.google.android.libraries.privateanalytics.PrivateAnalyticsRemoteConfig;
import com.google.android.libraries.privateanalytics.PrivateAnalyticsEnabledProvider;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListeningExecutorService;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;
import org.threeten.bp.Duration;

/**
 * Manages background work scheduling for the application.
 */
public class WorkScheduler {

  private static final Logger logger = Logger.getLogger("WorkScheduler");

  private final WorkManager workManager;
  private final ListeningExecutorService lightweightExecutor;
  private final Duration tekPublishInterval;
  private final PrivateAnalyticsEnabledProvider privateAnalyticsEnabledProvider;
  private final PrivateAnalyticsRemoteConfig privateAnalyticsRemoteConfig;

  public WorkScheduler(
      WorkManager workManager,
      @LightweightExecutor ListeningExecutorService lightweightExecutor,
      Duration tekPublishInterval,
      PrivateAnalyticsEnabledProvider privateAnalyticsEnabledProvider,
      PrivateAnalyticsRemoteConfig privateAnalyticsRemoteConfig) {
    this.workManager = workManager;
    this.lightweightExecutor = lightweightExecutor;
    this.tekPublishInterval = tekPublishInterval;
    this.privateAnalyticsEnabledProvider = privateAnalyticsEnabledProvider;
    this.privateAnalyticsRemoteConfig = privateAnalyticsRemoteConfig;
  }

  public void schedule() {
    logger.i("Scheduling post-enable periodic WorkManager jobs...");
    Futures.addCallback(
        UploadCoverTrafficWorker.schedule(workManager).getResult(),
        new FutureCallback<SUCCESS>() {
          @Override
          public void onSuccess(@NullableDecl SUCCESS result) {
            logger.i("Scheduled UploadCoverTrafficWorker.");
          }

          @Override
          public void onFailure(Throwable t) {
            logger.e("Failed to schedule UploadCoverTrafficWorker.", t);
          }
        }, lightweightExecutor);

    Futures.addCallback(
        ProvideDiagnosisKeysWorker.schedule(workManager, tekPublishInterval).getResult(),
        new FutureCallback<SUCCESS>() {
          @Override
          public void onSuccess(@NullableDecl SUCCESS result) {
            logger.i("Scheduled ProvideDiagnosisKeysWorker.");
          }

          @Override
          public void onFailure(Throwable t) {
            logger.e("Failed to schedule ProvideDiagnosisKeysWorker.", t);
          }
        }, lightweightExecutor);

    Futures.addCallback(
        CountryCheckingWorker.schedule(workManager).getResult(),
        new FutureCallback<SUCCESS>() {
          @Override
          public void onSuccess(@NullableDecl SUCCESS result) {
            logger.i("Scheduled CountryCheckingWorker.");
          }

          @Override
          public void onFailure(Throwable t) {
            logger.e("Failed to schedule CountryCheckingWorker.", t);
          }
        }, lightweightExecutor);

    Futures.addCallback(
        FirelogAnalyticsWorker.schedule(workManager).getResult(),
        new FutureCallback<SUCCESS>() {
          @Override
          public void onSuccess(@NullableDecl SUCCESS result) {
            logger.i("Scheduled FirelogAnalyticsWorker.");
          }

          @Override
          public void onFailure(Throwable t) {
            logger.e("Failed to schedule FirelogAnalyticsWorker.", t);
          }
        }, lightweightExecutor);

    if (privateAnalyticsEnabledProvider.isSupportedByApp() &&
        DefaultPrivateAnalyticsDeviceAttestation.isDeviceAttestationAvailable()) {
      Futures.addCallback(
          SubmitPrivateAnalyticsWorker.schedule(workManager).getResult(),
          new FutureCallback<SUCCESS>() {
            @Override
            public void onSuccess(@NullableDecl SUCCESS result) {
              logger.i("Scheduled SubmitPrivateAnalyticsWorker.");
            }

            @Override
            public void onFailure(Throwable t) {
              logger.e("Failed to schedule SubmitPrivateAnalyticsWorker.", t);
            }
          }, lightweightExecutor);
    } else {
      logger.d(String.format(
          "Private Analytics not scheduled. isFeatureSupported=%s, isDeviceAttestationAvailable=%s",
          privateAnalyticsEnabledProvider.isSupportedByApp(),
          DefaultPrivateAnalyticsDeviceAttestation.isDeviceAttestationAvailable()));
    }
  }
}
