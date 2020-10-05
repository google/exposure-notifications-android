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

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.apps.exposurenotification.R;
import com.google.android.material.switchmaterial.SwitchMaterial;
import dagger.hilt.android.AndroidEntryPoint;

/**
 * Activity to learn more and turn on and off app analytics.
 */
@AndroidEntryPoint
public class AppAnalyticsActivity extends AppCompatActivity {

  private static final String TAG = "AppAnalyticsActivity";

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_app_analytics);

    AppAnalyticsViewModel appAnalyticsViewModel =
        new ViewModelProvider(this).get(AppAnalyticsViewModel.class);

    View upButton = findViewById(android.R.id.home);
    upButton.setContentDescription(getString(R.string.navigate_up));
    upButton.setOnClickListener((v) -> onBackPressed());

    SwitchMaterial toggle = findViewById(R.id.app_analytics_switch);
    appAnalyticsViewModel.getAppAnalyticsLiveData().observe(this, isEnabled -> {
      toggle.setOnCheckedChangeListener(null);
      toggle.setChecked(isEnabled);
      toggle.setOnCheckedChangeListener(
          (v, checked) -> appAnalyticsViewModel.setAppAnalyticsState(checked));
    });

    Button learnMore = findViewById(R.id.app_analytics_learn_mode);
    learnMore.setOnClickListener(v -> startActivity(
        new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.app_analytics_link)))));
  }

}
