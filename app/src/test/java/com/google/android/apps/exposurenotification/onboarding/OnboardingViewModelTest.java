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

package com.google.android.apps.exposurenotification.onboarding;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.os.Bundle;
import androidx.test.core.app.ApplicationProvider;
import androidx.work.WorkManager;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.migrate.MigrationManager;
import com.google.android.apps.exposurenotification.nearby.ExposureNotificationClientWrapper;
import com.google.android.apps.exposurenotification.nearby.PackageConfigurationHelper;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences.OnboardingStatus;
import com.google.android.apps.exposurenotification.testsupport.ExposureNotificationRules;
import com.google.android.apps.exposurenotification.testsupport.FakeShadowResources;
import com.google.android.gms.nearby.exposurenotification.PackageConfiguration.PackageConfigurationBuilder;
import com.google.android.gms.tasks.Tasks;
import com.google.android.libraries.privateanalytics.PrivateAnalyticsEnabledProvider;
import com.google.common.base.Optional;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.annotation.LooperMode.Mode;

/**
 * Tests of {@link OnboardingViewModel}.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner.class)
@LooperMode(Mode.LEGACY)
@Config(application = HiltTestApplication.class, shadows = {FakeShadowResources.class})
public class OnboardingViewModelTest {

  private final Context context = ApplicationProvider.getApplicationContext();

  @Rule
  public ExposureNotificationRules rules = ExposureNotificationRules.forTest(this).withMocks()
      .build();

  @Inject
  ExposureNotificationSharedPreferences exposureNotificationSharedPreferences;

  @Mock
  PrivateAnalyticsEnabledProvider privateAnalyticsEnabledProvider;

  @Mock
  WorkManager workManager;

  @Mock
  ExposureNotificationClientWrapper exposureNotificationClientWrapper;

  @Mock
  MigrationManager migrationManager;

  OnboardingViewModel onboardingViewModel;

  @Before
  public void setup() {
    rules.hilt().inject();
    onboardingViewModel = new OnboardingViewModel(
        exposureNotificationClientWrapper,
        exposureNotificationSharedPreferences,
        privateAnalyticsEnabledProvider,
        workManager,
        migrationManager);
    // Provide an empty package configuration by default.
    when(exposureNotificationClientWrapper.getPackageConfiguration())
        .thenReturn(Tasks.forResult(new PackageConfigurationBuilder().build()));
  }

  @Test
  public void setOnboardedState_true_isStored() {
    assertThat(exposureNotificationSharedPreferences.getOnboardedState())
        .isEqualTo(OnboardingStatus.UNKNOWN);

    onboardingViewModel.setOnboardedState(true);

    assertThat(exposureNotificationSharedPreferences.getOnboardedState())
        .isEqualTo(OnboardingStatus.ONBOARDED);
  }

  @Test
  public void setOnboardedState_false_isStored() {
    assertThat(exposureNotificationSharedPreferences.getOnboardedState())
        .isEqualTo(OnboardingStatus.UNKNOWN);

    onboardingViewModel.setOnboardedState(false);

    assertThat(exposureNotificationSharedPreferences.getOnboardedState())
        .isEqualTo(OnboardingStatus.SKIPPED);
  }

  @Test
  public void isResultOkSet_returnsFalseInitially_returnsTrueWhenUpdated() {
    onboardingViewModel.setResultOk(true);

    assertThat(onboardingViewModel.isResultOkSet()).isTrue();

    onboardingViewModel = new OnboardingViewModel(
        exposureNotificationClientWrapper,
        exposureNotificationSharedPreferences,
        privateAnalyticsEnabledProvider,
        workManager,
        migrationManager);

    assertThat(onboardingViewModel.isResultOkSet()).isFalse();
  }

  @Test
  public void getShouldShowAppAnalyticsLiveData_enableV1toENXMigrationIsFalse_returnsFalse() {
    FakeShadowResources resources = (FakeShadowResources) shadowOf(context.getResources());
    resources.addFakeResource(R.bool.enx_enableV1toENXMigration, false);
    AtomicReference<Optional<Boolean>> showAppAnalytics = new AtomicReference<>(Optional.absent());
    onboardingViewModel.getShouldShowAppAnalyticsLiveData()
        .observeForever(showAppAnalytics::set);

    onboardingViewModel.updateShouldShowAppAnalytics(context.getResources());

    verify(exposureNotificationClientWrapper).getPackageConfiguration();
    Optional<Boolean> showAppAnalyticsOptional = showAppAnalytics.get();
    assertThat(showAppAnalyticsOptional).isPresent();
    assertThat(showAppAnalyticsOptional.get()).isFalse();
  }

  @Test
  public void getShouldShowAppAnalyticsLiveData_enableV1toENXMigrationIsTrueAndPckgConfigHasAppAnalytics_returnsFalse() {
    FakeShadowResources resources = (FakeShadowResources) shadowOf(context.getResources());
    resources.addFakeResource(R.bool.enx_enableV1toENXMigration, true);
    Bundle values = new Bundle();
    values.putBoolean(PackageConfigurationHelper.APP_ANALYTICS_OPT_IN, true);
    when(exposureNotificationClientWrapper.getPackageConfiguration())
        .thenReturn(Tasks.forResult(new PackageConfigurationBuilder().setValues(values).build()));
    onboardingViewModel = new OnboardingViewModel(
        exposureNotificationClientWrapper,
        exposureNotificationSharedPreferences,
        privateAnalyticsEnabledProvider,
        workManager,
        migrationManager);
    AtomicReference<Optional<Boolean>> showAppAnalytics = new AtomicReference<>(Optional.absent());
    onboardingViewModel.getShouldShowAppAnalyticsLiveData()
        .observeForever(showAppAnalytics::set);

    onboardingViewModel.updateShouldShowAppAnalytics(context.getResources());

    verify(exposureNotificationClientWrapper).getPackageConfiguration();
    Optional<Boolean> showAppAnalyticsOptional = showAppAnalytics.get();
    assertThat(showAppAnalyticsOptional).isPresent();
    assertThat(showAppAnalyticsOptional.get()).isFalse();
  }

  @Test
  public void getShouldShowAppAnalyticsLiveData_enableV1toENXMigrationIsTrueAndPckgConfigIsNull_returnsTrue() {
    FakeShadowResources resources = (FakeShadowResources) shadowOf(context.getResources());
    resources.addFakeResource(R.bool.enx_enableV1toENXMigration, true);
    when(exposureNotificationClientWrapper.getPackageConfiguration())
        .thenReturn(Tasks.forResult(null));
    onboardingViewModel = new OnboardingViewModel(
        exposureNotificationClientWrapper,
        exposureNotificationSharedPreferences,
        privateAnalyticsEnabledProvider,
        workManager,
        migrationManager);
    AtomicReference<Optional<Boolean>> showAppAnalytics = new AtomicReference<>(Optional.absent());
    onboardingViewModel.getShouldShowAppAnalyticsLiveData()
        .observeForever(showAppAnalytics::set);

    onboardingViewModel.updateShouldShowAppAnalytics(context.getResources());

    verify(exposureNotificationClientWrapper).getPackageConfiguration();
    Optional<Boolean> showAppAnalyticsOptional = showAppAnalytics.get();
    assertThat(showAppAnalyticsOptional).isPresent();
    assertThat(showAppAnalyticsOptional.get()).isTrue();
  }

  @Test
  public void getShouldShowAppAnalyticsLiveData_enableV1toENXMigrationIsTrueAndPckgConfigHasNoAppAnalytics_returnsTrue() {
    FakeShadowResources resources = (FakeShadowResources) shadowOf(context.getResources());
    resources.addFakeResource(R.bool.enx_enableV1toENXMigration, true);
    onboardingViewModel = new OnboardingViewModel(
        exposureNotificationClientWrapper,
        exposureNotificationSharedPreferences,
        privateAnalyticsEnabledProvider,
        workManager,
        migrationManager);
    AtomicReference<Optional<Boolean>> showAppAnalytics = new AtomicReference<>(Optional.absent());
    onboardingViewModel.getShouldShowAppAnalyticsLiveData()
        .observeForever(showAppAnalytics::set);

    onboardingViewModel.updateShouldShowAppAnalytics(context.getResources());

    verify(exposureNotificationClientWrapper).getPackageConfiguration();
    Optional<Boolean> showAppAnalyticsOptional = showAppAnalytics.get();
    assertThat(showAppAnalyticsOptional).isPresent();
    assertThat(showAppAnalyticsOptional.get()).isTrue();
  }

  @Test
  public void onboardAsMigratingUser_notOnboardedMigratingUser_doesNotMarkMigratingUserAsOnboarded() {
    when(migrationManager.shouldOnboardAsMigratingUser(context)).thenReturn(false);

    onboardingViewModel.maybeMarkMigratingUserAsOnboarded(context);

    verify(migrationManager, never()).markMigratingUserAsOnboarded();
  }

  @Test
  public void onboardAsMigratingUser_onboardedMigratingUser_marksMigratingUserAsOnboardede() {
    when(migrationManager.shouldOnboardAsMigratingUser(context)).thenReturn(true);

    onboardingViewModel.maybeMarkMigratingUserAsOnboarded(context);

    verify(migrationManager).markMigratingUserAsOnboarded();
  }
}
