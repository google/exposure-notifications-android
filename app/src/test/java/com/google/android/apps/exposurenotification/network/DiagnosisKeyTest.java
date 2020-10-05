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

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

/**
 * Tests of {@link DiagnosisKey}.
 */
@RunWith(AndroidJUnit4.class)
@HiltAndroidTest
@Config(application = HiltTestApplication.class)
public class DiagnosisKeyTest {

  @Test
  public void setKeyBytes_identity() {
    byte[] inputBuffer = {65, 66, 67, 68};
    DiagnosisKey key = DiagnosisKey.newBuilder()
        .setKeyBytes(inputBuffer)
        .setIntervalNumber(1)
        .build();
    assertThat(key.getKeyBytes()).isEqualTo(inputBuffer);
  }

  @Test
  public void setIntervalNumber_identity() {
    byte[] inputBuffer = {65, 66, 67, 68};
    int inputIntervalNumber = 2647121; // A recent, realistic interval number.
    DiagnosisKey key = DiagnosisKey.newBuilder()
        .setKeyBytes(inputBuffer)
        .setIntervalNumber(inputIntervalNumber)
        .build();
    assertThat(key.getIntervalNumber()).isEqualTo(inputIntervalNumber);
  }

  @Test
  public void key_compareValues() {
    byte[] inputBuffer = {65, 66, 67, 68};
    int inputIntervalNumber = 2647121; // A recent, realistic interval number.
    DiagnosisKey key1 = DiagnosisKey.newBuilder()
        .setKeyBytes(inputBuffer)
        .setIntervalNumber(inputIntervalNumber)
        .build();
    DiagnosisKey key2 = DiagnosisKey.newBuilder()
        .setKeyBytes(inputBuffer.clone())
        .setIntervalNumber(inputIntervalNumber)
        .build();

    assertThat(key1).isEqualTo(key2);
  }

  @Test
  public void setKeyBytes_cannotMutateUsingInputBytes() {
    byte[] inputBuffer = {65, 66, 67, 68};
    DiagnosisKey key = DiagnosisKey.newBuilder()
        .setKeyBytes(inputBuffer)
        .setIntervalNumber(1)
        .build();
    inputBuffer[0] = 89;
    assertThat(key.getKeyBytes()).isNotEqualTo(inputBuffer);
  }

  @Test
  public void setKeyBytes_cannotMutateUsingOutputBytes() {
    byte[] inputBuffer = {65, 66, 67, 68};
    DiagnosisKey key = DiagnosisKey.newBuilder()
        .setKeyBytes(inputBuffer)
        .setIntervalNumber(1)
        .build();
    byte[] bytes = key.getKeyBytes();
    bytes[0] = 89;
    assertThat(key.getKeyBytes()).isEqualTo(inputBuffer);
  }
}

