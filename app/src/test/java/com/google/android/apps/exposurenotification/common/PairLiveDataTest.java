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

package com.google.android.apps.exposurenotification.common;

import static com.google.common.truth.Truth.assertThat;

import androidx.lifecycle.MutableLiveData;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.apps.exposurenotification.testsupport.ExposureNotificationRules;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import java.util.ArrayList;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

@RunWith(AndroidJUnit4.class)
@HiltAndroidTest
@Config(application = HiltTestApplication.class)
public class PairLiveDataTest {

  @Rule
  public ExposureNotificationRules rules = ExposureNotificationRules.forTest(this).build();

  @Test
  public void setOneSourceValue_pairLiveDataDoesNotReact() {
    // GIVEN
    List<String> values = new ArrayList<>();
    MutableLiveData<String> firstLiveData = new MutableLiveData<>();
    MutableLiveData<String> secondLiveData = new MutableLiveData<>();
    PairLiveData<String, String> pairLiveData = PairLiveData.of(firstLiveData, secondLiveData);
    pairLiveData.observeForever((first, second) -> {
      values.add(first);
      values.add(second);
    });

    // WHEN
    firstLiveData.setValue("First");

    // THEN
    assertThat(values).isEmpty();
  }

  @Test
  public void updateBothSourceValuesOnce_pairLiveDataReacts() {
    // GIVEN
    List<String> values = new ArrayList<>();
    MutableLiveData<String> firstLiveData = new MutableLiveData<>();
    MutableLiveData<String> secondLiveData = new MutableLiveData<>();
    PairLiveData<String, String> pairLiveData = PairLiveData.of(firstLiveData, secondLiveData);
    pairLiveData.observeForever((first, second) -> {
      values.add(first);
      values.add(second);
    });

    // WHEN
    firstLiveData.setValue("First");
    secondLiveData.setValue("Second");

    // THEN
    assertThat(values).containsExactly("First", "Second");
  }

  @Test
  public void updateBothSourceValuesMultipleTimes_pairLiveDataReactsToEachUpdate() {
    // GIVEN
    List<String> valuesA = new ArrayList<>();
    List<String> valuesB = new ArrayList<>();
    MutableLiveData<String> firstLiveData = new MutableLiveData<>();
    MutableLiveData<String> secondLiveData = new MutableLiveData<>();
    PairLiveData<String, String> pairLiveData = PairLiveData.of(firstLiveData, secondLiveData);
    pairLiveData.observeForever((first, second) -> {
      valuesA.add(first);
      valuesB.add(second);
    });

    // WHEN
    firstLiveData.setValue("A");
    secondLiveData.setValue("X");
    firstLiveData.setValue("B");
    secondLiveData.setValue("Y");

    // THEN
    assertThat(valuesA).containsExactly("A", "B", "B").inOrder();
    assertThat(valuesB).containsExactly( "X", "X", "Y").inOrder();
  }
}
