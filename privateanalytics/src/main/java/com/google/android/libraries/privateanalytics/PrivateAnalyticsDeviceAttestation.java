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

import com.google.android.libraries.privateanalytics.proto.CreatePacketsResponse;
import com.google.android.libraries.privateanalytics.proto.PrioAlgorithmParameters;
import java.util.List;
import java.util.Map;

public interface PrivateAnalyticsDeviceAttestation {

  /**
   * Creates an attestation of the document payload (ie, the value of document.get("payload")), and
   * adds it to the document object.
   * <p>
   * returns true if the document is signed correctly
   */
  boolean signPayload(String metricName,
      Map<String, Object> document, PrioAlgorithmParameters params, CreatePacketsResponse response,
      long collectionFrequencyHours) throws Exception;

  /**
   * Makes the device attestation mechanism clear any data it could have stored (shared preferences,
   * keystore, ...) for the list of metrics given as a parameter.
   */
  void clearData(List<String> listOfMetrics);

}
