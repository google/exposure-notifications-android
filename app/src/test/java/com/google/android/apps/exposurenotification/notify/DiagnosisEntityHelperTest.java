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
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.android.apps.exposurenotification.common.time.RealTimeModule;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity.Shared;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity.TestResult;
import com.google.android.apps.exposurenotification.testsupport.ExposureNotificationRules;
import com.google.android.apps.exposurenotification.testsupport.FakeClock;
import dagger.hilt.android.testing.BindValue;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import dagger.hilt.android.testing.UninstallModules;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;
import org.threeten.bp.Duration;
import org.threeten.bp.Instant;

@RunWith(AndroidJUnit4.class)
@HiltAndroidTest
@Config(application = HiltTestApplication.class)
@UninstallModules({RealTimeModule.class})
public class DiagnosisEntityHelperTest {

  @Rule
  public ExposureNotificationRules rules = ExposureNotificationRules.forTest(this).build();

  @BindValue
  Clock clock = new FakeClock();

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

  @Test
  public void isWithinLast14Days_datesNotWithinLast14Days_returnsFalse() {
    Instant threeWeeksAgo = clock.now().minus(Duration.ofDays(21));
    Instant threeHoursIntoFuture = clock.now().plus(Duration.ofHours(3));
    Instant threeWeeksIntoFuture = clock.now().plus(Duration.ofDays(21));

    assertThat(DiagnosisEntityHelper.isWithinLast14Days(clock, threeWeeksAgo.toEpochMilli()))
        .isFalse();
    assertThat(DiagnosisEntityHelper.isWithinLast14Days(clock, threeHoursIntoFuture.toEpochMilli()))
        .isFalse();
    assertThat(DiagnosisEntityHelper.isWithinLast14Days(clock, threeWeeksIntoFuture.toEpochMilli()))
        .isFalse();
  }

  @Test
  public void isWithinLast14Days_datesWithinLast14Days_returnsTrue() {
    Instant dateToday = clock.now();
    Instant anotherDateToday = clock.now().minus(Duration.ofHours(2));
    Instant twoDaysAgo = clock.now().minus(Duration.ofDays(2));

    assertThat(DiagnosisEntityHelper.isWithinLast14Days(clock, dateToday.toEpochMilli())).isTrue();
    assertThat(DiagnosisEntityHelper.isWithinLast14Days(clock, anotherDateToday.toEpochMilli()))
        .isTrue();
    assertThat(DiagnosisEntityHelper.isWithinLast14Days(clock, twoDaysAgo.toEpochMilli())).isTrue();
  }

  @Test
  public void isNotInFuture_datesInFuture_returnsFalse() {
    Instant tomorrow = clock.now().plus(Duration.ofDays(1));
    Instant twoWeeksIntoFuture = clock.now().plus(Duration.ofDays(14));

    assertThat(DiagnosisEntityHelper.isNotInFuture(clock, tomorrow.toEpochMilli())).isFalse();
    assertThat(DiagnosisEntityHelper.isNotInFuture(clock, twoWeeksIntoFuture.toEpochMilli()))
        .isFalse();
  }

  @Test
  public void isNotInFuture_datesNotInFuture_returnsTrue() {
    Instant today = clock.now();
    Instant twoDaysAgo = clock.now().minus(Duration.ofDays(2));
    Instant threeWeeksAgo = clock.now().minus(Duration.ofDays(21));

    assertThat(DiagnosisEntityHelper.isNotInFuture(clock, today.toEpochMilli())).isTrue();
    assertThat(DiagnosisEntityHelper.isNotInFuture(clock, twoDaysAgo.toEpochMilli())).isTrue();
    assertThat(DiagnosisEntityHelper.isNotInFuture(clock, threeWeeksAgo.toEpochMilli())).isTrue();
  }

  @Test
  public void getTestResultStringResource_testLikely_returnsLikelyString() {
    int stringres =
        DiagnosisEntityHelper.getDiagnosisTypeStringResourceFromTestResult(TestResult.LIKELY);

    assertThat(stringres).isEqualTo(R.string.test_result_type_likely);
  }

  @Test
  public void getTestResultStringResource_testNegative_returnsNegativeString() {
    int stringres =
        DiagnosisEntityHelper.getDiagnosisTypeStringResourceFromTestResult(TestResult.NEGATIVE);

    assertThat(stringres).isEqualTo(R.string.test_result_type_negative);
  }

  @Test
  public void getTestResultStringResource_testUserReport_returnsConfirmedString() {
    int stringres =
        DiagnosisEntityHelper.getDiagnosisTypeStringResourceFromTestResult(TestResult.USER_REPORT);

    assertThat(stringres).isEqualTo(R.string.test_result_type_confirmed);
  }

  @Test
  public void getTestResultStringResource_testNull_returnsConfirmedString() {
    int stringres =
        DiagnosisEntityHelper.getDiagnosisTypeStringResourceFromTestResult(null);

    assertThat(stringres).isEqualTo(R.string.test_result_type_confirmed);
  }

}
