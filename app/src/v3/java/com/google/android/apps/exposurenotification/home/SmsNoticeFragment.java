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
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.IntentUtil;
import com.google.android.apps.exposurenotification.common.StringUtils;
import com.google.android.apps.exposurenotification.databinding.FragmentSmsNoticeBinding;
import com.google.android.apps.exposurenotification.utils.UrlUtils;
import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class SmsNoticeFragment extends BaseFragment {

  // Intent extra key to store whether this fragment was launched from a slice.
  private static final String EXTRA_LAUNCHED_FROM_SLICE = "EXTRA_LAUNCHED_FROM_SLICE";

  /**
   * Creates a {@link SmsNoticeFragment} fragment.
   */
  public static SmsNoticeFragment newInstance(boolean launchedFromSlice) {
    SmsNoticeFragment smsNoticeFragment = new SmsNoticeFragment();
    Bundle args = new Bundle();
    args.putBoolean(EXTRA_LAUNCHED_FROM_SLICE, launchedFromSlice);
    smsNoticeFragment.setArguments(args);
    return smsNoticeFragment;
  }

  private FragmentSmsNoticeBinding binding;
  private ExposureNotificationViewModel exposureNotificationViewModel;
  private boolean launchedFromSlice = false;

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent,
      Bundle savedInstanceState) {
    binding = FragmentSmsNoticeBinding.inflate(getLayoutInflater());

    // Add the "learn more" link to the body text
    String learnMoreText = getString(R.string.learn_more);
    String smsNoticeText = getString(R.string.sms_intercept_notice_content);
    binding.contentText.setText(StringUtils.generateTextWithHyperlink(
          UrlUtils.createURLSpan(getString(R.string.sms_notice_link)),
          smsNoticeText + " " + learnMoreText, learnMoreText));
    binding.contentText.setMovementMethod(LinkMovementMethod.getInstance());

    return binding.getRoot();
  }

  @Override
  public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    requireActivity().setTitle(R.string.settings_exposure_notifications_subtitle);

    if (getArguments() != null) {
      launchedFromSlice = getArguments().getBoolean(
          EXTRA_LAUNCHED_FROM_SLICE, false);
    }

    binding.home.setOnClickListener(v -> onBackPressed());

    // After the view is created, we can assume the users has noticed the SMS notice and dismiss it
    exposureNotificationViewModel =
        new ViewModelProvider(requireActivity()).get(ExposureNotificationViewModel.class);
    exposureNotificationViewModel.markInAppSmsInterceptNoticeSeenAsync();
  }

  @Override
  public boolean onBackPressed() {
    // If we come from the activeRegionUI we did not change apps, so we just pop the current
    // fragment transaction off the stack to land on the active region screen.
    if (!launchedFromSlice) {
      getParentFragmentManager().popBackStack();
      return true;
    }
    // If we come from the slice, we changed applications, so we need to return to the EN settings
    // page.
    else /*launchedFromSlice*/ {
      launchSettingsForBackPressed();
      return false;
    }
  }

  /**
   * Launches the EN settings screen and clears the activity stack to make it seems like a real back
   * action.
   */
  private void launchSettingsForBackPressed() {
    Intent intent = IntentUtil.getExposureNotificationsSettingsIntent();
    startActivity(intent);
    requireActivity().finishAndRemoveTask();
  }
}
