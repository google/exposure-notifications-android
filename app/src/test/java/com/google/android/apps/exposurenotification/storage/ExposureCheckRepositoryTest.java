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

package com.google.android.apps.exposurenotification.storage;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.android.apps.exposurenotification.common.time.RealTimeModule;
import com.google.android.apps.exposurenotification.testsupport.ExposureNotificationRules;
import com.google.android.apps.exposurenotification.testsupport.FakeClock;
import com.google.android.apps.exposurenotification.testsupport.InMemoryDb;
import com.google.common.collect.ImmutableList;
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
import org.robolectric.annotation.Config;
import org.threeten.bp.Duration;
import org.threeten.bp.Instant;

/**
 * Tests for operations in {@link ExposureCheckRepository} and the underlying DAO.
 */
@RunWith(AndroidJUnit4.class)
@HiltAndroidTest
@Config(application = HiltTestApplication.class)
@UninstallModules({DbModule.class, RealTimeModule.class})
public class ExposureCheckRepositoryTest {

  // Used to determine the threshold beyond which we mark the exposure checks as obsolete.
  // Currently, all exposure checks captured earlier than two weeks ago are deemed obsolete.
  private static final Duration TWO_WEEKS = Duration.ofDays(14);
  // Number of exposure checks we want to display on the UI.
  private static final int NUM_CHECKS_TO_DISPLAY = 5;

  @Rule
  public ExposureNotificationRules rules = ExposureNotificationRules.forTest(this).build();

  @Inject
  ExposureCheckRepository exposureCheckRepository;

  @BindValue
  ExposureNotificationDatabase db = InMemoryDb.create();
  @BindValue
  Clock clock = new FakeClock();

  private Instant earliestThreshold;

  @Before
  public void setUp() {
    rules.hilt().inject();
    earliestThreshold = clock.now().minus(TWO_WEEKS);
  }

  @Test
  public void getLastXExposureChecksLiveData_returnsLastNumChecksToRetrieveChecks() {
    // GIVEN
    List<ExposureCheckEntity> entities = getEntitiesWithoutObsoletes();
    List<ExposureCheckEntity> expectedChecks = entities
        .subList(entities.size() - NUM_CHECKS_TO_DISPLAY, entities.size());
    for (ExposureCheckEntity entity : entities) {
      exposureCheckRepository.insertExposureCheck(entity);
    }

    // WHEN
    List<ExposureCheckEntity> retrievedChecks = new ArrayList<>();
    exposureCheckRepository
        .getLastXExposureChecksLiveData(NUM_CHECKS_TO_DISPLAY)
        .observeForever(retrievedChecks::addAll);

    // THEN
    assertThat(retrievedChecks).hasSize(NUM_CHECKS_TO_DISPLAY);
    assertThat(retrievedChecks).containsExactlyElementsIn(expectedChecks);
  }

  @Test
  public void insertLessThanNumChecksToRetrieveChecks_getLastXExposureChecksLiveData_returnsAll() {
    // GIVEN
    List<ExposureCheckEntity> expectedChecks = ImmutableList.of(
        ExposureCheckEntity.create(earliestThreshold.plus(Duration.ofDays(1))),
        ExposureCheckEntity.create(earliestThreshold.plus(Duration.ofDays(2))),
        ExposureCheckEntity.create(earliestThreshold.plus(Duration.ofDays(3))));
    for (ExposureCheckEntity entity : expectedChecks) {
      exposureCheckRepository.insertExposureCheck(entity);
    }

    // WHEN
    List<ExposureCheckEntity> retrievedChecks = new ArrayList<>();
    exposureCheckRepository
        .getLastXExposureChecksLiveData(NUM_CHECKS_TO_DISPLAY)
        .observeForever(retrievedChecks::addAll);

    // THEN
    assertThat(retrievedChecks).hasSize(expectedChecks.size());
    assertThat(retrievedChecks).containsExactlyElementsIn(expectedChecks);
  }

  @Test
  public void insertChecks_deleteObsoleteChecksIfAny_noObsoletes_doesNotDelete() {
    // GIVEN
    List<ExposureCheckEntity> expectedChecks = getEntitiesWithoutObsoletes();
    for (ExposureCheckEntity entity : expectedChecks) {
      exposureCheckRepository.insertExposureCheck(entity);
    }

    // WHEN
    exposureCheckRepository.deleteObsoleteChecksIfAny(earliestThreshold);
    List<ExposureCheckEntity> retrievedChecks = exposureCheckRepository.getAllExposureChecks();

    // THEN
    assertThat(retrievedChecks).containsExactlyElementsIn(expectedChecks);
  }

  @Test
  public void insertChecks_deleteObsoleteChecksIfAny_deletesObsoletes() {
    // GIVEN
    List<ExposureCheckEntity> entities = getEntitiesWithObsoletes();
    List<ExposureCheckEntity> expectedChecks = entities.subList(2, entities.size());
    for (ExposureCheckEntity entity : expectedChecks) {
      exposureCheckRepository.insertExposureCheck(entity);
    }

    // WHEN
    exposureCheckRepository.deleteObsoleteChecksIfAny(earliestThreshold);
    List<ExposureCheckEntity> retrievedChecks = exposureCheckRepository.getAllExposureChecks();

    // THEN
    assertThat(retrievedChecks).containsExactlyElementsIn(expectedChecks);
  }

  private List<ExposureCheckEntity> getEntitiesWithObsoletes() {
    return ImmutableList.of(
        ExposureCheckEntity.create(earliestThreshold.minus(Duration.ofDays(2))),
        ExposureCheckEntity.create(earliestThreshold.minus(Duration.ofDays(1))),
        ExposureCheckEntity.create(earliestThreshold.plus(Duration.ofDays(1))),
        ExposureCheckEntity.create(earliestThreshold.plus(Duration.ofDays(2))),
        ExposureCheckEntity.create(earliestThreshold.plus(Duration.ofDays(3))),
        ExposureCheckEntity.create(earliestThreshold.plus(Duration.ofDays(4)))
    );
  }

  private List<ExposureCheckEntity> getEntitiesWithoutObsoletes() {
    return ImmutableList.of(
        ExposureCheckEntity.create(earliestThreshold.plus(Duration.ofDays(1))),
        ExposureCheckEntity.create(earliestThreshold.plus(Duration.ofDays(2))),
        ExposureCheckEntity.create(earliestThreshold.plus(Duration.ofDays(3))),
        ExposureCheckEntity.create(earliestThreshold.plus(Duration.ofDays(4))),
        ExposureCheckEntity.create(earliestThreshold.plus(Duration.ofDays(5))),
        ExposureCheckEntity.create(earliestThreshold.plus(Duration.ofDays(6))),
        ExposureCheckEntity.create(earliestThreshold.plus(Duration.ofDays(7)))
    );
  }
}
