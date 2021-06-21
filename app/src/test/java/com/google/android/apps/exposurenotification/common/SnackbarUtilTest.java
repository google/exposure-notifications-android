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

import static com.google.common.truth.Truth.assertThat;

import android.app.Activity;
import android.content.Context;
import android.widget.TextView;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.testsupport.ExposureNotificationRules;
import com.google.android.material.snackbar.Snackbar;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

/**
 * Tests for {@link SnackbarUtil} utility function helper class.
 */
@RunWith(AndroidJUnit4.class)
@HiltAndroidTest
@Config(application = HiltTestApplication.class)
public class SnackbarUtilTest {

  @Rule
  public ExposureNotificationRules rules = ExposureNotificationRules.forTest(this).withMocks()
      .build();

  Context context = ApplicationProvider.getApplicationContext();

  @Before
  public void setup() {
    rules.hilt().inject();
  }

  @Test
  public void testCreateLargeSnackbar_isLengthLongAnd5MaxLines() {
    ActivityController activityController = Robolectric.buildActivity(Activity.class);
    Activity activity = (Activity) activityController.get();
    activity.setContentView(R.layout.fragment_legal_terms);
    activityController.create();

    Snackbar snackbar = SnackbarUtil.createLargeSnackbar(activity.findViewById(android.R.id.home),
        R.string.health_authority_id);

    assertThat(snackbar.getDuration()).isEqualTo(Snackbar.LENGTH_LONG);
    TextView tv = snackbar.getView().findViewById(com.google.android.material.R.id.snackbar_text);
    assertThat(tv.getText()).isEqualTo(context.getString(R.string.health_authority_id));
    assertThat(tv.getMaxLines()).isEqualTo(5);
  }

}
