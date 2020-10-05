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

package com.google.android.apps.exposurenotification.storage;


import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import com.google.auto.value.AutoValue;
import com.google.auto.value.AutoValue.CopyAnnotations;
import org.threeten.bp.LocalDate;

/**
 * A diagnosis inputted by the user.
 *
 * <p>Partners should implement a daily TTL/expiry, for on-device storage of this data, and must
 * ensure compliance with all applicable laws and requirements with respect to encryption, storage,
 * and retention polices for end user data.
 *
 * <p>WARNING! The enums in this class MUST NOT EVER be changed / renamed, as this will result in
 * run time crashes when the app will attempt to read already stored {@link DiagnosisEntity}
 * values.
 */
@AutoValue
@Entity
public abstract class DiagnosisEntity {

  public enum TestResult {
    CONFIRMED, LIKELY, NEGATIVE;

    public static TestResult of(String apiTestType) {
      switch (apiTestType.toLowerCase()) {
        case "confirmed":
          return CONFIRMED;
        case "likely":
          return LIKELY;
        case "negative":
          return NEGATIVE;
      }
      throw new IllegalArgumentException("Unsupported test type " + apiTestType);
    }

    public String toApiType() {
      return name().toLowerCase();
    }
  }

  public enum Shared {
    NOT_ATTEMPTED, SHARED, NOT_SHARED
  }

  public enum TravelStatus {
    NOT_ATTEMPTED, TRAVELED, NOT_TRAVELED, NO_ANSWER
  }

  public enum HasSymptoms {
    UNSET, YES, NO, WITHHELD
  }

  @CopyAnnotations
  @PrimaryKey(autoGenerate = true)
  public abstract long getId();

  public abstract long getCreatedTimestampMs();

  @Nullable
  public abstract Shared getSharedStatus();

  @Nullable
  public abstract String getVerificationCode();

  @Nullable
  public abstract String getLongTermToken();

  @Nullable
  public abstract String getCertificate();

  @Nullable
  public abstract TestResult getTestResult();

  @Nullable
  public abstract LocalDate getOnsetDate();

  public abstract boolean getIsServerOnsetDate();

  public abstract HasSymptoms getHasSymptoms();

  @Nullable
  public abstract String getRevisionToken();

  @Nullable
  public abstract TravelStatus getTravelStatus();

  public abstract boolean getIsCodeFromLink();

  public abstract Builder toBuilder();

  public static Builder newBuilder() {
    return new AutoValue_DiagnosisEntity.Builder()
        // AutoValue complains if fields not marked @Nullable are not set, but primitives cannot be
        // @Nullable, so we set empty here.
        .setId(0L)
        .setCreatedTimestampMs(0L)
        .setIsServerOnsetDate(false)
        .setHasSymptoms(HasSymptoms.UNSET)
        .setTravelStatus(TravelStatus.NOT_ATTEMPTED)
        .setIsCodeFromLink(false);
  }

  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setId(long id);

    public abstract Builder setCreatedTimestampMs(long ts);

    public abstract Builder setSharedStatus(Shared shared);

    public abstract Builder setVerificationCode(String code);

    public abstract Builder setLongTermToken(String token);

    public abstract Builder setCertificate(String cert);

    public abstract Builder setTestResult(TestResult result);

    public abstract Builder setIsServerOnsetDate(boolean isServerOnsetDate);

    public abstract Builder setOnsetDate(LocalDate onsetDate);

    public abstract Builder setHasSymptoms(HasSymptoms selection);

    public abstract Builder setRevisionToken(String token);

    public abstract Builder setTravelStatus(TravelStatus travelStatus);

    public abstract Builder setIsCodeFromLink(boolean isCodeFromLink);

    public abstract DiagnosisEntity build();
  }

  /**
   * Creates a {@link DiagnosisEntity}. This is a factory required by Room. Normally the builder
   * should be used instead.
   */
  public static DiagnosisEntity create(
      long id,
      long createdTimestampMs,
      DiagnosisEntity.Shared sharedStatus,
      String verificationCode,
      String longTermToken,
      String certificate,
      DiagnosisEntity.TestResult testResult,
      boolean isServerOnsetDate,
      LocalDate onsetDate,
      HasSymptoms hasSymptoms,
      String revisionToken,
      TravelStatus travelStatus,
      boolean isCodeFromLink) {
    return newBuilder()
        .setId(id)
        .setCreatedTimestampMs(createdTimestampMs)
        .setSharedStatus(sharedStatus)
        .setVerificationCode(verificationCode)
        .setLongTermToken(longTermToken)
        .setCertificate(certificate)
        .setTestResult(testResult)
        .setIsServerOnsetDate(isServerOnsetDate)
        .setOnsetDate(onsetDate)
        .setHasSymptoms(hasSymptoms)
        .setRevisionToken(revisionToken)
        .setTravelStatus(travelStatus)
        .setIsCodeFromLink(isCodeFromLink)
        .build();
  }
}
