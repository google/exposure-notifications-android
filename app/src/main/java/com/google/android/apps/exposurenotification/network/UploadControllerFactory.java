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

import android.content.Context;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;

/**
 * A builder of {@link UploadController}s.
 *
 * <p>Encapsulates instantiation of its dependency graph.
 */
public final class UploadControllerFactory {

  // Prevent instantiation
  private UploadControllerFactory(){}

  public static UploadController create(Context context) {
    // Here's a lot of service setup that should be done in a DI container.
    RequestQueueWrapper queue =
        RequestQueueWrapper.wrapping(RequestQueueSingleton.get(context));
    ExposureNotificationSharedPreferences prefs =
        new ExposureNotificationSharedPreferences(context);
    Uris uris = new Uris(context, queue, prefs);
    DiagnosisAttestor attestor = new DiagnosisAttestor(context, uris, queue);
    DiagnosisKeyUploader uploader =
        new DiagnosisKeyUploader(context, uris, queue);
    CountryCodes countries = new CountryCodes(context);
    return new UploadController(countries, attestor, uploader, prefs);
  }
}
