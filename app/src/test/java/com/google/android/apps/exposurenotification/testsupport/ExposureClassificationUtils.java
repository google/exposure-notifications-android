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

package com.google.android.apps.exposurenotification.testsupport;

import static com.google.android.apps.exposurenotification.nearby.ExposureInformationHelper.EXPOSURE_EXPIRATION_DEFAULT_THRESHOLD;

import com.google.android.apps.exposurenotification.riskcalculation.ExposureClassification;
import org.threeten.bp.LocalDate;

/**
 * Helper class for creating {@link ExposureClassification} objects.
 */
public class ExposureClassificationUtils {

  public static ExposureClassification getOutdatedExposure() {
    // Current date & time is 1973-03-03, 09:46:40 since we are using a Fake Clock in a test setup.
    // And a default exposure expiration threshold is 14 days. So, set the exposure date to 15 days
    // before the current date to create an outdated exposure.
    LocalDate fifteenDayAgo = LocalDate.of(1973, 2, 16);
    return ExposureClassification.create(
        /* classificationIndex= */1, /* classificationName= */"", fifteenDayAgo.toEpochDay());
  }

  public static ExposureClassification getActiveExposure() {
    // Current date & time is 1973-03-03, 09:46:40 since we are using a Fake Clock in a test setup.
    // And a default exposure expiration threshold is 14 days. So, set the exposure date within 14
    // days before the current date to create an active exposure.
    LocalDate oneDayAgo = LocalDate.of(1973, 3, 2);
    return ExposureClassification.create(
        /* classificationIndex= */1, /* classificationName= */"", oneDayAgo.toEpochDay());
  }

  public static ExposureClassification getActiveExposureHappenedXDaysAgo(int numberOfDaysAgo) {
    if (numberOfDaysAgo > EXPOSURE_EXPIRATION_DEFAULT_THRESHOLD.toDays()) {
      return getOutdatedExposure();
    }
    // Current date & time is 1973-03-03, 09:46:40 since we are using a Fake Clock. And a current
    // exposure expiration threshold is 14 days. So, set the exposure date within 14 days before the
    // current date to create an active exposure.
    LocalDate xDaysAgoDate;
    if (numberOfDaysAgo < 3) {
      xDaysAgoDate = LocalDate.of(1973, 3, 3 - numberOfDaysAgo);
    } else {
      numberOfDaysAgo -= 3;
      xDaysAgoDate = LocalDate.of(1973, 2, 28 - numberOfDaysAgo);
    }
    return ExposureClassification.create(
        /* classificationIndex= */1, /* classificationName= */"", xDaysAgoDate.toEpochDay());
  }

  private ExposureClassificationUtils() {}

}
