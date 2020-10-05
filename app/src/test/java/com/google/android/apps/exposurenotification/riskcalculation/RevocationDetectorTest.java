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

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.apps.exposurenotification.nearby.DailySummaryWrapper;
import com.google.android.apps.exposurenotification.nearby.DailySummaryWrapper.ExposureSummaryDataWrapper;
import com.google.android.apps.exposurenotification.storage.ExposureEntity;
import com.google.android.gms.nearby.exposurenotification.DailySummariesConfig;
import com.google.android.gms.nearby.exposurenotification.DailySummariesConfig.DailySummariesConfigBuilder;
import com.google.android.gms.nearby.exposurenotification.ReportType;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;
import org.threeten.bp.LocalDate;
import org.threeten.bp.ZoneOffset;

/**
 * Tests edge cases for {@link RevocationDetector}.
 * The test data used here is based on values returned from Nearby API on 2020-08-28.
 */
@RunWith(AndroidJUnit4.class)
@HiltAndroidTest
@Config(application = HiltTestApplication.class)
public class RevocationDetectorTest {

  private static final int DAYS_SINCE_EXPOSURE_THRESHOLD = 14;

  private static final LocalDate DATE_DAY_OF_CALL = LocalDate.now(ZoneOffset.UTC);
  private static final DailySummariesConfig DAILY_SUMMARIES_CONFIG =
      new DailySummariesConfigBuilder()
          .setDaysSinceExposureThreshold(DAYS_SINCE_EXPOSURE_THRESHOLD)
          .setAttenuationBuckets(Arrays.asList(1, 2, 3), Arrays.asList(0.0, 0.0, 0.0, 0.0))
          .build();

  private RevocationDetector revocationDetector;

  @Before
  public void setUp() {
    this.revocationDetector = new RevocationDetector(DAILY_SUMMARIES_CONFIG);
  }

  /**
   * Simple test for dailySummaryToExposureEntity, asserting that date and score get carried over
   * to the ExposureEntity list.
   */
  @Test
  public void dailySummaryToExposureEntity_returns_daysSinceEpoch_scoreSum() {
    double sampleScoreA = 10.0;
    double sampleScoreB = 42.0;
    ExposureSummaryDataWrapper exposureSummaryDataA =
        createExposureSummarySampleScore(sampleScoreA);
    ExposureSummaryDataWrapper exposureSummaryDataB =
        createExposureSummarySampleScore(sampleScoreB);
    List<DailySummaryWrapper> input =
        ImmutableList.of(
            DailySummaryWrapper.newBuilder()
                .setDaysSinceEpoch((int) DATE_DAY_OF_CALL.toEpochDay())
                .setReportSummary(ReportType.CONFIRMED_TEST, exposureSummaryDataA)
                .setSummaryData(exposureSummaryDataA)
                .build(),
            DailySummaryWrapper.newBuilder()
                .setDaysSinceEpoch(
                    (int) DATE_DAY_OF_CALL.minusDays(DAYS_SINCE_EXPOSURE_THRESHOLD).toEpochDay())
                .setReportSummary(ReportType.CONFIRMED_TEST, exposureSummaryDataB)
                .setSummaryData(exposureSummaryDataB)
                .build());

    List<ExposureEntity> result = revocationDetector.dailySummaryToExposureEntity(input);

    assertThat(result).containsExactly(
        ExposureEntity.create(DATE_DAY_OF_CALL.toEpochDay(), sampleScoreA),
        ExposureEntity.create(
            DATE_DAY_OF_CALL.minusDays(DAYS_SINCE_EXPOSURE_THRESHOLD).toEpochDay(), sampleScoreB));
  }

  private ExposureSummaryDataWrapper createExposureSummarySampleScore(double sampleScore) {
    return ExposureSummaryDataWrapper.newBuilder()
        .setMaximumScore(sampleScore)
        .setScoreSum(sampleScore)
        .setWeightedDurationSum(sampleScore)
        .build();
  }

  /**
   * Base case: No change in exposure score: no revocation
   */
  @Test
  public void isRevocation_exposureSummaryNotRevoked_returnsFalse() {
    List<ExposureEntity> previousExposureEntityList = Lists.newArrayList(
        ExposureEntity.create(DATE_DAY_OF_CALL.toEpochDay(), 100.0)
    );
    List<ExposureEntity> currentExposureEntityList = Lists.newArrayList(
        ExposureEntity.create(DATE_DAY_OF_CALL.toEpochDay(), 100.0)
    );

    boolean result =
        revocationDetector.isRevocation(previousExposureEntityList, currentExposureEntityList);

    assertThat(result).isFalse();
  }

