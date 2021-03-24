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

import com.google.android.gms.nearby.exposurenotification.TemporaryExposureKey;
import com.google.android.gms.nearby.exposurenotification.TemporaryExposureKey.TemporaryExposureKeyBuilder;
import com.google.common.io.BaseEncoding;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Helper class for encoding and decoding TemporaryExposureKeys for interop testing.
 */
public class TemporaryExposureKeyEncodingHelper {

  private static final BaseEncoding BASE64 = BaseEncoding.base64();

  private static final String KEY_DATA = "keyData";
  private static final String ROLLING_START_NUMBER = "rollingStartIntervalNumber";
  private static final String ROLLING_PERIOD = "rollingPeriod";
  private static final String TRANSMISSION_RISK_LEVEL = "transmissionRiskLevel";
  private static final String DAYS_SINCE_ONSET_OF_SYMPTOMS = "daysSinceOnsetOfSymptoms";
  private static final String REPORT_TYPE = "reportType";

  /**
   * Encodes a {@link List} of {@link TemporaryExposureKey} as a JSON array string.
   */
  public static String encodeList(List<TemporaryExposureKey> temporaryExposureKeys)
      throws EncodeException {
    JSONArray jsonArray = new JSONArray();
    try {
      for (TemporaryExposureKey temporaryExposureKey : temporaryExposureKeys) {
        jsonArray.put(encodeSingleAsJsonObject(temporaryExposureKey));
      }
    } catch (JSONException e) {
      throw new EncodeException(e.getMessage());
    }
    return jsonArray.toString();
  }

  /**
   * Encodes a {@link TemporaryExposureKey} as a JSON object string.
   */
  public static String encodeSingle(TemporaryExposureKey temporaryExposureKey) throws EncodeException {
    try {
      return encodeSingleAsJsonObject(temporaryExposureKey).toString();
    } catch (JSONException e) {
      throw new EncodeException(e.getMessage());
    }
  }

  private static JSONObject encodeSingleAsJsonObject(TemporaryExposureKey temporaryExposureKey)
      throws JSONException {
    return new JSONObject()
        .put(KEY_DATA, BASE64.encode(temporaryExposureKey.getKeyData()))
        .put(ROLLING_START_NUMBER, temporaryExposureKey.getRollingStartIntervalNumber())
        .put(ROLLING_PERIOD, temporaryExposureKey.getRollingPeriod())
        .put(TRANSMISSION_RISK_LEVEL, temporaryExposureKey.getTransmissionRiskLevel());
  }

  /**
   * Decodes a JSON array encoding to a {@link List} of {@link TemporaryExposureKey}s.
   */
  public static List<TemporaryExposureKey> decodeList(String encodedList) throws DecodeException {
    List<TemporaryExposureKey> temporaryExposureKeys = new ArrayList<>();
    JSONArray jsonArray;
    try {
      jsonArray = new JSONArray(encodedList);
      for (int i = 0; i < jsonArray.length(); i++) {
        JSONObject jsonObject = jsonArray.getJSONObject(i);
        temporaryExposureKeys.add(decodeObject(jsonObject));
      }
    } catch (JSONException | IllegalArgumentException e) {
      throw new DecodeException(e.getMessage());
    }
    return temporaryExposureKeys;
  }

  /**
   * Decodes a JSON object encoding to a {@link TemporaryExposureKey}.
   */
  public static TemporaryExposureKey decodeSingle(String encodedSingle) throws DecodeException {
    try {
      return decodeObject(new JSONObject(encodedSingle));
    } catch (JSONException | IllegalArgumentException e) {
      throw new DecodeException(e.getMessage());
    }
  }

  private static TemporaryExposureKey decodeObject(JSONObject jsonObject)
      throws JSONException, IllegalArgumentException {
    TemporaryExposureKeyBuilder temporaryExposureKeyBuilder = new TemporaryExposureKeyBuilder()
        .setKeyData(BASE64.decode(jsonObject.getString(KEY_DATA)))
        .setRollingStartIntervalNumber(jsonObject.getInt(ROLLING_START_NUMBER))
        .setRollingPeriod(jsonObject.getInt(ROLLING_PERIOD))
        .setTransmissionRiskLevel(jsonObject.getInt(TRANSMISSION_RISK_LEVEL));
    if (jsonObject.has(DAYS_SINCE_ONSET_OF_SYMPTOMS)) {
      temporaryExposureKeyBuilder
          .setDaysSinceOnsetOfSymptoms(jsonObject.getInt(DAYS_SINCE_ONSET_OF_SYMPTOMS));
    }
    if (jsonObject.has(REPORT_TYPE)) {
      temporaryExposureKeyBuilder.setReportType(jsonObject.getInt(REPORT_TYPE));
    }
    return temporaryExposureKeyBuilder.build();
  }

  public static class EncodeException extends JSONException {
    public EncodeException(String s) {
      super(s);
    }
  }

  public static class DecodeException extends JSONException {
    public DecodeException(String s) {
      super(s);
    }
  }
}
