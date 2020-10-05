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

package com.google.android.apps.exposurenotification.riskcalculation;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.gms.nearby.exposurenotification.DiagnosisKeysDataMapping;
import com.google.android.gms.nearby.exposurenotification.DiagnosisKeysDataMapping.DiagnosisKeysDataMappingBuilder;
import com.google.android.gms.nearby.exposurenotification.ReportType;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

@RunWith(AndroidJUnit4.class)
@HiltAndroidTest
@Config(application = HiltTestApplication.class)
public class DiagnosisKeyDataMappingHelperTest {

  @Test
  public void createDiagnosisKeysDataMapping_allZeroString_createAllNoneMapping() {
    String input = "0x0000000000000000";

    DiagnosisKeysDataMapping result = DiagnosisKeyDataMappingHelper
        .createDiagnosisKeysDataMapping(input, ReportType.CONFIRMED_TEST);

    Map<Integer, Integer> daysSinceOnsetToInfectiousness = new HashMap<>();
    for (int day = -14; day <= 14; day++) {
      daysSinceOnsetToInfectiousness.put(day, 0);
    }

    assertThat(result)
        .isEqualTo(new DiagnosisKeysDataMappingBuilder()
            .setReportTypeWhenMissing(ReportType.CONFIRMED_TEST)
            .setInfectiousnessWhenDaysSinceOnsetMissing(0)
            .setDaysSinceOnsetToInfectiousness(daysSinceOnsetToInfectiousness)
            .build());
  }

  @Test
  public void createDiagnosisKeysDataMapping_allAString_createAllHighMapping() {
    String input = "0x0aaaaaaaaaaaaaaa";

    DiagnosisKeysDataMapping result = DiagnosisKeyDataMappingHelper
        .createDiagnosisKeysDataMapping(input, ReportType.CONFIRMED_TEST);

    Map<Integer, Integer> daysSinceOnsetToInfectiousness = new HashMap<>();
    for (int day = -14; day <= 14; day++) {
      daysSinceOnsetToInfectiousness.put(day, 2);
    }

    assertThat(result)
        .isEqualTo(new DiagnosisKeysDataMappingBuilder()
            .setReportTypeWhenMissing(ReportType.CONFIRMED_TEST)
            .setInfectiousnessWhenDaysSinceOnsetMissing(2)
            .setDaysSinceOnsetToInfectiousness(daysSinceOnsetToInfectiousness)
            .build());
  }

  /**
   * Test correct conversion of the configuration-tools default mapping.
   */
  @Test
  public void createDiagnosisKeysDataMapping_configtoolExample_createCorrectMapping() {
    String input = "0x0104555aa6100000";

    DiagnosisKeysDataMapping result = DiagnosisKeyDataMappingHelper
        .createDiagnosisKeysDataMapping(input, ReportType.CONFIRMED_TEST);

    Map<Integer, Integer> daysSinceOnsetToInfectiousness = new HashMap<>();
    daysSinceOnsetToInfectiousness.put(-14, 0);
    daysSinceOnsetToInfectiousness.put(-13, 0);
    daysSinceOnsetToInfectiousness.put(-12, 0);
    daysSinceOnsetToInfectiousness.put(-11, 0);
    daysSinceOnsetToInfectiousness.put(-10, 0);
    daysSinceOnsetToInfectiousness.put(-9, 0);
    daysSinceOnsetToInfectiousness.put(-8, 0);
    daysSinceOnsetToInfectiousness.put(-7, 0);
    daysSinceOnsetToInfectiousness.put(-6, 0);
    daysSinceOnsetToInfectiousness.put(-5, 0);
    daysSinceOnsetToInfectiousness.put(-4, 1);
    daysSinceOnsetToInfectiousness.put(-3, 0);
    daysSinceOnsetToInfectiousness.put(-2, 2);
    daysSinceOnsetToInfectiousness.put(-1, 1);
    daysSinceOnsetToInfectiousness.put(0, 2);
    daysSinceOnsetToInfectiousness.put(1, 2);
    daysSinceOnsetToInfectiousness.put(2, 2);
    daysSinceOnsetToInfectiousness.put(3, 2);
    daysSinceOnsetToInfectiousness.put(4, 1);
    daysSinceOnsetToInfectiousness.put(5, 1);
    daysSinceOnsetToInfectiousness.put(6, 1);
    daysSinceOnsetToInfectiousness.put(7, 1);
    daysSinceOnsetToInfectiousness.put(8, 1);
    daysSinceOnsetToInfectiousness.put(9, 1);
    daysSinceOnsetToInfectiousness.put(10, 0);
    daysSinceOnsetToInfectiousness.put(11, 1);
    daysSinceOnsetToInfectiousness.put(12, 0);
    daysSinceOnsetToInfectiousness.put(13, 0);
    daysSinceOnsetToInfectiousness.put(14, 1);
    assertThat(result)
        .isEqualTo(new DiagnosisKeysDataMappingBuilder()
            .setReportTypeWhenMissing(ReportType.CONFIRMED_TEST)
            .setInfectiousnessWhenDaysSinceOnsetMissing(0)
            .setDaysSinceOnsetToInfectiousness(daysSinceOnsetToInfectiousness)
            .build());
  }