  /**
   * Base case: Exposure completely revoked, no summaries on the current day: Revocation
   */
  @Test
  public void isRevocation_exposureSummaryRevokedToNull_returnsTrue() {
    List<ExposureEntity> previousExposureEntityList = Lists.newArrayList(
        ExposureEntity.create(DATE_DAY_OF_CALL.toEpochDay(), 100.0)
    );
    List<ExposureEntity> currentExposureEntityList = Lists.newArrayList();

    boolean result =
        revocationDetector.isRevocation(previousExposureEntityList, currentExposureEntityList);

    assertThat(result).isTrue();
  }

  /**
   * Base case: Exposure partly revoked, lower-score summaries on the current day: Revocation
   */
  @Test
  public void isRevocation_exposureSummaryRevokedReducedScore_returnsTrue() {
    List<ExposureEntity> previousExposureEntityList = Lists.newArrayList(
        ExposureEntity.create(DATE_DAY_OF_CALL.toEpochDay(), 100.0)
    );
    List<ExposureEntity> currentExposureEntityList = Lists.newArrayList(
        ExposureEntity.create(DATE_DAY_OF_CALL.toEpochDay(), 80.0)
    );

    boolean result =
        revocationDetector.isRevocation(previousExposureEntityList, currentExposureEntityList);

    assertThat(result).isTrue();
  }

  /**
   * The case where one window (day 15 or DAYS_SINCE_EXPOSURE_THRESHOLD + 1) fades out.
   * => A quite common case that must not be detected as a revocation (would result in a wrong
   *    notification to the user)
   */
  @Test
  public void isRevocation_exposureSummaryFadesOut_returnsFalse() {
    List<ExposureEntity> previousExposureEntityList = Lists.newArrayList(
        ExposureEntity.create(DATE_DAY_OF_CALL.minusDays(13).toEpochDay(), 100.0),
        ExposureEntity.create(DATE_DAY_OF_CALL.minusDays(14).toEpochDay(), 100.0),
        ExposureEntity.create(DATE_DAY_OF_CALL.minusDays(15).toEpochDay(), 100.0)
    );
    List<ExposureEntity> currentExposureEntityList = Lists.newArrayList(
        ExposureEntity.create(DATE_DAY_OF_CALL.minusDays(0).toEpochDay(), 100.0),
        ExposureEntity.create(DATE_DAY_OF_CALL.minusDays(13).toEpochDay(), 100.0),
        ExposureEntity.create(DATE_DAY_OF_CALL.minusDays(14).toEpochDay(), 100.0)
    );

    boolean result =
        revocationDetector.isRevocation(previousExposureEntityList, currentExposureEntityList);

    assertThat(result).isFalse();
  }

  /**
   * The case where the windows one day before the one fading out are completely revoked (missing).
   * => Valid revocation
   */
  @Test
  public void isRevocation_latestPossibleExposureSummaryRevokedToNull_returnsTrue() {
    List<ExposureEntity> previousExposureEntityList = Lists.newArrayList(
        ExposureEntity.create(DATE_DAY_OF_CALL.minusDays(13).toEpochDay(), 100.0),
        ExposureEntity.create(DATE_DAY_OF_CALL.minusDays(14).toEpochDay(), 100.0),
        ExposureEntity.create(DATE_DAY_OF_CALL.minusDays(15).toEpochDay(), 100.0)
    );
    List<ExposureEntity> currentExposureEntityList = Lists.newArrayList(
        ExposureEntity.create(DATE_DAY_OF_CALL.minusDays(0).toEpochDay(), 100.0),
        ExposureEntity.create(DATE_DAY_OF_CALL.minusDays(13).toEpochDay(), 100.0)
    );

    boolean result =
        revocationDetector.isRevocation(previousExposureEntityList, currentExposureEntityList);

    assertThat(result).isTrue();
  }

  /**
   * The case where the windows one day before the one fading out are revoked, but others are
   * still there, so only their score is reduced (missing).
   * => Valid revocation
   */
  @Test
  public void isRevocation_latestPossibleExposureSummaryRevokedReducedScore_returnsTrue() {
    List<ExposureEntity> previousExposureEntityList = Lists.newArrayList(
        ExposureEntity.create(DATE_DAY_OF_CALL.minusDays(13).toEpochDay(), 100.0),
        ExposureEntity.create(DATE_DAY_OF_CALL.minusDays(14).toEpochDay(), 100.0),
        ExposureEntity.create(DATE_DAY_OF_CALL.minusDays(15).toEpochDay(), 100.0)
    );
    List<ExposureEntity> currentExposureEntityList = Lists.newArrayList(
        ExposureEntity.create(DATE_DAY_OF_CALL.minusDays(0).toEpochDay(), 100.0),
        ExposureEntity.create(DATE_DAY_OF_CALL.minusDays(13).toEpochDay(), 100.0),
        ExposureEntity.create(DATE_DAY_OF_CALL.minusDays(14).toEpochDay(), 80.0)
    );

    boolean result =
        revocationDetector.isRevocation(previousExposureEntityList, currentExposureEntityList);

    assertThat(result).isTrue();
  }

}
