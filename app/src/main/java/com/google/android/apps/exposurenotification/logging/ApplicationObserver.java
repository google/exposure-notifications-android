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

package com.google.android.apps.exposurenotification.logging;

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.OnLifecycleEvent;
import com.google.android.apps.exposurenotification.proto.UiInteraction.EventType;

/**
 * Lifecycle observer that logs APP_OPENED every time the app is moved
 * back into foreground. This does not record activity transitions between the app,
 * but covers all calls into the app from settings or an exposure notification.
 */
public class ApplicationObserver implements LifecycleObserver {
  private final AnalyticsLogger analyticsLogger;

  ApplicationObserver(AnalyticsLogger analyticsLogger) {
    this.analyticsLogger = analyticsLogger;
  }

  public void observeLifecycle(LifecycleOwner lifecycleOwner) {
    lifecycleOwner.getLifecycle().addObserver(this);
  }

  @OnLifecycleEvent(Lifecycle.Event.ON_START)
  void onForeground() {
    analyticsLogger.logUiInteraction(EventType.APP_OPENED);
  }

}
