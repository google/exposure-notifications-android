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

package com.google.android.apps.exposurenotification.common;

import android.content.Context;
import android.location.LocationManager;
import android.telephony.TelephonyManager;
import androidx.work.WorkManager;
import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.components.ApplicationComponent;
import dagger.hilt.android.qualifiers.ApplicationContext;
import java.security.SecureRandom;
import javax.inject.Singleton;

@Module
@InstallIn(ApplicationComponent.class)
public class CommonModule {

  @Provides
  public LocationManager provideLocationManager(@ApplicationContext Context context) {
    return (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
  }

  @Provides
  public TelephonyManager provideTelephonyManager(@ApplicationContext Context context) {
    return (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
  }

  @Singleton
  @Provides
  public SecureRandom provideSecureRandom(){
    return new SecureRandom();
  }

  @Provides
  public NotificationHelper provideNotificationHelper() {
    return new NotificationHelper();
  }

}
