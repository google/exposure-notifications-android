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

import com.google.auto.value.AutoValue;

/**
 * Responsible for storing the classification.
 */
@AutoValue
public abstract class ExposureClassification {
  public static final int NO_EXPOSURE_CLASSIFICATION_INDEX = 0;
  public static final String NO_EXPOSURE_CLASSIFICATION_NAME = "No Exposure";
  public static final long NO_EXPOSURE_CLASSIFICATION_DATE = 0;

  public abstract int getClassificationIndex();

  public abstract String getClassificationName();

  /*
   * In days since epoch -> Currently that is the date where this classification last occurred
   */
  public abstract long getClassificationDate();

  public static ExposureClassification createNoExposureClassification() {
    return create(NO_EXPOSURE_CLASSIFICATION_INDEX, NO_EXPOSURE_CLASSIFICATION_NAME,
        NO_EXPOSURE_CLASSIFICATION_DATE);
  }

  public static ExposureClassification create(int classificationIndex,
      String classificationName, long classificationDate) {
    return new AutoValue_ExposureClassification(classificationIndex, classificationName,
        classificationDate);
  }

}
