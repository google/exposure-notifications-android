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

package com.google.android.apps.exposurenotification.debug;

import android.content.Context;
import android.content.res.Resources;
import androidx.hilt.lifecycle.ViewModelInject;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.Qualifiers.BackgroundExecutor;
import com.google.android.apps.exposurenotification.common.Qualifiers.LightweightExecutor;
import com.google.android.apps.exposurenotification.common.Qualifiers.ScheduledExecutor;
import com.google.android.apps.exposurenotification.common.SingleLiveEvent;
import com.google.android.apps.exposurenotification.common.TaskToFutureAdapter;
import com.google.android.apps.exposurenotification.common.logging.Logger;
import com.google.android.apps.exposurenotification.keydownload.KeyFile;
import com.google.android.apps.exposurenotification.nearby.DiagnosisKeyFileSubmitter;
import com.google.android.apps.exposurenotification.nearby.ExposureNotificationClientWrapper;
import com.google.android.gms.nearby.exposurenotification.TemporaryExposureKey;
import com.google.android.gms.nearby.exposurenotification.TemporaryExposureKey.TemporaryExposureKeyBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.io.BaseEncoding;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import dagger.hilt.android.qualifiers.ApplicationContext;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import org.threeten.bp.Duration;
import org.threeten.bp.Instant;

/**
 * View model for {@link ProvideMatchingFragment}.
 */
public class ProvideMatchingViewModel extends ViewModel {

  private static final Logger logger = Logger.getLogger("ProvideMatchingViewModel");

  private static final BaseEncoding BASE16 = BaseEncoding.base16().lowerCase();
  private static final Duration IS_ENABLED_TIMEOUT = Duration.ofSeconds(10);

  private final MutableLiveData<String> singleInputKeyLiveData;
  private final MutableLiveData<Integer> singleInputIntervalNumberLiveData;
  private final MutableLiveData<Integer> singleInputRollingPeriodLiveData;
  private final MutableLiveData<Integer> singleInputTransmissionRiskLevelLiveData;
  private final MutableLiveData<Integer> singleInputReportTypeLiveData;
  private final MutableLiveData<Integer> singleInputDaysSinceOnsetOfSymptomsLiveData;

  private final SingleLiveEvent<String> snackbarLiveEvent = new SingleLiveEvent<>();

  private final ExposureNotificationClientWrapper exposureNotificationClientWrapper;
  private final KeyFileWriter keyFileWriter;
  private final DiagnosisKeyFileSubmitter diagnosisKeyFileSubmitter;
  private final Resources resources;
  private final ExecutorService backgroundExecutor;
  private final ExecutorService lightweightExecutor;
  private final ScheduledExecutorService scheduledExecutor;

  @ViewModelInject
  public ProvideMatchingViewModel(@ApplicationContext Context context,
      ExposureNotificationClientWrapper exposureNotificationClientWrapper,
      DiagnosisKeyFileSubmitter diagnosisKeyFileSubmitter,
      @BackgroundExecutor ExecutorService backgroundExecutor,
      @LightweightExecutor ExecutorService lightweightExecutor,
      @ScheduledExecutor ScheduledExecutorService scheduledExecutor) {
    this.exposureNotificationClientWrapper = exposureNotificationClientWrapper;
    this.diagnosisKeyFileSubmitter = diagnosisKeyFileSubmitter;
    this.backgroundExecutor = backgroundExecutor;
    this.lightweightExecutor = lightweightExecutor;
    this.scheduledExecutor = scheduledExecutor;
    keyFileWriter = new KeyFileWriter(context);
    resources = context.getResources();

    singleInputKeyLiveData = new MutableLiveData<>("");
    singleInputIntervalNumberLiveData = new MutableLiveData<>(0);
    singleInputRollingPeriodLiveData = new MutableLiveData<>(144);
    singleInputTransmissionRiskLevelLiveData = new MutableLiveData<>(0);
    singleInputDaysSinceOnsetOfSymptomsLiveData = new MutableLiveData<>(0);
    singleInputReportTypeLiveData = new MutableLiveData<>(0);
  }

  public LiveData<String> getSingleInputKeyLiveData() {
    return singleInputKeyLiveData;
  }

  public void setSingleInputKey(String singleInputKey) {
    singleInputKeyLiveData.setValue(singleInputKey);
  }

  public LiveData<Integer> getSingleInputIntervalNumberLiveData() {
    return singleInputIntervalNumberLiveData;
  }

  public void setSingleInputIntervalNumber(int intervalNumber) {
    singleInputIntervalNumberLiveData.setValue(intervalNumber);
  }

  public LiveData<Integer> getSingleInputRollingPeriodLiveData() {
    return singleInputRollingPeriodLiveData;
  }

  public void setSingleInputRollingPeriod(int rollingPeriod) {
    singleInputRollingPeriodLiveData.setValue(rollingPeriod);
  }

