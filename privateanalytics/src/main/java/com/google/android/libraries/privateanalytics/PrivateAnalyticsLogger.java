/*
 * Copyright 2021 Google LLC
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

package com.google.android.libraries.privateanalytics;

/**
 * Interface that allows to abstracts local logging during app execution.
 */
public interface PrivateAnalyticsLogger {

  interface Factory {

    PrivateAnalyticsLogger create(String tag);
  }

  void d(String msg);

  void i(String msg);

  void w(String msg);

  void w(String msg, Throwable throwable);

  void e(String msg);

  void e(String msg, Throwable throwable);
}
