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

package com.google.android.apps.exposurenotification.nearby;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.android.apps.exposurenotification.logging.AnalyticsLogger;
import com.google.android.apps.exposurenotification.testsupport.ExposureNotificationRules;
import com.google.android.gms.nearby.exposurenotification.ExposureNotificationClient;
import com.google.android.gms.nearby.exposurenotification.PackageConfiguration;
import com.google.android.gms.nearby.exposurenotification.PackageConfiguration.PackageConfigurationBuilder;
import com.google.android.gms.tasks.Tasks;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.annotation.LooperMode.Mode;

@HiltAndroidTest
@RunWith(RobolectricTestRunner.class)
@LooperMode(Mode.LEGACY)
@Config(application = HiltTestApplication.class)
public class ExposureNotificationClientWrapperTest {

  @Rule
  public ExposureNotificationRules rules = ExposureNotificationRules.forTest(this).withMocks()
      .build();

  @Inject
  AnalyticsLogger logger;

  @Mock
  ExposureNotificationClient exposureNotificationClient;

  private ExposureNotificationClientWrapper exposureNotificationClientWrapper;

  @Before
  public void setup() {
    rules.hilt().inject();
    exposureNotificationClientWrapper = new ExposureNotificationClientWrapper(
        exposureNotificationClient,
        logger);
  }

  @Test
  public void getPackageConfiguration_is1Pt7_returnsPackageConfiguration() {
    when(exposureNotificationClient.getVersion()).thenReturn(Tasks.forResult(1712345L));
    PackageConfiguration expected = new PackageConfigurationBuilder().build();
    when(exposureNotificationClient.getPackageConfiguration())
        .thenReturn(Tasks.forResult(expected));

    PackageConfiguration result = exposureNotificationClientWrapper.getPackageConfiguration()
        .getResult();

    assertThat(expected).isEqualTo(result);
  }

  @Test
  public void getPackageConfiguration_is1Pt6_returnsNull() {
    when(exposureNotificationClient.getVersion()).thenReturn(Tasks.forResult(16345L));
    PackageConfiguration expected = new PackageConfigurationBuilder().build();
    when(exposureNotificationClient.getPackageConfiguration())
        .thenReturn(Tasks.forResult(expected));

    PackageConfiguration result = exposureNotificationClientWrapper.getPackageConfiguration()
        .getResult();

    assertThat(result).isNull();
    verify(exposureNotificationClient, times(0)).getPackageConfiguration();
  }

  @Test
  public void getPackageConfiguration_versionFailure_returnsNull() {
    when(exposureNotificationClient.getVersion()).thenReturn(Tasks.forException(new Exception()));
    PackageConfiguration expected = new PackageConfigurationBuilder().build();
    when(exposureNotificationClient.getPackageConfiguration())
        .thenReturn(Tasks.forResult(expected));

    PackageConfiguration result = exposureNotificationClientWrapper.getPackageConfiguration()
        .getResult();

    assertThat(result).isNull();
    verify(exposureNotificationClient, times(0)).getPackageConfiguration();
  }

  @Test
  public void getPackageConfiguration_packageFailure_returnsPackageConfiguration() {
    when(exposureNotificationClient.getVersion()).thenReturn(Tasks.forResult(1712345L));
    when(exposureNotificationClient.getPackageConfiguration())
        .thenReturn(Tasks.forException(new Exception()));

    boolean successful = exposureNotificationClientWrapper.getPackageConfiguration()
        .isSuccessful();

    assertThat(successful).isFalse();
  }
}
