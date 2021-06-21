package com.google.android.apps.exposurenotification.home;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.Nullable;
import com.google.android.apps.exposurenotification.common.IntentUtil;
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

  private static final String TAG = "ExposureNotificationActivity";

  private static final String EXTRA_POSSIBLE_EXPOSURE_SLICE_DISABLED = "slice_disabled";
  public static final String EXTRA_NOT_NOW_CONFIRMATION = "not_now_confirmation";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
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
        fragment = new ShareHistoryFragment();
        break;
      case IntentUtil.ACTION_SHARE_DIAGNOSIS:
        fragment = ShareDiagnosisFragment.newInstance();
        break;
      case Intent.ACTION_VIEW:
        Optional<String> codeFromDeepLink = IntentUtil.maybeGetCodeFromDeepLinkUri(uri);
        if (codeFromDeepLink.isPresent()) {
          fragment = ShareDiagnosisFragment.newInstance(codeFromDeepLink.get());
        } else {
          assertCallerGms();
          fragment = ShareDiagnosisFragment.newInstance();
        }
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
          } else {
            assertCallerGms();
            fragment = ActiveRegionFragment
                .newInstance(true);
          }
        }
        break;
      default:
        finish();
        return;
    }
    transitionToFragmentDirect(fragment);
  }
}
