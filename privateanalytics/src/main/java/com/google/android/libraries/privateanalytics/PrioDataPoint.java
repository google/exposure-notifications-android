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
}
