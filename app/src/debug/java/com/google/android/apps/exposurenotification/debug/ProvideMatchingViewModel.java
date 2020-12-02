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
import android.util.Log;
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
import com.google.android.apps.exposurenotification.keydownload.KeyFile;
import com.google.android.apps.exposurenotification.nearby.DiagnosisKeyFileSubmitter;
import com.google.android.apps.exposurenotification.nearby.ExposureNotificationClientWrapper;
import com.google.android.apps.exposurenotification.proto.SignatureInfo;
import com.google.android.gms.nearby.exposurenotification.TemporaryExposureKey;
import com.google.android.gms.nearby.exposurenotification.TemporaryExposureKey.TemporaryExposureKeyBuilder;
import com.google.auto.value.AutoValue;
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

  private static final String TAG = "ProvideKeysViewModel";

  private static final BaseEncoding BASE16 = BaseEncoding.base16().lowerCase();
  private static final Duration IS_ENABLED_TIMEOUT = Duration.ofSeconds(10);

  private final MutableLiveData<String> singleInputKeyLiveData;
  private final MutableLiveData<Integer> singleInputIntervalNumberLiveData;
  private final MutableLiveData<Integer> singleInputRollingPeriodLiveData;
  private final MutableLiveData<Integer> singleInputTransmissionRiskLevelLiveData;

  private static SingleLiveEvent<String> snackbarLiveEvent = new SingleLiveEvent<>();

  private final MutableLiveData<SigningKeyInfo> keyInfoLiveData;

  private final ExposureNotificationClientWrapper exposureNotificationClientWrapper;
  private final KeyFileSigner keyFileSigner;
  private final KeyFileWriter keyFileWriter;
  private final DiagnosisKeyFileSubmitter diagnosisKeyFileSubmitter;
  private final Resources resources;
  private final String packageName;
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
    keyFileSigner = KeyFileSigner.get();
    keyFileWriter = new KeyFileWriter(context);
    resources = context.getResources();
    packageName = context.getPackageName();

    singleInputKeyLiveData = new MutableLiveData<>("");
    singleInputIntervalNumberLiveData = new MutableLiveData<>(0);
    singleInputRollingPeriodLiveData = new MutableLiveData<>(144);
    singleInputTransmissionRiskLevelLiveData = new MutableLiveData<>(0);
    keyInfoLiveData = new MutableLiveData<>();
    // The keyfile signing key info doesn't change throughout the run of the app.
    setSigningKeyInfo();
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

  public SingleLiveEvent<String> getSnackbarLiveEvent() {
    return snackbarLiveEvent;
  }

  public LiveData<SigningKeyInfo> getSigningKeyInfoLiveData() {
    return keyInfoLiveData;
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
    Log.d(TAG, "Submitting " + key);

    TemporaryExposureKey temporaryExposureKey;
    try {
      temporaryExposureKey =
          new TemporaryExposureKeyBuilder()
              .setKeyData(BASE16.decode(key))
              .setRollingPeriod(getSingleInputRollingPeriodLiveData().getValue())
              .setTransmissionRiskLevel(getSingleInputTransmissionRiskLevelLiveData().getValue())
              .setRollingStartIntervalNumber(getSingleInputIntervalNumberLiveData().getValue())
              .build();
    } catch (IllegalArgumentException e) {
      Log.e(TAG, "Error creating TemporaryExposureKey", e);
      snackbarLiveEvent.postValue(resources.getString(R.string.debug_matching_single_error));
      return;
    }
    Log.d(TAG, "Composed " + temporaryExposureKey + " for submission.");

    if (!isSingleInputTemporaryExposureKeyValid(temporaryExposureKey)) {
      snackbarLiveEvent.postValue(resources.getString(R.string.debug_matching_single_error));
      return;
    }
    List<TemporaryExposureKey> keys = Lists.newArrayList(temporaryExposureKey);

    Log.d(TAG, "Creating keyfile...");
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
    Log.d(TAG, String.format("About to provide %d key files.", files.size()));

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
              Log.w(TAG, "Error, isEnabled is false", x);
              return null;
            },
            backgroundExecutor)
        .catching(
            Exception.class,
            x -> {
              snackbarLiveEvent.postValue(
                  resources.getString(R.string.debug_matching_provide_error_unknown));
              Log.w(TAG, "Unknown exception when providing", x);
              return null;
            },
            backgroundExecutor);
  }

  private void setSigningKeyInfo() {
    SignatureInfo signatureInfo = keyFileSigner.signatureInfo();
    SigningKeyInfo info =
        SigningKeyInfo.newBuilder()
            .setPackageName(packageName)
            .setKeyVersion(signatureInfo.getVerificationKeyVersion())
            .setKeyId(signatureInfo.getVerificationKeyId())
            .setPublicKeyBase64(keyFileSigner.getPublicKeyBase64())
            .build();
    keyInfoLiveData.postValue(info);
  }

  @AutoValue
  abstract static class SigningKeyInfo {

    abstract String packageName();

    abstract String keyId();

    abstract String keyVersion();

    abstract String publicKeyBase64();

    static Builder newBuilder() {
      return new AutoValue_ProvideMatchingViewModel_SigningKeyInfo.Builder();
    }

    @AutoValue.Builder
    abstract static class Builder {

      abstract Builder setPackageName(String p);

      abstract Builder setKeyId(String p);

      abstract Builder setKeyVersion(String p);

      abstract Builder setPublicKeyBase64(String p);

      abstract SigningKeyInfo build();
    }
  }
}
