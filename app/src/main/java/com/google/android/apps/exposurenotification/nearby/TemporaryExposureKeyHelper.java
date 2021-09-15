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

package com.google.android.apps.exposurenotification.nearby;

import static com.google.android.gms.nearby.exposurenotification.TemporaryExposureKey.DAYS_SINCE_ONSET_OF_SYMPTOMS_UNKNOWN;

import androidx.annotation.VisibleForTesting;
import com.google.android.apps.exposurenotification.network.DiagnosisKey;
import com.google.android.apps.exposurenotification.proto.TemporaryExposureKey;
import com.google.android.apps.exposurenotification.proto.TemporaryExposureKey.ReportType;
import com.google.android.apps.exposurenotification.proto.TemporaryExposureKeyExport;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.ArrayList;
import java.util.List;

/**
 * Helper class for transforming TEKs between their different expressions:
 * <ul>
 *   <li>TEK proto messages {@link TemporaryExposureKey}
 *   <li>EN API TEKs {@link com.google.android.gms.nearby.exposurenotification.TemporaryExposureKey}
 *   <li>and our network expression of TEKs {@link DiagnosisKey}
 * </ul>
 */
public final class TemporaryExposureKeyHelper {

  /**
   * Serializes EN API's TEK objects to the byte array with the help of
   * {@link TemporaryExposureKeyExport} proto under the hood.
   */
  public static byte[] keysToTEKExportBytes(
      List<com.google.android.gms.nearby.exposurenotification.TemporaryExposureKey> keys) {
    TemporaryExposureKeyExport keysForExport = TemporaryExposureKeyExport.newBuilder()
        .addAllKeys(keysToKeyProtos(keys))
        .build();
    return keysForExport.toByteArray();
  }

  /**
   * Transforms EN API's TEK objects to the TEK proto message objects.
   */
  @VisibleForTesting static List<TemporaryExposureKey> keysToKeyProtos(
      List<com.google.android.gms.nearby.exposurenotification.TemporaryExposureKey> keys) {
    List<TemporaryExposureKey> protos = new ArrayList<>();
    for (com.google.android.gms.nearby.exposurenotification.TemporaryExposureKey k : keys) {
      TemporaryExposureKey.Builder temporaryExposureKeyBuilder = TemporaryExposureKey.newBuilder();
      temporaryExposureKeyBuilder.setKeyData(ByteString.copyFrom(k.getKeyData()));
      temporaryExposureKeyBuilder.setTransmissionRiskLevel(k.getTransmissionRiskLevel());
      temporaryExposureKeyBuilder.setRollingStartIntervalNumber(k.getRollingStartIntervalNumber());
      temporaryExposureKeyBuilder.setRollingPeriod(k.getRollingPeriod());
      if (k.getReportType() != ReportType.REPORT_TYPE_UNKNOWN_VALUE) {
        temporaryExposureKeyBuilder.setReportType(ReportType.forNumber(k.getReportType()));
      }
      if (k.getDaysSinceOnsetOfSymptoms() != DAYS_SINCE_ONSET_OF_SYMPTOMS_UNKNOWN) {
        temporaryExposureKeyBuilder.setDaysSinceOnsetOfSymptoms(k.getDaysSinceOnsetOfSymptoms());
      }
      protos.add(temporaryExposureKeyBuilder.build());
    }
    return protos;
  }

  /**
   * Parses {@link DiagnosisKey} objects from the byte array with the help of
   * {@link TemporaryExposureKeyExport} proto under the hood.
   */
  public static Optional<List<DiagnosisKey>> maybeBytesToDiagnosisKeys(byte[] tekExportBytes) {
    List<TemporaryExposureKey> keys;
    try {
      keys = TemporaryExposureKeyExport.parseFrom(tekExportBytes).getKeysList();
    } catch (InvalidProtocolBufferException e) {
      return Optional.absent();
    }
    return Optional.of(TemporaryExposureKeyHelper.keyProtosToDiagnosisKeys(keys));
  }

  /**
   * Transforms the TEK proto message objects to our network package's expression of the TEK.
   */
  @VisibleForTesting static ImmutableList<DiagnosisKey> keyProtosToDiagnosisKeys(
      List<TemporaryExposureKey> keys) {
    ImmutableList.Builder<DiagnosisKey> builder = new ImmutableList.Builder<>();
    for (TemporaryExposureKey k : keys) {
      builder.add(
          DiagnosisKey.newBuilder()
              .setKeyBytes(k.getKeyData().toByteArray())
              .setIntervalNumber(k.getRollingStartIntervalNumber())
              .setRollingPeriod(k.getRollingPeriod())
              .setTransmissionRisk(k.getTransmissionRiskLevel())
              .build());
    }
    return builder.build();
  }

  private TemporaryExposureKeyHelper() {
  }

}
