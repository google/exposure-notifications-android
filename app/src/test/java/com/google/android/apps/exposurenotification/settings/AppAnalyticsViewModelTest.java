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

package com.google.android.apps.exposurenotification.settings;

import static com.google.common.truth.Truth.assertThat;

import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.apps.exposurenotification.testsupport.ExposureNotificationRules;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@HiltAndroidTest
@RunWith(RobolectricTestRunner.class)
@Config(application = HiltTestApplication.class)
public class AppAnalyticsViewModelTest {

  @Rule
  public ExposureNotificationRules rules = ExposureNotificationRules.forTest(this).build();

  @Inject
  ExposureNotificationSharedPreferences exposureNotificationSharedPreferences;

  private AppAnalyticsViewModel appAnalyticsViewModel;

  @Before
  public void setup() {
    rules.hilt().inject();
    appAnalyticsViewModel = new AppAnalyticsViewModel(exposureNotificationSharedPreferences);
  }

  @Test
  public void getAppAnalyticsLiveData_defaultFalse() {
    List<Boolean> observedValues = new ArrayList<>();
    appAnalyticsViewModel.getAppAnalyticsLiveData().observeForever(observedValues::add);

    assertThat(observedValues).containsExactly(false);
  }

  @Test
  public void getAppAnalyticsLiveData_initialValueFalse() {
    List<Boolean> observedValues = new ArrayList<>();

    // Set the initial value.
    appAnalyticsViewModel.setAppAnalyticsState(false);
    // Now begin observing.
    appAnalyticsViewModel.getAppAnalyticsLiveData().observeForever(observedValues::add);

    assertThat(observedValues).containsExactly(false);
  }

  @Test
  public void setAppAnalyticsState_trueThenFalse_observedInLiveData() {
    List<Boolean> observedValues = new ArrayList<>();
    appAnalyticsViewModel.getAppAnalyticsLiveData().observeForever(observedValues::add);

    appAnalyticsViewModel.setAppAnalyticsState(true);
    appAnalyticsViewModel.setAppAnalyticsState(false);

    assertThat(observedValues).containsExactly(false, true, false).inOrder();
  }

}