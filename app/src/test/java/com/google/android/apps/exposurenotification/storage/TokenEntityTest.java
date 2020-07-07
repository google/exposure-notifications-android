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
 * Tests for {@link TokenEntity} data class.
 */
@RunWith(RobolectricTestRunner.class)
public class TokenEntityTest {

  private static final String TOKEN_1 = "TOKEN_1";
  private static final boolean RESPONDED_1 = false;
  private static final String TOKEN_2 = "TOKEN_2";
  private static final boolean RESPONDED_2 = true;

  @Test
  public void create_isCreated() {
    TokenEntity entity1 = TokenEntity.create(TOKEN_1, RESPONDED_1);
    TokenEntity entity2 = TokenEntity.create(TOKEN_2, RESPONDED_2);

    assertThat(entity1.getToken()).isEqualTo(TOKEN_1);
    assertThat(entity1.isResponded()).isEqualTo(RESPONDED_1);
    assertThat(entity2.getToken()).isEqualTo(TOKEN_2);
    assertThat(entity2.isResponded()).isEqualTo(RESPONDED_2);
  }

  @Test
  public void create_thenModify_updates() {
    TokenEntity entity = TokenEntity.create(TOKEN_1, RESPONDED_1);

    entity.setToken(TOKEN_2);
    entity.setResponded(RESPONDED_2);

    assertThat(entity.getToken()).isEqualTo(TOKEN_2);
    assertThat(entity.isResponded()).isEqualTo(RESPONDED_2);
  }

}