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

import static com.google.android.apps.exposurenotification.common.StringUtils.ELLIPSIS;
import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import java.util.Locale;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Tests for {@link StringUtils} utility function helper class.
 */
@RunWith(AndroidJUnit4.class)
@HiltAndroidTest
@Config(application = HiltTestApplication.class)
public class StringUtilsTest {

  private static final long TIMESTAMP_APR_10_MS = 1586476800000L;

  @Test
  public void epochTimestampToMediumUTCDateString_localeUS() {
    String formattedDate = StringUtils
        .epochTimestampToMediumUTCDateString(TIMESTAMP_APR_10_MS, Locale.US);

    assertThat(formattedDate).isEqualTo("April 10, 2020");
  }

  @Test
  public void epochTimestampToLongUTCDateString_localeUS() {
    String formattedDate = StringUtils
        .epochTimestampToLongUTCDateTimeString(TIMESTAMP_APR_10_MS, Locale.US);

    assertThat(formattedDate).isEqualTo("2020-04-10, 00:00:00 UTC");
  }

  @Test
  public void epochTimestampToMediumUTCDateString_localeFRANCE() {
    String formattedDate = StringUtils
        .epochTimestampToMediumUTCDateString(TIMESTAMP_APR_10_MS, Locale.FRANCE);

    assertThat(formattedDate).isEqualTo("10 avril 2020");
  }

  @Test
  public void epochTimestampToMediumUTCDateString_localeJAPAN() {
    String formattedDate = StringUtils
        .epochTimestampToMediumUTCDateString(TIMESTAMP_APR_10_MS, Locale.JAPAN);

    assertThat(formattedDate).isEqualTo("2020年4月10日");
  }


  @Test
  public void randomBase64Data_zeroLength() {
    String base64Data = StringUtils.randomBase64Data(0);

    assertThat(base64Data).hasLength(0);
  }

  @Test
  public void randomBase64Data_notSame() {
    String base64Data1 = StringUtils.randomBase64Data(100);
    String base64Data2 = StringUtils.randomBase64Data(100);

    assertThat(base64Data1).isNotEqualTo(base64Data2);
  }

  @Test
  public void truncateWithEllipsis_normal() {
    String truncated = StringUtils.truncateWithEllipsis("1234567890", 7);

    assertThat(truncated).isEqualTo("123456" + ELLIPSIS);
  }

  @Test
  public void truncateWithEllipsis_lengthLessThanOne() {
    String truncated = StringUtils.truncateWithEllipsis("1234567890", 0);

    assertThat(truncated).isEqualTo(ELLIPSIS);
  }

}
