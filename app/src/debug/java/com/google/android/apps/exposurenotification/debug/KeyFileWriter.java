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
import androidx.annotation.Nullable;
import com.google.android.apps.exposurenotification.keydownload.KeyFileConstants;
import com.google.android.apps.exposurenotification.proto.SignatureInfo;
import com.google.android.apps.exposurenotification.proto.TEKSignature;
import com.google.android.apps.exposurenotification.proto.TEKSignatureList;
import com.google.android.apps.exposurenotification.proto.TemporaryExposureKeyExport;
import com.google.android.gms.nearby.exposurenotification.TemporaryExposureKey;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.primitives.Bytes;
import com.google.protobuf.ByteString;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.threeten.bp.Instant;

public class KeyFileWriter {
  private static final String FILENAME_PATTERN = "test-keyfile-%d.zip";
  private static final String HEADER_V1 = "EK Export v1";
  private static final int HEADER_LEN = 16;
  private static final int DEFAULT_MAX_BATCH_SIZE = 10000;

  private final Context context;
  @Nullable private final KeyFileSigner signer;

  /**
   * CTor is overloaded to allow instantiation without {@link KeyFileSigner}, needed in tests
   * because Robolectric doesn't support the KeyStore operations the signer uses.
   */
  public KeyFileWriter(Context context) {
    this(context, KeyFileSigner.get(context));
  }

  /**
   * CTor is overloaded to allow instantiation without {@link KeyFileSigner}, needed in tests
   * because Robolectric doesn't support the KeyStore operations the signer uses.
   */
  KeyFileWriter(Context context, @Nullable KeyFileSigner signer) {
    this.context = context;
    this.signer = signer;
  }

  public List<File> writeForKeys(
      List<TemporaryExposureKey> keys, Instant start, Instant end, String regionIsoAlpha2) {
    return writeForKeys(keys, start, end, regionIsoAlpha2, DEFAULT_MAX_BATCH_SIZE);
  }

  public List<File> writeForKeys(
      List<TemporaryExposureKey> keys,
      Instant start,
      Instant end,
      String regionIsoAlpha2,
      int maxBatchSize) {

    if (keys.isEmpty()) {
      return ImmutableList.of();
    }

    List<File> outFiles = new ArrayList<>();

    int batchNum = 1;
    for (List<TemporaryExposureKey> batch : Iterables.partition(keys, maxBatchSize)) {
      File outFile =
          new File(
              context.getFilesDir(), String.format(Locale.ENGLISH, FILENAME_PATTERN, batchNum));
      try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(outFile))) {
        ZipEntry signatureEntry = new ZipEntry(KeyFileConstants.SIG_FILENAME);
        ZipEntry exportEntry = new ZipEntry(KeyFileConstants.EXPORT_FILENAME);

        TemporaryExposureKeyExport exportProto =
            export(batch, start, end, regionIsoAlpha2, batchNum);
        byte[] exportBytes = Bytes.concat(header().getBytes(), exportProto.toByteArray());
        TEKSignatureList signature = sign(exportBytes, batch.size(), batchNum);

        out.putNextEntry(signatureEntry);
        out.write(signature.toByteArray());

        out.putNextEntry(exportEntry);
        out.write(exportBytes);

        outFiles.add(outFile);
        batchNum++;
      } catch (IOException e) {
        // TODO: better exception.
        throw new RuntimeException(e);
      }
    }

    return outFiles;
  }

  private TemporaryExposureKeyExport export(
      List<TemporaryExposureKey> keys,
      Instant start,
      Instant end,
      String regionIsoAlpha2,
      int batchNum) {

    return TemporaryExposureKeyExport.newBuilder()
        .addAllKeys(toProto(keys))
        .addSignatureInfos(signatureInfo())
        .setStartTimestamp(start.toEpochMilli())
        .setEndTimestamp(end.toEpochMilli())
        .setRegion(regionIsoAlpha2)
        .setBatchSize(keys.size())
        .setBatchNum(batchNum)
        .build();
  }

  private TEKSignatureList sign(byte[] exportBytes, int batchSize, int batchNum) {
    // In tests the signer is null because Robolectric doesn't support the crypto constructs we use.
    ByteString signature =
        ByteString.copyFrom(
            signer != null ? signer.sign(exportBytes) : "fake-signature".getBytes());

    return TEKSignatureList.newBuilder()
        .addSignatures(
            TEKSignature.newBuilder()
                .setSignatureInfo(signatureInfo())
                .setBatchNum(batchNum)
                .setBatchSize(batchSize)
                .setSignature(signature))
        .build();
  }

  private SignatureInfo signatureInfo() {
    return signer != null ? signer.signatureInfo() : SignatureInfo.getDefaultInstance();
  }

  private String header() {
    return Strings.padEnd(HEADER_V1, HEADER_LEN, ' ');
  }

  private static List<com.google.android.apps.exposurenotification.proto.TemporaryExposureKey>
      toProto(List<TemporaryExposureKey> keys) {
    List<com.google.android.apps.exposurenotification.proto.TemporaryExposureKey> protos =
        new ArrayList<>();
    for (TemporaryExposureKey k : keys) {
      protos.add(
          com.google.android.apps.exposurenotification.proto.TemporaryExposureKey.newBuilder()
              .setKeyData(ByteString.copyFrom(k.getKeyData()))
              .setRollingStartIntervalNumber(k.getRollingStartIntervalNumber())
              .setRollingPeriod(k.getRollingPeriod())
              .setTransmissionRiskLevel(k.getTransmissionRiskLevel())
              .build());
    }
    return protos;
  }
}
