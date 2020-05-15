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
import java.util.Objects;
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

  @ColumnInfo(name = "created_timestamp_ms")
  private long createdTimestampMs;

  @ColumnInfo(name = "test_timestamp")
  @NonNull
  private ZonedDateTime testTimestamp;

  @ColumnInfo(name = "shared")
  private boolean shared;

  PositiveDiagnosisEntity(@NonNull ZonedDateTime testTimestamp, boolean shared) {
    this.createdTimestampMs = System.currentTimeMillis();
    this.testTimestamp = testTimestamp;
    this.shared = shared;
  }

  /**
   * Creates a PositiveDiagnosisEntry.
   *
   * @param testTimestamp The time at which the test was taken.
   * @param shared        whether the diagnosis has been shared or not
   */
  public static PositiveDiagnosisEntity create(
      @NonNull ZonedDateTime testTimestamp, boolean shared) {
    return new PositiveDiagnosisEntity(Preconditions.checkNotNull(testTimestamp), shared);
  }

  public long getId() {
    return id;
  }

  public long getCreatedTimestampMs() {
    return createdTimestampMs;
  }

  void setCreatedTimestampMs(long ms) {
    this.createdTimestampMs = ms;
  }

  @NonNull
  public ZonedDateTime getTestTimestamp() {
    return testTimestamp;
  }

  void setTestTimestamp(@NonNull ZonedDateTime testTimestamp) {
    this.testTimestamp = Preconditions.checkNotNull(testTimestamp);
  }

  public boolean isShared() {
    return shared;
  }

  public void setShared(boolean shared) {
    this.shared = shared;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    PositiveDiagnosisEntity entity = (PositiveDiagnosisEntity) o;
    return id == entity.id &&
        createdTimestampMs == entity.createdTimestampMs &&
        shared == entity.shared &&
        testTimestamp.equals(entity.testTimestamp);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, createdTimestampMs, testTimestamp, shared);
  }
}
