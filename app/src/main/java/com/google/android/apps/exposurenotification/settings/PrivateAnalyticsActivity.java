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
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.databinding.ActivityPrivateAnalyticsBinding;
import dagger.hilt.android.AndroidEntryPoint;

/**
 * Activity to learn more and turn on and off private analytics.
 */
@AndroidEntryPoint
public class PrivateAnalyticsActivity extends AppCompatActivity {

  private static final String TAG = "PrivateAnalyticsActivity";

  private ActivityPrivateAnalyticsBinding binding;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    binding = ActivityPrivateAnalyticsBinding.inflate(getLayoutInflater());
    setContentView(binding.getRoot());

    PrivateAnalyticsViewModel privateAnalyticsViewModel =
        new ViewModelProvider(this).get(PrivateAnalyticsViewModel.class);

    binding.home.setContentDescription(getString(R.string.navigate_up));
    binding.home.setOnClickListener((v) -> onBackPressed());

    binding.privateAnalyticsSwitch.setText(getString(R.string.private_analytics_subtitle));
    privateAnalyticsViewModel.getPrivateAnalyticsLiveData().observe(this, isEnabled -> {
      binding.privateAnalyticsSwitch.setOnCheckedChangeListener(null);
      binding.privateAnalyticsSwitch.setChecked(isEnabled);
      binding.privateAnalyticsSwitch.setOnCheckedChangeListener(
          (v, checked) -> privateAnalyticsViewModel.setPrivateAnalyticsState(checked));
    });

    String learnMore = getString(R.string.private_analytics_footer_learn_more);
    URLSpan learnMoreClickableSpan = new URLSpan(getString(R.string.private_analytics_link));
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
