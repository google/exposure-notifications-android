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

import com.android.volley.Request;
import com.android.volley.RequestQueue;

/**
 * A razor-thin wrapper to make testing code that uses Volley easier to test with fakes.
 */
public abstract class RequestQueueWrapper {

  /**
   * See {@link RequestQueue#add(Request)}
   */
  public abstract <T> Request<T> add(Request<T> request);

  public static RequestQueueWrapper wrapping(RequestQueue innerQueue) {
    return new RequestQueueWrapper() {
      @Override
      public <T> Request<T> add(Request<T> request) {
        return innerQueue.add(request);
      }
    };
  }
}
