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
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.apps.exposurenotification.R;
import dagger.hilt.android.AndroidEntryPoint;

/**
 * Activity for information about the agency.
 */
@AndroidEntryPoint
public class AgencyActivity extends AppCompatActivity {

  private static final String TAG = "AgencyActivity";

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.activity_agency);

    View upButton = findViewById(android.R.id.home);
    upButton.setContentDescription(getString(R.string.navigate_up));
    upButton.setOnClickListener((v) -> onBackPressed());

    String websiteUrl = getString(R.string.health_authority_website_url);
    URLSpan linkClickableSpan = new URLSpan(websiteUrl);
    String agencyMessageLinkText = getString(R.string.agency_message_link, websiteUrl);
    SpannableString agencyMessageLinkSpannableString = new SpannableString(agencyMessageLinkText);
    int websiteUrlStart = agencyMessageLinkText.indexOf(websiteUrl);
    agencyMessageLinkSpannableString
        .setSpan(linkClickableSpan, websiteUrlStart, websiteUrlStart + websiteUrl.length(),
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    TextView linkTextView = findViewById(R.id.agency_message_link);
    linkTextView.setText(agencyMessageLinkSpannableString);
    linkTextView.setMovementMethod(LinkMovementMethod.getInstance());
  }

}