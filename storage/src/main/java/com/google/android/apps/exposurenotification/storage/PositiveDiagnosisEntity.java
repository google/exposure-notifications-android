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
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import com.google.common.base.Preconditions;
import org.threeten.bp.ZonedDateTime;

/**
 * A positive diagnosis inputted by the user.
 *
 * <p>Partners should implement a daily TTL/expiry, for on-device storage of this data, and must
 * ensure compliance with all applicable laws and requirements with respect to encryption, storage,
 * and retention polices for end user data.
 */
@Entity
public class PositiveDiagnosisEntity {

  @PrimaryKey(autoGenerate = true)
  long id;

  @ColumnInfo(name = "test_timestamp")
  @NonNull
  private ZonedDateTime testTimestamp;

  PositiveDiagnosisEntity(@NonNull ZonedDateTime testTimestamp) {
    this.testTimestamp = testTimestamp;
  }

  public static PositiveDiagnosisEntity create(@NonNull ZonedDateTime testTimestamp) {
    return new PositiveDiagnosisEntity(Preconditions.checkNotNull(testTimestamp));
  }

  public long getId() {
    return id;
  }

  @NonNull
  public ZonedDateTime getTestTimestamp() {
    return testTimestamp;
  }

  void setTestTimestamp(@NonNull ZonedDateTime testTimestamp) {
    this.testTimestamp = Preconditions.checkNotNull(testTimestamp);
  }

}
