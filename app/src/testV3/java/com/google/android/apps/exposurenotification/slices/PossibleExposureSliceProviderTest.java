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
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.app.slice.SliceManager;
import android.app.slice.SliceSpec;
import android.content.Context;
import android.content.pm.ProviderInfo;
import androidx.slice.Slice;
import androidx.slice.SliceItem;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.apps.exposurenotification.BuildConfig;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.StringUtils;
import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.android.apps.exposurenotification.common.time.RealTimeModule;
import com.google.android.apps.exposurenotification.nearby.ExposureInformationHelper;
import com.google.android.apps.exposurenotification.riskcalculation.ExposureClassification;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.apps.exposurenotification.testsupport.ExposureNotificationRules;
import com.google.android.apps.exposurenotification.testsupport.FakeClock;
import com.google.android.apps.exposurenotification.testsupport.FakeShadowResources;
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
@Config(application = HiltTestApplication.class, shadows = {FakeShadowResources.class})
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

  @BindValue
  @Mock
  ExposureInformationHelper exposureInformationHelper;

  @Rule
  public ExposureNotificationRules rules =
      ExposureNotificationRules.forTest(this).withMocks().build();

  @Before
  public void setUp() {
    rules.hilt().inject();
    context = ApplicationProvider.getApplicationContext();
    possibleExposureSliceProvider = setupPossibleExposureSliceProviderForTest(sliceManager);
    FakeShadowResources resources = (FakeShadowResources) shadowOf(context.getResources());
    resources.addFakeResource(R.bool.enx_enableTextMessageVerification, true);
    resources.addFakeResource(R.string.enx_testVerificationNotificationBody, "non-empty");
  }

  @Test
  public void onBindSlice_callingUidHasNoAccess_returnsNull() {
    when(slicePermissionManager.callingUidHasAccess()).thenReturn(false);

    Slice slice = possibleExposureSliceProvider.onBindSlice(POSSIBLE_EXPOSURE_SLICE_URI);

    assertThat(slice).isNull();
  }

  @Test
  public void onBindSlice_noExposureSmsNoticeSeenInApp_returnsNull() {
    // Given permission to access slice, no exposure, and sms notice seen
    when(slicePermissionManager.callingUidHasAccess()).thenReturn(true);
    exposureNotificationSharedPreferences.setExposureClassification(ExposureClassification
        .create(0,ExposureClassification.NO_EXPOSURE_CLASSIFICATION_NAME, 0));
    exposureNotificationSharedPreferences.markInAppSmsNoticeSeen();

    Slice slice = possibleExposureSliceProvider.onBindSlice(POSSIBLE_EXPOSURE_SLICE_URI);

    assertThat(slice).isNull();
  }

  @Test
  public void onBindSlice_noExposureSmsNoticeSeenPlay_returnsNull() {
    // Given permission to access slice, no exposure, and sms notice seen in play
    when(slicePermissionManager.callingUidHasAccess()).thenReturn(true);
    exposureNotificationSharedPreferences.setExposureClassification(ExposureClassification
        .create(0,ExposureClassification.NO_EXPOSURE_CLASSIFICATION_NAME, 0));
    exposureNotificationSharedPreferences.setPlaySmsNoticeSeen(true);

    Slice slice = possibleExposureSliceProvider.onBindSlice(POSSIBLE_EXPOSURE_SLICE_URI);

    assertThat(slice).isNull();
  }

  @Test
  public void onBindSlice_noExposureSmsInterceptDisabled_returnsNull() {
    // Given permission to access slice, no exposure, and disable SMS intercept
    when(slicePermissionManager.callingUidHasAccess()).thenReturn(true);
    exposureNotificationSharedPreferences.setExposureClassification(ExposureClassification
        .create(0, ExposureClassification.NO_EXPOSURE_CLASSIFICATION_NAME, 0));
    exposureNotificationSharedPreferences.markInAppSmsNoticeSeen();
    FakeShadowResources resources = (FakeShadowResources) shadowOf(context.getResources());
    resources.addFakeResource(R.bool.enx_enableTextMessageVerification, false);

    Slice slice = possibleExposureSliceProvider.onBindSlice(POSSIBLE_EXPOSURE_SLICE_URI);

    assertThat(slice).isNull();
  }

  @Test
  public void onBindSlice_activeRevocationAndSmsNoticeNotSeen_returnNonEmptySliceAndSmsNotice() {
    // Given permission to access slice, an active revocation, and sms notice not seen (default)
    when(slicePermissionManager.callingUidHasAccess()).thenReturn(true);
    when(exposureInformationHelper.isActiveExposurePresent()).thenReturn(true);
    exposureNotificationSharedPreferences.setIsExposureClassificationRevoked(true);

    Slice slice = possibleExposureSliceProvider.onBindSlice(POSSIBLE_EXPOSURE_SLICE_URI);

    assertThat(slice).isNotNull();
    assertSliceIsSmsNoticeSlice(slice.getItems().get(2).getSlice());
  }

  @Test
  public void onBindSlice_outdatedRevocationAndSmsNoticeNotSeen_returnSmsNoticeSlice() {
    // Given permission to access slice, an outdated revocation, and sms notice not seen (default)
    when(slicePermissionManager.callingUidHasAccess()).thenReturn(true);
    when(exposureInformationHelper.isActiveExposurePresent()).thenReturn(false);
    exposureNotificationSharedPreferences.setIsExposureClassificationRevoked(true);

    Slice slice = possibleExposureSliceProvider.onBindSlice(POSSIBLE_EXPOSURE_SLICE_URI);

    assertThat(slice).isNotNull();
    assertSliceIsSmsNoticeSlice(slice.getItems().get(1).getSlice());
  }

  @Test
  public void onBindSlice_activeExposureAndSmsNoticeNotSeen_returnPossibleExposureSliceAndSmsNotice() {
    // GIVEN - permission to access slice, an active exposure, and sms notice not seen (default)
    when(slicePermissionManager.callingUidHasAccess()).thenReturn(true);
    when(exposureInformationHelper.isActiveExposurePresent()).thenReturn(true);
    ExposureClassification exposureClassification = ExposureClassification
        .create(1,"exp", LocalDate.of(2021,2,6).toEpochDay());
    exposureNotificationSharedPreferences.setExposureClassification(exposureClassification);
    ((FakeClock) clock).set(Instant.from(OffsetDateTime
        .of(2021, 2 ,8, 18,0,0,0, ZoneOffset.UTC)));

    // WHEN
    Slice slice = possibleExposureSliceProvider.onBindSlice(POSSIBLE_EXPOSURE_SLICE_URI);

    // THEN
    assertThat(slice).isNotNull();
    assertSliceIsPossibleExposureSlice(slice.getItems().get(1).getSlice(), exposureClassification);
    assertSliceIsSmsNoticeSlice(slice.getItems().get(2).getSlice());
  }

  @Test
  public void onBindSlice_outdatedExposureAndSmsNoticeNotSeen_returnSmsNoticeSlice() {
    // GIVEN - permission to access slice, an outdated exposure, and sms notice not seen (default)
    when(slicePermissionManager.callingUidHasAccess()).thenReturn(true);
    when(exposureInformationHelper.isActiveExposurePresent()).thenReturn(false);
    ExposureClassification exposureClassification = ExposureClassification
        .create(1,"exp", LocalDate.of(2021,2,6).toEpochDay());
    exposureNotificationSharedPreferences.setExposureClassification(exposureClassification);
    ((FakeClock) clock).set(Instant.from(OffsetDateTime
        .of(2021, 3 ,8, 18,0,0,0, ZoneOffset.UTC)));

    // WHEN
    Slice slice = possibleExposureSliceProvider.onBindSlice(POSSIBLE_EXPOSURE_SLICE_URI);

    // THEN
    assertThat(slice).isNotNull();
    assertSliceIsSmsNoticeSlice(slice.getItems().get(1).getSlice());
  }

  @Test
  public void onBindSlice_noExposureSmsNoticeNotSeen_returnSmsNoticeSlice() {
    // GIVEN - permission to access slice, no exposure, and sms notice not seen (default)
    when(slicePermissionManager.callingUidHasAccess()).thenReturn(true);
    exposureNotificationSharedPreferences.setExposureClassification(ExposureClassification
        .create(0, ExposureClassification.NO_EXPOSURE_CLASSIFICATION_NAME, 0));

    // WHEN
    Slice slice = possibleExposureSliceProvider.onBindSlice(POSSIBLE_EXPOSURE_SLICE_URI);

    // THEN
    assertThat(slice).isNotNull();
    assertSliceIsSmsNoticeSlice(slice.getItems().get(1).getSlice());
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

  private void assertSliceIsPossibleExposureSlice(Slice slice,
      ExposureClassification exposureClassification) {
    List<SliceItem> sliceItems = slice.getItems();
    assertThat(sliceItems.get(0).getSlice().getItems().get(0).getFormat()).isEqualTo("image");
    assertThat(sliceItems.get(1).getText()).isEqualTo( /*"Possible COVID-19 exposure"*/
        context.getString(R.string.exposure_details_status_exposure));
    assertThat(sliceItems.get(2).getText()).isEqualTo( /*"2 days ago"*/
        StringUtils.daysFromStartOfExposure(exposureClassification, clock.now(), context));
    assertThat(sliceItems.get(3).getAction().getCreatorPackage())
        .isEqualTo(BuildConfig.APPLICATION_ID);
  }

  private void assertSliceIsSmsNoticeSlice(Slice slice) {
    List<SliceItem> sliceItems = slice.getItems();
    assertThat(sliceItems.get(0).getSlice().getItems().get(0).getFormat()).isEqualTo("image");
    assertThat(sliceItems.get(1).getText()).isEqualTo(
        context.getString(R.string.sms_intercept_notice_card_title));
    assertThat(sliceItems.get(2).getText()).isEqualTo(
        context.getString(R.string.sms_intercept_notice_card_content));
    assertThat(sliceItems.get(3).getAction().getCreatorPackage())
        .isEqualTo(BuildConfig.APPLICATION_ID);
  }

}
