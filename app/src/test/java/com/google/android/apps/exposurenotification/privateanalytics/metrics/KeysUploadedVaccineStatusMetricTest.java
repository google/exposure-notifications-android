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

package com.google.android.apps.exposurenotification.privateanalytics.metrics;

import static com.google.android.apps.exposurenotification.privateanalytics.metrics.KeysUploadedVaccineStatusMetric.BIN_KEY_UPLOADED_NO_PAST_EN;
import static com.google.android.apps.exposurenotification.privateanalytics.metrics.KeysUploadedVaccineStatusMetric.BIN_KEY_UPLOADED_NO_PAST_EN_UPLOADER_NOT_VACCINATED;
import static com.google.android.apps.exposurenotification.privateanalytics.metrics.KeysUploadedVaccineStatusMetric.BIN_KEY_UPLOADED_NO_PAST_EN_UPLOADER_VACCINATED;
import static com.google.android.apps.exposurenotification.privateanalytics.metrics.KeysUploadedVaccineStatusMetric.BIN_KEY_UPLOADED_PAST_EN;
import static com.google.android.apps.exposurenotification.privateanalytics.metrics.KeysUploadedVaccineStatusMetric.BIN_KEY_UPLOADED_PAST_EN_CASE_NOT_VACCINATED;
import static com.google.android.apps.exposurenotification.privateanalytics.metrics.KeysUploadedVaccineStatusMetric.BIN_KEY_UPLOADED_PAST_EN_UPLOADER_NOT_VACCINATED;
import static com.google.android.apps.exposurenotification.privateanalytics.metrics.KeysUploadedVaccineStatusMetric.BIN_KEY_UPLOADED_PAST_EN_UPLOADER_NOT_VACCINATED_CASE_NOT_VACCINATED;
import static com.google.android.apps.exposurenotification.privateanalytics.metrics.KeysUploadedVaccineStatusMetric.BIN_KEY_UPLOADED_PAST_EN_UPLOADER_VACCINATED;
import static com.google.android.apps.exposurenotification.privateanalytics.metrics.KeysUploadedVaccineStatusMetric.BIN_KEY_UPLOADED_PAST_EN_UPLOADER_VACCINATED_CASE_NOT_VACCINATED;
import static com.google.android.apps.exposurenotification.privateanalytics.metrics.KeysUploadedVaccineStatusMetric.BIN_LENGTH;
import static com.google.android.apps.exposurenotification.privateanalytics.metrics.KeysUploadedVaccineStatusMetric.BIN_NOT_VACCINATED;
import static com.google.android.apps.exposurenotification.privateanalytics.metrics.KeysUploadedVaccineStatusMetric.BIN_UPLOADED;
import static com.google.android.apps.exposurenotification.privateanalytics.metrics.KeysUploadedVaccineStatusMetric.BIN_VACCINATED;
import static com.google.common.truth.Truth.assertThat;

import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.android.apps.exposurenotification.common.time.RealTimeModule;
import com.google.android.apps.exposurenotification.riskcalculation.ExposureClassification;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences.VaccinationStatus;
import com.google.android.apps.exposurenotification.testsupport.ExposureNotificationRules;
import com.google.android.apps.exposurenotification.testsupport.FakeClock;
import com.google.common.primitives.Ints;
import dagger.hilt.android.testing.BindValue;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import dagger.hilt.android.testing.UninstallModules;
import java.util.List;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.threeten.bp.Duration;
import org.threeten.bp.Instant;

