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

package com.google.android.apps.exposurenotification.testsupport;

import static org.robolectric.shadow.api.Shadow.directlyOn;

import android.content.res.Resources;
import android.util.SparseArray;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.shadows.ShadowResources;

@Implements(Resources.class)
public class FakeShadowResources extends ShadowResources {

  private final SparseArray<Object> resources = new SparseArray<>();

  @RealObject
  private Resources realResources;

  /** Adds a fake resource object. */
  public void addFakeResource(int resourceId, Object value) {
    resources.put(resourceId, value);
  }

  @Implementation
  public String getString(int id) throws Resources.NotFoundException {
    final Object value = resources.get(id);
    if (value == null) {
      // Call the real object since no value was set for this shadow.
      return directlyOn(realResources, Resources.class).getString(id);
    }
    return (String) value;
  }
}