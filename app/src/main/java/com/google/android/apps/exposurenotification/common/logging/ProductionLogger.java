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

package com.google.android.apps.exposurenotification.common.logging;

/**
 * Logger intended for use in production builds of the application. Logs will
 * not be written in production.
 */
public class ProductionLogger extends Logger {

  protected ProductionLogger(String tag, String secondaryTag) {
    super(tag, secondaryTag);
  }

  public void d(String message) {
    // Do nothing.
  }

  public void d(String message, Throwable tr) {
    // Do nothing.
  }

  public void i(String message) {
    // Do nothing.
  }

  public void i(String message, Throwable tr) {
    // Do nothing.
  }

  public void w(String message) {
    // Do nothing.
  }

  public void w(String message, Throwable tr) {
    // Do nothing.
  }

  public void e(String message) {
    // Do nothing.
  }

  public void e(String message, Throwable tr) {
    // Do nothing.
  }
}
