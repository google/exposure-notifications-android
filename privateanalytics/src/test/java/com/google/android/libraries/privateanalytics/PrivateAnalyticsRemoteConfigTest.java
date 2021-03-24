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

import static com.google.common.truth.Truth.assertThat;

import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.libraries.privateanalytics.Qualifiers.RemoteConfigUri;
import com.google.android.libraries.privateanalytics.testsupport.FakeRequestQueue;
import com.google.android.libraries.privateanalytics.testsupport.LogcatEventListener;
import com.google.android.libraries.privateanalytics.utils.RequestQueueWrapper;
import com.google.common.base.Optional;
import junit.framework.TestCase;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class PrivateAnalyticsRemoteConfigTest extends TestCase {

  @RemoteConfigUri
  static final Uri REMOTE_CONFIG_URI = Uri.parse("http://sampleurls.com/remote_config");
  private final static RemoteConfigs DEFAULT_REMOTE_CONFIGS = RemoteConfigs.newBuilder().build();

  RequestQueueWrapper queue = new FakeRequestQueue();
  private final LogcatEventListener logger = new LogcatEventListener();

  DefaultPrivateAnalyticsRemoteConfig privateAnalyticsRemoteConfig;

  @Before
  public void setUp() {
    privateAnalyticsRemoteConfig = new DefaultPrivateAnalyticsRemoteConfig(REMOTE_CONFIG_URI,
        Optional.of(logger));
    privateAnalyticsRemoteConfig.setRequestQueue(queue);
  }


  @Test
  public void successfullyFetchConfigFromServer_badJSON() throws Exception {
    String jsonResponse = "{ \"enpa_collection_frequency\": invalid value here }";
    fakeQueue().addResponse(REMOTE_CONFIG_URI.toString(), 200, jsonResponse);

    RemoteConfigs configs = privateAnalyticsRemoteConfig.fetchUpdatedConfigs().get();
    assertThat(configs).isEqualTo(DEFAULT_REMOTE_CONFIGS);
  }

  @Test
  public void failedToFetchConfigFromServer_defaultConfigsReturned() throws Exception {
    fakeQueue().addResponse(REMOTE_CONFIG_URI.toString(), 404, "any-response");

    RemoteConfigs configs = privateAnalyticsRemoteConfig.fetchUpdatedConfigs().get();
    assertThat(configs).isEqualTo(DEFAULT_REMOTE_CONFIGS);
  }

  @Test
  public void successfullyFetchConfigFromServer_overridesDefaultValue() throws Exception {
    String jsonResponse = "{ \"enpa_collection_frequency\": 1000 }";
    fakeQueue().addResponse(REMOTE_CONFIG_URI.toString(), 200, jsonResponse);

    RemoteConfigs configs = privateAnalyticsRemoteConfig.fetchUpdatedConfigs().get();
    RemoteConfigs expectedConfigs = RemoteConfigs.newBuilder().setCollectionFrequencyHours(1000)
        .build();
    assertThat(configs).isEqualTo(expectedConfigs);
  }

  @Test
  public void successfullyFetchConfigFromServer_invalidJsonValueReceived_defaultConfigsReturned()
      throws Exception {
    String jsonResponse = "{ \"random_field\": 1000 }";
    fakeQueue().addResponse(REMOTE_CONFIG_URI.toString(), 200, jsonResponse);

    RemoteConfigs configs = privateAnalyticsRemoteConfig.fetchUpdatedConfigs().get();
    assertThat(configs).isEqualTo(DEFAULT_REMOTE_CONFIGS);
  }

  /**
   * Just some syntactical sugar to encapsulate the ugly cast.
   */
  private FakeRequestQueue fakeQueue() {
    return (FakeRequestQueue) queue;
  }
}