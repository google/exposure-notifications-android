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

import com.google.android.apps.exposurenotification.proto.CreatePacketsParameters;
import com.google.android.apps.exposurenotification.proto.CreatePacketsResponse;
import com.google.auto.value.AutoValue;


/**
 * A value class for working with a private analytics packet payload.
 */
@AutoValue
public abstract class PrioPacketPayload {

  public abstract CreatePacketsParameters createPacketsParameters();

  public abstract CreatePacketsResponse createPacketsResponse();

  public static PrioPacketPayload.Builder newBuilder() {
    return new AutoValue_PrioPacketPayload.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setCreatePacketsParameters(CreatePacketsParameters value);

    public abstract Builder setCreatePacketsResponse(CreatePacketsResponse value);

    public abstract PrioPacketPayload build();
  }
}
