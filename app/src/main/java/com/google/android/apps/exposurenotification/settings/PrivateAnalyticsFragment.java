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

package com.google.android.apps.exposurenotification.settings;

import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.URLSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.databinding.FragmentPrivateAnalyticsBinding;
import com.google.android.apps.exposurenotification.home.BaseFragment;
import com.google.android.apps.exposurenotification.utils.UrlUtils;
import dagger.hilt.android.AndroidEntryPoint;

/**
 * Fragment to learn more and turn on and off private analytics.
 */
@AndroidEntryPoint
public class PrivateAnalyticsFragment extends BaseFragment {

  private static final String TAG = "PrivateAnalyticsFragment";

  private FragmentPrivateAnalyticsBinding binding;

  public static PrivateAnalyticsFragment newInstance() {
    return new PrivateAnalyticsFragment();
  }

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent,
      Bundle savedInstanceState) {
    binding = FragmentPrivateAnalyticsBinding.inflate(getLayoutInflater());
    return binding.getRoot();
  }

  @Override
  public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    requireActivity().setTitle(R.string.settings_private_analytics_subtitle);

    PrivateAnalyticsViewModel privateAnalyticsViewModel =
        new ViewModelProvider(this).get(PrivateAnalyticsViewModel.class);

    binding.home.setContentDescription(getString(R.string.navigate_up));
    binding.home.setOnClickListener(v -> requireActivity().onBackPressed());

    binding.privateAnalyticsSwitch.setText(getString(R.string.private_analytics_subtitle));
    privateAnalyticsViewModel.getPrivateAnalyticsLiveData()
        .observe(getViewLifecycleOwner(), isEnabled -> {
          binding.privateAnalyticsSwitch.setOnCheckedChangeListener(null);
          binding.privateAnalyticsSwitch.setChecked(isEnabled);
          binding.privateAnalyticsSwitch.setOnCheckedChangeListener(
              (v, checked) -> privateAnalyticsViewModel.setPrivateAnalyticsState(checked));
        });

    String learnMore = getString(R.string.private_analytics_footer_learn_more);
    URLSpan learnMoreClickableSpan =
        UrlUtils.createURLSpan(getString(R.string.private_analytics_link));
    String footer = getString(R.string.private_analytics_footer, learnMore);
    SpannableString footerSpannableString = new SpannableString(footer);
    int learnMoreStart = footer.indexOf(learnMore);
    footerSpannableString
        .setSpan(learnMoreClickableSpan, learnMoreStart, learnMoreStart + learnMore.length(),
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    binding.privateAnalyticsFooter.setText(footerSpannableString);
    binding.privateAnalyticsFooter.setMovementMethod(LinkMovementMethod.getInstance());
  }

}
