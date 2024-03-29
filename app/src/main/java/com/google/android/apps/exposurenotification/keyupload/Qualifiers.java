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

package com.google.android.apps.exposurenotification.keyupload;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import javax.inject.Qualifier;

public class Qualifiers {

  @Qualifier
  @Retention(RetentionPolicy.RUNTIME)
  public @interface DownloadUri {}

  @Qualifier
  @Retention(RetentionPolicy.RUNTIME)
  public @interface UploadUri {}

  @Qualifier
  @Retention(RetentionPolicy.RUNTIME)
  public @interface VerificationCodeUri {}

  @Qualifier
  @Retention(RetentionPolicy.RUNTIME)
  public @interface VerificationCertUri {}

  @Qualifier
  @Retention(RetentionPolicy.RUNTIME)
  public @interface VerificationUserReportUri {}

}