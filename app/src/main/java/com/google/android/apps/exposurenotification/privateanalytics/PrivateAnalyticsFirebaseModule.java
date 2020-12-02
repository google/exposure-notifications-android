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
import androidx.annotation.Nullable;
import com.google.firebase.FirebaseApp;
import com.google.firebase.firestore.FirebaseFirestore;
import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.components.ApplicationComponent;
import dagger.hilt.android.qualifiers.ApplicationContext;
import javax.inject.Singleton;

@Module
@InstallIn(ApplicationComponent.class)
public class PrivateAnalyticsFirebaseModule {

  @Provides
  @Nullable
  @Singleton
  public FirebaseApp provideFirebaseApp(@ApplicationContext Context context) {
    if (!PrivateAnalyticsSettingsUtil.isPrivateAnalyticsSupported()) {
      return null;
    }
    //This call is idempotent
    return FirebaseApp.initializeApp(context);
  }

  @Provides
  @Singleton
  @Nullable
  public FirebaseFirestore provideFirebaseFirestore(@Nullable FirebaseApp firebaseApp) {
    if (firebaseApp == null) {
      return null;
    }
    return FirebaseFirestore.getInstance();
  }
}
