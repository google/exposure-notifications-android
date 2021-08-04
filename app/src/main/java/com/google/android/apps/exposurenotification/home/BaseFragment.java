/*
 * Copyright 2021 Google LLC
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

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import com.google.common.base.Preconditions;

/**
 * Base {@link Fragment} for the app fragments.
 */
public abstract class BaseFragment extends Fragment {

  protected static final String EXTRA_CODE_FROM_DEEP_LINK = "BaseFragment.CODE_FROM_DEEP_LINK";
  protected static final String EXTRA_IS_SELF_REPORT_FLOW = "BaseFragment.IS_SELF_REPORT_FLOW";
  protected static final String EXTRA_EXPOSURE_NOTIFICATION_FLOW =
      "BaseFragment.EXTRA_EXPOSURE_NOTIFICATION_FLOW";

  protected ExposureNotificationViewModel exposureNotificationViewModel;

  @Override
  public abstract View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent,
      Bundle savedInstanceState);

  @CallSuper
  @Override
  public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
    exposureNotificationViewModel =
        new ViewModelProvider(requireActivity()).get(ExposureNotificationViewModel.class);
  }

  /**
   * Helper to transition to the given {@link BaseFragment} fragment. Adds the current fragment
   * transaction to the back stack.
   *
   * @param baseFragment The fragment to transit to.
   */
  protected void transitionToFragmentWithBackStack(BaseFragment baseFragment) {
    requireBaseActivity().transitionToFragmentWithBackStack(baseFragment);
  }

  /**
   * Allows child fragments to handle onBackPressed() on their own. Child fragments need to override
   * this method and return true to signal that they handle onBackPressed() on their own.
   *
   * @return true if onBackPressed() is handled by child fragments and false otherwise.
   */
  public boolean onBackPressed() {
    return false;
  }

  /**
   * Returns the parent {@link BaseActivity}.
   *
   * @throws IllegalStateException if not currently associated with an {@link BaseActivity}.
   */
  public BaseActivity requireBaseActivity() {
    FragmentActivity activity = requireActivity();
    Preconditions.checkArgument(activity instanceof BaseActivity);
    return (BaseActivity) activity;
  }
}
