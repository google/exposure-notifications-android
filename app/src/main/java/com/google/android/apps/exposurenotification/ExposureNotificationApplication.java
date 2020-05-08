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

package com.google.android.apps.exposurenotification;

import androidx.multidex.MultiDexApplication;
import com.google.android.apps.exposurenotification.activities.ExposureNotificationActivity;
import com.google.android.apps.exposurenotification.nearby.ProvideDiagnosisKeysWorker;
import com.jakewharton.threetenabp.AndroidThreeTen;

/**
 * ExposureNotificationApplication is instantiated whenever the app is running.
 *
 * <p>For UI see {@link ExposureNotificationActivity}
 */
public final class ExposureNotificationApplication extends MultiDexApplication {

  @Override
  public void onCreate() {
    super.onCreate();
    AndroidThreeTen.init(this);
  }
}
