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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Tests for {@link RoamingConfigParser}.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner.class)
@Config(application = HiltTestApplication.class)
public class RoamingConfigParserTest {

  @Test
  public void emptyString_returnsEmptyConfigMap() {
    assertThat(RoamingConfigParser.parse("")).isEmpty();
  }

  @Test
  public void nullString_returnsEmptyConfigMap() {
    assertThat(RoamingConfigParser.parse(null)).isEmpty();
  }

  @Test
  public void invalidJson_returnsEmptyConfigMap() {
    assertThat(RoamingConfigParser.parse("this is not json")).isEmpty();
  }

  @Test
  public void singleCountry_andSingleServer_returnsSingleServerConfig() throws Exception {
    String config = new JSONObject()
        .put("JP", new JSONArray().put(new JSONObject()
            .put("index", "http://example.com/JP/index")
            .put("base", "http://example.com/JP/base")))
        .toString();

    Map<String, List<DownloadUriPair>> expected =
        ImmutableMap.of("JP", ImmutableList.of(
            DownloadUriPair.create("http://example.com/JP/index", "http://example.com/JP/base")));

    assertThat(RoamingConfigParser.parse(config)).isEqualTo(expected);
  }

  @Test
  public void singleCountry_withMultipleServers_returnsMultipleServerConfigs() throws Exception {
    String config = new JSONObject()
        .put("JP", new JSONArray()
            .put(new JSONObject()
                .put("index", "http://example.com/JP/index")
                .put("base", "http://example.com/JP/base"))
            .put(new JSONObject()
                .put("index", "http://example.com/TRAVEL/index")
                .put("base", "http://example.com/TRAVEL/base"))
        )
        .toString();

    Map<String, List<DownloadUriPair>> expected =
        ImmutableMap.of(
            "JP",
            ImmutableList.of(DownloadUriPair.create(
                "http://example.com/JP/index",
                "http://example.com/JP/base"),
                DownloadUriPair.create(
                    "http://example.com/TRAVEL/index",
                    "http://example.com/TRAVEL/base"))
        );

    assertThat(RoamingConfigParser.parse(config)).isEqualTo(expected);
  }

  @Test
  public void multipleCountries_withSingleServers_returnsMultipleCountryConfigs() throws Exception {
    String config = new JSONObject()
        .put("JP", new JSONArray().put(new JSONObject()
            .put("index", "http://example.com/JP/index")
            .put("base", "http://example.com/JP/base")))
        .put("DE", new JSONArray().put(new JSONObject()
            .put("index", "http://example.com/DE/index")
            .put("base", "http://example.com/DE/base")))
        .toString();

    Map<String, List<DownloadUriPair>> expected =
        ImmutableMap.of(
            "JP",
            ImmutableList.of(DownloadUriPair.create(
                "http://example.com/JP/index",
                "http://example.com/JP/base")),
            "DE",
            ImmutableList.of(DownloadUriPair.create(
                "http://example.com/DE/index",
                "http://example.com/DE/base")));

    assertThat(RoamingConfigParser.parse(config)).isEqualTo(expected);
  }
}
