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

package com.google.android.apps.exposurenotification.common;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static com.google.common.truth.Truth.assertThat;

@RunWith(AndroidJUnit4.class)
@HiltAndroidTest
@Config(application = HiltTestApplication.class)
public class SingleLiveEventTest {

  private AppCompatActivity activity = Robolectric.buildActivity(AppCompatActivity.class).create()
      .resume().get();
  volatile private AtomicInteger sumOfObservedValues = new AtomicInteger(0);
  private Observer<Integer> observer1 = sumOfObservedValues::addAndGet;
  private Observer<Integer> observer2 = sumOfObservedValues::addAndGet;

  @Test
  public void setValue_onlyOneObserverCalled() {
    SingleLiveEvent<Integer> singleLiveEvent = new SingleLiveEvent<>();
    // This simulates orientation change, when new observer is added and receives the same value again
    singleLiveEvent.observe(activity, observer1);
    singleLiveEvent.setValue(1);
    singleLiveEvent.observe(activity, observer2);
    assertThat(sumOfObservedValues.get()).isEqualTo(1);
  }

  @Test
  public void postValue_onlyOneObserverCalled() {
    SingleLiveEvent<Integer> singleLiveEvent = new SingleLiveEvent<>();
    // This simulates orientation change, when new observer is added and receives the same value again
    singleLiveEvent.observe(activity, observer1);
    singleLiveEvent.postValue(1);
    singleLiveEvent.observe(activity, observer2);
    assertThat(sumOfObservedValues.get()).isEqualTo(1);
  }
}