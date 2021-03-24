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

package com.google.android.apps.exposurenotification.network;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.BasicNetwork;
import com.android.volley.toolbox.HurlStack;
import com.android.volley.toolbox.NoCache;
import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.components.ApplicationComponent;
import dagger.hilt.components.SingletonComponent;
import javax.inject.Singleton;

/**
 * Module to provide the real Volley {@link RequestQueue} (well, {@link RequestQueueWrapper} really)
 * so that tests can easily exclude it and provide a fake.
 */
@Module
@InstallIn(SingletonComponent.class)
public final class RealRequestQueueModule {

  @Singleton
  @Provides
  public RequestQueueWrapper provideRequestQueueWrapper() {
    RequestQueue queue = new RequestQueue(new NoCache(), new BasicNetwork(new HurlStack()));
    queue.start();
    return RequestQueueWrapper.wrapping(queue);
  }
}
