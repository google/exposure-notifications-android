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

package com.google.android.apps.exposurenotification.keydownload;


import android.content.Context;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.keydownload.Qualifiers.HomeDownloadUriPair;
import com.google.android.apps.exposurenotification.keydownload.Qualifiers.TravellerDownloadUriPairs;
import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.components.ApplicationComponent;
import dagger.hilt.android.qualifiers.ApplicationContext;
import java.util.List;
import java.util.Map;

/**
 * Module providing production locations of key download URIs.
 *
 * <p>Tests that do file downloads may want to exclude this module and supply their own URIs.
 */
@Module
@InstallIn(ApplicationComponent.class)
public class DownloadUrisModule {

  @Provides
  @HomeDownloadUriPair
  public DownloadUriPair provideHomeDownloads(@ApplicationContext Context context) {
    return DownloadUriPair.create(
        context.getString(R.string.enx_tekLocalDownloadIndexFile),
        context.getString(R.string.enx_tekLocalDownloadBasePath));
  }

  @Provides
  @TravellerDownloadUriPairs
  public Map<String, List<DownloadUriPair>> provideTravellerDownloads(
      @ApplicationContext Context context) {
    return RoamingConfigParser.parse(context.getString(R.string.enx_tekRoamingUrls));
  }
}
