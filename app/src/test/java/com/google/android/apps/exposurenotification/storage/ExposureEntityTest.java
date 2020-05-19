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

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * Tests for {@link ExposureEntity} data class.
 */
@RunWith(RobolectricTestRunner.class)
public class ExposureEntityTest {

  private static final long DATE_MILLIS_SINCE_EPOCH_1 = 1234L;
  private static final long RECEIVED_TIMESTAMP_MS_1 = 5678L;
  private static final long DATE_MILLIS_SINCE_EPOCH_2 = 1234L;
  private static final long RECEIVED_TIMESTAMP_MS_2 = 5678L;

  @Test
  public void create_isCreated() {
    ExposureEntity entity1 = ExposureEntity
        .create(DATE_MILLIS_SINCE_EPOCH_1, RECEIVED_TIMESTAMP_MS_1);
    ExposureEntity entity2 = ExposureEntity
        .create(DATE_MILLIS_SINCE_EPOCH_2, RECEIVED_TIMESTAMP_MS_2);

    assertThat(entity1.getDateMillisSinceEpoch()).isEqualTo(DATE_MILLIS_SINCE_EPOCH_1);
    assertThat(entity1.getReceivedTimestampMs()).isEqualTo(RECEIVED_TIMESTAMP_MS_1);
    assertThat(entity2.getDateMillisSinceEpoch()).isEqualTo(DATE_MILLIS_SINCE_EPOCH_2);
    assertThat(entity2.getReceivedTimestampMs()).isEqualTo(RECEIVED_TIMESTAMP_MS_2);
  }

  @Test
  public void create_thenModify_updates() {
    ExposureEntity entity = ExposureEntity
        .create(DATE_MILLIS_SINCE_EPOCH_1, RECEIVED_TIMESTAMP_MS_1);

    entity.setDateMillisSinceEpoch(DATE_MILLIS_SINCE_EPOCH_2);
    entity.setReceivedTimestampMs(RECEIVED_TIMESTAMP_MS_2);

    assertThat(entity.getDateMillisSinceEpoch()).isEqualTo(DATE_MILLIS_SINCE_EPOCH_2);
    assertThat(entity.getReceivedTimestampMs()).isEqualTo(RECEIVED_TIMESTAMP_MS_2);
  }

}
