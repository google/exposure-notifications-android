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

package com.google.android.apps.exposurenotification.storage;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import com.google.auto.value.AutoValue;
import com.google.auto.value.AutoValue.CopyAnnotations;
import org.threeten.bp.Instant;

/**
 * A (checkTime) tuple to store the time of the exposure check.
 */
@AutoValue
@Entity
public abstract class ExposureCheckEntity {

  @CopyAnnotations
  @PrimaryKey
  @NonNull
  public abstract Instant getCheckTime();

  /**
   * Creates a {@link ExposureCheckEntity}. This is a factory method required by Room.
   */
  public static ExposureCheckEntity create(Instant checkTime) {
    return new AutoValue_ExposureCheckEntity(checkTime);
  }
}
