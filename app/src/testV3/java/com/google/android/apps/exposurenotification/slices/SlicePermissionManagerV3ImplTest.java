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

package com.google.android.apps.exposurenotification.slices;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.slice.SliceManager;
import android.content.Context;
import android.os.Build;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.apps.exposurenotification.testsupport.ExposureNotificationRules;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

@RunWith(AndroidJUnit4.class)
@HiltAndroidTest
@Config(application = HiltTestApplication.class, sdk = Build.VERSION_CODES.P)
public class SlicePermissionManagerV3ImplTest {
  @Rule
  public ExposureNotificationRules rules =
      ExposureNotificationRules.forTest(this).withMocks().build();

  @Before
  public void setUp() {
    rules.hilt().inject();
  }

  @Test
  public void grantSlicePermission_sliceConfiguredCorrectly_callsGrantSlicePermission() {
    SliceManager sliceManager = mock(SliceManager.class);
    Context context = mock(Context.class);
    // Note: this correctly replaces SliceManager by a mock for Android 28+ only
    when(context.getSystemService(android.app.slice.SliceManager.class)).thenReturn(sliceManager);

    new SlicePermissionManagerV3Impl(context).grantSlicePermission();

    verify(sliceManager).grantSlicePermission(any(), any());
  }

  /*
   * This reproduces a very rare bug, where the framework wrongly routes the slice call and
   * tries to grant permission to a slice it does not own.
   * As the dynamic grantSlicePermissions are only second line of defense, this exception
   * should just be caught.
   */
  @Test /* Does not expect exceptions - would fail if one is thrown */
  public void grantSlicePermission_callerNotOwner_catchesException() {
    SliceManager sliceManager = mock(SliceManager.class);
    doThrow(new SecurityException("Caller must own content://*.slice/possible_exposure"))
        .when(sliceManager).grantSlicePermission(any(), any());
    Context context = mock(Context.class);
    when(context.getSystemService(android.app.slice.SliceManager.class)).thenReturn(sliceManager);

    new SlicePermissionManagerV3Impl(context).grantSlicePermission();
  }

  @Test(expected = NullPointerException.class)
  public void grantSlicePermission_otherException_doesNotCatchException() {
    SliceManager sliceManager = mock(SliceManager.class);
    doThrow(new NullPointerException()).when(sliceManager).grantSlicePermission(any(), any());
    Context context = mock(Context.class);
    when(context.getSystemService(android.app.slice.SliceManager.class)).thenReturn(sliceManager);

    new SlicePermissionManagerV3Impl(context).grantSlicePermission();
  }
}
