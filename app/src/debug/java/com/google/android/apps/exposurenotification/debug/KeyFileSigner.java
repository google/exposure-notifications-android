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
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.security.keystore.KeyGenParameterSpec.Builder;
import android.security.keystore.KeyProperties;
import android.util.Log;
import androidx.annotation.RequiresApi;
import com.google.android.apps.exposurenotification.proto.SignatureInfo;
import com.google.common.io.BaseEncoding;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
import java.security.spec.ECGenParameterSpec;

/**
 * Signs diagnosis key files.
 *
 * <p>Uses a randomly generated public/private keypair to sign files.
 */
public class KeyFileSigner {
  private static final String TAG = "KeyFileSigner";

  private static final String KEY_STORE_NAME = "AndroidKeyStore";
  private static final String KEY_NAME = "KeyFileSigningKey";
  private static final String EC_PARAM_SPEC_NAME = "secp256r1";
  private static final String SIG_ALGO = "SHA256withECDSA";
  // http://oid-info.com/get/1.2.840.10045.4.3.2
  private static final String SIG_ALGO_OID = "1.2.840.10045.4.3.2";
  static final String SIGNATURE_ID = "test_signature_id";
  static final String SIGNATURE_VERSION = "test_signature_version";
  private static final BaseEncoding BASE16 = BaseEncoding.base16().lowerCase();
  private static final BaseEncoding BASE64 = BaseEncoding.base64();

  private static KeyFileSigner INSTANCE;

  private final Context context;
  private KeyPair keyPair;

  /** Private constructor with static creator method, for singleton operation. */
  private KeyFileSigner(Context context) {
    this.context = context;
    init();
  }

  /** Creator method used with private constructor, for singleton operation. */
  public static KeyFileSigner get(Context context) {
    if (INSTANCE == null) {
      INSTANCE = new KeyFileSigner(context);
    }
    return INSTANCE;
  }

  private void init() {
    if (VERSION.SDK_INT < VERSION_CODES.M) {
      initPriorToM();
      return;
    }
    try {
      // See if we already have a key in the store.
      KeyStore keyStore = KeyStore.getInstance(KEY_STORE_NAME);
      keyStore.load(null);
      KeyStore.Entry entry = keyStore.getEntry(KEY_NAME, null);
      if (entry != null) {
        // If we do, use it.
        PrivateKey privateKey = ((KeyStore.PrivateKeyEntry) entry).getPrivateKey();
        PublicKey publicKey = keyStore.getCertificate(KEY_NAME).getPublicKey();
        keyPair = new KeyPair(publicKey, privateKey);
      } else {
        // If we do not have a key already in the store, generate a new one in the store and use it.
        KeyPairGenerator keyPairGenerator =
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KEY_STORE_NAME);
        keyPairGenerator.initialize(
            new Builder(KEY_NAME, KeyProperties.PURPOSE_SIGN)
                .setAlgorithmParameterSpec(new ECGenParameterSpec(EC_PARAM_SPEC_NAME))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setUserAuthenticationRequired(false)
                .build());
        keyPair = keyPairGenerator.generateKeyPair();
      }
    } catch (UnrecoverableEntryException
        | NoSuchProviderException
        | IOException
        | KeyStoreException
        | CertificateException
        | InvalidAlgorithmParameterException
        | NoSuchAlgorithmException e) {
      // TODO: Better exception.
      throw new RuntimeException(e);
    }
  }

  @RequiresApi(api = VERSION_CODES.M)
  private void initPriorToM() {
    try {
      KeyPairGenerator keyGen = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC);
      keyGen.initialize(new ECGenParameterSpec(EC_PARAM_SPEC_NAME));
      // Creates a random key each time.
      keyPair = keyGen.generateKeyPair();
    } catch (InvalidAlgorithmParameterException | NoSuchAlgorithmException e) {
      // TODO: Better exception.
      throw new RuntimeException(e);
    }
  }

  byte[] sign(byte[] message) {
    Log.d(TAG, "Signing " + message.length + " bytes: " + BASE16.encode(message));
    checkKeyStoreInit();
    try {
      Signature sig = Signature.getInstance(SIG_ALGO);
      sig.initSign(keyPair.getPrivate());
      sig.update(message);
      return sig.sign();
    } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
      // TODO: Better exception.
      throw new RuntimeException(e);
    }
  }

  SignatureInfo signatureInfo() {
    // KeyStore init is not strictly required here, but this sig info is useless without KeyStore.
    checkKeyStoreInit();
    return SignatureInfo.newBuilder()
        .setVerificationKeyId(SIGNATURE_ID)
        .setVerificationKeyVersion(SIGNATURE_VERSION)
        .setSignatureAlgorithm(SIG_ALGO_OID)
        .build();
  }

  KeyPair getKeyPair() {
    return keyPair;
  }

  String getPublicKeyBase64() {
    return BASE64.encode(keyPair.getPublic().getEncoded());
  }

  private void checkKeyStoreInit() {
    if (keyPair == null) {
      throw new IllegalStateException(
          "KeyPair was not initialised. That really shouldn't be possible.");
    }
  }
}
