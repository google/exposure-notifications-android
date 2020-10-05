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

package com.google.android.apps.exposurenotification.debug;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.KeyboardHelper;
import com.google.android.material.tabs.TabLayout;
import dagger.hilt.android.AndroidEntryPoint;

/**
 * Activity for the various debug UIs related to matching like viewing and providing keys.
 */
@AndroidEntryPoint
public final class MatchingDebugActivity extends AppCompatActivity {

  public static final String TAB_EXTRA = "TAB_EXTRA";

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.activity_matching);

    Toolbar toolbar = findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
    if (getSupportActionBar() != null) {
      getSupportActionBar().setDisplayShowTitleEnabled(false);
      getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    TabLayout tabLayout = findViewById(R.id.tab_layout);
    ViewPager viewPager = findViewById(R.id.view_pager);
    FragmentPagerAdapter fragmentPagerAdapter =
        new TemporaryExposureKeyViewPagerAdapter(getSupportFragmentManager());
    viewPager.setAdapter(fragmentPagerAdapter);
    tabLayout.setupWithViewPager(viewPager);
    tabLayout.addOnTabSelectedListener(
        KeyboardHelper.createOnTabSelectedMaybeHideKeyboardListener(this, toolbar.getRootView()));

    Intent intent = getIntent();
    int page;
    if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
      page = 1;
    } else {
      page = intent.getIntExtra(TAB_EXTRA, 0);
    }
    viewPager.setCurrentItem(page);
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    for (Fragment fragment : getSupportFragmentManager().getFragments()) {
      fragment.onActivityResult(requestCode, resultCode, data);
    }
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == android.R.id.home) {
      onBackPressed();
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  /**
   * Simple {@link FragmentPagerAdapter} for viewing the different databases related to infected
   * ids.
   */
  public class TemporaryExposureKeyViewPagerAdapter extends FragmentPagerAdapter {

    TemporaryExposureKeyViewPagerAdapter(FragmentManager fm) {
      super(fm, FragmentPagerAdapter.BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
    }

    @NonNull
    @Override
    public Fragment getItem(int i) {
      switch (i) {
        case 0:
          return new KeysMatchingFragment();
        case 1:
          // fall through.
        default:
          return new ProvideMatchingFragment();
      }
    }

    @Override
    public int getCount() {
      return 2;
    }

    @Override
    public String getPageTitle(int i) {
      switch (i) {
        case 0:
          return getString(R.string.debug_matching_view_tab);
        case 1:
          return getString(R.string.debug_matching_provide_tab);
        default:
          return "";
      }
    }
  }
}