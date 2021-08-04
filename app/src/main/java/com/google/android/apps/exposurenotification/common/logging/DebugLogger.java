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

import android.util.Log;

/**
 * Logger intended for use in debug builds of the application. Logs will be
 * written to logcat logs using the shared tag:
 * <code>ExposureNotificationsExpress</code>.
 */
public class DebugLogger extends Logger {

  protected DebugLogger(String tag, String secondaryTag) {
    super(tag, secondaryTag);
  }

  public void d(String message) {
    Log.d(tag, getTaggedMessage(message));
  }

  public void d(String message, Throwable tr) {
    Log.d(tag, getTaggedMessage(message), tr);
  }

  public void i(String message) {
    Log.i(tag, getTaggedMessage(message));
  }

  public void i(String message, Throwable tr) {
    Log.i(tag, getTaggedMessage(message), tr);
  }

  public void w(String message) {
    Log.w(tag, getTaggedMessage(message));
  }

  public void w(String message, Throwable tr) {
    Log.w(tag, getTaggedMessage(message), tr);
  }

  public void e(String message) {
    Log.e(tag, getTaggedMessage(message));
  }

  public void e(String message, Throwable tr) {
    Log.e(tag, getTaggedMessage(message), tr);
  }
}
