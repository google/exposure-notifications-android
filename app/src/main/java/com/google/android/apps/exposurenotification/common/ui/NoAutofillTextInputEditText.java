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

package com.google.android.apps.exposurenotification.common.ui;

import android.content.Context;
import android.widget.TextView;
import android.util.AttributeSet;
import com.google.android.material.textfield.TextInputEditText;


/**
 * Alternate TextInputEditText that is ignored by autofill.
 */
public class NoAutofillTextInputEditText extends TextInputEditText {
  public NoAutofillTextInputEditText(Context context) {
    super(context);
  }

  public NoAutofillTextInputEditText(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public NoAutofillTextInputEditText(Context context, AttributeSet attrs,
                                     int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  @Override
  public int getAutofillType () {
    // Prevent autofill from even processing this element.
    return TextView.AUTOFILL_TYPE_NONE;
  }
}

