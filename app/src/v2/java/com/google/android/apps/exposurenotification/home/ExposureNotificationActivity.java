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
import android.view.View;
import androidx.annotation.Nullable;
import com.google.android.apps.exposurenotification.common.IntentUtil;
import com.google.android.apps.exposurenotification.exposure.PossibleExposureFragment;
import com.google.android.apps.exposurenotification.notify.ShareDiagnosisFragment;
import com.google.android.apps.exposurenotification.settings.ExposureAboutFragment;
import com.google.common.base.Optional;
import dagger.hilt.android.AndroidEntryPoint;

/**
 * Main Activity for the Exposure Notification Application.
 *
 * <p>This activity uses fragments to show the various screens of the application.
 */
@AndroidEntryPoint
public final class ExposureNotificationActivity extends BaseActivity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
  }

  @Override
  protected void handleIntent(@Nullable String action, @Nullable Bundle extras, @Nullable Uri uri,
      boolean isOnNewIntent) {
    if (extras == null) {
      extras = new Bundle();
    }

    switch (action == null ? "" : action) {
      case Intent.ACTION_VIEW:
        maybeLaunchFromDeeplink(uri);
        break;
      case Intent.ACTION_MAIN:
        if (extras.getBoolean(IntentUtil.EXTRA_NOTIFICATION, false)
            && exposureNotificationViewModel.isPossibleExposurePresent()) {
          exposureNotificationViewModel.updateLastExposureNotificationLastClickedTime();
          BaseFragment possibleExposureFragment = PossibleExposureFragment.newInstance();
          transitionToFragmentThroughAnotherFragmentWithBackStack(
              /* destinationFragment= */possibleExposureFragment,
              /* transitFragment= */SinglePageHomeFragment.newInstance());
        } else if (extras.getBoolean(IntentUtil.EXTRA_SMS_VERIFICATION, false)) {
          Uri deeplinkUri = extras.getParcelable(IntentUtil.EXTRA_DEEP_LINK);
          maybeLaunchFromDeeplink(deeplinkUri);
        } else {
          transitionToFragmentDirect(isOnNewIntent
              ? SinglePageHomeFragment.newInstance() : SplashFragment.newInstance());
        }
        break;
      default:
        transitionToFragmentDirect(SplashFragment.newInstance());
        break;
    }
  }

  private void maybeLaunchFromDeeplink(@Nullable Uri uri) {
    BaseFragment shareDiagnosisFragment;
    Optional<String> codeFromDeepLink = IntentUtil.maybeGetCodeFromDeepLinkUri(uri);
    if (codeFromDeepLink.isPresent()) {
      shareDiagnosisFragment = ShareDiagnosisFragment.newInstanceForCode(codeFromDeepLink.get());
    } else if (IntentUtil.isSelfReportUri(uri)) {
      shareDiagnosisFragment = ShareDiagnosisFragment.newInstanceForSelfReport();
    } else {
      shareDiagnosisFragment = ShareDiagnosisFragment.newInstance();
    }
    transitionToFragmentThroughAnotherFragmentWithBackStack(
        /* destinationFragment= */shareDiagnosisFragment,
        /* transitFragment= */SinglePageHomeFragment.newInstance());
  }

  /**
   * Open the exposure notifications about fragment.
   */
  public void launchExposureNotificationsAbout(View view) {
    transitionToFragmentWithBackStack(ExposureAboutFragment.newInstance());
  }
}
