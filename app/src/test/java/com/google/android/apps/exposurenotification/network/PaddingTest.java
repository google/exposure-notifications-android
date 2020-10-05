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
import static com.google.common.truth.Truth.assertWithMessage;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.apps.exposurenotification.common.StringUtils;
import com.google.android.apps.exposurenotification.keyupload.ApiConstants.UploadV1;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

/**
 * Tests for {@link Padding}.
 */
@RunWith(AndroidJUnit4.class)
@HiltAndroidTest
@Config(application = HiltTestApplication.class)
public class PaddingTest {

  // Padding doesn't have to be perfect size; just pretty close, and consistent.
  private static final int TOLERANCE = (int) (Padding.TARGET_PAYLOAD_SIZE_BYTES * 0.01);

  @Test
  public void objectSmallerThanTarget_shouldAddNewPaddingFieldToJsonObject() throws Exception {
    // Make an arbitrary non-empty object.
    JSONObject object = new JSONObject().put("foo", StringUtils.randomBase64Data(2));

    // Pad it out
    JSONObject padded = Padding.addPadding(object);

    // And check it has the new field expected.
    assertThat(padded.has(UploadV1.PADDING)).isTrue();
  }

  @Test
  public void objectSmallerThanTarget_shouldPadToApproximatelyTheTargetSize() throws Exception {
    // Make an arbitrary non-empty object.
    JSONObject object = new JSONObject().put("foo", StringUtils.randomBase64Data(2));

    // Pad it out
    JSONObject padded = Padding.addPadding(object);

    // And check it's pretty close to our target size
    // Casting to double because DoubleSubject has this really nice "isWithin" feature.
    assertThat((double) padded.toString().getBytes().length)
        .isWithin(TOLERANCE)
        .of(Padding.TARGET_PAYLOAD_SIZE_BYTES);
  }

  @Test
  public void objectLargerThanTarget_shouldReturnUnchanged() throws Exception {
    // Make an arbitrary non-empty object.
    JSONObject object = new JSONObject()
        .put("foo", StringUtils.randomBase64Data(Padding.TARGET_PAYLOAD_SIZE_BYTES + 100));

    // Try to pad it (but it shouldn't be actually)
    JSONObject padded = Padding.addPadding(object);

    // Check the JSON object hasn't be changed at all.
    assertWithMessage("Should not have padded the object").that(object).isEqualTo(padded);
  }

  @Test
  public void manyDifferentSizedObjects_shouldPadToApproximatelyTheSameSize() throws Exception {
    // Make some objects of different sizes.
    JSONObject small = new JSONObject().put("foo", StringUtils.randomBase64Data(123));
    JSONObject medium = new JSONObject().put("foo", StringUtils.randomBase64Data(500));
    JSONObject large = new JSONObject().put("foo", StringUtils.randomBase64Data(1000));

    // Pad them all out
    JSONObject smallPadded = Padding.addPadding(small);
    JSONObject mediumPadded = Padding.addPadding(medium);
    JSONObject largePadded = Padding.addPadding(large);

    // And check they're pretty close to the same size.
    // Casting to double because DoubleSubject has this really nice "isWithin" feature.
    assertWithMessage("Small and medium objects' padded sizes are too different from one another.")
        .that((double) smallPadded.toString().getBytes().length)
        .isWithin(TOLERANCE)
        .of(mediumPadded.toString().getBytes().length);
    assertWithMessage("Medium and large objects' padded sizes are too different from one another.")
        .that((double) mediumPadded.toString().getBytes().length)
        .isWithin(TOLERANCE)
        .of(largePadded.toString().getBytes().length);
    assertWithMessage("Small and large objects' padded sizes are too different from one another.")
        .that((double) smallPadded.toString().getBytes().length)
        .isWithin(TOLERANCE)
        .of(largePadded.toString().getBytes().length);
  }
}
