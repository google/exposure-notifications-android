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
import org.threeten.bp.ZoneId;
import org.threeten.bp.ZonedDateTime;

/**
 * Tests for {@link PositiveDiagnosisEntity} data class.
 */
@RunWith(RobolectricTestRunner.class)
public class PositiveDiagnosisEntityTest {

  private static final ZonedDateTime TEST_TIMESTAMP_1 =
      ZonedDateTime.of(2020, 1, 3, 4, 5, 6, 7, ZoneId.of("UTC"));
  private static final boolean SHARED_1 = false;
  private static final ZonedDateTime TEST_TIMESTAMP_2 =
      ZonedDateTime.of(2020, 11, 13, 14, 15, 16, 17, ZoneId.of("UTC"));
  private static final boolean SHARED_2 = true;

  @Test
  public void create_isCreated() {
    PositiveDiagnosisEntity entity1 = PositiveDiagnosisEntity.create(TEST_TIMESTAMP_1, SHARED_1);
    PositiveDiagnosisEntity entity2 = PositiveDiagnosisEntity.create(TEST_TIMESTAMP_2, SHARED_2);

    assertThat(entity1.getTestTimestamp()).isEqualTo(TEST_TIMESTAMP_1);
    assertThat(entity1.isShared()).isEqualTo(SHARED_1);
    assertThat(entity2.getTestTimestamp()).isEqualTo(TEST_TIMESTAMP_2);
    assertThat(entity2.isShared()).isEqualTo(SHARED_2);
  }

  @Test
  public void create_thenModify_updates() {
    PositiveDiagnosisEntity entity = PositiveDiagnosisEntity.create(TEST_TIMESTAMP_1, SHARED_1);

    entity.setTestTimestamp(TEST_TIMESTAMP_2);
    entity.setShared(SHARED_2);

    assertThat(entity.getTestTimestamp()).isEqualTo(TEST_TIMESTAMP_2);
    assertThat(entity.isShared()).isEqualTo(SHARED_2);
  }

}
