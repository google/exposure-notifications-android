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

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import com.google.android.apps.exposurenotification.migrate.MigrationManager;
import com.google.android.apps.exposurenotification.nearby.ExposureNotificationClientWrapper;
import com.google.android.apps.exposurenotification.nearby.PackageConfigurationHelper;
import com.google.android.apps.exposurenotification.privateanalytics.SubmitPrivateAnalyticsWorker;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.libraries.privateanalytics.PrivateAnalyticsEnabledProvider;
import com.google.common.base.Optional;
import dagger.hilt.android.lifecycle.HiltViewModel;
import javax.inject.Inject;

/**
 * View model for onboarding related actions.
 */
@HiltViewModel
public class OnboardingViewModel extends ViewModel {

  private final ExposureNotificationClientWrapper exposureNotificationClientWrapper;
  private final ExposureNotificationSharedPreferences exposureNotificationSharedPreferences;
  private final PrivateAnalyticsEnabledProvider privateAnalyticsEnabledProvider;
  private final WorkManager workManager;
  private final MigrationManager migrationManager;

  private final MutableLiveData<Optional<Boolean>> shouldShowAppAnalyticsLiveData =
      new MutableLiveData<>(Optional.absent());

  private boolean resultOkSet = false;

  @Inject
  public OnboardingViewModel(
      ExposureNotificationClientWrapper exposureNotificationClientWrapper,
      ExposureNotificationSharedPreferences exposureNotificationSharedPreferences,
      PrivateAnalyticsEnabledProvider privateAnalyticsEnabledProvider,
      WorkManager workManager,
      MigrationManager migrationManager) {
    this.exposureNotificationClientWrapper = exposureNotificationClientWrapper;
    this.exposureNotificationSharedPreferences = exposureNotificationSharedPreferences;
    this.privateAnalyticsEnabledProvider = privateAnalyticsEnabledProvider;
    this.workManager = workManager;
    this.migrationManager = migrationManager;
  }

  public void setOnboardedState(boolean onboarded) {
    exposureNotificationSharedPreferences.setOnboardedState(onboarded);
  }

  public void setAppAnalyticsState(boolean isEnabled) {
    exposureNotificationSharedPreferences.setAppAnalyticsState(isEnabled);
  }

  public void markSmsInterceptNoticeSeenAsync() {
    exposureNotificationSharedPreferences.markInAppSmsNoticeSeenAsync();
  }

  public LiveData<Boolean> isPrivateAnalyticsStateSetLiveData() {
    return exposureNotificationSharedPreferences.isPrivateAnalyticsStateSetLiveData();
  }

  public LiveData<Boolean> shouldShowPrivateAnalyticsOnboardingLiveData() {
    return Transformations.map(isPrivateAnalyticsStateSetLiveData(),
        (isPrivateAnalyticsStateSet) -> !isPrivateAnalyticsStateSet
            && privateAnalyticsEnabledProvider.isSupportedByApp());
  }

  public void setPrivateAnalyticsState(boolean isEnabled) {
    exposureNotificationSharedPreferences.setPrivateAnalyticsState(isEnabled);
  }

  /**
   * Triggers a one off submit private analytics job.
   */
  public void submitPrivateAnalytics() {
    workManager.enqueue(new OneTimeWorkRequest.Builder(SubmitPrivateAnalyticsWorker.class).build());
  }

  /**
   * Returns whether we've set {@link Activity#RESULT_OK} for the caller as this information
   * may have been lost upon device configuration changes (e.g. rotations).
   */
  public boolean isResultOkSet() {
    return resultOkSet;
  }

  /**
   * Marks if {@link Activity#RESULT_OK} can be set for the caller.
   */
  public void setResultOk(boolean resultOkSet) {
    this.resultOkSet = resultOkSet;
  }

  /**
   * LiveData that informs whether we should display App Analytics switch during the onboarding in
   * the case when EN has been already enabled.
   *
   * <p>Check {@code maybeCheckIfShouldShowAppAnalytics()} to see how this LiveData is updated.
   *
   * @return an optional of true when migration object is present and the package configuration did
   *     not have app analytics opt-in, false otherwise, or absent if not yet determined.
   */
  public LiveData<Optional<Boolean>> getShouldShowAppAnalyticsLiveData() {
    return shouldShowAppAnalyticsLiveData;
  }

  /**
   * Attempts to update the {@code shouldShowAppAnalyticsLiveData}, whose value depends on the
   * presence of the app analytics opt-in in the current package configuration (which we retrieve
   * with the EN client API).
   *
   * <p>We may fail to update {@code shouldShowAppAnalyticsLiveData} if the EN API call fails.
   * @param resources application's resources.
   */
  public void updateShouldShowAppAnalytics(Resources resources) {
    exposureNotificationClientWrapper
        .getPackageConfiguration()
        .addOnSuccessListener(
            packageConfiguration -> {
              if (MigrationManager.isMigrationEnabled(resources)) {
                shouldShowAppAnalyticsLiveData.postValue(
                    Optional.of(
                        !PackageConfigurationHelper.isAppAnalyticsPresentInPackageConfiguration(
                            packageConfiguration)));
              } else {
                shouldShowAppAnalyticsLiveData.postValue(Optional.of(false));
              }
            });
  }

  public void maybeMarkMigratingUserAsOnboarded(Context context) {
    if (migrationManager.shouldOnboardAsMigratingUser(context)) {
      migrationManager.markMigratingUserAsOnboarded();
    }
  }
}
