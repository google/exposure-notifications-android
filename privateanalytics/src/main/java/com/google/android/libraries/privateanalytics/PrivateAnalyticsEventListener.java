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

package com.google.android.libraries.privateanalytics;

/**
 * Listener interface that can be implemented if it is relevant to observe asynchronous events
 * triggered by the SDK (analytics worker starting, or status of the network request for remote
 * config).
 */
public interface PrivateAnalyticsEventListener {

  void onPrivateAnalyticsWorkerTaskStarted();

  void onPrivateAnalyticsRemoteConfigCallSuccess(int length);

  void onPrivateAnalyticsRemoteConfigCallFailure(Exception err);
}
