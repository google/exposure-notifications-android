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

package com.google.android.apps.exposurenotification.storage;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import com.google.auto.value.AutoValue;
import com.google.auto.value.AutoValue.CopyAnnotations;

/**
 * A (day, exposure score) tuple, computed from dailySummaryScore.
 * Mainly used to detect revocations.
 */
@AutoValue
@Entity
public abstract class ExposureEntity {

  /**
   * The dateDaysSinceEpoch provided by the DailyExposureSummaries API.
   */
  @CopyAnnotations
  @PrimaryKey
  public abstract long getDateDaysSinceEpoch();

  /**
   * The score that DailySummaries reported for this day (from getScoreSum())
   */
  public abstract double getExposureScore();

  public abstract Builder toBuilder();

  public static Builder newBuilder() {
    return new AutoValue_ExposureEntity.Builder()
        // AutoValue complains if fields not marked @Nullable are not set, but primitives cannot be
        // @Nullable, so we set empty here.
        .setDateDaysSinceEpoch(0L)
        .setExposureScore(0.0);
  }

  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setDateDaysSinceEpoch(long dateDaysSinceEpoch);

    public abstract Builder setExposureScore(double exposureScore);

    public abstract ExposureEntity build();
  }

  /**
   * Creates a {@link ExposureEntity}. This is a factory required by Room. Normally the builder
   * should be used instead.
   */
  public static ExposureEntity create(long dateDaysSinceEpoch, double exposureScore) {
    return newBuilder()
        .setDateDaysSinceEpoch(dateDaysSinceEpoch)
        .setExposureScore(exposureScore)
        .build();
  }

}