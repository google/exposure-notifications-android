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

package com.google.android.apps.exposurenotification.activities;

import static android.view.View.VISIBLE;
import static com.google.android.apps.exposurenotification.activities.ExposureNotificationActivity.HOME_FRAGMENT_TAG;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.fragment.app.FragmentTransaction;
import androidx.viewpager.widget.ViewPager;
import com.google.android.apps.exposurenotification.R;
import com.google.android.material.tabs.TabLayout;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Main screen of the application.
 * <p>
 * HomeFragment display the current status as well as important actions a user may take while using
 * the application.
 * <p>
 * This fragment will be shown first whenever the onboarding flow has already been completed.
 */
public class HomeFragment extends Fragment {

  // Constants so the tabs are settable by name and not just index.
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({TAB_EXPOSURES, TAB_NOTIFY, TAB_DEBUG})
  @interface TabName {}
  static final int TAB_EXPOSURES = 0;
  static final int TAB_NOTIFY = 1;
  static final int TAB_DEBUG = 2;

  @TabName private final int defaultTab;

  private TemporaryExposureKeyViewPagerAdapter fragmentPagerAdapter;

  /** Creates the HomeFragment with the Notify tab as the default view. */
  public HomeFragment() {
    this(TAB_NOTIFY);
  }

  /** Creates the HomeFragment with a specified tab as the default view. */
  HomeFragment(@TabName int defaultTab) {
    this.defaultTab = defaultTab;
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_home, parent, false);
  }

  @SuppressWarnings("ConstantConditions")
  @Override
  public void onViewCreated(View view, Bundle savedInstanceState) {
    TabLayout tabLayout = view.findViewById(R.id.tab_layout);
    ViewPager viewPager = view.findViewById(R.id.view_pager);
    viewPager.setOffscreenPageLimit(2);
    fragmentPagerAdapter = new TemporaryExposureKeyViewPagerAdapter(getParentFragmentManager());
    viewPager.setAdapter(fragmentPagerAdapter);
    viewPager.setCurrentItem(defaultTab);
    tabLayout.setupWithViewPager(viewPager);
    tabLayout.getTabAt(TAB_EXPOSURES).setIcon(R.drawable.ic_bell);
    tabLayout.getTabAt(TAB_NOTIFY).setIcon(R.drawable.ic_flag);
    tabLayout.getTabAt(TAB_DEBUG).setIcon(R.drawable.ic_cog);
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    fragmentPagerAdapter.getCurrentFragment().onActivityResult(requestCode, resultCode, data);
  }

  /**
   * Simple {@link FragmentPagerAdapter} for viewing the different databases related to infected
   * ids.
   */
  public class TemporaryExposureKeyViewPagerAdapter extends FragmentPagerAdapter {

    private Fragment currentFragment;

    TemporaryExposureKeyViewPagerAdapter(FragmentManager fm) {
      super(fm, FragmentPagerAdapter.BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
    }

    @NonNull
    @Override
    public Fragment getItem(int i) {
      switch (i) {
        case TAB_EXPOSURES:
          return new ExposureFragment();
        case TAB_DEBUG:
          return new DebugFragment();
        default:
          return new NotifyFragment();
      }
    }

    @Override
    public int getCount() {
      return 3;
    }

    @Override
    public String getPageTitle(int i) {
      switch (i) {
        case TAB_EXPOSURES:
          return getString(R.string.home_tab_exposures_text);
        case TAB_NOTIFY:
          return getString(R.string.home_tab_notify_text);
        case TAB_DEBUG:
          return getString(R.string.home_tab_notify_debug_text);
        default:
          return "";
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

  /**
   * Helper to transition from one fragment to {@link HomeFragment}
   *
   * @param fragment The fragment to transit from
   */
  static void transitionToHomeFragment(Fragment fragment) {
    // Remove previous fragment from the stack if it is there.
    fragment.getParentFragmentManager()
        .popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);

    FragmentTransaction fragmentTransaction = fragment.getParentFragmentManager()
        .beginTransaction();
    fragmentTransaction.replace(R.id.home_fragment, new HomeFragment(), HOME_FRAGMENT_TAG);
    fragmentTransaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
    fragmentTransaction.commit();
  }
}
