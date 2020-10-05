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

package com.google.android.apps.exposurenotification.notify;

import static com.google.common.truth.Truth.assertThat;

import com.google.android.apps.exposurenotification.storage.DbModule;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity;
import com.google.android.apps.exposurenotification.storage.DiagnosisRepository;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationDatabase;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences.OnboardingStatus;
import com.google.android.apps.exposurenotification.testsupport.ExposureNotificationRules;
import com.google.android.apps.exposurenotification.testsupport.InMemoryDb;
import dagger.hilt.android.testing.BindValue;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import dagger.hilt.android.testing.UninstallModules;
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
@UninstallModules({DbModule.class})
public class NotifyHomeViewModelTest {

  @Rule
  public ExposureNotificationRules rules = ExposureNotificationRules.forTest(this).build();

  @BindValue
  ExposureNotificationDatabase database = InMemoryDb.create();

  @Inject
  DiagnosisRepository diagnosisRepository;
  @Inject
  ExposureNotificationSharedPreferences exposureNotificationSharedPreferences;

  private NotifyHomeViewModel notifyHomeViewModel;

  @Before
  public void setup() {
    rules.hilt().inject();
    notifyHomeViewModel = new NotifyHomeViewModel(diagnosisRepository,
        exposureNotificationSharedPreferences);
  }

  @Test
  public void diagnosisRepository_upsertAsync_deliversDiagnosisEntityToObservers() {
    List<DiagnosisEntity> diagnosisEntityList = new ArrayList<>();
    notifyHomeViewModel.getAllDiagnosisEntityLiveData().observeForever(diagnosisEntityList::addAll);

    diagnosisRepository.upsertAsync(DiagnosisEntity.newBuilder().setId(12345L).build());

    assertThat(diagnosisEntityList).hasSize(1);
    assertThat(diagnosisEntityList.get(0).getId()).isEqualTo(12345L);
  }
}