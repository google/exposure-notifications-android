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

package com.google.android.apps.exposurenotification.privateanalytics;

import static com.google.common.truth.Truth.assertThat;

import android.net.Uri;
import androidx.hilt.work.HiltWorkerFactory;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.work.ListenableWorker;
import androidx.work.WorkerParameters;
import com.google.android.apps.exposurenotification.network.RealRequestQueueModule;
import com.google.android.apps.exposurenotification.network.RequestQueueWrapper;
import com.google.android.apps.exposurenotification.testsupport.ExposureNotificationRules;
import com.google.android.libraries.privateanalytics.PrivateAnalyticsDeviceAttestation;
import com.google.android.libraries.privateanalytics.PrivateAnalyticsEnabledProvider;
import com.google.android.libraries.privateanalytics.PrivateAnalyticsEventListener;
import com.google.android.libraries.privateanalytics.PrivateAnalyticsRemoteConfig;
import com.google.android.libraries.privateanalytics.Qualifiers.RemoteConfigUri;
import com.google.common.base.Optional;
import dagger.hilt.android.testing.BindValue;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import dagger.hilt.android.testing.UninstallModules;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.annotation.Config;

@RunWith(AndroidJUnit4.class)
@HiltAndroidTest
@Config(application = HiltTestApplication.class)
@UninstallModules({PrivateAnalyticsModule.class, RealRequestQueueModule.class})
public class SubmitPrivateAnalyticsWorkerTest {

  @Rule
  public ExposureNotificationRules rules = ExposureNotificationRules.forTest(this).withMocks()
      .build();

  @Mock
  WorkerParameters workerParameters;
  @Inject
  HiltWorkerFactory workerFactory;
  @BindValue
  @Mock
  PrivateAnalyticsDeviceAttestation deviceAttestation;
  @BindValue
  @Mock
  PrivateAnalyticsRemoteConfig remoteConfig;
  @BindValue
  @RemoteConfigUri
  @Mock
  Uri remoteConfigUri;
  @BindValue
  @Mock
  PrivateAnalyticsEnabledProvider privateAnalyticsEnabledProvider;
  @BindValue
  Optional<PrivateAnalyticsEventListener> eventListener = Optional.absent();
  @BindValue
  @Mock
  RequestQueueWrapper requestQueueWrapper;


  @Before
  public void setUp() {
    rules.hilt().inject();
  }

  @Test
  public void testAnalyticsWorker_canBeInitialized() {
    ListenableWorker worker = workerFactory
        .createWorker(ApplicationProvider.getApplicationContext(),
            SubmitPrivateAnalyticsWorker.class.getName(),
            workerParameters);
    assertThat(worker).isNotNull();
  }
}
