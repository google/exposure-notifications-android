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

package com.google.android.apps.exposurenotification.network;

import androidx.annotation.Nullable;
import com.google.auto.value.AutoValue;
import java.util.Arrays;

/**
 * A carrier of diagnosis key into and out of the network operations.
 */
@AutoValue
public abstract class DiagnosisKey {
  // The number of 10-minute intervals the key is valid for
  public static final int DEFAULT_PERIOD = 144;
  public abstract ByteArrayValue getKey();

  public abstract int getIntervalNumber();

  public static Builder newBuilder() {
    return new AutoValue_DiagnosisKey.Builder();
  }

  public byte[] getKeyBytes() {
    return getKey().getBytes();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setKey(ByteArrayValue key);
    public abstract Builder setIntervalNumber(int intervalNumber);
    public abstract DiagnosisKey build();

    public Builder setKeyBytes(byte[] keyBytes) {
      setKey(new ByteArrayValue(keyBytes));
      return this;
    }
  }

  public static class ByteArrayValue {
    private final byte[] bytes;

    public ByteArrayValue(byte[] bytes) {
      this.bytes = bytes.clone();
    }

    public byte[] getBytes() {
      return bytes.clone();
    }

    @Override
    public boolean equals(@Nullable Object other) {
      if (this == other) {
        return true;
      }
      if (!(other instanceof ByteArrayValue)) {
        return false;
      }
      ByteArrayValue that = (ByteArrayValue) other;
      return Arrays.equals(bytes, that.bytes);
    }

    @Override
    public int hashCode() {
      return Arrays.hashCode(bytes);
    }

    @Override
    public String toString() {
      return Arrays.toString(bytes);
    }
  }
}
