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
public abstract class CountryEntity {

  @CopyAnnotations
  @PrimaryKey
  @NonNull
  public abstract String getCountryCode();

  public abstract long getLastSeenTimestampMillis();

  public static CountryEntity create(String countryCode, long lastSeenTimestampMillis) {
    return new AutoValue_CountryEntity(countryCode, lastSeenTimestampMillis);
  }
}
