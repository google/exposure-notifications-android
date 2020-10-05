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

package com.google.android.apps.exposurenotification.common;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener;
import com.google.android.material.tabs.TabLayout.Tab;

/**
 * Helper class for managing keyboard in the app.
 */
public final class KeyboardHelper {

  /**
   * Hides the soft keyboard for a given {@link View} if keyboard is visible.
   */
  public static void maybeHideKeyboard(Context context, View view) {
    InputMethodManager inputMethodManager =
        (InputMethodManager) context.getSystemService(Activity.INPUT_METHOD_SERVICE);
    inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
  }

  /**
   * Generates an {@link OnTabSelectedListener} for hiding the keyboard on tab changing.
   */
  public static OnTabSelectedListener createOnTabSelectedMaybeHideKeyboardListener(
      Context context, View view) {
    return new OnTabSelectedListener() {
      @Override
      public void onTabSelected(Tab tab) {
        maybeHideKeyboard(context, view);
      }

      @Override
      public void onTabUnselected(Tab tab) {
      }

      @Override
      public void onTabReselected(Tab tab) {
      }
    };
  }

  private KeyboardHelper() {
  }

}
