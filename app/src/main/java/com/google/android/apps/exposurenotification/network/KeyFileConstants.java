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

package com.google.android.apps.exposurenotification.network;

import androidx.annotation.VisibleForTesting;

/**
 * Some static constants for key file parsing.
 */
public final class KeyFileConstants {
  @VisibleForTesting public static final String SIG_FILENAME = "export.sig";
  @VisibleForTesting public static final String EXPORT_FILENAME = "export.bin";

  private KeyFileConstants() {};
}
