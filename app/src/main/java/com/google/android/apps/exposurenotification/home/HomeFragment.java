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

import static com.google.android.apps.exposurenotification.home.ExposureNotificationActivity.HOME_FRAGMENT_TAG;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.viewpager.widget.ViewPager;
import com.google.android.apps.exposurenotification.R;
import com.google.android.material.tabs.TabLayout;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Main screen of the application.
 *
 * <p>HomeFragment display the current status as well as important actions a user may take while
 * using the application.
 *
 * <p>This fragment will be shown first whenever the onboarding flow has already been completed.
 */
public class HomeFragment extends Fragment {

  private static final String TAG = "HomeFragment";

  private static final String KEY_START_TAB = "KEY_START_TAB";

  // Constants so the tabs are settable by name and not just index.
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({TAB_EXPOSURES, TAB_NOTIFY, TAB_DEBUG})
  @interface TabName {

  }

  static final int TAB_EXPOSURES = 0;
  static final int TAB_NOTIFY = 1;
  static final int TAB_DEBUG = 2;

  static final int TAB_DEFAULT = TAB_EXPOSURES;

  private HomeFragmentPagerAdapter fragmentPagerAdapter;

  /**
   * Creates a {@link HomeFragment} instance with a default start tab {@value #TAB_DEFAULT}.
   */
  public static HomeFragment newInstance() {
    return new HomeFragment();
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_home, parent, false);
  }

  private @TabName
  int getStartTab() {
    if (getArguments() != null) {
      return getArguments().getInt(KEY_START_TAB, TAB_DEFAULT);
    } else {
      return TAB_DEFAULT;
    }
  }

  @SuppressWarnings("ConstantConditions")
  @Override
  public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
    fragmentPagerAdapter = new HomeFragmentPagerAdapter(getParentFragmentManager(),
        requireActivity().getClassLoader());

    ViewPager viewPager = view.findViewById(R.id.view_pager);
    viewPager.setOffscreenPageLimit(2);
    viewPager.setAdapter(fragmentPagerAdapter);
    viewPager.setCurrentItem(getStartTab());

    TabLayout tabLayout = view.findViewById(R.id.tab_layout);
    tabLayout.setupWithViewPager(viewPager);
    tabLayout.getTabAt(TAB_EXPOSURES).setIcon(R.drawable.ic_bell);
    tabLayout.getTabAt(TAB_EXPOSURES).setText(R.string.home_tab_exposures_text);
    tabLayout.getTabAt(TAB_NOTIFY).setIcon(R.drawable.ic_flag);
    tabLayout.getTabAt(TAB_NOTIFY).setText(R.string.home_tab_notify_text);
    if (tabLayout.getTabCount() > TAB_DEBUG) {
      tabLayout.getTabAt(TAB_DEBUG).setIcon(R.drawable.ic_cog);
      tabLayout.getTabAt(TAB_DEBUG).setText(R.string.home_tab_notify_debug_text);
    }
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    fragmentPagerAdapter.getCurrentFragment().onActivityResult(requestCode, resultCode, data);
  }

  /**
   * Helper to transition from one fragment to {@link HomeFragment}
   *
   * @param fragment The fragment to transit from
   */
  public static void transitionToHomeFragment(Fragment fragment) {
    // Remove previous fragment from the stack if it is there.
    fragment
        .getParentFragmentManager()
        .popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);

    FragmentTransaction fragmentTransaction =
        fragment.getParentFragmentManager().beginTransaction();
    fragmentTransaction.replace(R.id.home_fragment, HomeFragment.newInstance(), HOME_FRAGMENT_TAG);
    fragmentTransaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
    fragmentTransaction.commit();
  }

  public void setTab(@TabName int tab) {
    View rootView = getView();
    if (rootView == null) {
      Log.w(TAG, "Unable to set the tab");
      return;
    }
    ViewPager viewPager = rootView.findViewById(R.id.view_pager);
    viewPager.setCurrentItem(tab);
  }

}
