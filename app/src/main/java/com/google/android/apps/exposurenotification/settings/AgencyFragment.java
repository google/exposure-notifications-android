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
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.databinding.FragmentAgencyBinding;
import com.google.android.apps.exposurenotification.home.BaseFragment;
import com.google.android.apps.exposurenotification.utils.UrlUtils;
import dagger.hilt.android.AndroidEntryPoint;

/**
 * Fragment for information about the agency.
 */
@AndroidEntryPoint
public class AgencyFragment extends BaseFragment {

  private FragmentAgencyBinding binding;

  public static AgencyFragment newInstance() {
    return new AgencyFragment();
  }

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent,
      Bundle savedInstanceState) {
    binding = FragmentAgencyBinding.inflate(getLayoutInflater());
    return binding.getRoot();
  }

  @Override
  public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    requireActivity().setTitle(R.string.agency_message_title);

    binding.home.setContentDescription(getString(R.string.navigate_up));
    binding.home.setOnClickListener(v -> requireActivity().onBackPressed());

    String websiteUrl = getString(R.string.health_authority_website_url);
    URLSpan linkClickableSpan = UrlUtils.createURLSpan(websiteUrl);
    String agencyMessageLinkText = getString(R.string.agency_message_link, websiteUrl);
    SpannableString agencyMessageLinkSpannableString = new SpannableString(agencyMessageLinkText);
    int websiteUrlStart = agencyMessageLinkText.indexOf(websiteUrl);
    agencyMessageLinkSpannableString
        .setSpan(linkClickableSpan, websiteUrlStart, websiteUrlStart + websiteUrl.length(),
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    binding.agencyMessageLink.setText(agencyMessageLinkSpannableString);
    binding.agencyMessageLink.setMovementMethod(LinkMovementMethod.getInstance());
  }

}
