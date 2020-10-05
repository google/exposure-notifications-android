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

import android.content.SharedPreferences;
import androidx.lifecycle.LiveData;
import com.google.common.collect.ImmutableSet;

/**
 * A {@link LiveData} that allows you to subscribe to updates of various types of {@link
 * SharedPreferences}.
 */
public abstract class SharedPreferenceLiveData<T> extends LiveData<T> {

  final SharedPreferences sharedPreferences;
  final ImmutableSet<String> keys;

  protected SharedPreferenceLiveData(
      SharedPreferences sharedPreferences,
      String ... keys) {
    this.sharedPreferences = sharedPreferences;
    this.keys = ImmutableSet.copyOf(keys);
  }

  /**
   * Updates the value of the live data.
   */
  protected abstract void updateValue();

  @Override
  protected void onActive() {
    super.onActive();
    updateValue();
    sharedPreferences.registerOnSharedPreferenceChangeListener(preferenceChangeListener);
  }

  @Override
  protected void onInactive() {
    sharedPreferences.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener);
    super.onInactive();
  }

  private final SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener =
      (sharedPreferences, k) -> {
      if (SharedPreferenceLiveData.this.keys.contains(k)) {
          updateValue();
        }
      };

}
