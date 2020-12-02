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

import static com.google.common.truth.Truth.assertThat;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.apps.exposurenotification.keydownload.KeyFileConstants;
import com.google.android.apps.exposurenotification.proto.TEKSignatureList;
import com.google.android.apps.exposurenotification.proto.TemporaryExposureKeyExport;
import com.google.android.gms.nearby.exposurenotification.TemporaryExposureKey;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.protobuf.ByteString;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;
import org.threeten.bp.Instant;

/** Tests of {@link KeyFileWriter}. */
@RunWith(AndroidJUnit4.class)
@HiltAndroidTest
@Config(application = HiltTestApplication.class)
public class KeyFileWriterTest {
  public static final String HEADER_V1 = "EK Export v1";

  private KeyFileWriter writer;

  @Before
  public void setUp() {
    // Set up the writer with a null signer so we skip the crypto setup that isn't supported on
    // Robolectric.
    writer = new KeyFileWriter(ApplicationProvider.getApplicationContext(), /* signer= */ null);
  }

  @Test
  public void instantiation_works() {
    assertThat(writer).isNotNull();
  }

  @Test
  public void givenEmptyListOfkeys_returnsEmptyFileList() {
    Instant start = Instant.ofEpochMilli(1234);
    Instant end = Instant.ofEpochMilli(5678);
    String region = "GB";
    int batchSize = 100;
    List<File> actual = writer.writeForKeys(ImmutableList.of(), start, end, region, batchSize);
    assertThat(actual).isEmpty();
  }

  @Test
  public void exportHeader_isExpectedString_paddedTo16Chars() throws Exception {
    Instant start = Instant.ofEpochMilli(1234);
    Instant end = Instant.ofEpochMilli(5678);
    String region = "GB";
    int batchSize = 100;
    File file =
        writer
            .writeForKeys(
                ImmutableList.of(keyOf("key".getBytes(), 1, 1, 1)), start, end, region, batchSize)
            .get(0);

    FileContent actual = readFile(file);
    String expectedHeader = Strings.padEnd(HEADER_V1, 16, ' ');
    assertThat(actual.header).isEqualTo(expectedHeader);
  }

  @Test
  public void export_hasKeys() throws Exception {
    Instant start = Instant.ofEpochMilli(1234);
    Instant end = Instant.ofEpochMilli(5678);
    String region = "GB";
    int batchSize = 100;
    ImmutableList<TemporaryExposureKey> keys =
        ImmutableList.of(
            keyOf("key-1".getBytes(), 111111, 11, 1),
            keyOf("key-1".getBytes(), 222222, 22, 2),
            keyOf("key-1".getBytes(), 333333, 33, 3));

    File file = writer.writeForKeys(keys, start, end, region, batchSize).get(0);

    FileContent actual = readFile(file);
    assertThat(actual.export.getKeysList()).isEqualTo(toProto(keys));
  }

  @Test
  public void export_hasStartAndEndTimestamp() throws Exception {
    Instant start = Instant.ofEpochMilli(1234);
    Instant end = Instant.ofEpochMilli(5678);
    String region = "GB";
    int batchSize = 100;
    ImmutableList<TemporaryExposureKey> keys =
        ImmutableList.of(
            keyOf("key-1".getBytes(), 111111, 11, 1),
            keyOf("key-1".getBytes(), 222222, 22, 2),
            keyOf("key-1".getBytes(), 333333, 33, 3));

    File file = writer.writeForKeys(keys, start, end, region, batchSize).get(0);

    FileContent actual = readFile(file);
    assertThat(actual.export.getStartTimestamp()).isEqualTo(start.toEpochMilli());
    assertThat(actual.export.getEndTimestamp()).isEqualTo(end.toEpochMilli());
  }

  @Test
  public void export_hasRegion() throws Exception {
    Instant start = Instant.ofEpochMilli(1234);
    Instant end = Instant.ofEpochMilli(5678);
    String region = "GB";
    int batchSize = 100;
    ImmutableList<TemporaryExposureKey> keys =
        ImmutableList.of(
            keyOf("key-1".getBytes(), 111111, 11, 1),
            keyOf("key-1".getBytes(), 222222, 22, 2),
            keyOf("key-1".getBytes(), 333333, 33, 3));

    File file = writer.writeForKeys(keys, start, end, region, batchSize).get(0);

    FileContent actual = readFile(file);
    assertThat(actual.export.getRegion()).isEqualTo(region);
  }

