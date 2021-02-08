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
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.databinding.ActivityAgencyBinding;
import dagger.hilt.android.AndroidEntryPoint;

/**
 * Activity for information about the agency.
 */
@AndroidEntryPoint
public class AgencyActivity extends AppCompatActivity {

  private static final String TAG = "AgencyActivity";
  private ActivityAgencyBinding binding;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    binding = ActivityAgencyBinding.inflate(getLayoutInflater());
    setContentView(binding.getRoot());

    binding.home.setContentDescription(getString(R.string.navigate_up));
    binding.home.setOnClickListener((v) -> onBackPressed());

    String websiteUrl = getString(R.string.health_authority_website_url);
    URLSpan linkClickableSpan = new URLSpan(websiteUrl);
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