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

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.work.WorkManager;
import com.google.android.apps.exposurenotification.nearby.ProvideDiagnosisKeysWorker;
import com.google.android.apps.exposurenotification.storage.TokenEntity;
import com.google.android.apps.exposurenotification.storage.TokenRepository;
import java.util.List;

/** View model for the {@link TokenDebugActivity}. */
public class TokenDebugViewModel extends AndroidViewModel {

  private static final String TAG = "TokenDebugViewModel";

  private final TokenRepository tokenRepository;

  private final LiveData<List<TokenEntity>> tokenEntityLiveData;

  public TokenDebugViewModel(@NonNull Application application) {
    super(application);
    tokenRepository = new TokenRepository(application);
    tokenEntityLiveData = tokenRepository.getAllLiveData();
  }

  public LiveData<List<TokenEntity>> getTokenEntityLiveData() {
    return tokenEntityLiveData;
  }

}
