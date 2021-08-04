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

package com.google.android.apps.exposurenotification.common.logging;

import androidx.annotation.VisibleForTesting;
import com.google.android.apps.exposurenotification.BuildConfig;

/**
 * Base class for writing local logging information during app execution.
 * <p>
 * The level of logging that occurs will depend on whether the app is built
 * in debug mode. To get an instance of the appropriate logger, call
 * <code>Logger.getLogger("SecondaryTag")</code>.
 */
public abstract class Logger {

  @VisibleForTesting
  static Logger buildLogger(boolean isDebug, String tag, String secondaryTag) {
    if (isDebug) {
      return new DebugLogger(tag, secondaryTag);
    } else {
      return new ProductionLogger(tag, secondaryTag);
    }
  }

  public static Logger getLogger(String secondaryTag) {
    return buildLogger(
        BuildConfig.DEBUG || BuildConfig.RELEASE_LOGGING,
        "ENX." + BuildConfig.REGION,
        secondaryTag);
  }

  protected final String tag;
  protected final String secondaryTag;

  protected Logger(String tag, String secondaryTag) {
    this.tag = tag;
    this.secondaryTag = secondaryTag;
  }

  protected String getTaggedMessage(String message) {
    return secondaryTag + ": " + message;
  }

  public abstract void d(String message);

  public abstract void d(String message, Throwable tr);

  public abstract void i(String message);

  public abstract void i(String message, Throwable tr);

  public abstract void w(String message);

  public abstract void w(String message, Throwable tr);

  public abstract void e(String message);

  public abstract void e(String message, Throwable tr);
}
