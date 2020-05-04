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

package com.google.android.apps.exposurenotification.network;

import static com.google.common.truth.Truth.assertThat;

import android.support.test.espresso.core.internal.deps.guava.collect.ImmutableList;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Tests of {@link DiagnosisKeys}. */
@RunWith(RobolectricTestRunner.class)
public class DiagnosisKeysTest {

  private DiagnosisKeys keys;

  @Before
  public void setup() {
    keys = new DiagnosisKeys(ApplicationProvider.getApplicationContext());
  }

  @Test
  public void download_shouldReturnEmptyListOfFiles() throws Exception {
    assertThat(keys.download().get()).isEmpty();
  }

  @Test
  public void upload_shouldReturnNUllValuedFuture() throws Exception {
    assertThat(keys.upload(ImmutableList.of()).get()).isNull();
  }
}
