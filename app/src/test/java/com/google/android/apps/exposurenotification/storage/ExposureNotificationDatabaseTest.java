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
import com.google.android.apps.exposurenotification.testsupport.InMemoryDb;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

/**
 * Basic test for {@link ExposureNotificationDatabase}. Dao's tested separately.
 */
@RunWith(AndroidJUnit4.class)
@HiltAndroidTest
@Config(application = HiltTestApplication.class)
public class ExposureNotificationDatabaseTest {

  private final ExposureNotificationDatabase database = InMemoryDb.create();

  @Test
  public void testDatabase() {
    assertThat(database).isNotNull();
    assertThat(database.exposureDao()).isNotNull();
    assertThat(database.diagnosisDao()).isNotNull();
  }

}