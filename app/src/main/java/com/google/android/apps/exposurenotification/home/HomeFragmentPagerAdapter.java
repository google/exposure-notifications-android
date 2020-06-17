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

import static com.google.android.apps.exposurenotification.home.HomeFragment.TAB_DEBUG;
import static com.google.android.apps.exposurenotification.home.HomeFragment.TAB_EXPOSURES;
import static com.google.android.apps.exposurenotification.home.HomeFragment.TAB_NOTIFY;

import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import com.google.android.apps.exposurenotification.exposure.ExposureHomeFragment;
import com.google.android.apps.exposurenotification.notify.NotifyHomeFragment;

/** Simple {@link FragmentPagerAdapter} for the different home tabs. */
public class HomeFragmentPagerAdapter extends FragmentPagerAdapter {

  private Fragment currentFragment;
  private FragmentManager fm;
  private ClassLoader classLoader;

  HomeFragmentPagerAdapter(FragmentManager fm, ClassLoader classLoader) {
    super(fm, FragmentPagerAdapter.BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
    this.fm = fm;
    this.classLoader = classLoader;
  }

  @Nullable
  private Fragment maybeGetDebugHomeFragment() {
    try {
      return fm.getFragmentFactory().instantiate(
          classLoader,
          "com.google.android.apps.exposurenotification.debug.DebugHomeFragment");
    } catch(Fragment.InstantiationException exception) {
      // Must be release build.
      return null;
    }
  }

  @NonNull
  @Override
  public Fragment getItem(int i) {
    switch (i) {
      case TAB_EXPOSURES:
        return new ExposureHomeFragment();
      case TAB_DEBUG:
        return maybeGetDebugHomeFragment();
      case TAB_NOTIFY:
        // fall through.
      default:
        return new NotifyHomeFragment();
    }
  }

  @Override
  public int getCount() {
    if (maybeGetDebugHomeFragment() != null) {
      return 3;
    } else {
      return 2;
    }
  }

  @Override
  public void setPrimaryItem(@NonNull ViewGroup group, int position, @NonNull Object object) {
    currentFragment = ((Fragment) object);
    super.setPrimaryItem(group, position, object);
  }

  Fragment getCurrentFragment() {
    return currentFragment;
  }
}
