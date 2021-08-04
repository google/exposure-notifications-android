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

package com.google.android.apps.exposurenotification.debug;

import android.content.Context;
import android.content.res.Resources;
import android.text.TextUtils;
import androidx.hilt.lifecycle.ViewModelInject;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.SingleLiveEvent;
import com.google.android.apps.exposurenotification.common.TelephonyHelper;
import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.android.apps.exposurenotification.debug.VerificationCodeCreator.VerificationCode;
import com.google.common.base.Optional;
import dagger.hilt.android.qualifiers.ApplicationContext;

/**
 * View model for the {@link VerifiableSmsActivity}.
 */
public class VerifiableSmsViewModel extends ViewModel {

  private static final SingleLiveEvent<String> snackbarLiveEvent = new SingleLiveEvent<>();
  private final MutableLiveData<String> phoneNumberLiveData = new MutableLiveData<>("");
  private final MutableLiveData<String> verifiableSmsLiveData = new MutableLiveData<>("");

  private final Resources resources;
  private final VerifiableSmsCreator verifiableSmsCreator;
  private final TelephonyHelper telephonyHelper;

  private VerificationCode verificationCode;

  @ViewModelInject
  public VerifiableSmsViewModel(
      @ApplicationContext Context context,
      TelephonyHelper telephonyHelper,
      Clock clock) {
    resources = context.getResources();
    verifiableSmsCreator = new VerifiableSmsCreator(clock);
    this.telephonyHelper = telephonyHelper;
  }

  public void setVerificationCode(
      VerificationCode verificationCode) {
    this.verificationCode = verificationCode;
  }

  public SingleLiveEvent<String> getSnackbarSingleLiveEvent() {
    return snackbarLiveEvent;
  }

  public MutableLiveData<String> getVerifiableSmsLiveData() {
    return verifiableSmsLiveData;
  }

  public MutableLiveData<String> getPhoneNumberLiveData() {
    return phoneNumberLiveData;
  }

  public void setPhoneNumber(String phonenNumber) {
    phoneNumberLiveData.postValue(phonenNumber);
  }

  public void queryAndMaybeSetPhoneNumber() {
    Optional<String> phoneNumber = telephonyHelper.maybeGetPhoneNumber();
    if (phoneNumber.isPresent()) {
      phoneNumberLiveData.postValue(phoneNumber.get());
    } else {
      snackbarLiveEvent.postValue(
          resources.getString(R.string.debug_verifiable_sms_no_phone_number_error));
    }
  }

  public void createVerifiableSms(String phoneNumber) {
    if (VerificationCode.EMPTY.equals(verificationCode)) {
      snackbarLiveEvent.postValue(
          resources.getString(R.string.debug_verifiable_sms_no_code_error));
      return;
    }

    String phoneNumberNormalized = telephonyHelper.normalizePhoneNumber(phoneNumber);
    if (TextUtils.isEmpty(phoneNumberNormalized)) {
      snackbarLiveEvent.postValue(
          resources.getString(R.string.debug_verifiable_sms_invalid_phone_number_error));
      return;
    }

    String verifiableSms = verifiableSmsCreator.craftVerifiableSmsFromVerificationCode(
        phoneNumberNormalized, verificationCode);
    if (verifiableSms == null || verifiableSms.isEmpty()) {
      snackbarLiveEvent.postValue(
          resources.getString(R.string.debug_verifiable_sms_generation_error));
      return;
    }
    verifiableSmsLiveData.postValue(verifiableSms);

  }

}
