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

import static com.google.android.apps.exposurenotification.nearby.ProvideDiagnosisKeysWorker.DEFAULT_API_TIMEOUT;

import android.app.Application;
import android.text.TextUtils;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.AppExecutors;
import com.google.android.apps.exposurenotification.common.SingleLiveEvent;
import com.google.android.apps.exposurenotification.common.TaskToFutureAdapter;
import com.google.android.apps.exposurenotification.debug.TemporaryExposureKeyEncodingHelper.DecodeException;
import com.google.android.apps.exposurenotification.nearby.DiagnosisKeyFileSubmitter;
import com.google.android.apps.exposurenotification.nearby.ExposureNotificationClientWrapper;
import com.google.android.apps.exposurenotification.network.KeyFileBatch;
import com.google.android.apps.exposurenotification.storage.TokenEntity;
import com.google.android.apps.exposurenotification.storage.TokenRepository;
import com.google.android.gms.nearby.exposurenotification.TemporaryExposureKey;
import com.google.android.gms.nearby.exposurenotification.TemporaryExposureKey.TemporaryExposureKeyBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.io.BaseEncoding;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import java.io.File;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.threeten.bp.Duration;
import org.threeten.bp.Instant;

/** View model for {@link ProvideMatchingFragment}. */
public class ProvideMatchingViewModel extends AndroidViewModel {

  private static final String TAG = "ProvideKeysViewModel";

  private static final BaseEncoding BASE16 = BaseEncoding.base16().lowerCase();

  private final MutableLiveData<Integer> displayedChildLiveData;
  private final MutableLiveData<String> singleInputKeyLiveData;
  private final MutableLiveData<Integer> singleInputIntervalNumberLiveData;
  private final MutableLiveData<Integer> singleInputRollingPeriodLiveData;
  private final MutableLiveData<Integer> singleInputTransmissionRiskLevelLiveData;
  private final MutableLiveData<String> batchInputLiveData;
  private final MutableLiveData<File> fileInputLiveData;
  private final MutableLiveData<String> tokenLiveData;

  private static SingleLiveEvent<String> snackbarLiveEvent = new SingleLiveEvent<>();

  public ProvideMatchingViewModel(@NonNull Application application) {
    super(application);
    displayedChildLiveData = new MutableLiveData<>(0);
    singleInputKeyLiveData = new MutableLiveData<>("");
    singleInputIntervalNumberLiveData =
        new MutableLiveData<>((int) (System.currentTimeMillis() / (10 * 60 * 1000L)));
    singleInputRollingPeriodLiveData = new MutableLiveData<>(144);
    singleInputTransmissionRiskLevelLiveData = new MutableLiveData<>(0);
    batchInputLiveData = new MutableLiveData<>("");
    tokenLiveData = new MutableLiveData<>("");
    fileInputLiveData = new MutableLiveData<>(null);
  }

  public LiveData<Integer> getDisplayedChildLiveData() {
    return displayedChildLiveData;
  }

