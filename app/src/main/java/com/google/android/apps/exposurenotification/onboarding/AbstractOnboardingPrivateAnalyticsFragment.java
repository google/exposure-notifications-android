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

package com.google.android.apps.exposurenotification.onboarding;

import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.URLSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.home.BaseFragment;
import com.google.android.apps.exposurenotification.utils.UrlUtils;

/**
 * Abstract superclass fragment for onboarding private analytics fragments.
 */
public abstract class AbstractOnboardingPrivateAnalyticsFragment extends BaseFragment {

  protected OnboardingViewModel onboardingViewModel;

  @Override
  public abstract View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent,
      Bundle savedInstanceState);

  @CallSuper
  @Override
  public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    onboardingViewModel = new ViewModelProvider(this).get(OnboardingViewModel.class);

    TextView privateAnalyticsFooter = view.findViewById(R.id.private_analytics_footer);
    Button dontShareButton = view.findViewById(R.id.private_analytics_dismiss);
    Button shareButton = view.findViewById(R.id.private_analytics_accept);

    String learnMore = getString(R.string.private_analytics_footer_learn_more);
    URLSpan learnMoreClickableSpan =
        UrlUtils.createURLSpan(getString(R.string.private_analytics_link));
    String footer = getString(R.string.private_analytics_footer_onboarding, learnMore);
    SpannableString footerSpannableString = new SpannableString(footer);
    int learnMoreStart = footer.indexOf(learnMore);
    footerSpannableString
        .setSpan(learnMoreClickableSpan, learnMoreStart, learnMoreStart + learnMore.length(),
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    // Set up footer.
    privateAnalyticsFooter.setText(footerSpannableString);
    privateAnalyticsFooter.setMovementMethod(LinkMovementMethod.getInstance());
    // Set up buttons.
    dontShareButton.setOnClickListener(v -> dontSharePrivateAnalytics());
    shareButton.setOnClickListener(v -> sharePrivateAnalytics());
  }

  /**
   * Called if user decides not to share private analytics.
   */
  abstract void dontSharePrivateAnalytics();

  /**
   * Called if user decides to share private analytics.
   */
  abstract void sharePrivateAnalytics();
}
