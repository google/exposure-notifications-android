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

package com.google.android.apps.exposurenotification.nearby;

import static com.google.common.truth.Truth.assertThat;

import android.support.test.espresso.core.internal.deps.guava.collect.ImmutableList;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.apps.exposurenotification.network.DiagnosisKey;
import com.google.android.apps.exposurenotification.proto.TemporaryExposureKey;
import com.google.android.gms.nearby.exposurenotification.ReportType;
import com.google.android.gms.nearby.exposurenotification.TemporaryExposureKey.TemporaryExposureKeyBuilder;
import com.google.common.base.Optional;
import com.google.common.io.BaseEncoding;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

@RunWith(AndroidJUnit4.class)
@HiltAndroidTest
@Config(application = HiltTestApplication.class)
public class TemporaryExposureKeyHelperTest {

  private static final BaseEncoding BASE64 = BaseEncoding.base64();
  private static final int NO_VALUE_SET_FOR_FIELD = 0;

  @Test
  public void keysToKeyProtos_keysWithSomeValuesNotSet_fieldValuesSetAsExpected() {
    List<com.google.android.gms.nearby.exposurenotification.TemporaryExposureKey> keys =
        ImmutableList.of(keyWithSomeValuesNotSet("key1"),
            keyWithSomeValuesNotSet("key2"), keyWithSomeValuesNotSet("key3"),
            keyWithSomeValuesNotSet("key4"));

    List<TemporaryExposureKey> protos = TemporaryExposureKeyHelper.keysToKeyProtos(keys);

    assertThat(protos.size()).isEqualTo(keys.size());
    // Verify that initial TEK objects match proto TEK objects.
    for (int i = 0; i < keys.size(); i++) {
      assertThat(protos.get(i).getKeyData().toByteArray()).isEqualTo(keys.get(i).getKeyData());
      assertThat(protos.get(i).getTransmissionRiskLevel())
          .isEqualTo(keys.get(i).getTransmissionRiskLevel());
      assertThat(protos.get(i).getRollingStartIntervalNumber())
          .isEqualTo(keys.get(i).getRollingStartIntervalNumber());
      assertThat(protos.get(i).getRollingPeriod()).isEqualTo(keys.get(i).getRollingPeriod());
      assertThat(protos.get(i).getReportType().getNumber()).isEqualTo(NO_VALUE_SET_FOR_FIELD);
      assertThat(protos.get(i).getDaysSinceOnsetOfSymptoms()).isEqualTo(NO_VALUE_SET_FOR_FIELD);
    }
  }

  @Test
  public void keysToKeyProtos_fieldValuesSetAsExpected() {
    List<com.google.android.gms.nearby.exposurenotification.TemporaryExposureKey> keys =
        ImmutableList.of(key("key1"), key("key2"), key("key3"),
            key("key4"));

    List<TemporaryExposureKey> protos = TemporaryExposureKeyHelper.keysToKeyProtos(keys);

    assertThat(protos.size()).isEqualTo(keys.size());
    // Verify that initial TEK objects match proto TEK objects.
    for (int i = 0; i < keys.size(); i++) {
      assertThat(protos.get(i).getKeyData().toByteArray()).isEqualTo(keys.get(i).getKeyData());
      assertThat(protos.get(i).getTransmissionRiskLevel())
          .isEqualTo(keys.get(i).getTransmissionRiskLevel());
      assertThat(protos.get(i).getRollingStartIntervalNumber())
          .isEqualTo(keys.get(i).getRollingStartIntervalNumber());
      assertThat(protos.get(i).getRollingPeriod()).isEqualTo(keys.get(i).getRollingPeriod());
      assertThat(protos.get(i).getReportType().getNumber()).isEqualTo(keys.get(i).getReportType());
      assertThat(protos.get(i).getDaysSinceOnsetOfSymptoms())
          .isEqualTo(keys.get(i).getDaysSinceOnsetOfSymptoms());
    }
  }

  @Test
  public void keysToKeyProtos_andKeyProtosToDiagnosisKeys_keysMatchDiagnosisKeys() {
    List<com.google.android.gms.nearby.exposurenotification.TemporaryExposureKey> keys =
        ImmutableList.of(key("key1"), key("key2"), key("key3"),
            key("key4"));

    List<TemporaryExposureKey> protos = TemporaryExposureKeyHelper.keysToKeyProtos(keys);
    List<DiagnosisKey> diagnosisKeys = TemporaryExposureKeyHelper.keyProtosToDiagnosisKeys(protos);

    assertThat(diagnosisKeys.size()).isEqualTo(keys.size());
    // Verify that initial TEK objects match final diagnosis key objects.
    for (int i = 0; i < keys.size(); i++) {
      assertThat(diagnosisKeys.get(i).getKeyBytes()).isEqualTo(keys.get(i).getKeyData());
      assertThat(diagnosisKeys.get(i).getTransmissionRisk())
          .isEqualTo(keys.get(i).getTransmissionRiskLevel());
      assertThat(diagnosisKeys.get(i).getIntervalNumber())
          .isEqualTo(keys.get(i).getRollingStartIntervalNumber());
      assertThat(diagnosisKeys.get(i).getRollingPeriod()).isEqualTo(keys.get(i).getRollingPeriod());
    }
  }

  @Test
  public void keysToTEKExportBytes_andMaybeBytesToDiagnosisKeys_serializedKeysMatchParsedKeys() {
    List<com.google.android.gms.nearby.exposurenotification.TemporaryExposureKey> keys =
        ImmutableList.of(key("key1"), key("key2"), key("key3"),
            key("key4"));

    byte[] tekExportBytes = TemporaryExposureKeyHelper.keysToTEKExportBytes(keys);
    Optional<List<DiagnosisKey>> optionalParsedKeys =
        TemporaryExposureKeyHelper.maybeBytesToDiagnosisKeys(tekExportBytes);

    assertThat(optionalParsedKeys).isPresent();
    List<DiagnosisKey> parsedKeys = optionalParsedKeys.get();
    assertThat(keys.size()).isEqualTo(parsedKeys.size());
    // Verify that initial TEK objects match final diagnosis key objects.
    for (int i = 0; i < keys.size(); i++) {
      assertThat(parsedKeys.get(i).getKeyBytes()).isEqualTo(keys.get(i).getKeyData());
      assertThat(parsedKeys.get(i).getTransmissionRisk())
          .isEqualTo(keys.get(i).getTransmissionRiskLevel());
      assertThat(parsedKeys.get(i).getIntervalNumber())
          .isEqualTo(keys.get(i).getRollingStartIntervalNumber());
      assertThat(parsedKeys.get(i).getRollingPeriod()).isEqualTo(keys.get(i).getRollingPeriod());
    }
  }

  private static com.google.android.gms.nearby.exposurenotification.TemporaryExposureKey key(
      String keyData) {
    return new TemporaryExposureKeyBuilder()
        .setKeyData(BASE64.decode(keyData))
        .setReportType(ReportType.CONFIRMED_TEST)
        .setRollingPeriod(144)
        .setRollingStartIntervalNumber(1)
        .setTransmissionRiskLevel(1)
        .setDaysSinceOnsetOfSymptoms(1)
        .build();
  }

  private static com.google.android.gms.nearby.exposurenotification.TemporaryExposureKey
  keyWithSomeValuesNotSet(String keyData) {
    return new TemporaryExposureKeyBuilder()
        .setKeyData(BASE64.decode(keyData))
        .setRollingPeriod(144)
        .setRollingStartIntervalNumber(1)
        .setTransmissionRiskLevel(1)
        .build();
  }

}
