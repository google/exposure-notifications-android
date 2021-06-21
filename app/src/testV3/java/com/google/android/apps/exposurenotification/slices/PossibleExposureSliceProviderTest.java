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

import static com.google.android.apps.exposurenotification.slices.PossibleExposureSliceProvider.POSSIBLE_EXPOSURE_SLICE_URI;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.app.Application;
import android.app.slice.SliceManager;
import android.app.slice.SliceSpec;
import android.content.Context;
import android.content.pm.ProviderInfo;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import androidx.slice.Slice;
import androidx.slice.SliceItem;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.apps.exposurenotification.BuildConfig;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.StringUtils;
import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.android.apps.exposurenotification.common.time.RealTimeModule;
import com.google.android.apps.exposurenotification.riskcalculation.ExposureClassification;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.apps.exposurenotification.testsupport.ExposureNotificationRules;
import com.google.android.apps.exposurenotification.testsupport.FakeClock;
import com.google.common.collect.Sets;
import dagger.hilt.android.testing.BindValue;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import dagger.hilt.android.testing.UninstallModules;
import java.util.HashSet;
import java.util.List;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowContextImpl;
import org.threeten.bp.Instant;
import org.threeten.bp.LocalDate;
import org.threeten.bp.OffsetDateTime;
import org.threeten.bp.ZoneOffset;

@RunWith(AndroidJUnit4.class)
@HiltAndroidTest
@Config(application = HiltTestApplication.class)
@UninstallModules({SlicesModuleImpl.class, RealTimeModule.class})
public class PossibleExposureSliceProviderTest {

  Context context;
  PossibleExposureSliceProvider possibleExposureSliceProvider;

  @Mock
  SliceManager sliceManager;

  @BindValue
  @Mock
  SlicePermissionManager slicePermissionManager;

  @BindValue
  Clock clock = new FakeClock();

  @Inject
  ExposureNotificationSharedPreferences exposureNotificationSharedPreferences;

  @Rule
  public ExposureNotificationRules rules =
      ExposureNotificationRules.forTest(this).withMocks().build();

  @Before
  public void setUp() {
    rules.hilt().inject();
    context = ApplicationProvider.getApplicationContext();
    possibleExposureSliceProvider = setupPossibleExposureSliceProviderForTest(sliceManager);
  }

  @Test
  public void onBindSlice_callingUidHasNoAccess_returnsNull() {
    when(slicePermissionManager.callingUidHasAccess()).thenReturn(false);

    Slice slice = possibleExposureSliceProvider.onBindSlice(POSSIBLE_EXPOSURE_SLICE_URI);

    assertThat(slice).isNull();
  }

  @Test
  public void onBindSlice_noExposure_returnsNull() {
    // Given permission to access slice and no exposure
    when(slicePermissionManager.callingUidHasAccess()).thenReturn(true);
    exposureNotificationSharedPreferences.setExposureClassification(ExposureClassification
        .create(0,ExposureClassification.NO_EXPOSURE_CLASSIFICATION_NAME, 0));

    Slice slice = possibleExposureSliceProvider.onBindSlice(POSSIBLE_EXPOSURE_SLICE_URI);

    assertThat(slice).isNull();
  }

  @Test
  public void onBindSlice_revocation_returnSlice() {
    // Given permission to access slice and a revocation
    when(slicePermissionManager.callingUidHasAccess()).thenReturn(true);
    exposureNotificationSharedPreferences.setIsExposureClassificationRevoked(true);

    Slice slice = possibleExposureSliceProvider.onBindSlice(POSSIBLE_EXPOSURE_SLICE_URI);

    assertThat(slice).isNotNull();
  }

  @Test
  public void onBindSlice_exposure_returnSlice() {
    // GIVEN - permission to access slice and exposure
    when(slicePermissionManager.callingUidHasAccess()).thenReturn(true);
    ExposureClassification exposureClassification = ExposureClassification
        .create(1,"exp", LocalDate.of(2021,2,6).toEpochDay());
    exposureNotificationSharedPreferences.setExposureClassification(exposureClassification);
    ((FakeClock) clock).set(Instant.from(OffsetDateTime
        .of(2021, 2 ,8, 18,0,0,0, ZoneOffset.UTC)));

    // WHEN
    Slice slice = possibleExposureSliceProvider.onBindSlice(POSSIBLE_EXPOSURE_SLICE_URI);

    // THEN
    assertThat(slice).isNotNull();
    List<SliceItem> sliceItems = slice.getItems().get(1).getSlice().getItems();
    assertThat(sliceItems.get(0).getSlice().getItems().get(0).getFormat()).isEqualTo("image");
    assertThat(sliceItems.get(1).getText()).isEqualTo( /*"Possible COVID-19 exposure"*/
        context.getString(R.string.exposure_details_status_exposure));
    assertThat(sliceItems.get(2).getText()).isEqualTo( /*"2 days ago"*/
        StringUtils.daysFromStartOfExposure(exposureClassification, clock.now(), context));
    assertThat(sliceItems.get(3).getAction().getCreatorPackage())
        .isEqualTo(BuildConfig.APPLICATION_ID);
  }

  private static PossibleExposureSliceProvider setupPossibleExposureSliceProviderForTest(
      SliceManager sliceManagerMock) {
    PossibleExposureSliceProvider sliceProvider = new PossibleExposureSliceProvider();

    ProviderInfo info = new ProviderInfo();
    info.authority = POSSIBLE_EXPOSURE_SLICE_URI.getAuthority();
    sliceProvider.attachInfo(ApplicationProvider.getApplicationContext(), info);

    // Set up SliceSpec for Slice builders.
    HashSet<SliceSpec> specs = Sets.newHashSetWithExpectedSize(4);
    specs.add(new SliceSpec("androidx.app.slice.BASIC", 1));
    specs.add(new SliceSpec("androidx.app.slice.LIST", 1));
    specs.add(new SliceSpec("androidx.slice.BASIC", 1));
    specs.add(new SliceSpec("androidx.slice.LIST", 1));
    when(sliceManagerMock.getPinnedSpecs(any())).thenReturn(specs);
    ShadowContextImpl shadowContext =
        Shadow.extract(
            ((Application) ApplicationProvider.getApplicationContext()).getBaseContext());
    shadowContext.setSystemService("slice", sliceManagerMock);
    return sliceProvider;
  }
}