  public LiveData<Integer> getSingleInputTransmissionRiskLevelLiveData() {
    return singleInputTransmissionRiskLevelLiveData;
  }

  public void setSingleInputTransmissionRiskLevel(int transmissionRiskLevel) {
    singleInputTransmissionRiskLevelLiveData.setValue(transmissionRiskLevel);
  }

  public LiveData<Integer> getSingleInputDaysSinceOnsetOfSymptomsLiveData() {
    return singleInputDaysSinceOnsetOfSymptomsLiveData;
  }

  public void setSingleInputDaysSinceOnsetOfSymptomsLiveData(int daysSinceOnsetOfSymptoms) {
    singleInputDaysSinceOnsetOfSymptomsLiveData.setValue(daysSinceOnsetOfSymptoms);
  }

  public LiveData<Integer> getSingleInputReportTypeLiveData() {
    return singleInputReportTypeLiveData;
  }

  public void setSingleInputReportTypeLiveData(int reportType) {
    singleInputReportTypeLiveData.setValue(reportType);
  }

  public SingleLiveEvent<String> getSnackbarLiveEvent() {
    return snackbarLiveEvent;
  }

  private boolean isSingleInputTemporaryExposureKeyValid(
      TemporaryExposureKey temporaryExposureKey) {
    return temporaryExposureKey.getRollingStartIntervalNumber() != 0
        && temporaryExposureKey.getKeyData() != null
        && temporaryExposureKey.getKeyData().length == 16
        && temporaryExposureKey.getRollingStartIntervalNumber() != 0
        && temporaryExposureKey.getRollingPeriod() != 0;
  }

  public void provideSingleAction() {
    String key = getSingleInputKeyLiveData().getValue();
    logger.d("Submitting " + key);

    TemporaryExposureKey temporaryExposureKey;
    try {
      temporaryExposureKey =
          new TemporaryExposureKeyBuilder()
              .setKeyData(BASE16.decode(key))
              .setRollingPeriod(getSingleInputRollingPeriodLiveData().getValue())
              .setTransmissionRiskLevel(getSingleInputTransmissionRiskLevelLiveData().getValue())
              .setRollingStartIntervalNumber(getSingleInputIntervalNumberLiveData().getValue())
              .setDaysSinceOnsetOfSymptoms(
                  getSingleInputDaysSinceOnsetOfSymptomsLiveData().getValue())
              .setReportType(getSingleInputReportTypeLiveData().getValue())
              .build();
    } catch (IllegalArgumentException e) {
      logger.e("Error creating TemporaryExposureKey", e);
      snackbarLiveEvent.postValue(resources.getString(R.string.debug_matching_single_error));
      return;
    }
    logger.d("Composed " + temporaryExposureKey + " for submission.");

    if (!isSingleInputTemporaryExposureKeyValid(temporaryExposureKey)) {
      snackbarLiveEvent.postValue(resources.getString(R.string.debug_matching_single_error));
      return;
    }
    List<TemporaryExposureKey> keys = Lists.newArrayList(temporaryExposureKey);

    logger.d("Creating keyfile...");
    List<File> files =
        keyFileWriter.writeForKeys(
            keys, Instant.now().minus(Duration.ofDays(14)), Instant.now(), "GB");

    List<KeyFile> keyFiles = new ArrayList<>();
    for (File f : files) {
      keyFiles.add(KeyFile.createNonProd(f));
    }

    provideFiles(keyFiles);
  }

  static class NotEnabledException extends Exception {

  }

  private void provideFiles(List<KeyFile> files) {
    logger.d(String.format("About to provide %d key files.", files.size()));

    FluentFuture<Object> unusedResult = FluentFuture.from(
        TaskToFutureAdapter.getFutureWithTimeout(
            exposureNotificationClientWrapper.isEnabled(),
            IS_ENABLED_TIMEOUT,
            scheduledExecutor))
        .transformAsync(
            (isEnabled) -> {
              // Only continue if it is enabled.
              if (isEnabled) {
                return Futures.immediateFuture(ImmutableList.copyOf(files));
              } else {
                return Futures.immediateFailedFuture(new NotEnabledException());
              }
            },
            backgroundExecutor)
        .transformAsync(
            diagnosisKeyFileSubmitter::submitFiles,
            backgroundExecutor)
        .transform(
            done -> {
              snackbarLiveEvent.postValue(
                  resources.getString(R.string.debug_matching_provide_success));
              return null;
            },
            lightweightExecutor)
        .catching(
            NotEnabledException.class,
            x -> {
              snackbarLiveEvent.postValue(
                  resources.getString(R.string.debug_matching_provide_error_disabled));
              logger.w("Error, isEnabled is false", x);
              return null;
            },
            backgroundExecutor)
        .catching(
            Exception.class,
            x -> {
              snackbarLiveEvent.postValue(
                  resources.getString(R.string.debug_matching_provide_error_unknown));
              logger.w("Unknown exception when providing", x);
              return null;
            },
            backgroundExecutor);
  }

}
