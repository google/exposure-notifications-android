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
import com.google.android.apps.exposurenotification.common.AppExecutors;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.exposurenotification.ExposureConfiguration;
import com.google.android.gms.nearby.exposurenotification.ExposureInformation;
import com.google.android.gms.nearby.exposurenotification.ExposureNotificationClient;
import com.google.android.gms.nearby.exposurenotification.ExposureSummary;
import com.google.android.gms.nearby.exposurenotification.TemporaryExposureKey;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.util.List;

/**
 * Wrapper around {@link com.google.android.gms.nearby.Nearby} APIs.
 */
public class ExposureNotificationClientWrapper {

  private static ExposureNotificationClientWrapper INSTANCE;

  private final Context appContext;
  private final ListeningExecutorService backgroundExecutor;
  private final ExposureNotificationClient exposureNotificationClient;
  private final ExposureNotificationSharedPreferences exposureNotificationSharedPreferences;

  public static ExposureNotificationClientWrapper get(Context context) {
    if (INSTANCE == null) {
      INSTANCE = new ExposureNotificationClientWrapper(context);
    }
    return INSTANCE;
  }

  public ExposureNotificationClientWrapper(Context context) {
    this.appContext = context.getApplicationContext();
    backgroundExecutor = AppExecutors.getBackgroundExecutor();
    exposureNotificationClient = Nearby.getExposureNotificationClient(appContext);
    exposureNotificationSharedPreferences = new ExposureNotificationSharedPreferences(appContext);
  }

  public Task<Void> start() {
    ExposureConfiguration exposureConfiguration =
        new ExposureConfiguration.ExposureConfigurationBuilder()
            .setMinimumRiskScore(4)
            .setAttenuationScores(new int[]{4, 4, 4, 4, 4, 4, 4, 4})
            .setAttenuationWeight(50)
            .setDaysSinceLastExposureScores(new int[]{4, 4, 4, 4, 4, 4, 4, 4})
            .setDaysSinceLastExposureWeight(50)
            .setDurationScores(new int[]{4, 4, 4, 4, 4, 4, 4, 4})
            .setDurationWeight(50)
            .setTransmissionRiskScores(new int[]{4, 4, 4, 4, 4, 4, 4, 4})
            .setTransmissionRiskWeight(50)
            .build();
    return exposureNotificationClient.start(exposureConfiguration);
  }

  public Task<Void> stop() {
    return exposureNotificationClient.stop();
  }

  public Task<Boolean> isEnabled() {
    return exposureNotificationClient.isEnabled();
  }

  public Task<List<TemporaryExposureKey>> getTemporaryExposureKeyHistory() {
    return exposureNotificationClient.getTemporaryExposureKeyHistory();
  }

  public Task<Void> provideDiagnosisKeys(List<TemporaryExposureKey> keys) {
    return exposureNotificationClient.provideDiagnosisKeys(keys);
  }

  public Task<Integer> getMaxDiagnosisKeysCount() {
    return exposureNotificationClient.getMaxDiagnosisKeyCount();
  }

  public Task<ExposureSummary> getExposureSummary() {
    if (exposureNotificationSharedPreferences.getFakeAtRisk()) {
      return Tasks.forResult(
          new ExposureSummary.ExposureSummaryBuilder()
              .setMatchedKeyCount(2)
              .setDaysSinceLastExposure(1)
              .build());
    } else {
      return exposureNotificationClient.getExposureSummary();
    }
  }

  public Task<List<ExposureInformation>> getExposureInformation() {
    if (exposureNotificationSharedPreferences.getFakeAtRisk()) {
      return Tasks.forResult(
          Lists.newArrayList(
              new ExposureInformation.ExposureInformationBuilder()
                  .setAttenuationValue(1)
                  .setDateMillisSinceEpoch(System.currentTimeMillis())
                  .setDurationMinutes(5)
                  .build(),
              new ExposureInformation.ExposureInformationBuilder()
                  .setAttenuationValue(1)
                  .setDateMillisSinceEpoch(1588075162258L)
                  .setDurationMinutes(10)
                  .build()));
    } else {
      return exposureNotificationClient.getExposureInformation();
    }
  }

  public Task<Void> resetAllData() {
    return exposureNotificationClient.resetAllData();
  }

  public Task<Void> resetTemporaryExposureKey() {
    return exposureNotificationClient.resetTemporaryExposureKey();
  }

}