  public void setDisplayedChild(int displayedChild) {
    displayedChildLiveData.setValue(displayedChild);
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

  public LiveData<String> getBatchInputLiveData() {
    return batchInputLiveData;
  }

  public void setBatchInput(String batchInput) {
    batchInputLiveData.setValue(batchInput);
  }

  public LiveData<File> getFileInputLiveData() {
    return fileInputLiveData;
  }

  public void setFileInput(File file) {
    fileInputLiveData.setValue(file);
  }

  public LiveData<String> getTokenLiveData() {
    return tokenLiveData;
  }

  public void setToken(String token) {
    tokenLiveData.setValue(token);
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

  private boolean isFileInputValid(File file) {
    return file != null;
  }

  private boolean isTokenValid(String token) {
    return !TextUtils.isEmpty(token);
  }

  public void provideSingleAction() {
    String key = getSingleInputKeyLiveData().getValue();

    KeyFileWriter keyFileWriter = new KeyFileWriter(getApplication());
    TemporaryExposureKey temporaryExposureKey;
    try {
      temporaryExposureKey =
        new TemporaryExposureKeyBuilder()
            .setKeyData(BASE16.decode(key))
            .setRollingPeriod(getSingleInputRollingPeriodLiveData().getValue())
            .setTransmissionRiskLevel(getSingleInputTransmissionRiskLevelLiveData().getValue())
            .setRollingStartIntervalNumber(getSingleInputIntervalNumberLiveData().getValue())
            .build();
    } catch(IllegalArgumentException e) {
      Log.e(TAG, "Error creating TemporaryExposureKey", e);
      snackbarLiveEvent.postValue(getApplication().getString(R.string.debug_matching_single_error));
      return;
    }

    if (!isSingleInputTemporaryExposureKeyValid(temporaryExposureKey)) {
      snackbarLiveEvent.postValue(getApplication().getString(R.string.debug_matching_single_error));
      return;
    }
    List<TemporaryExposureKey> keys = Lists.newArrayList(temporaryExposureKey);

    List<File> files =
        keyFileWriter.writeForKeys(
            keys, Instant.now().minus(Duration.ofDays(14)), Instant.now(), "GB");

    String encodedToken = getTokenLiveData().getValue();
    provideFiles(files, encodedToken);
  }

  public void provideBatchAction() {
    KeyFileWriter keyFileWriter = new KeyFileWriter(getApplication());
    try {
      List<TemporaryExposureKey> keys =
          TemporaryExposureKeyEncodingHelper.decodeList(getBatchInputLiveData().getValue());
      List<File> files =
          keyFileWriter.writeForKeys(
              keys, Instant.now().minus(Duration.ofDays(14)), Instant.now(), "GB");

      String encodedToken = getTokenLiveData().getValue();
      provideFiles(files, encodedToken);
    } catch (DecodeException e) {
      Log.e(TAG, "Error decoding", e);
      snackbarLiveEvent.postValue(getApplication().getString(R.string.debug_matching_batch_error));
    }
  }

  public void provideFileAction() {
    File file = getFileInputLiveData().getValue();
    if (!isFileInputValid(file)) {
      snackbarLiveEvent.postValue(getApplication().getString(R.string.debug_matching_file_error));
      return;
    }

    List<File> files = Lists.newArrayList(file);

    String encodedToken = getTokenLiveData().getValue();
    provideFiles(files, encodedToken);
  }

  static class NotEnabledException extends Exception {}

  private void provideFiles(List<File> files, String token) {
    if (!isTokenValid(token)) {
      snackbarLiveEvent.postValue(getApplication().getString(R.string.debug_matching_token_error));
      return;
    }
    Log.d(TAG, String.format("About to provide %d key files.", files.size()));
    DiagnosisKeyFileSubmitter submitter = new DiagnosisKeyFileSubmitter(getApplication());
    TokenRepository repository = new TokenRepository(getApplication());

    KeyFileBatch batch = KeyFileBatch.ofFiles("US", 1, files);

    FluentFuture.from(
            TaskToFutureAdapter.getFutureWithTimeout(
                ExposureNotificationClientWrapper.get(getApplication()).isEnabled(),
                DEFAULT_API_TIMEOUT.toMillis(),
                TimeUnit.MILLISECONDS,
                AppExecutors.getScheduledExecutor()))
        .transformAsync(
            (isEnabled) -> {
              // Only continue if it is enabled.
              if (isEnabled) {
                return Futures.immediateFuture(ImmutableList.of(batch));
              } else {
                return Futures.immediateFailedFuture(new NotEnabledException());
              }
            },
            AppExecutors.getBackgroundExecutor())
        .transformAsync(
            batches -> submitter.submitFiles(batches, token), AppExecutors.getBackgroundExecutor())
        .transformAsync(
            done -> repository.upsertAsync(TokenEntity.create(token, false)),
            AppExecutors.getBackgroundExecutor())
        .transform(
            done -> {
              snackbarLiveEvent.postValue(
                  getApplication().getString(R.string.debug_matching_provide_success));
              return null;
            },
            AppExecutors.getLightweightExecutor())
        .catching(
            NotEnabledException.class,
            x -> {
              snackbarLiveEvent.postValue(
                  getApplication().getString(R.string.debug_matching_provide_error_disabled));
              Log.w(TAG, "Error, isEnabled is false", x);
              return null;
            },
            AppExecutors.getBackgroundExecutor())
        .catching(
            Exception.class,
            x -> {
              snackbarLiveEvent.postValue(
                  getApplication().getString(R.string.debug_matching_provide_error_unknown));
              Log.w(TAG, "Unknown exception when providing", x);
              return null;
            },
            AppExecutors.getBackgroundExecutor());
  }
}
