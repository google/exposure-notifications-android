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

package com.google.android.apps.exposurenotification.common.logging;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

@RunWith(AndroidJUnit4.class)
@HiltAndroidTest
@Config(application = HiltTestApplication.class)
public class LoggerTest {

  private static final String TEST_TAG = "test-tag";
  private static final String TEST_SECONDARY_TAG = "test-secondary-tag";
  private static final String TEST_MESSAGE = "test-message";
  private static final String TEST_TAGGED_MESSAGE = "test-secondary-tag: test-message";

  @Test
  public void buildLogger_debug() {
    Logger logger = Logger.buildLogger(true, TEST_TAG, TEST_SECONDARY_TAG);
    assertThat(logger instanceof DebugLogger).isTrue();
    assertThat(logger.tag).isEqualTo(TEST_TAG);
    assertThat(logger.secondaryTag).isEqualTo(TEST_SECONDARY_TAG);
  }

  @Test
  public void buildLogger_production() {
    Logger logger = Logger.buildLogger(false, TEST_TAG, TEST_SECONDARY_TAG);
    assertThat(logger instanceof ProductionLogger).isTrue();
    assertThat(logger.tag).isEqualTo(TEST_TAG);
    assertThat(logger.secondaryTag).isEqualTo(TEST_SECONDARY_TAG);
  }

  @Test
  public void getLogger() {
    Logger logger = Logger.getLogger(TEST_SECONDARY_TAG);
    assertThat(logger.secondaryTag).isEqualTo(TEST_SECONDARY_TAG);
  }

  @Test
  public void getTaggedMessage() {
    Logger logger = Logger.buildLogger(true, TEST_TAG, TEST_SECONDARY_TAG);
    assertThat(logger.getTaggedMessage(TEST_MESSAGE)).isEqualTo(TEST_TAGGED_MESSAGE);
  }
}
