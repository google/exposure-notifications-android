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

package com.google.android.apps.exposurenotification.notify;

import android.app.Application;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.AppExecutors;
import com.google.android.apps.exposurenotification.common.SingleLiveEvent;
import com.google.android.apps.exposurenotification.common.TaskToFutureAdapter;
import com.google.android.apps.exposurenotification.nearby.ExposureNotificationClientWrapper;
import com.google.android.apps.exposurenotification.network.DiagnosisKey;
import com.google.android.apps.exposurenotification.network.DiagnosisKeys;
import com.google.android.apps.exposurenotification.storage.PositiveDiagnosisEntity;
import com.google.android.apps.exposurenotification.storage.PositiveDiagnosisRepository;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.nearby.exposurenotification.ExposureNotificationStatusCodes;
import com.google.android.gms.nearby.exposurenotification.TemporaryExposureKey;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;
import org.threeten.bp.Duration;
import org.threeten.bp.Instant;
import org.threeten.bp.ZonedDateTime;

/** View model for {@link ShareDiagnosisActivity} and fragments. */
public class ShareDiagnosisViewModel extends AndroidViewModel {

  private static final String TAG = "ShareDiagnosisViewModel";

  public static final long NO_EXISTING_ID = -1;
  private static final Duration GET_TEKS_TIMEOUT = Duration.ofSeconds(10);

  private final PositiveDiagnosisRepository repository;

  private final MutableLiveData<String> testIdentifierLiveData = new MutableLiveData<>();
  private final MutableLiveData<ZonedDateTime> testTimestampLiveData = new MutableLiveData<>();
  private final MutableLiveData<Long> existingIdLiveData = new MutableLiveData<>(NO_EXISTING_ID);
  private final MutableLiveData<Boolean> inFlightResolutionLiveData = new MutableLiveData<>(false);

  private final SingleLiveEvent<Void> deletedLiveEvent = new SingleLiveEvent<>();
  private final SingleLiveEvent<String> snackbarLiveEvent = new SingleLiveEvent<>();
  private final SingleLiveEvent<ApiException> resolutionRequiredLiveEvent = new SingleLiveEvent<>();
  private final SingleLiveEvent<Boolean> sharedLiveEvent = new SingleLiveEvent<>();
  private final SingleLiveEvent<Boolean> savedLiveEvent = new SingleLiveEvent<>();

  public ShareDiagnosisViewModel(Application application) {
    super(application);
    repository = new PositiveDiagnosisRepository(application);
  }

  @NonNull
  public LiveData<String> getTestIdentifierLiveData() {
    return testIdentifierLiveData;
  }

  public void setTestIdentifier(String testIdentifier) {
    testIdentifierLiveData.setValue(testIdentifier);
  }

  @NonNull
  public LiveData<ZonedDateTime> getTestTimestampLiveData() {
    return Transformations.distinctUntilChanged(testTimestampLiveData);
  }

  public void onTestTimestampChanged(@NonNull ZonedDateTime testTimestamp) {
    if (Instant.now().isAfter(testTimestamp.toInstant())) {
      // If the value given is in the past we can use the value
      testTimestampLiveData.setValue(testTimestamp);
    }
  }

  @NonNull
  public LiveData<Long> getExistingIdLiveData() {
    return existingIdLiveData;
  }

  public void setExistingId(long existingId) {
    existingIdLiveData.setValue(existingId);
  }

  @NonNull
  public LiveData<Boolean> getInFlightResolutionLiveData() {
    return inFlightResolutionLiveData;
  }

  public void setInflightResolution(boolean inFlightResolution) {
    inFlightResolutionLiveData.setValue(inFlightResolution);
  }

  @NonNull
  public LiveData<PositiveDiagnosisEntity> getByIdLiveData(long id) {
    // TODO: cache this locally.
    return repository.getByIdLiveData(id);
  }

  /**
   * A {@link SingleLiveEvent} to trigger a snackbar.
   */
  public SingleLiveEvent<String> getSnackbarSingleLiveEvent() {
    return snackbarLiveEvent;
  }

  /**
   * A {@link SingleLiveEvent} that signifies a successful deletion.
   */
  public SingleLiveEvent<Void> getDeletedSingleLiveEvent() {
    return deletedLiveEvent;
  }

  /**
   * A {@link SingleLiveEvent} that returns {@link ApiException} to help with starting the
   * resolution.
   */
  public SingleLiveEvent<ApiException> getResolutionRequiredLiveEvent() {
    return resolutionRequiredLiveEvent;
  }

  /**
   * A {@link SingleLiveEvent} that returns {@link Boolean} to show whether the data was shared.
   */
  public SingleLiveEvent<Boolean> getSharedLiveEvent() {
    return sharedLiveEvent;
  }

