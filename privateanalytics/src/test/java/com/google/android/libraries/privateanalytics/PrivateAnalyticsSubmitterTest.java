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

package com.google.android.libraries.privateanalytics;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.Calendar;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class PrivateAnalyticsSubmitterTest {

  @Test
  public void testAnalyticsWorker_isCalendarTheBiweeklyMetricsUploadDay_matchesCorrectDays() {
    Calendar calendar = Calendar.getInstance();

    int metricsPeriodInDays = 14;
    int biweeklyMetricsUploadDay = 0;

    for (int i = 0; i < metricsPeriodInDays; i++) {
      int matchingDays = 0;
      biweeklyMetricsUploadDay =
          (biweeklyMetricsUploadDay + 1) % metricsPeriodInDays;
      for (int j = 0; j < metricsPeriodInDays; j++) {
        if (PrivateAnalyticsSubmitter
            .isCalendarTheBiweeklyMetricsUploadDay(biweeklyMetricsUploadDay,
                calendar)) {
          matchingDays++;
        }
        calendar.add(Calendar.DAY_OF_YEAR, 1);
      }
      assertThat(matchingDays).isEqualTo(1);
    }
  }
}
