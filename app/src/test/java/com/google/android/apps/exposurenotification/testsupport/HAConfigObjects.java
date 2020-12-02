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

package com.google.android.apps.exposurenotification.testsupport;

import com.google.android.apps.exposurenotification.riskcalculation.ClassificationThreshold;
import com.google.android.gms.nearby.exposurenotification.DailySummariesConfig;
import com.google.android.gms.nearby.exposurenotification.DailySummariesConfig.DailySummariesConfigBuilder;
import com.google.android.gms.nearby.exposurenotification.Infectiousness;
import com.google.android.gms.nearby.exposurenotification.ReportType;
import java.util.Arrays;

/**
 * This class provides sane defaults for objects that are usually created based on the HA config
 */
public class HAConfigObjects {

  public static final ClassificationThreshold[] CLASSIFICATION_THRESHOLDS_ARRAY =
      new ClassificationThreshold[] {
        new ClassificationThreshold(
            1,
            "Classification 1",
            2700,
            0,
            0,
            0,
            0,
            0,
            0),
        new ClassificationThreshold(
            2,
            "Classification 2",
            1,
            0,
            0,
            0,
            0,
            0,
            0),
        new ClassificationThreshold(
            3,
            "Classification 3",
            0,
            2700,
            0,
            0,
            0,
            0,
            0),
        new ClassificationThreshold(
            4,
            "Classification 4",
            0,
            1,
            0,
            0,
            0,
            0,
            0),
    };

  public static final DailySummariesConfig DAILY_SUMMARIES_CONFIG = new DailySummariesConfigBuilder()
        .setAttenuationBuckets(Arrays.asList(30, 50, 60), Arrays.asList(1.5, 1.0, 0.5, 0.0))
        .setInfectiousnessWeight(Infectiousness.STANDARD, 1.0)
        .setInfectiousnessWeight(Infectiousness.HIGH, 1.0)
        .setReportTypeWeight(ReportType.CONFIRMED_TEST, 1.0)
        .setReportTypeWeight(ReportType.CONFIRMED_CLINICAL_DIAGNOSIS, 1.0)
        .setReportTypeWeight(ReportType.SELF_REPORT, 1.0)
        .setDaysSinceExposureThreshold(14)
        .build();

}
