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

package com.google.android.apps.exposurenotification.onboarding;

import static com.google.common.truth.Truth.assertThat;

import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences.OnboardingStatus;
import com.google.android.apps.exposurenotification.testsupport.ExposureNotificationRules;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Tests of {@link OnboardingViewModel}.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner.class)
@Config(application = HiltTestApplication.class)
public class OnboardingViewModelTest {

  @Rule
  public ExposureNotificationRules rules = ExposureNotificationRules.forTest(this).build();

  @Inject
  ExposureNotificationSharedPreferences exposureNotificationSharedPreferences;

  OnboardingViewModel onboardingViewModel;

  @Before
  public void setup() {
    rules.hilt().inject();
    onboardingViewModel = new OnboardingViewModel(exposureNotificationSharedPreferences);
  }

  @Test
  public void setOnboardedState_true_isStored() {
    assertThat(exposureNotificationSharedPreferences.getOnboardedState())
        .isEqualTo(OnboardingStatus.UNKNOWN);

    onboardingViewModel.setOnboardedState(true);

    assertThat(exposureNotificationSharedPreferences.getOnboardedState())
        .isEqualTo(OnboardingStatus.ONBOARDED);
  }

  @Test
  public void setOnboardedState_false_isStored() {
    assertThat(exposureNotificationSharedPreferences.getOnboardedState())
        .isEqualTo(OnboardingStatus.UNKNOWN);

    onboardingViewModel.setOnboardedState(false);

    assertThat(exposureNotificationSharedPreferences.getOnboardedState())
        .isEqualTo(OnboardingStatus.SKIPPED);
  }

}
