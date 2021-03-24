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

package com.google.android.apps.exposurenotification.notify;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity.Shared;
import com.google.android.apps.exposurenotification.testsupport.ExposureNotificationRules;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

@RunWith(AndroidJUnit4.class)
@HiltAndroidTest
@Config(application = HiltTestApplication.class)
public class DiagnosisEntityHelperTest {

  @Rule
  public ExposureNotificationRules rules = ExposureNotificationRules.forTest(this).build();

  @Test
  public void hasVerified_verifiedDiagnosis_returnsTrue() {
    // GIVEN
    DiagnosisEntity verifiedDiagnosis = DiagnosisEntity.newBuilder()
        .setVerificationCode("123vc").setLongTermToken("longTermToken_for_123vc").build();

    // THEN
    assertThat(DiagnosisEntityHelper.hasVerified(verifiedDiagnosis)).isTrue();
  }

  @Test
  public void hasVerified_notVerifiedDiagnosis_returnsFalse() {
    // GIVEN
    DiagnosisEntity notVerifiedDiagnosis = DiagnosisEntity.newBuilder().build();
    DiagnosisEntity anotherNotVerifiedDiagnosis = DiagnosisEntity.newBuilder()
        .setVerificationCode("123vc").build();

    // THEN
    assertThat(DiagnosisEntityHelper.hasVerified(notVerifiedDiagnosis)).isFalse();
    assertThat(DiagnosisEntityHelper.hasVerified(anotherNotVerifiedDiagnosis)).isFalse();
  }

  @Test
  public void hasBeenShared_sharedStatus_returnsTrue() {
    // GIVEN
    DiagnosisEntity sharedDiagnosis = DiagnosisEntity.newBuilder()
        .setSharedStatus(Shared.SHARED).build();

    // THEN
    assertThat(DiagnosisEntityHelper.hasBeenShared(sharedDiagnosis)).isTrue();
  }


  @Test
  public void hasBeenShared_notSharedAndNotAttemptedStatuses_returnsFalse() {
    // GIVEN
    DiagnosisEntity notSharedDiagnosis = DiagnosisEntity.newBuilder()
        .setSharedStatus(Shared.NOT_SHARED).build();
    DiagnosisEntity notAttemptedDiagnosis = DiagnosisEntity.newBuilder()
        .setSharedStatus(Shared.NOT_ATTEMPTED).build();

    // THEN
    assertThat(DiagnosisEntityHelper.hasBeenShared(notSharedDiagnosis)).isFalse();
    assertThat(DiagnosisEntityHelper.hasBeenShared(notAttemptedDiagnosis)).isFalse();
  }

}
