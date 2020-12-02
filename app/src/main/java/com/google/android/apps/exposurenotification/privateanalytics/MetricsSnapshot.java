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

import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences.NotificationInteraction;
import com.google.auto.value.AutoValue;
import org.threeten.bp.Instant;


/**
 * A value class for taking a snapshot of metrics at the current time. This is used to resolve any
 * race conditions as we modify / reset metrics during the async submission process.
 */
@AutoValue
public abstract class MetricsSnapshot {
  public abstract Instant exposureNotificationLastShownTime();

  public abstract int exposureNotificationLastShownClassification();

  public abstract Instant exposureNotificationLastInteractionTime();

  public abstract NotificationInteraction exposureNotificationLastInteractionType();

  public static MetricsSnapshot fromPreferences(ExposureNotificationSharedPreferences preferences) {
    return new AutoValue_MetricsSnapshot.Builder()
        .setExposureNotificationLastShownTime(preferences.getExposureNotificationLastShownTime())
        .setExposureNotificationLastShownClassification(preferences.getExposureNotificationLastShownClassification())
        .setExposureNotificationLastInteractionTime(preferences.getExposureNotificationLastInteractionTime())
        .setExposureNotificationLastInteractionType(preferences.getExposureNotificationLastInteractionType())
        .build();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract MetricsSnapshot.Builder setExposureNotificationLastShownTime(Instant value);

    public abstract MetricsSnapshot.Builder setExposureNotificationLastShownClassification(int value);

    public abstract MetricsSnapshot.Builder setExposureNotificationLastInteractionTime(Instant value);

    public abstract MetricsSnapshot.Builder setExposureNotificationLastInteractionType(NotificationInteraction value);

    public abstract MetricsSnapshot build();
  }
}
