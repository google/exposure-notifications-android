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

package com.google.android.apps.exposurenotification.network;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowTelephonyManager;

/**
 * Tests of {@link DownloadController}.
 */
@RunWith(RobolectricTestRunner.class)
@Config(shadows = {ShadowTelephonyManager.class})
public class DownloadControllerTest {

  private ShadowTelephonyManager telephonyManager;

  private DownloadController controller;

  @Before
  public void setup() {
    Context context = ApplicationProvider.getApplicationContext();
    telephonyManager = Shadow.extract(context.getSystemService(Context.TELEPHONY_SERVICE));
    telephonyManager.setNetworkCountryIso("US");
    controller = new DownloadController(context);
  }

  @Test
  public void download_shouldReturnSampleFile() throws Exception {
    assertThat(controller.download().get()).hasSize(1);
  }
}
