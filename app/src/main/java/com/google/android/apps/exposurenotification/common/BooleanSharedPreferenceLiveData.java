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

/**
 * An {@link androidx.lifecycle.LiveData} of boolean from {@link SharedPreferences}.
 */
public class BooleanSharedPreferenceLiveData extends SharedPreferenceLiveData<Boolean> {

  private final String key;
  private final boolean defaultValue;

  public BooleanSharedPreferenceLiveData(
      SharedPreferences sharedPreferences, String key, boolean defaultValue) {
    super(sharedPreferences, key);
    this.key = key;
    this.defaultValue = defaultValue;
  }

  @Override
  protected void updateValue() {
    setValue(sharedPreferences.getBoolean(key, defaultValue));
  }
}
