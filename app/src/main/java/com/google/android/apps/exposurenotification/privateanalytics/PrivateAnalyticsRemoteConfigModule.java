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
import android.net.Uri;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.privateanalytics.Qualifiers.RemoteConfigUri;
import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.components.ApplicationComponent;
import dagger.hilt.android.qualifiers.ApplicationContext;

@Module
@InstallIn(ApplicationComponent.class)
public class PrivateAnalyticsRemoteConfigModule {
  @Provides
  @RemoteConfigUri
  public Uri provideRemoteConfigUri(@ApplicationContext Context context) {
    return Uri.parse(context.getString(R.string.enx_enpaRemoteConfigURL));
  }
}