  @Test
  public void moreKeysThanMaxBatchSize_exportFileHasBatchSize_equalToMax() throws Exception {
    Instant start = Instant.ofEpochMilli(1234);
    Instant end = Instant.ofEpochMilli(5678);
    String region = "GB";
    ImmutableList<TemporaryExposureKey> keys =
        ImmutableList.of(
            keyOf("key-1".getBytes(), 111111, 11, 1),
            keyOf("key-1".getBytes(), 222222, 22, 2),
            keyOf("key-1".getBytes(), 333333, 33, 3));
    // A number fewer than the number of keys, so that the batch size limit kicks in
    int maxBatchSize = keys.size() - 1;

    File file = writer.writeForKeys(keys, start, end, region, maxBatchSize).get(0);

    FileContent actual = readFile(file);
    assertThat(actual.export.getBatchSize()).isEqualTo(maxBatchSize);
  }

  @Test
  public void fewerKeysThanMaxBatchSize_exportFileHasBatchSize_equalToKeyCount() throws Exception {
    Instant start = Instant.ofEpochMilli(1234);
    Instant end = Instant.ofEpochMilli(5678);
    String region = "GB";
    ImmutableList<TemporaryExposureKey> keys =
        ImmutableList.of(
            keyOf("key-1".getBytes(), 111111, 11, 1),
            keyOf("key-1".getBytes(), 222222, 22, 2),
            keyOf("key-1".getBytes(), 333333, 33, 3));
    // A number greater than the number of keys, so that the batch size limit does not kick in
    int maxBatchSize = keys.size() + 1;

    File file = writer.writeForKeys(keys, start, end, region, maxBatchSize).get(0);

    FileContent actual = readFile(file);
    assertThat(actual.export.getBatchSize()).isEqualTo(keys.size());
  }

  @Test
  public void export_splitsIntoBatches() throws Exception {
    Instant start = Instant.ofEpochMilli(1234);
    Instant end = Instant.ofEpochMilli(5678);
    String region = "GB";
    int maxBatchSize = 10;
    ImmutableList.Builder<TemporaryExposureKey> builder = new Builder<>();
    for (int i = 0; i < 105; i++) {
      // I'm pretty sure there are 8 transmission risk levels.
      builder.add(keyOf(("key-" + i).getBytes(), 11111 + i, i + 1, (i % 7) + 1));
    }
    ImmutableList<TemporaryExposureKey> keys = builder.build();

    List<File> files = writer.writeForKeys(keys, start, end, region, maxBatchSize);
    assertThat(files).hasSize(11);

    FileContent firstFile = readFile(files.get(0));
    assertThat(firstFile.export.getKeysList()).hasSize(maxBatchSize);
    assertThat(firstFile.export.getBatchSize()).isEqualTo(maxBatchSize);
    assertThat(firstFile.export.getBatchNum()).isEqualTo(1);

    FileContent lastFile = readFile(files.get(10));
    assertThat(lastFile.export.getKeysList()).hasSize(5);
    assertThat(lastFile.export.getBatchSize()).isEqualTo(5);
    assertThat(lastFile.export.getBatchNum()).isEqualTo(11);
  }

  private static TemporaryExposureKey keyOf(
      byte[] key, int intervalNum, int period, int transmissionRisk) {
    return new TemporaryExposureKey.TemporaryExposureKeyBuilder()
        .setKeyData(key)
        .setRollingStartIntervalNumber(intervalNum)
        .setRollingPeriod(period)
        .setTransmissionRiskLevel(transmissionRisk)
        .build();
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

  private FileContent readFile(File file) throws Exception {
    ZipFile zip = new ZipFile(file);
    assertThat(zip.size()).isGreaterThan(0);

    ZipEntry signatureEntry = zip.getEntry(KeyFileConstants.SIG_FILENAME);
    ZipEntry exportEntry = zip.getEntry(KeyFileConstants.EXPORT_FILENAME);

    byte[] sigData = IOUtils.toByteArray(zip.getInputStream(signatureEntry));
    byte[] bodyData = IOUtils.toByteArray(zip.getInputStream(exportEntry));

    byte[] header = Arrays.copyOf(bodyData, 16);
    byte[] exportData = Arrays.copyOfRange(bodyData, 16, bodyData.length);

    String headerString = new String(header);
    TEKSignatureList signature = TEKSignatureList.parseFrom(sigData);
    TemporaryExposureKeyExport export = TemporaryExposureKeyExport.parseFrom(exportData);

    return new FileContent(headerString, export, signature);
  }

  private static class FileContent {
    private final String header;
    private final TemporaryExposureKeyExport export;
    private final TEKSignatureList signature;

    FileContent(String header, TemporaryExposureKeyExport export, TEKSignatureList signature) {
      this.export = export;
      this.header = header;
      this.signature = signature;
    }
  }
}
