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

package com.google.android.libraries.privateanalytics;

import static com.google.android.libraries.privateanalytics.PrivateAnalyticsFirestoreRepository.SCHEMA_VERSION_KEY;
import static com.google.android.libraries.privateanalytics.PrivateAnalyticsFirestoreRepository.UUID;
import static org.threeten.bp.temporal.ChronoUnit.HOURS;
import static org.threeten.bp.temporal.ChronoUnit.MINUTES;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;
import com.google.android.libraries.privateanalytics.Qualifiers.PackageName;
import com.google.android.libraries.privateanalytics.proto.CreatePacketsResponse;
import com.google.android.libraries.privateanalytics.proto.Payload;
import com.google.android.libraries.privateanalytics.proto.PrioAlgorithmParameters;
import com.google.android.libraries.privateanalytics.utils.Clock;
import com.google.common.io.BaseEncoding;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.inject.Inject;
import org.threeten.bp.DateTimeUtils;
import org.threeten.bp.Instant;
import org.threeten.bp.ZoneOffset;
import org.threeten.bp.format.DateTimeFormatter;

/**
 * Provide device attestation for submitted payloads.
 */
public class DefaultPrivateAnalyticsDeviceAttestation implements PrivateAnalyticsDeviceAttestation {

  private static final String TAG = "PAPrioDeviceAttestation"; // Logging TAG

  // Strings for Keystore and specifying the cryptographic primitives used.
  private static final String HASH_TYPE = "SHA-256";
  private static final String KEYSTORE_TYPE = "AndroidKeyStore";
  private static final String SIGNATURE_TYPE = "SHA256withECDSA";
  // Function to obtain the UTC date as yyyyMMdd
  private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter
      .ofPattern("yyyyMMdd", Locale.US)
      .withZone(ZoneOffset.UTC);
  private static final BaseEncoding BASE64 = BaseEncoding.base64();

  private final Context context;
  private final String packageName;
  private final PrivateAnalyticsLogger logger;
  private Clock clock = Instant::now;

  @Inject
  public DefaultPrivateAnalyticsDeviceAttestation(Context context,
      @PackageName String packageName, PrivateAnalyticsLogger.Factory loggerFactory) {
    this.context = context;
    this.packageName = packageName;
    this.logger = loggerFactory.create(TAG);
  }

  // Device attestation is only available on Android N and above.
  public static boolean isDeviceAttestationAvailable() {
    return VERSION.SDK_INT >= VERSION_CODES.N;
  }

  private String getFormattedDate() {
    return DATE_TIME_FORMATTER.format(clock.now());
  }

  /**
   * Add the signature of the document payload, and the chain of certificates, to the document.
   * Device attestation is performed by generating a new signing key in Android Keystore.
   * <p>
   * returns true if the document is signed correctly
   */
  @RequiresApi(api = VERSION_CODES.N)
  @Override
  public boolean signPayload(String metricName,
      Map<String, Object> document, PrioAlgorithmParameters params, CreatePacketsResponse response,
      long collectionFrequencyHours)
      throws Exception {
    // Get default Keystore.
    KeyStore keyStore = KeyStore.getInstance(KEYSTORE_TYPE);
    keyStore.load(null, null);

    // Check if a key with the right alias was already created.
    // If this is the case, throws an exception as no submission is needed.
    String keyAlias = getDailyAlias(metricName);
    if (keyStore.containsAlias(keyAlias)) {
      logger.w("Cancelling: private analytic already shared today for this metric.");
      return false;
    }

    // There is no key under this alias in the Android Keystore. Let's create one valid for about
    // collectionFrequencyHours hours (with a 30min buffer).
    Instant tomorrow = clock.now().plus(collectionFrequencyHours, HOURS).minus(30, MINUTES);
    byte[] attestation = getDailyAttestation(metricName); // Embed an attestation in the key.
    KeyPair keyPair = generateKeyPair(context, keyAlias, attestation, tomorrow);

    // Create the payload to sign.
    Map<String, Object> payloadMap = (Map<String, Object>) document.get("payload");
    Payload payload = Payload.newBuilder()
        .setUuid((String) payloadMap.get(UUID))
        .setPrioParams(params)
        .setSchemaVersion((Integer) payloadMap.get(SCHEMA_VERSION_KEY))
        .setPacketsResponse(response)
        .build();

    // Sign the payload.
    document.put("signature", getPayloadSignature(keyPair.getPrivate(), payload));

    // Add the certificate chain to the document for the key just created.
    Certificate[] certificateChain = keyStore.getCertificateChain(keyAlias);
    document.put("certificateChain", convertCertificateChainToStrings(certificateChain));
    return true;
  }

