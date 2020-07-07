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

package com.google.android.apps.exposurenotification.common;

import static com.google.common.truth.Truth.assertThat;

import java.util.Locale;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * Tests for {@link StringUtils} utility function helper class.
 */
@RunWith(RobolectricTestRunner.class)
public class StringUtilsTest {

  private static final long TIMESTAMP_APR_10_MS = 1586476800000L;

  @Test
  public void epochTimestampToMediumUTCDateString_localeUS() {
    String formattedDate = StringUtils
        .epochTimestampToMediumUTCDateString(TIMESTAMP_APR_10_MS, Locale.US);

    assertThat(formattedDate).isEqualTo("April 10, 2020");
  }

  @Test
  public void epochTimestampToMediumUTCDateString_localeFRANCE() {
    String formattedDate = StringUtils
        .epochTimestampToMediumUTCDateString(TIMESTAMP_APR_10_MS, Locale.FRANCE);

    assertThat(formattedDate).isEqualTo("avril 10, 2020");
  }

}