  /**
   * A {@link SingleLiveEvent} that returns whether the
   */
  public SingleLiveEvent<Boolean> getSavedLiveEvent() {
    return savedLiveEvent;
  }

  /**
   * Deletes a given entity
   */
  public void deleteEntity(PositiveDiagnosisEntity positiveDiagnosisEntity) {
    Futures.addCallback(
        repository.deleteByIdAsync(positiveDiagnosisEntity.getId()),
        new FutureCallback<Void>() {
          @Override
          public void onSuccess(@NullableDecl Void result) {
            deletedLiveEvent.postCall();
          }

          @Override
          public void onFailure(Throwable t) {
            Log.w(TAG, "Failed to delete", t);
          }
        },
        AppExecutors.getLightweightExecutor());
  }

  /**
   * Share the keys.
   */
  public void share() {
    FluentFuture<Boolean> getKeysAndSubmitToService =
        FluentFuture.from(getRecentKeys())
            .transformAsync(this::submitKeysToService, AppExecutors.getBackgroundExecutor());

    Futures.addCallback(
        getKeysAndSubmitToService,
        new FutureCallback<Boolean>() {
          @Override
          public void onSuccess(Boolean shared) {
            sharedLiveEvent.postValue(shared);
          }

          @Override
          public void onFailure(Throwable exception) {
            if (!(exception instanceof ApiException)) {
              Log.e(TAG, "Unknown error", exception);
              snackbarLiveEvent.postValue(
                  getApplication().getString(R.string.generic_error_message));
              return;
            }
            ApiException apiException = (ApiException) exception;
            if (apiException.getStatusCode()
                == ExposureNotificationStatusCodes.RESOLUTION_REQUIRED) {
              resolutionRequiredLiveEvent.postValue(apiException);
            } else {
              Log.w(TAG, "No RESOLUTION_REQUIRED in result", apiException);
              snackbarLiveEvent.postValue(
                  getApplication().getString(R.string.generic_error_message));;
            }
          }
        },
        AppExecutors.getLightweightExecutor());
  }

  /**
   * Performs the save operation and whether to mark as shared or not.
   */
  public void save(boolean shared) {
    Futures.addCallback(
        insertOrUpdateDiagnosis(shared),
        new FutureCallback<Void>() {
          @Override
          public void onSuccess(@NullableDecl Void result) {
            savedLiveEvent.postValue(shared);
          }

          @Override
          public void onFailure(Throwable t) {
            snackbarLiveEvent.postValue(getApplication().getString(R.string.generic_error_message));
          }
        },
        AppExecutors.getLightweightExecutor());
  }

  /** Inserts current diagnosis into the local database with a shared state. */
  private ListenableFuture<Void> insertOrUpdateDiagnosis(boolean shared) {
    long positiveDiagnosisId = existingIdLiveData.getValue();
    if (positiveDiagnosisId == NO_EXISTING_ID) {
      // Add flow so add the entity
      return repository.upsertAsync(
          PositiveDiagnosisEntity.create(testTimestampLiveData.getValue(), shared));
    } else {
      // Update flow so just update the shared status
      return repository.markSharedForIdAsync(positiveDiagnosisId, shared);
    }
  }

  /** Gets recent (initially 14 days) Temporary Exposure Keys from Google Play Services. */
  private ListenableFuture<List<TemporaryExposureKey>> getRecentKeys() {
    return TaskToFutureAdapter.getFutureWithTimeout(
        ExposureNotificationClientWrapper.get(getApplication()).getTemporaryExposureKeyHistory(),
        GET_TEKS_TIMEOUT.toMillis(),
        TimeUnit.MILLISECONDS,
        AppExecutors.getScheduledExecutor());
  }

  /**
   * Submits the given Temporary Exposure Keys to the key sharing service, designating them as
   * Diagnosis Keys.
   *
   * @return a {@link ListenableFuture} of type {@link Boolean} of successfully submitted state
   */
  private ListenableFuture<Boolean> submitKeysToService(List<TemporaryExposureKey> recentKeys) {
    ImmutableList.Builder<DiagnosisKey> builder = new Builder<>();
    for (TemporaryExposureKey k : recentKeys) {
      builder.add(
          DiagnosisKey.newBuilder()
              .setKeyBytes(k.getKeyData())
              .setIntervalNumber(k.getRollingStartIntervalNumber())
              .build());
    }
    return FluentFuture.from(new DiagnosisKeys(getApplication()).upload(builder.build()))
        .transform(
            v -> {
              // Successfully submitted
              return true;
            },
            AppExecutors.getLightweightExecutor())
        .catching(
            ApiException.class,
            (e) -> {
              // Not successfully submitted,
              return false;
            },
            AppExecutors.getLightweightExecutor());
  }
}