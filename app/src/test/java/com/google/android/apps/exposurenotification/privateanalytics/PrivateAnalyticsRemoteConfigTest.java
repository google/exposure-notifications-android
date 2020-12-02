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

package com.google.android.apps.exposurenotification.privateanalytics;

import static com.google.common.truth.Truth.assertThat;

import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.android.apps.exposurenotification.common.time.RealTimeModule;
import com.google.android.apps.exposurenotification.network.RealRequestQueueModule;
import com.google.android.apps.exposurenotification.network.RequestQueueWrapper;
import com.google.android.apps.exposurenotification.privateanalytics.Qualifiers.RemoteConfigUri;
import com.google.android.apps.exposurenotification.testsupport.ExposureNotificationRules;
import com.google.android.apps.exposurenotification.testsupport.FakeClock;
import com.google.android.apps.exposurenotification.testsupport.FakeRequestQueue;
import dagger.hilt.android.testing.BindValue;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import dagger.hilt.android.testing.UninstallModules;
import javax.inject.Inject;
import junit.framework.TestCase;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

@RunWith(AndroidJUnit4.class)
@HiltAndroidTest
@Config(application = HiltTestApplication.class)
@UninstallModules({PrivateAnalyticsRemoteConfigModule.class, RealRequestQueueModule.class,
    RealTimeModule.class,})
public class PrivateAnalyticsRemoteConfigTest extends TestCase {

  @BindValue
  @RemoteConfigUri
  static final Uri REMOTE_CONFIG_URI = Uri.parse("http://sampleurls.com/remote_config");
  private final static RemoteConfigs DEFAULT_REMOTE_CONFIGS = RemoteConfigs.newBuilder().build();

  @Rule
  public ExposureNotificationRules rules = ExposureNotificationRules.forTest(this).build();

  @BindValue
  RequestQueueWrapper queue = new FakeRequestQueue();

  @BindValue
  Clock clock = new FakeClock();

  @Before
  public void setUp() {
    rules.hilt().inject();
  }


  @Inject
  PrivateAnalyticsRemoteConfig privateAnalyticsRemoteConfig;

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