  @Override
  public void clearData(List<String> listOfMetrics) {
    // Delete key Alias
    try {
      KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
      keyStore.load(null, null);

      for (String metric : listOfMetrics) {
        logger.d("PrioPrivateAnalytics: deleting key for metric " + metric);
        String keyAlias = getDailyAlias(metric);
        if (keyStore.containsAlias(keyAlias)) {
          keyStore.deleteEntry(keyAlias);
        }
      }
    } catch (KeyStoreException | CertificateException | IOException | NoSuchAlgorithmException e) {
      logger.w("Error clearing keystore", e);
    }
  }

  /**
   * Generate an ECDSA private key over P-256 in Keystore for a specific alias, a specific
   * attestation challenge, and valid until endDate.
   */
  @RequiresApi(api = VERSION_CODES.N)
  private static KeyPair generateKeyPair(Context context, String alias, byte[] attestation,
      Instant endDate)
      throws NoSuchProviderException, NoSuchAlgorithmException, InvalidAlgorithmParameterException {
    KeyPairGenerator kpg =
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KEYSTORE_TYPE);

    KeyGenParameterSpec.Builder certBuilder = new KeyGenParameterSpec.Builder(alias,
        KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
        .setDigests(KeyProperties.DIGEST_SHA256)
        .setAttestationChallenge(attestation)
        .setKeyValidityEnd(DateTimeUtils.toDate(endDate));

    // Enable StrongBox if possible.
    if (VERSION.SDK_INT >= VERSION_CODES.P
        && context.getPackageManager()
        .hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)) {
      certBuilder.setIsStrongBoxBacked(true);
    }

    kpg.initialize(certBuilder.build());
    return kpg.generateKeyPair();
  }

  // Compute an ECDSA signature over P-256 for the specified payload with the privateKey.
  private static String getPayloadSignature(PrivateKey privateKey, Payload payload)
      throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
    Signature signature = Signature.getInstance(SIGNATURE_TYPE);
    signature.initSign(privateKey);
    signature.update(payload.toByteArray());
    return BASE64.encode(signature.sign());
  }

  // Convert the array of certificates into a list of strings.
  private static List<String> convertCertificateChainToStrings(Certificate[] certificates)
      throws CertificateEncodingException {
    List<String> result = new ArrayList<>(certificates.length);
    for (Certificate certificate : certificates) {
      result.add(BASE64.encode(certificate.getEncoded()));
    }
    return result;
  }

  public static byte[] generateSHA256Hash(String... content) throws NoSuchAlgorithmException {
    MessageDigest digest = MessageDigest.getInstance(HASH_TYPE);
    for (String s : content) {
      digest.update(s.getBytes());
    }
    return digest.digest();
  }

  /**
   * We generate key aliases and key attestations deterministically, by computing alias =
   * SHA256("ENPA::alias" || package_name || metricName || date) attestation =
   * SHA256("ENPA::attestation" || package_name || metricName || date) where date is the UTC date
   * formatted as "yyyyMMdd". This will be recomputed deterministically by the ingestion service for
   * verification purpose.
   */
  public String getDailyAlias(String metricName) throws NoSuchAlgorithmException {
    return BASE64.encode(
        generateSHA256Hash("ENPA::alias", packageName, metricName,
            getFormattedDate()));
  }

  private byte[] getDailyAttestation(String metricName) throws NoSuchAlgorithmException {
    return generateSHA256Hash("ENPA::attestation", packageName, metricName,
        getFormattedDate());
  }

  @VisibleForTesting
  void setClock(Clock clock) {
    this.clock = clock;
  }
}
