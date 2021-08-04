/*
 * Copyright 2021 Google LLC
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

import static com.google.android.apps.exposurenotification.common.SecureRandomUtil.HMAC_KEY_LEN_BYTES;
import static com.google.android.apps.exposurenotification.common.SecureRandomUtil.NONCE_LEN_BYTES;
import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.apps.exposurenotification.testsupport.ExposureNotificationRules;
import com.google.common.io.BaseEncoding;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import java.security.SecureRandom;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

@RunWith(AndroidJUnit4.class)
@HiltAndroidTest
@Config(application = HiltTestApplication.class)
public class SecureRandomUtilTest {

  private static final BaseEncoding BASE64 = BaseEncoding.base64();

  @Inject
  SecureRandom secureRandom;

  @Rule
  public ExposureNotificationRules rules = ExposureNotificationRules.forTest(this).build();

  @Before
  public void setUp() {
    rules.hilt().inject();
  }

  @Test
  public void newHmacKey_base64Encoded() {
    String hmacKey = SecureRandomUtil.newHmacKey(secureRandom);

    assertThat(BASE64.canDecode(hmacKey)).isTrue();
  }

  @Test
  public void newHmacKey_hasExpectedLength() {
    String hmacKey = SecureRandomUtil.newHmacKey(secureRandom);

    byte[] decodedBytes = BASE64.decode(hmacKey);

    assertThat(decodedBytes.length).isEqualTo(HMAC_KEY_LEN_BYTES);
  }

  @Test
  public void newNonce_base64Encoded() {
    String nonce = SecureRandomUtil.newNonce(secureRandom);

    assertThat(BASE64.canDecode(nonce)).isTrue();
  }

  @Test
  public void newNonce_hasExpectedLength() {
    String nonce = SecureRandomUtil.newNonce(secureRandom);

    byte[] decodedBytes = BASE64.decode(nonce);

    assertThat(decodedBytes.length).isEqualTo(NONCE_LEN_BYTES);
  }

}
