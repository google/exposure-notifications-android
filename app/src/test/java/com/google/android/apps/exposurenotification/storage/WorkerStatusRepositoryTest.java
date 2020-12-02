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

package com.google.android.apps.exposurenotification.storage;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.android.apps.exposurenotification.common.time.RealTimeModule;
import com.google.android.apps.exposurenotification.proto.WorkManagerTask.Status;
import com.google.android.apps.exposurenotification.proto.WorkManagerTask.WorkerTask;
import com.google.android.apps.exposurenotification.testsupport.ExposureNotificationRules;
import com.google.android.apps.exposurenotification.testsupport.FakeClock;
import com.google.android.apps.exposurenotification.testsupport.InMemoryDb;
import com.google.common.base.Optional;
import dagger.hilt.android.testing.BindValue;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import dagger.hilt.android.testing.UninstallModules;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;
import org.threeten.bp.Instant;

/**
 * Tests for operations in {@link DiagnosisRepository}, which serves to also test {@link
 * DiagnosisDao} which it wraps.
 */
@RunWith(AndroidJUnit4.class)
@HiltAndroidTest
@Config(application = HiltTestApplication.class)
@UninstallModules({DbModule.class, RealTimeModule.class})
public class WorkerStatusRepositoryTest {

  @Rule
  public ExposureNotificationRules rules = ExposureNotificationRules.forTest(this).build();

  @BindValue
  ExposureNotificationDatabase db = InMemoryDb.create();
  @BindValue
  Clock clock = new FakeClock();

  @Inject
  WorkerStatusRepository workerStatusRepo;

  @Before
  public void setUp() {
    rules.hilt().inject();
  }

  @Test
  public void getById_forNonExistentId_shouldReturnAbsent() throws Exception {
    // Create a record
    Instant lastRun = Instant.ofEpochMilli(123456789L);
    workerStatusRepo.upsert(WorkerTask.TASK_PROVIDE_DIAGNOSIS_KEYS,
        Status.STATUS_STARTED.toString(), lastRun);

    // But read a different ID and assert nothing is returned.
    assertThat(
        workerStatusRepo.getLastRunTimestamp(WorkerTask.TASK_PROVIDE_DIAGNOSIS_KEYS,
            Status.STATUS_FAIL.toString())).isEqualTo(Optional.absent());
  }

  @Test
  public void upsert_shouldCreateNewEntity_andGetById_shouldReturnIt() throws Exception {
    Instant lastRun = Instant.ofEpochMilli(123456789L);
    workerStatusRepo.upsert(WorkerTask.TASK_PROVIDE_DIAGNOSIS_KEYS,
        Status.STATUS_STARTED.toString(), lastRun);
    assertThat(
        workerStatusRepo.getLastRunTimestamp(WorkerTask.TASK_PROVIDE_DIAGNOSIS_KEYS,
            Status.STATUS_STARTED.toString())).hasValue(lastRun);
  }
}