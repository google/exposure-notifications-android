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

import static com.google.android.apps.exposurenotification.notify.NotifyHomeFragment.DELETE_DIALOG_CLOSED;

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.hilt.lifecycle.ViewModelInject;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;
import com.google.android.apps.exposurenotification.common.Qualifiers.LightweightExecutor;
import com.google.android.apps.exposurenotification.common.SingleLiveEvent;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity;
import com.google.android.apps.exposurenotification.storage.DiagnosisRepository;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import java.util.List;
import java.util.concurrent.ExecutorService;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

/**
 * View model for the {@link NotifyHomeFragment}.
 */
public class NotifyHomeViewModel extends ViewModel {

  private static final String TAG = "NotifyHomeViewModel";

  private final SingleLiveEvent<Void> deletedLiveEvent = new SingleLiveEvent<>();
  private final LiveData<List<DiagnosisEntity>> getAllDiagnosisLiveData;
  private final DiagnosisRepository diagnosisRepository;
  private final ExecutorService lightweightExecutor;

  // Stores the position of a to-be-deleted diagnosis entity in the DiagnosisEntityAdapter to
  // preserve the delete dialog state upon rotations.
  private int deleteOpenPosition = DELETE_DIALOG_CLOSED;

  @ViewModelInject
  public NotifyHomeViewModel(
      DiagnosisRepository diagnosisRepository,
      @LightweightExecutor ExecutorService lightweightExecutor) {
    this.diagnosisRepository = diagnosisRepository;
    this.lightweightExecutor = lightweightExecutor;
    getAllDiagnosisLiveData = diagnosisRepository.getAllLiveData();
  }

  /**
   * A {@link LiveData} to track the list of all diagnosis entities.
   */
  public LiveData<List<DiagnosisEntity>> getAllDiagnosisEntityLiveData() {
    return getAllDiagnosisLiveData;
  }

  /**
   * A {@link SingleLiveEvent} that signifies a successful deletion.
   */
  public SingleLiveEvent<Void> getDeletedSingleLiveEvent() {
    return deletedLiveEvent;
  }

  /**
   * Deletes a given entity.
   */
  public void deleteEntity(DiagnosisEntity diagnosis) {
    Futures.addCallback(
        diagnosisRepository.deleteByIdAsync(diagnosis.getId()),
        new FutureCallback<Void>() {
          @Override
          public void onSuccess(@NullableDecl Void result) {
            deletedLiveEvent.postCall();
          }

          @Override
          public void onFailure(@NonNull Throwable t) {
            Log.w(TAG, "Failed to delete", t);
          }
        },
        lightweightExecutor);
  }

  public void setDeleteOpenPosition(int deleteOpenPosition) {
    this.deleteOpenPosition = deleteOpenPosition;
  }

  public int getDeleteOpenPosition() {
    return deleteOpenPosition;
  }
}
