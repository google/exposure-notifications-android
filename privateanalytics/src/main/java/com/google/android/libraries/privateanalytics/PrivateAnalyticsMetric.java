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

package com.google.android.libraries.privateanalytics;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;

/**
 * Interface for a Metrics class that outputs a data vector.
 */
public interface PrivateAnalyticsMetric {

  /**
   * Access the name of the metric.
   **/
  String getMetricName();

  /**
   * Returns the Hamming weight of the metric.
   * <p>
   * A nonzero Hamming weight indicates what will be the Hamming weight of the data, and will be
   * populated in the PrioAlgorithmParameters and verified on the server.
   **/
  int getMetricHammingWeight();

  /**
   * Generates the data vector associated with this metric. The vector will be represented as a
   * binary set of 0 and 1s.
   **/
  ListenableFuture<List<Integer>> getDataVector();

}