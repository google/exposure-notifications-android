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

package com.google.android.apps.exposurenotification.exposure;

import static com.google.common.truth.Truth.assertThat;

import androidx.lifecycle.LiveData;
import com.google.android.apps.exposurenotification.riskcalculation.ExposureClassification;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences.BadgeStatus;
import com.google.android.apps.exposurenotification.testsupport.ExposureNotificationRules;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import java.util.concurrent.atomic.AtomicReference;
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
public class ExposureHomeViewModelTest {

  @Rule
  public ExposureNotificationRules rules = ExposureNotificationRules.forTest(this).build();

  @Inject
  ExposureNotificationSharedPreferences exposureNotificationSharedPreferences;

  private ExposureHomeViewModel exposureHomeViewModel;

  @Before
  public void setup() {
    rules.hilt().inject();
    exposureHomeViewModel = new ExposureHomeViewModel(
        exposureNotificationSharedPreferences);
  }

  @Test
  public void getExposureClassificationLiveData_deliversExposureClassificationToObservers() {
    ExposureClassification exposureClassification = ExposureClassification
        .create(0, "exposureClassification", 0L);
    AtomicReference<ExposureClassification> exposureClassificationAtomicReference = new AtomicReference<>();

    LiveData<ExposureClassification> liveData = exposureHomeViewModel
        .getExposureClassificationLiveData();
    liveData.observeForever(exposureClassificationAtomicReference::set);
    exposureNotificationSharedPreferences.setExposureClassification(exposureClassification);

    assertThat(exposureClassificationAtomicReference.get().getClassificationName())
        .isEqualTo("exposureClassification");
  }

  @Test
  public void getExposureClassification_returnsExposureClassificationThatWasSet() {
    ExposureClassification exposureClassification = ExposureClassification
        .create(0, "exposureClassification", 0L);
    exposureNotificationSharedPreferences.setExposureClassification(exposureClassification);

    ExposureClassification exposureClassification1 = exposureHomeViewModel
        .getExposureClassification();

    assertThat(exposureClassification1.getClassificationName())
        .isEqualTo("exposureClassification");
  }

  @Test
  public void getIsExposureClassificationRevoked_returnsExposureClassificationThatWasSet() {
    assertThat(exposureHomeViewModel.getIsExposureClassificationRevoked()).isFalse();
    exposureNotificationSharedPreferences.setIsExposureClassificationRevoked(true);
    assertThat(exposureHomeViewModel.getIsExposureClassificationRevoked()).isTrue();
  }

  @Test
  public void getIsExposureClassificationNewLiveData_deliversBadgeStatusToObservers() {
    BadgeStatus badgeStatus = BadgeStatus.DISMISSED;
    AtomicReference<BadgeStatus> badgeStatusAtomicReference = new AtomicReference<>();

    LiveData<BadgeStatus> liveData = exposureHomeViewModel
        .getIsExposureClassificationNewLiveData();
    liveData.observeForever(badgeStatusAtomicReference::set);
    exposureNotificationSharedPreferences.setIsExposureClassificationNewAsync(badgeStatus);

    assertThat(badgeStatusAtomicReference.get())
        .isEqualTo(BadgeStatus.DISMISSED);
  }

  @Test
  public void getIsExposureClassificationDateNewLiveData_deliversBadgeStatusToObservers() {
    BadgeStatus badgeStatus = BadgeStatus.DISMISSED;
    AtomicReference<BadgeStatus> badgeStatusAtomicReference = new AtomicReference<>();

    LiveData<BadgeStatus> liveData = exposureHomeViewModel
        .getIsExposureClassificationDateNewLiveData();
    liveData.observeForever(badgeStatusAtomicReference::set);
    exposureNotificationSharedPreferences.setIsExposureClassificationDateNewAsync(badgeStatus);

    assertThat(badgeStatusAtomicReference.get())
        .isEqualTo(BadgeStatus.DISMISSED);
  }

  @Test
  public void tryTransitionExposureClassificationNew_valueIsSet() {
    BadgeStatus from = BadgeStatus.SEEN;
    BadgeStatus to = BadgeStatus.DISMISSED;
    exposureNotificationSharedPreferences.setIsExposureClassificationNewAsync(BadgeStatus.SEEN);

    exposureHomeViewModel.tryTransitionExposureClassificationNew(from, to);

    assertThat(exposureNotificationSharedPreferences.getIsExposureClassificationNew())
        .isEqualTo(BadgeStatus.DISMISSED);
  }

  @Test
  public void tryTransitionExposureClassificationNew_valueNotSet() {
    BadgeStatus from = BadgeStatus.SEEN;
    BadgeStatus to = BadgeStatus.DISMISSED;
    exposureNotificationSharedPreferences.setIsExposureClassificationNewAsync(BadgeStatus.NEW);

    exposureHomeViewModel.tryTransitionExposureClassificationNew(from, to);

    assertThat(exposureNotificationSharedPreferences.getIsExposureClassificationNew())
        .isEqualTo(BadgeStatus.NEW);
  }

  @Test
  public void tryTransitionExposureClassificationDateNew_valueIsSet() {
    BadgeStatus from = BadgeStatus.SEEN;
    BadgeStatus to = BadgeStatus.DISMISSED;
    exposureNotificationSharedPreferences.setIsExposureClassificationDateNewAsync(BadgeStatus.SEEN);

    exposureHomeViewModel.tryTransitionExposureClassificationDateNew(from, to);

    assertThat(exposureNotificationSharedPreferences.getIsExposureClassificationDateNew())
        .isEqualTo(BadgeStatus.DISMISSED);
  }

  @Test
  public void tryTransitionExposureClassificationDateNew_valueNotSet() {
    BadgeStatus from = BadgeStatus.SEEN;
    BadgeStatus to = BadgeStatus.DISMISSED;
    exposureNotificationSharedPreferences.setIsExposureClassificationDateNewAsync(BadgeStatus.NEW);

    exposureHomeViewModel.tryTransitionExposureClassificationDateNew(from, to);

    assertThat(exposureNotificationSharedPreferences.getIsExposureClassificationDateNew())
        .isEqualTo(BadgeStatus.NEW);
  }
}