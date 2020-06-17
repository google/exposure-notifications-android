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

package com.google.android.apps.exposurenotification.exposure;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.google.android.apps.exposurenotification.common.AppExecutors;
import com.google.android.apps.exposurenotification.common.TaskToFutureAdapter;
import com.google.android.apps.exposurenotification.nearby.ExposureNotificationClientWrapper;
import com.google.android.apps.exposurenotification.storage.ExposureEntity;
import com.google.android.apps.exposurenotification.storage.ExposureRepository;
import com.google.android.apps.exposurenotification.storage.TokenEntity;
import com.google.android.apps.exposurenotification.storage.TokenRepository;
import com.google.android.gms.nearby.exposurenotification.ExposureInformation;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.threeten.bp.Duration;

/** View model for the {@link ExposureHomeFragment}. */
public class ExposureHomeViewModel extends AndroidViewModel {
  private static final Duration GET_EXPOSURE_INFO_TIMEOUT = Duration.ofSeconds(30);

  private final ExposureRepository exposureRepository;
  private final TokenRepository tokenRepository;

  private final MutableLiveData<Boolean> isEnabledLiveData;
  private final LiveData<List<ExposureEntity>> getAllLiveData;

  public ExposureHomeViewModel(@NonNull Application application) {
    super(application);
    exposureRepository = new ExposureRepository(application);
    tokenRepository = new TokenRepository(application);
    getAllLiveData = exposureRepository.getAllLiveData();
    isEnabledLiveData = new MutableLiveData<>(false);
  }

  public LiveData<List<ExposureEntity>> getAllExposureEntityLiveData() {
    return getAllLiveData;
  }

}
