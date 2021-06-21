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

import static com.google.android.apps.exposurenotification.nearby.ExposureNotificationClientWrapper.GMSCORE_PACKAGE_NAME;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.accessibility.AccessibilityEvent;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.databinding.ActivityExposureNotificationBinding;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * Base {@link AppCompatActivity} for the app activities.
 */
public abstract class BaseActivity extends AppCompatActivity {

  public static final String MAIN_FRAGMENT_TAG = "MAIN_FRAGMENT_TAG";
  protected static final String SAVED_INSTANCE_STATE_FRAGMENT_KEY =
      "BaseActivity.SAVED_INSTANCE_STATE_FRAGMENT_KEY";

  protected ExposureNotificationViewModel exposureNotificationViewModel;
  private @Nullable BroadcastReceiver refreshStateBroadcastReceiver = null;
  private ActivityExposureNotificationBinding binding;

  @CallSuper
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    exposureNotificationViewModel =
        new ViewModelProvider(this).get(ExposureNotificationViewModel.class);

    // Handle resolutions for EN opt-in
    exposureNotificationViewModel.registerResolutionForActivityResult(this);

    binding = ActivityExposureNotificationBinding.inflate(getLayoutInflater());
    setContentView(binding.getRoot());

    if (savedInstanceState != null) {
      // If this is a configuration change such as rotation, restore the fragment that was
      // previously saved in onSaveInstanceState
      transitionToFragmentDirect(
          (BaseFragment) Objects.requireNonNull(getSupportFragmentManager()
              .getFragment(savedInstanceState, SAVED_INSTANCE_STATE_FRAGMENT_KEY)));
    } else {
      // This is a fresh launch.
      handleIntent(getIntent(), /* isOnNewIntent= */false);
    }
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    binding = null;
  }

  /**
   * Handles an intent either on fresh launch or onNewIntent().
   *
   * <p>Should transition based on the event.
   *
   * @param intent intent to handle
   * @param isOnNewIntent true if we are handling intent on onNewIntent()
   */
  private void handleIntent(Intent intent, boolean isOnNewIntent) {
    handleIntent(intent.getAction(), intent.getExtras(), intent.getData(), isOnNewIntent);
  }

  /**
   * Handler function to handle UI transitions for either a fresh launch or onNewIntent().
   *
   * @param action the action to perform, may be {@code null} when no action specified
   * @param extras any extras associated with the action, may be {@code null} when no extras exist
   * @param uri    the data URI of the intent, may be {@code null} when no data uri is specified
   * @param isOnNewIntent true if we are handling on onNewIntent()
   */
  protected abstract void handleIntent(
      @Nullable String action,
      @Nullable Bundle extras,
      @Nullable Uri uri,
      boolean isOnNewIntent);

  @CallSuper
  @Override
  public void onResume() {
    super.onResume();
    refreshState();
    if (refreshStateBroadcastReceiver == null) {
      IntentFilter intentFilter = new IntentFilter();
      intentFilter.addAction(LocationManager.PROVIDERS_CHANGED_ACTION);
      intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);

      refreshStateBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          refreshState();
        }
      };

      registerReceiver(refreshStateBroadcastReceiver, intentFilter);
    }
  }

  @CallSuper
  @Override
  public void onPause() {
    super.onPause();
    if (refreshStateBroadcastReceiver != null) {
      unregisterReceiver(refreshStateBroadcastReceiver);
      refreshStateBroadcastReceiver = null;
    }
  }

  @Override
  public void onBackPressed() {
    @Nullable BaseFragment baseFragment = getCurrentMainFragment();
    if (baseFragment != null) {
      if (baseFragment.onBackPressed()) {
        // onBackPressed() is handled by the child fragment, so no need for the activity
        // to handle it. Hence, return immediately.
        return;
      }
    }
    super.onBackPressed();
  }

  @CallSuper
  @Override
  public void onTitleChanged(CharSequence title, int color) {
    super.onTitleChanged(title, color);
    // Fire a TYPE_WINDOW_STATE_CHANGED event so that the accessibility service will be notified
    // of window title change.
    getWindow().getDecorView().sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
  }

  @CallSuper
  @Override
  public void onRequestPermissionsResult(
      int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    // Propagate to the fragments.
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
  }

  /**
   * Save the child {@link BaseFragment} across rotations or other configuration changes.
   *
   * @param outState passed to onCreate when the app finishes the configuration change.
   */
  @CallSuper
  @Override
  protected void onSaveInstanceState(@NonNull Bundle outState) {
    super.onSaveInstanceState(outState);
    getSupportFragmentManager()
        .putFragment(
            outState,
            SAVED_INSTANCE_STATE_FRAGMENT_KEY,
            Objects.requireNonNull(getCurrentMainFragment()));
  }

  @Override
  public void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
    getSupportFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
    handleIntent(intent, /* isOnNewIntent= */true);
  }

  /**
   * Trigger checks for changes in the Exposure Notifications state to refresh UI as needed when the
   * activity is resumed (e.g. App brought back from background)
   */
  private void refreshState() {
    exposureNotificationViewModel.refreshState();
  }

  /**
   * Checks if the caller was GMSCore. Will only work when the caller was GMSCore & the activity was
   * started with startActivityForResult(). If unable to verify, the activity will be finished.
   */
  public final void assertCallerGms() {
    if (!GMSCORE_PACKAGE_NAME.equals(getCallingPackage())) {
      finish();
    }
  }

  /**
   * Returns the current main fragment in focus.
   */
  @Nullable
  public BaseFragment getCurrentMainFragment() {
    return (BaseFragment) getSupportFragmentManager().findFragmentByTag(MAIN_FRAGMENT_TAG);
  }

  /**
   * Helper to transition to the destination {@link BaseFragment} fragment through another
   * {@link BaseFragment} transit fragment. Adds the destination fragment transaction to the back
   * stack.
   *
   * @param destinationFragment The fragment to transit to.
   * @param transitFragment     The fragment to transit through.
   */
  protected void transitionToFragmentThroughAnotherFragmentWithBackStack(
      BaseFragment destinationFragment, BaseFragment transitFragment) {
    transitionToFragmentDirect(transitFragment);
    transitionToFragmentWithBackStack(destinationFragment);
  }

  /**
   * Helper to transition to the given {@link BaseFragment} fragment. Adds the current fragment
   * transaction to the back stack.
   *
   * @param baseFragment The fragment to transit to.
   */
  protected void transitionToFragmentWithBackStack(BaseFragment baseFragment) {
    getSupportFragmentManager().beginTransaction()
        .replace(R.id.main_fragment, baseFragment, MAIN_FRAGMENT_TAG)
        .addToBackStack(null)
        .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
        .commit();
  }

  /**
   * Helper to transition to the given {@link BaseFragment} fragment. Don't add the current fragment
   * transaction to the back stack.
   *
   * @param baseFragment The fragment to transit to.
   */
  protected void transitionToFragmentDirect(BaseFragment baseFragment) {
    getSupportFragmentManager().beginTransaction()
        .replace(R.id.main_fragment, baseFragment, MAIN_FRAGMENT_TAG)
        .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
        .commit();
  }

}
