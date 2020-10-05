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

package com.google.android.apps.exposurenotification.riskcalculation;

import com.google.android.gms.nearby.exposurenotification.DiagnosisKeysDataMapping;
import com.google.android.gms.nearby.exposurenotification.DiagnosisKeysDataMapping.DiagnosisKeysDataMappingBuilder;
import com.google.common.io.BaseEncoding;
import java.util.HashMap;
import java.util.Map;

/*
 * Class to craft a DiagnosisKeysDataMapping object to pass to Nearby.setDiagnosisKeysDataMapping()
 */
public class DiagnosisKeyDataMappingHelper {
  /**
   * Convert the day to infectioness mapping from its String representation back to a
   * DiagnosisKeysDataMapping object.
   */
  public static DiagnosisKeysDataMapping createDiagnosisKeysDataMapping(
      String symptomOnsetToInfectiousnessString, int reportTypeWhenMissing) {

    // Convert the mapping string back to an array
    int[] symptomOnsetToInfectiousnessArray =
        getDiagnosisKeyDataMappingStringBase4Rep(symptomOnsetToInfectiousnessString);

    // Entry 0 - 28 of the mapping array belong to the day mapping:
    Map<Integer, Integer> daysSinceOnsetToInfectiousness = new HashMap<>();
    for (int day = -14; day <= 14; day++) {
      daysSinceOnsetToInfectiousness.put(day, symptomOnsetToInfectiousnessArray[day + 14]);
    }
    return new DiagnosisKeysDataMappingBuilder()
        .setDaysSinceOnsetToInfectiousness(daysSinceOnsetToInfectiousness)
        // Entry 29 denotes the infectiousness for missing onset dates (30 and 31 are padding)
        .setInfectiousnessWhenDaysSinceOnsetMissing(symptomOnsetToInfectiousnessArray[29])
        // When the report type is missing, we remap to the HA provided default
        .setReportTypeWhenMissing(reportTypeWhenMissing)
        .build();
  }

  /**
   * The symptomOnsetToInfectiousnessMap string is read lowest significant byte first, with 2 bits
   * each representing one entry. Three possible values per entry (in binary): 00 drop, 01 standard
   * infectiousness, 10 high infectiousness LSB first [-14,-13 …. 13, 14][None]
   * <p>
   * To be able to extract its values, we convert it back to an integer/byte array where each entry
   * corresponds to two bits ("base4")
   */
  private static int[] getDiagnosisKeyDataMappingStringBase4Rep(String mappingString) {
    byte[] mappingBase16 = BaseEncoding.base16().decode(mappingString.substring(2).toUpperCase());
    int[] mappingBase4 = new int[mappingBase16.length * 4];
    for (int offset16 = mappingBase16.length - 1; offset16 >= 0; offset16--) {
      byte b = mappingBase16[offset16];
      for (byte offset4 = 3; offset4 >= 0; offset4--) {
        mappingBase4[(mappingBase16.length - 1 - offset16) * 4 + (3 - offset4)] = (byte) (b & 0x03);
        b >>= 2;
      }
    }
    return mappingBase4;
  }

}
