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

package com.google.android.apps.exposurenotification.activities;

import static com.google.common.truth.Truth.assertThat;

import android.os.Bundle;
import androidx.fragment.app.FragmentFactory;
import androidx.fragment.app.testing.FragmentScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.apps.exposurenotification.R;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests of {@link HomeFragment}.
 */
@RunWith(AndroidJUnit4.class)
@Ignore // Until androidx fixes b/147803993 and b/118495999.
public class HomeFragmentTest {

  @Test
  public void launch_isNotNull() {
    FragmentScenario<HomeFragment> fragmentScenario =
        FragmentScenario.launch(
            HomeFragment.class, new Bundle(), R.style.ExposureNotification, new FragmentFactory());

    assertThat(fragmentScenario).isNotNull();
  }
}
