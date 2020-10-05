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

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.viewpager2.widget.ViewPager2;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.KeyboardHelper;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import dagger.hilt.android.AndroidEntryPoint;
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
@AndroidEntryPoint
public class HomeFragment extends Fragment {

  private static final String TAG = "HomeFragment";

  private static final String SAVED_INSTANCE_STATE_CURRENT_ITEM =
      "HomeFragment.SAVED_INSTANCE_STATE_CURRENT_ITEM";

  private static final String KEY_START_TAB = "KEY_START_TAB";

  // Constants so the tabs are settable by name and not just index.
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({TAB_EXPOSURES, TAB_NOTIFY, TAB_SETTINGS})
  @interface TabName {

  }

  static final int TAB_EXPOSURES = 0;
  static final int TAB_NOTIFY = 1;
  static final int TAB_SETTINGS = 2;

  static final int TAB_DEFAULT = TAB_EXPOSURES;

  private HomeFragmentStateAdapter fragmentStateAdapter;
  private ViewPager2 viewPager;

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

  @Override
  public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
    getActivity().setTitle(R.string.app_name);
    fragmentStateAdapter = new HomeFragmentStateAdapter(this);

    viewPager = view.findViewById(R.id.view_pager);
    viewPager.setUserInputEnabled(false);
    viewPager.setOffscreenPageLimit(2);
    viewPager.setAdapter(fragmentStateAdapter);
    if (savedInstanceState != null) {
      viewPager.setCurrentItem(
          savedInstanceState.getInt(SAVED_INSTANCE_STATE_CURRENT_ITEM, getStartTab()));
    } else {
      viewPager.setCurrentItem(getStartTab());
    }

    TabLayout tabLayout = view.findViewById(R.id.tab_layout);
    new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
      switch (position) {
        case TAB_EXPOSURES:
          tab.setIcon(R.drawable.ic_bell);
          tab.setText(R.string.home_tab_exposures_text);
          break;
        case TAB_NOTIFY:
          tab.setIcon(R.drawable.ic_flag);
          tab.setText(R.string.home_tab_notify_text);
          break;
        case TAB_SETTINGS:
          tab.setIcon(R.drawable.ic_cog);
          tab.setText(R.string.home_tab_settings_text);
          break;
      }
    }).attach();

    tabLayout.addOnTabSelectedListener(
        KeyboardHelper.createOnTabSelectedMaybeHideKeyboardListener(requireContext(), view));
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putInt(SAVED_INSTANCE_STATE_CURRENT_ITEM, viewPager.getCurrentItem());
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
    ViewPager2 viewPager = rootView.findViewById(R.id.view_pager);
    viewPager.setCurrentItem(tab);
  }

}
