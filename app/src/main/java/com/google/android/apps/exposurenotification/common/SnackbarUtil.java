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

package com.google.android.apps.exposurenotification.common;

import android.view.View;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import com.google.android.material.snackbar.Snackbar;

/**
 * Util class for creating snackbars.
 */
public class SnackbarUtil {

  /**
   * Shows a regular {@link Snackbar} attached to the given view but only if that view is not null.
   *
   * @param view the view to attach the snackbar to
   * @param message the message to show
   */
  public static void maybeShowRegularSnackbar(@Nullable View view, String message) {
    if (view != null) {
      SnackbarUtil.createRegularSnackbar(view, message).show();
    }
  }

  /**
   * Creates a regular {@link Snackbar}. Defaults with duration {@value Snackbar#LENGTH_LONG}.
   *
   * @param view the view to attach the snackbar to
   * @param message the message to show
   * @return the Snackbar to be shown.
   */
  public static Snackbar createRegularSnackbar(View view, String message) {
    return Snackbar.make(view, message, Snackbar.LENGTH_LONG);
  }

  /**
   * Creates a large {@link Snackbar} with 5 lines. Defaults with duration {@value
   * Snackbar#LENGTH_LONG}.
   *
   * @param view the view to attach the snackbar to
   * @param messageId the resource id of the message to use
   * @return the Snackbar to be shown.
   */
  public static Snackbar createLargeSnackbar(View view, @StringRes int messageId) {
    return createLargeSnackbar(view, view.getContext().getString(messageId));
  }

  private static Snackbar createLargeSnackbar(View view, String message) {
    Snackbar snackbar = Snackbar.make(view, message, Snackbar.LENGTH_LONG);
    View snackbarView = snackbar.getView();
    TextView tv = snackbarView.findViewById(com.google.android.material.R.id.snackbar_text);
    tv.setMaxLines(5);
    return snackbar;
  }

  private SnackbarUtil() {
  }
}
