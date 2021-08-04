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

package com.google.android.apps.exposurenotification.keydownload;

import com.google.android.apps.exposurenotification.common.logging.Logger;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Parser for the download server configs for roaming servers.
 */
class RoamingConfigParser {

  private static final Logger logger = Logger.getLogger("RoamingConfigParser");

  /**
   * Parses a JSON string like:
   *
   * <pre>{@code
   * {
   *   "US": [
   *     {"index": "https://my-key-server/exposureKeyExport-US/index.txt",
   *      "base": "https://my-key-serrver/exposureKeyExport-US"},
   *     {"index": "https://my-key-server/exposureKeyExport-TRAVEL/index.txt",
   *      "base": "https://my-key-server/exposureKeyExport-TRAVEL"},
   *   ],
   *   "DE": [
   *     {"index": "https://my-key-server/exposureKeyExport-DE/index.txt",
   *      "base": "https://my-key-server/exposureKeyExport-DE"},
   *     {"index": "https://my-key-server/exposureKeyExport-TRAVEL/index.txt",
   *      "base": "https://my-key-server/exposureKeyExport-TRAVEL"},
   *   ],
   * }
   * }</pre>
   * <p>
   * into a map keyed by ISO-Alpha-2 country codes, containing lists of server configs.
   */
  static Map<String, List<DownloadUriPair>> parse(String config) {
    try {
      JSONObject roamingConfig = new JSONObject(config);
      ImmutableMap.Builder<String, List<DownloadUriPair>> outBuilder = ImmutableMap.builder();

      Iterator<String> jsonKeys = roamingConfig.keys();
      while (jsonKeys.hasNext()) {
        String countryCode = jsonKeys.next();
        JSONArray servers = roamingConfig.getJSONArray(countryCode);
        ImmutableList.Builder<DownloadUriPair> uriPairsBuilder = ImmutableList.builder();
        for (int i = 0; i < servers.length(); i++) {
          JSONObject server = servers.getJSONObject(i);
          DownloadUriPair uris =
              DownloadUriPair.create(server.getString("index"), server.getString("base"));
          uriPairsBuilder.add(uris);
        }
        outBuilder.put(countryCode, uriPairsBuilder.build());
      }

      logger.d("Parsed " + outBuilder.build().size() + " region(s) roaming config");
      return outBuilder.build();
    } catch (Exception e) {
      // Swallow all failures to parse this config and continue with no roaming servers.
      // TODO: log this failure to Firelog.
      logger.e(
          "Failed to parse JSON roaming download config, continuing with no roaming servers.", e);
      return new HashMap<>();
    }
  }
}
