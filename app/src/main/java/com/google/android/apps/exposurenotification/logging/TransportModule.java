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

package com.google.android.apps.exposurenotification.logging;

import android.content.Context;
import com.google.android.apps.exposurenotification.BuildConfig;
import com.google.android.apps.exposurenotification.proto.EnxLogExtension;
import com.google.android.datatransport.Encoding;
import com.google.android.datatransport.Transport;
import com.google.android.datatransport.TransportFactory;
import com.google.android.datatransport.cct.CCTDestination;
import com.google.android.datatransport.runtime.TransportRuntime;
import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;
import javax.inject.Singleton;

/**
 * Provides AnalyticLogger depending on build configuration
 */
@Module
@InstallIn(SingletonComponent.class)
public class TransportModule {

  @Provides
  @Singleton
  synchronized Transport<EnxLogExtension> provideFirelogTransport(
      @ApplicationContext Context context) {
    TransportRuntime.initialize(context);
    TransportFactory factory = TransportRuntime.getInstance()
        .newFactory(CCTDestination.LEGACY_INSTANCE);
    return factory
        .getTransport(BuildConfig.LOGSOURCE_ID, EnxLogExtension.class, Encoding.of("proto"),
            EnxLogExtension::toByteArray);
  }
}

