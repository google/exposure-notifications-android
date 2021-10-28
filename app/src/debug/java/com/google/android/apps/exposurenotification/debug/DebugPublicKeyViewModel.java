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
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.google.android.apps.exposurenotification.common.SingleLiveEvent;
import com.google.android.apps.exposurenotification.proto.SignatureInfo;
import com.google.auto.value.AutoValue;
import dagger.hilt.android.lifecycle.HiltViewModel;
import dagger.hilt.android.qualifiers.ApplicationContext;
import javax.inject.Inject;

/**
 * View model for {@link DebugPublicKeyFragment}.
 */
@HiltViewModel
public class DebugPublicKeyViewModel extends ViewModel {

  private final SingleLiveEvent<String> snackbarLiveEvent = new SingleLiveEvent<>();

  private final MutableLiveData<SigningKeyInfo> keyInfoLiveData;
  private final KeyFileSigner keyFileSigner;
  private final String packageName;


  @Inject
  public DebugPublicKeyViewModel(@ApplicationContext Context context) {
    keyFileSigner = KeyFileSigner.get();
    packageName = context.getPackageName();

    keyInfoLiveData = new MutableLiveData<>();
    // The keyfile signing key info doesn't change throughout the run of the app.
    setSigningKeyInfo();
  }

  public SingleLiveEvent<String> getSnackbarLiveEvent() {
    return snackbarLiveEvent;
  }

  public LiveData<SigningKeyInfo> getSigningKeyInfoLiveData() {
    return keyInfoLiveData;
  }

  private void setSigningKeyInfo() {
    SignatureInfo signatureInfo = keyFileSigner.signatureInfo();
    SigningKeyInfo info =
        SigningKeyInfo.newBuilder()
            .setPackageName(packageName)
            .setKeyVersion(signatureInfo.getVerificationKeyVersion())
            .setKeyId(signatureInfo.getVerificationKeyId())
            .setPublicKeyBase64(keyFileSigner.getPublicKeyBase64())
            .build();
    keyInfoLiveData.postValue(info);
  }

  @AutoValue
  abstract static class SigningKeyInfo {

    abstract String packageName();

    abstract String keyId();

    abstract String keyVersion();

    abstract String publicKeyBase64();

    static Builder newBuilder() {
      return new AutoValue_DebugPublicKeyViewModel_SigningKeyInfo.Builder();
    }

    @AutoValue.Builder
    abstract static class Builder {

      abstract Builder setPackageName(String p);

      abstract Builder setKeyId(String p);

      abstract Builder setKeyVersion(String p);

      abstract Builder setPublicKeyBase64(String p);

      abstract SigningKeyInfo build();
    }
  }
}