  /**
   * A test to catch off-by-one errors where values are accidentally taken from neighboring days
   */
  @Test
  public void createDiagnosisKeysDataMapping_stringAlternating_createCorrectMapping() {
    String input = "0x0492492492492492";

    DiagnosisKeysDataMapping result = DiagnosisKeyDataMappingHelper
        .createDiagnosisKeysDataMapping(input, ReportType.CONFIRMED_TEST);

    Map<Integer, Integer> daysSinceOnsetToInfectiousness = new HashMap<>();
    daysSinceOnsetToInfectiousness.put(-14, 2);
    daysSinceOnsetToInfectiousness.put(-13, 0);
    daysSinceOnsetToInfectiousness.put(-12, 1);
    daysSinceOnsetToInfectiousness.put(-11, 2);
    daysSinceOnsetToInfectiousness.put(-10, 0);
    daysSinceOnsetToInfectiousness.put(-9, 1);
    daysSinceOnsetToInfectiousness.put(-8, 2);
    daysSinceOnsetToInfectiousness.put(-7, 0);
    daysSinceOnsetToInfectiousness.put(-6, 1);
    daysSinceOnsetToInfectiousness.put(-5, 2);
    daysSinceOnsetToInfectiousness.put(-4, 0);
    daysSinceOnsetToInfectiousness.put(-3, 1);
    daysSinceOnsetToInfectiousness.put(-2, 2);
    daysSinceOnsetToInfectiousness.put(-1, 0);
    daysSinceOnsetToInfectiousness.put(0, 1);
    daysSinceOnsetToInfectiousness.put(1, 2);
    daysSinceOnsetToInfectiousness.put(2, 0);
    daysSinceOnsetToInfectiousness.put(3, 1);
    daysSinceOnsetToInfectiousness.put(4, 2);
    daysSinceOnsetToInfectiousness.put(5, 0);
    daysSinceOnsetToInfectiousness.put(6, 1);
    daysSinceOnsetToInfectiousness.put(7, 2);
    daysSinceOnsetToInfectiousness.put(8, 0);
    daysSinceOnsetToInfectiousness.put(9, 1);
    daysSinceOnsetToInfectiousness.put(10, 2);
    daysSinceOnsetToInfectiousness.put(11, 0);
    daysSinceOnsetToInfectiousness.put(12, 1);
    daysSinceOnsetToInfectiousness.put(13, 2);
    daysSinceOnsetToInfectiousness.put(14, 0);
    assertThat(result)
        .isEqualTo(new DiagnosisKeysDataMappingBuilder()
            .setReportTypeWhenMissing(ReportType.CONFIRMED_TEST)
            .setInfectiousnessWhenDaysSinceOnsetMissing(1)
            .setDaysSinceOnsetToInfectiousness(daysSinceOnsetToInfectiousness)
            .build());
  }


}
