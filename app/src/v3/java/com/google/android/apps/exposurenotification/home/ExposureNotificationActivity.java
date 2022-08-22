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

package com.google.android.apps.exposurenotification.home;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.biometric.BiometricPrompt;
import com.google.android.apps.exposurenotification.common.AuthenticationUtils;
import com.google.android.apps.exposurenotification.common.BiometricUtil;
import com.google.android.apps.exposurenotification.common.BiometricUtil.BiometricAuthenticationCallback;
import com.google.android.apps.exposurenotification.common.IntentUtil;
import com.google.android.apps.exposurenotification.common.KeyboardHelper;
import com.google.android.apps.exposurenotification.exposure.PossibleExposureFragment;
import com.google.android.apps.exposurenotification.notify.ShareDiagnosisFragment;
import com.google.android.apps.exposurenotification.notify.ShareHistoryFragment;
import com.google.android.apps.exposurenotification.onboarding.OnboardingV3Fragment;
import com.google.common.base.Optional;
import dagger.hilt.android.AndroidEntryPoint;

/**
 * Main activity for Exposure Notifications V3 app.
 *
 * <p>Handles all flows via fragments that are transitioned via this activity.
 */
@AndroidEntryPoint
public class ExposureNotificationActivity extends BaseActivity {

  private static final String SAVED_INSTANCE_STATE_IS_BIOMETRIC_PROMPT_SHOWING_KEY
      = "is_biometric_prompt_showing";
  public static final String EXTRA_NOT_NOW_CONFIRMATION = "not_now_confirmation";

  private boolean biometricInProgress;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    if (savedInstanceState != null &&
        savedInstanceState.getBoolean(SAVED_INSTANCE_STATE_IS_BIOMETRIC_PROMPT_SHOWING_KEY,
            false)) {
      biometricInProgress = true;
      BiometricUtil.createBiometricPrompt(this, authenticationCallback);
    }
  }

  @Override
  protected void onSaveInstanceState(@NonNull Bundle savedInstanceState) {
    super.onSaveInstanceState(savedInstanceState);

    savedInstanceState.putBoolean(SAVED_INSTANCE_STATE_IS_BIOMETRIC_PROMPT_SHOWING_KEY,
        biometricInProgress);
  }

  @Override
  protected void handleIntent(@Nullable String action, @Nullable Bundle extras, @Nullable Uri uri,
      boolean isOnNewIntent) {
    // Android will return null extras if empty, if so override for safer calls.
    if (extras == null) {
      extras = new Bundle();
    }

    boolean isPossibleExposurePresent = exposureNotificationViewModel.isPossibleExposurePresent();

    BaseFragment fragment;
    switch (action == null ? "" : action) {
      case IntentUtil.ACTION_ONBOARDING:
        assertCallerGms();
        fragment = OnboardingV3Fragment
            .newInstance(extras.getBoolean(EXTRA_NOT_NOW_CONFIRMATION, false));
        break;
      case IntentUtil.ACTION_SHARE_HISTORY:
        authenticateUserAndMaybeShowShareHistory();
        return;
      case IntentUtil.ACTION_SHARE_DIAGNOSIS:
        fragment = ShareDiagnosisFragment.newInstance();
        break;
      case Intent.ACTION_VIEW:
        fragment = maybeLaunchFromDeeplink(uri);
        break;
      case IntentUtil.ACTION_LAUNCH_HOME:
        if (extras.getBoolean(IntentUtil.EXTRA_NOTIFICATION, false)
            && isPossibleExposurePresent) {
          exposureNotificationViewModel.updateLastExposureNotificationLastClickedTime();
          fragment = PossibleExposureFragment.newInstance();
        } else {
          if (extras.getBoolean(IntentUtil.EXTRA_SLICE, false)
              && isPossibleExposurePresent) {
            fragment = PossibleExposureFragment.newInstance();
          } else if (extras.getBoolean(IntentUtil.EXTRA_SMS_NOTICE_SLICE, false)) {
            fragment = SmsNoticeFragment.newInstance(true);
          } else if (extras.getBoolean(IntentUtil.EXTRA_SMS_VERIFICATION, false)) {
            Uri deeplinkUri = extras.getParcelable(IntentUtil.EXTRA_DEEP_LINK);
            fragment = maybeLaunchFromDeeplink(deeplinkUri);
          } else {
            assertCallerGms();
            fragment = ActiveRegionFragment.newInstance(true);
          }
        }
        break;
      default:
        finish();
        return;
    }
    transitionToFragmentDirect(fragment);
  }

  private BaseFragment maybeLaunchFromDeeplink(@Nullable Uri uri) {
    Optional<String> codeFromDeepLink = IntentUtil.maybeGetCodeFromDeepLinkUri(uri);
    if (codeFromDeepLink.isPresent()) {
      return ShareDiagnosisFragment.newInstanceForCode(codeFromDeepLink.get());
    } else if (IntentUtil.isSelfReportUri(uri)) {
      return ShareDiagnosisFragment.newInstanceForSelfReport();
    } else {
      return ShareDiagnosisFragment.newInstance();
    }
  }

  /**
   * This method is called for the user to authenticate when the share history button is clicked.
   */
  private void authenticateUserAndMaybeShowShareHistory() {
    if (!AuthenticationUtils.isAuthenticationAvailable(this)) {
      transitionToSharingHistoryFragment();
      return;
    }

    biometricInProgress = true;
    BiometricPrompt biometricPrompt = BiometricUtil.createBiometricPrompt(this,
        authenticationCallback);
    BiometricUtil.startBiometricPromptAuthentication( this, biometricPrompt);
  }

  BiometricAuthenticationCallback authenticationCallback = new BiometricAuthenticationCallback() {
    @Override
    public void onAuthenticationSucceeded() {
      transitionToSharingHistoryFragment();
      biometricInProgress = false;
      KeyboardHelper.maybeHideKeyboard(ExposureNotificationActivity.this,
          binding.getRoot());
    }

    @Override
    public void onAuthenticationError(boolean isBiometricErrorSkippable) {
      if (isBiometricErrorSkippable) {
        transitionToSharingHistoryFragment();
      } else {
        biometricInProgress = false;
        KeyboardHelper.maybeHideKeyboard(ExposureNotificationActivity.this,
            binding.getRoot());
        finish();
      }
    }

    @Override
    public void onAuthenticationFailed() {
      biometricInProgress = false;
      KeyboardHelper.maybeHideKeyboard(ExposureNotificationActivity.this,
          binding.getRoot());
      finish();
    }
  };

  private void transitionToSharingHistoryFragment() {
    transitionToFragmentDirect(new ShareHistoryFragment());
  }
}