/**
 * Tests of {@link KeysUploadedVaccineStatusMetric}.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner.class)
@Config(application = HiltTestApplication.class)
@UninstallModules({RealTimeModule.class})
public class KeysUploadedVaccineStatusMetricTest {

  @Rule
  public ExposureNotificationRules rules = ExposureNotificationRules.forTest(this).build();

  @Inject
  KeysUploadedVaccineStatusMetric keysUploadedVaccineStatusMetricMetric;

  @Inject
  ExposureNotificationSharedPreferences exposureNotificationSharedPreferences;
  @BindValue
  Clock clock = new FakeClock();

  @Before
  public void setup() {
    rules.hilt().inject();
    exposureNotificationSharedPreferences.setPrivateAnalyticsState(true);
  }

  @Test
  public void testGetDataVector_keyUploadBeforeWorkerLastRun_noKeyUpload() throws Exception {
    // GIVEN
    Instant workerRunTime = clock.now().minus(Duration.ofDays(2));
    Instant keyUploadTime = workerRunTime.minus(Duration.ofDays(1));

    exposureNotificationSharedPreferences.setPrivateAnalyticsWorkerLastTime(workerRunTime);
    exposureNotificationSharedPreferences
        .setLastVaccinationResponse(keyUploadTime, VaccinationStatus.VACCINATED);

    // WHEN
    List<Integer> vector = keysUploadedVaccineStatusMetricMetric.getDataVector().get();

    // THEN
    assertThat(vector)
        .containsExactlyElementsIn(vectorNoKeyUpload())
        .inOrder();
  }

  @Test
  public void testGetDataVector_keyUploadedVaccinatedNoEnInThePast_returnsCorrectVector()
      throws Exception {
    // GIVEN
    Instant keyUploadTime = clock.now().minus(Duration.ofDays(1));
    Instant workerRunTime = clock.now().minus(Duration.ofDays(2));
    ExposureClassification exposureClassification = ExposureClassification
        .create(ExposureClassification.NO_EXPOSURE_CLASSIFICATION_INDEX,
            ExposureClassification.NO_EXPOSURE_CLASSIFICATION_NAME, workerRunTime.toEpochMilli());

    exposureNotificationSharedPreferences.setPrivateAnalyticsWorkerLastTime(workerRunTime);
    exposureNotificationSharedPreferences
        .setLastVaccinationResponse(keyUploadTime, VaccinationStatus.VACCINATED);
    exposureNotificationSharedPreferences.setExposureClassification(exposureClassification);

    // WHEN
    List<Integer> vector = keysUploadedVaccineStatusMetricMetric.getDataVector().get();

    // THEN
    assertThat(vector)
        .containsExactlyElementsIn(getVaccinatedNoEnInThePast())
        .inOrder();
  }

  @Test
  public void testGetDataVector_keyUploadedVaccinatedWithEnInThePast_returnsCorrectVector()
      throws Exception {
    // GIVEN
    Instant keyUploadTime = clock.now().minus(Duration.ofDays(1));
    Instant workerRunTime = clock.now().minus(Duration.ofDays(2));
    ExposureClassification exposureClassification = ExposureClassification
        .create(ExposureClassification.NO_EXPOSURE_CLASSIFICATION_INDEX + 1, "exposed",
            workerRunTime.toEpochMilli());

    exposureNotificationSharedPreferences.setPrivateAnalyticsWorkerLastTime(workerRunTime);
    exposureNotificationSharedPreferences
        .setLastVaccinationResponse(keyUploadTime, VaccinationStatus.VACCINATED);
    exposureNotificationSharedPreferences.setExposureClassification(exposureClassification);

    // WHEN
    List<Integer> vector = keysUploadedVaccineStatusMetricMetric.getDataVector().get();

    // THEN
    assertThat(vector)
        .containsExactlyElementsIn(getVaccinatedEnInThePast())
        .inOrder();
  }

  @Test
  public void testGetDataVector_keyUploadedNotVaccinatedNoEnInThePast_returnsCorrectVector()
      throws Exception {
    // GIVEN
    Instant keyUploadTime = clock.now().minus(Duration.ofDays(1));
    Instant workerRunTime = clock.now().minus(Duration.ofDays(2));
    ExposureClassification exposureClassification = ExposureClassification
        .create(ExposureClassification.NO_EXPOSURE_CLASSIFICATION_INDEX,
            ExposureClassification.NO_EXPOSURE_CLASSIFICATION_NAME, workerRunTime.toEpochMilli());

    exposureNotificationSharedPreferences.setPrivateAnalyticsWorkerLastTime(workerRunTime);
    exposureNotificationSharedPreferences
        .setLastVaccinationResponse(keyUploadTime, VaccinationStatus.NOT_VACCINATED);
    exposureNotificationSharedPreferences.setExposureClassification(exposureClassification);

    // WHEN
    List<Integer> vector = keysUploadedVaccineStatusMetricMetric.getDataVector().get();

    // THEN
    assertThat(vector)
        .containsExactlyElementsIn(getNotVaccinatedNoEnInThePast())
        .inOrder();
  }

  @Test
  public void testGetDataVector_keyUploadedNotVaccinatedWithEnInThePast_returnsCorrectVector()
      throws Exception {
    // GIVEN
    Instant keyUploadTime = clock.now().minus(Duration.ofDays(1));
    Instant workerRunTime = clock.now().minus(Duration.ofDays(2));
    ExposureClassification exposureClassification = ExposureClassification
        .create(ExposureClassification.NO_EXPOSURE_CLASSIFICATION_INDEX + 1, "exposed",
            workerRunTime.toEpochMilli());

    exposureNotificationSharedPreferences.setPrivateAnalyticsWorkerLastTime(workerRunTime);
    exposureNotificationSharedPreferences
        .setLastVaccinationResponse(keyUploadTime, VaccinationStatus.NOT_VACCINATED);
    exposureNotificationSharedPreferences.setExposureClassification(exposureClassification);

    // WHEN
    List<Integer> vector = keysUploadedVaccineStatusMetricMetric.getDataVector().get();

    // THEN
    assertThat(vector)
        .containsExactlyElementsIn(getNotVaccinatedEnInThePast())
        .inOrder();
  }


  @Test
  public void testHammingWeight() {
    assertThat(keysUploadedVaccineStatusMetricMetric.getMetricHammingWeight()).isEqualTo(0);
  }

  private static List<Integer> vectorEmpty() {
    int[] retVector = new int[BIN_LENGTH];
    return Ints.asList(retVector);
  }

  private static List<Integer> vectorNoKeyUpload() {
    List<Integer> retVector = vectorEmpty();
    return retVector;
  }

  private static List<Integer> getVaccinatedNoEnInThePast() {
    List<Integer> vector = vectorEmpty();
    vector.set(BIN_UPLOADED, 1); // key uploaded
    vector.set(BIN_VACCINATED, 1); // Vaccinated
    vector.set(BIN_KEY_UPLOADED_NO_PAST_EN, 1); // no En in the past
    // Vaccinated + no En in the past
    vector.set(BIN_KEY_UPLOADED_NO_PAST_EN_UPLOADER_VACCINATED, 1);
    return vector;
  }

  private static List<Integer> getVaccinatedEnInThePast() {
    List<Integer> vector = vectorEmpty();
    vector.set(BIN_UPLOADED, 1); // key uploaded
    vector.set(BIN_VACCINATED, 1); // Vaccinated
    vector.set(BIN_KEY_UPLOADED_PAST_EN, 1); // En in the past
    vector.set(BIN_KEY_UPLOADED_PAST_EN_UPLOADER_VACCINATED, 1); // Vaccinated + En in the past

    // En in the past + case not vaccinated
    vector.set(BIN_KEY_UPLOADED_PAST_EN_CASE_NOT_VACCINATED, 1);
    // Vaccinated + En in the past + case not vaccinated
    vector.set(BIN_KEY_UPLOADED_PAST_EN_UPLOADER_VACCINATED_CASE_NOT_VACCINATED, 1);
    return vector;
  }

  private static List<Integer> getNotVaccinatedNoEnInThePast() {
    List<Integer> vector = vectorEmpty();
    vector.set(BIN_UPLOADED, 1); // key uploaded
    vector.set(BIN_NOT_VACCINATED, 1); // Not vaccinated
    vector.set(BIN_KEY_UPLOADED_NO_PAST_EN, 1); // No En in the past
    // Not vaccinated + no En in the past
    vector.set(BIN_KEY_UPLOADED_NO_PAST_EN_UPLOADER_NOT_VACCINATED, 1);
    return vector;
  }

  private static List<Integer> getNotVaccinatedEnInThePast() {
    List<Integer> vector = vectorEmpty();
    vector.set(BIN_UPLOADED, 1); // key uploaded
    vector.set(BIN_NOT_VACCINATED, 1); // Not vaccinated
    vector.set(BIN_KEY_UPLOADED_PAST_EN_UPLOADER_NOT_VACCINATED, 1); // En in the past
    vector.set(BIN_KEY_UPLOADED_PAST_EN, 1); // Not vaccinated + En in the past

    // En in the past + case not vaccinated
    vector.set(BIN_KEY_UPLOADED_PAST_EN_CASE_NOT_VACCINATED, 1);
    // Not vaccinated + En in the past + case not vaccinated
    vector.set(BIN_KEY_UPLOADED_PAST_EN_UPLOADER_NOT_VACCINATED_CASE_NOT_VACCINATED, 1);
    return vector;
  }
}
