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

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * An exposure element for display in the exposures UI.
 *
 * <p>Partners should implement a daily TTL/expiry, for on-device storage of this data, and must
 * ensure compliance with all applicable laws and requirements with respect to encryption, storage,
 * and retention polices for end user data.
 */
@Entity
public class ExposureEntity {

  @PrimaryKey(autoGenerate = true)
  long id;

  /**
   * The dateMillisSinceEpoch provided by the ExposureInformation in the Exposure Notifications API.
   *
   * <p>Represents a date of an exposure in millis since epoch rounded to the day.
   */
  @ColumnInfo(name = "date_millis_since_epoch")
  private long dateMillisSinceEpoch;

  @ColumnInfo(name = "created_timestamp_ms")
  private long createdTimestampMs;

  ExposureEntity(long dateMillisSinceEpoch) {
    this.createdTimestampMs = System.currentTimeMillis();
    this.dateMillisSinceEpoch = dateMillisSinceEpoch;
  }

  /**
   * Creates a ExposureEntity.
   *
   * @param dateMillisSinceEpoch .
   */
  public static ExposureEntity create(long dateMillisSinceEpoch) {
    return new ExposureEntity(dateMillisSinceEpoch);
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

  public long getDateMillisSinceEpoch() {
    return dateMillisSinceEpoch;
  }

  public void setDateMillisSinceEpoch(long dateMillisSinceEpoch) {
    this.dateMillisSinceEpoch = dateMillisSinceEpoch;
  }

}