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

public class PrioDataPoint {

  /*
    Those values create non-overlapping intervals for sampling rate vs epsilon, which allows to
    check that values have not been inverted by accident.

    The minimum allowed value for epsilon can be arbitrary Prio-wise, but from the Exposure
    Notifications perspective, it is never expected for an epsilon to be that low.
   */
  private static final double MIN_SAMPLING_RATE = 0.;
  private static final double MAX_SAMPLING_RATE = 1.;
  private static final double MIN_EPSILON = 2;

  private final PrivateAnalyticsMetric metric;
  private final double epsilon;
  private final double sampleRate;

  public PrioDataPoint(PrivateAnalyticsMetric metric, double epsilon, double sampleRate) {
    this.metric = metric;
    this.epsilon = epsilon;
    this.sampleRate = sampleRate;
  }

  public PrivateAnalyticsMetric getMetric() {
    return metric;
  }

  public double getEpsilon() {
    return epsilon;
  }

  public double getSampleRate() {
    return sampleRate;
  }

  public boolean isValid() {
    if (sampleRate < MIN_SAMPLING_RATE || sampleRate > MAX_SAMPLING_RATE) {
      return false;
    }
    if (epsilon < MIN_EPSILON) {
      return false;
    }
    return true;
  }
}
