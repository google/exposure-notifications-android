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

package com.google.android.libraries.privateanalytics.testsupport;

import android.util.Log;
import com.google.android.libraries.privateanalytics.PrivateAnalyticsEventListener;

/**
 * A {@code PrivateAnalyticsEventListener} instance that just prints the events to Logcat.
 */
public class LogcatEventListener implements PrivateAnalyticsEventListener {

  private static final String TAG = "PALogger";

  @Override
  public void onPrivateAnalyticsWorkerTaskStarted() {
    Log.i(TAG, "PA worker task started.");
  }

  @Override
  public void onPrivateAnalyticsRemoteConfigCallSuccess(int length) {
    Log.i(TAG, "PA Remote Config call succeeded");
  }

  @Override
  public void onPrivateAnalyticsRemoteConfigCallFailure(Exception err) {
    Log.e(TAG, "PA Remote Config call failed with server error", err);
  }
}
