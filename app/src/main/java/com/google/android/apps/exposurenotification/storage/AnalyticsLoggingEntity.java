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

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import com.google.auto.value.AutoValue;
import com.google.auto.value.AutoValue.CopyAnnotations;

@AutoValue
@Entity
public abstract class AnalyticsLoggingEntity {

  @CopyAnnotations
  @PrimaryKey(autoGenerate = true)
  public abstract long getKey();

  @CopyAnnotations
  @NonNull
  public abstract String getEventProto();

  public static AnalyticsLoggingEntity create(long key, @NonNull String eventProto) {
    return new AutoValue_AnalyticsLoggingEntity(key, eventProto);
  }
}
