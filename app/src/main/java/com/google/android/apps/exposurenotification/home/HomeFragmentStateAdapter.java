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

package com.google.android.apps.exposurenotification.home;

import static com.google.android.apps.exposurenotification.home.HomeFragment.TAB_EXPOSURES;
import static com.google.android.apps.exposurenotification.home.HomeFragment.TAB_NOTIFY;
import static com.google.android.apps.exposurenotification.home.HomeFragment.TAB_SETTINGS;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import com.google.android.apps.exposurenotification.exposure.ExposureHomeFragment;
import com.google.android.apps.exposurenotification.notify.NotifyHomeFragment;
import com.google.android.apps.exposurenotification.settings.SettingsHomeFragment;

/**
 * Simple {@link FragmentStateAdapter} for the different home tabs.
 */
public class HomeFragmentStateAdapter extends FragmentStateAdapter {

  HomeFragmentStateAdapter(Fragment fragment) {
    super(fragment);
  }

  @NonNull
  @Override
  public Fragment createFragment(int position) {
    switch (position) {
      case TAB_EXPOSURES:
        return new ExposureHomeFragment();
      case TAB_SETTINGS:
        return new SettingsHomeFragment();
      case TAB_NOTIFY:
        // fall through.
      default:
        return new NotifyHomeFragment();
    }
  }

  @Override
  public int getItemCount() {
    return 3;
  }
}
