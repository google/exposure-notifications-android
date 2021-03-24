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
package com.google.android.apps.exposurenotification.privateanalytics.metrics;

import static com.google.android.apps.exposurenotification.privateanalytics.metrics.HistogramMetric.NUM_ATTENUATION_BINS;
import static com.google.android.apps.exposurenotification.privateanalytics.metrics.HistogramMetric.NUM_DURATION_BINS;
import static com.google.android.apps.exposurenotification.privateanalytics.metrics.HistogramMetric.NUM_EXPOSURE_DAY_BINS;
import static com.google.android.apps.exposurenotification.privateanalytics.metrics.HistogramMetric.NUM_INFECTIOUSNESS_BINS;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.android.apps.exposurenotification.nearby.ExposureNotificationClientWrapper;
import com.google.android.apps.exposurenotification.testsupport.ExposureNotificationRules;
import com.google.android.apps.exposurenotification.testsupport.FakeClock;
import com.google.android.apps.exposurenotification.testsupport.HAConfigObjects;
import com.google.android.gms.nearby.exposurenotification.ExposureWindow;
import com.google.android.gms.nearby.exposurenotification.ReportType;
import com.google.android.gms.nearby.exposurenotification.ScanInstance;
import com.google.android.gms.tasks.Tasks;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.testing.TestingExecutors;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.threeten.bp.Duration;

/**
 * Tests of {@link HistogramMetric}.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner.class)
@Config(application = HiltTestApplication.class)
public class HistogramMetricTest {

  @Rule
  public ExposureNotificationRules rules =
      ExposureNotificationRules.forTest(this).withMocks().build();

  @Mock
  ExposureNotificationClientWrapper exposureNotificationClientWrapper;
  private final Context context = ApplicationProvider.getApplicationContext();
  private final ScheduledExecutorService sameThreadScheduledExecutorService =
      TestingExecutors.sameThreadScheduledExecutor();
  private final Clock clock = new FakeClock();

  private HistogramMetric histogramMetric;

  @Before
  public void setup() {
    this.histogramMetric =
        new HistogramMetric(
            sameThreadScheduledExecutorService,
            exposureNotificationClientWrapper,
            HAConfigObjects.DAILY_SUMMARIES_CONFIG,
            clock);
  }

  @Test
  public void getDataVector_outOfRangeExposures_ignored() throws Exception {
    // GIVEN
    List<ExposureWindow> windows = new ArrayList<>();
    ExposureWindow window = new ExposureWindow.Builder().setDateMillisSinceEpoch(1000L).build();
    windows.add(window);

    // WHEN
    when(exposureNotificationClientWrapper.getExposureWindows())
        .thenReturn(Tasks.forResult(windows));
    ListenableFuture<List<Integer>> val = histogramMetric.getDataVector();

    // THEN
    int[] emptyDurationVector = getEmptyDurationVector();
    assertThat(val.get()).containsExactlyElementsIn(Ints.asList(emptyDurationVector));
  }

  @Test
  public void getDataVector_inRangeExposures_included() throws Exception {
    // GIVEN
    List<ExposureWindow> windows = new ArrayList<>();
    ScanInstance scanInstance =
        new ScanInstance.Builder()
            .setMinAttenuationDb(60)
            .setSecondsSinceLastScan(400)
            .setTypicalAttenuationDb(70)
            .build();
    ExposureWindow window =
        new ExposureWindow.Builder()
            .setDateMillisSinceEpoch(clock.now().toEpochMilli())
            .setInfectiousness(1)
            .setReportType(ReportType.CONFIRMED_TEST)
            .setScanInstances(ImmutableList.of(scanInstance))
            .build();
    windows.add(window);

    // WHEN
    when(exposureNotificationClientWrapper.getExposureWindows())
        .thenReturn(Tasks.forResult(windows));
    ListenableFuture<List<Integer>> val = histogramMetric.getDataVector();
    /*
      Attenuation is set to 70 -> falls under the 5th attenuation bin

      Infectiousness is set to 1 -> falls under the 2nd Infectiousness bin

      Duration is set to 400 -> falls under the 2nd duration bin

      Clock is now -> falls under 1st day bin

      <p>(bin's are zero indexed so minus 1) We'd expect a given vector with (dayBin=0,
      infectiousness=1, attenuation=3, duration=1) set to true and duration=0 for all other combos
      of infectiousness, attenuation and dayBin set to true
     */

    // THEN
    // Start with an empty duration vector
    int[] emptyDurationVector = getEmptyDurationVector();
    // Fill in the 2nd duration bin for this tuple
    int[] expectedVector = fillVectorWithDurationBin(emptyDurationVector, 1, 4, 1, 0, 1);
    assertThat(val.get()).containsExactlyElementsIn(Ints.asList(expectedVector)).inOrder();
  }

  @Test
  public void getDataVector_zeroWeightReportTypeWindows_ignored() throws Exception {
    // GIVEN
    List<ExposureWindow> windows = new ArrayList<>();
    ScanInstance scanInstance =
        new ScanInstance.Builder()
            .setMinAttenuationDb(60)
            .setSecondsSinceLastScan(400)
            .setTypicalAttenuationDb(70)
            .build();
    ExposureWindow window =
        new ExposureWindow.Builder()
            .setDateMillisSinceEpoch(clock.now().toEpochMilli())
            .setInfectiousness(1)
            .setReportType(ReportType.RECURSIVE)
            .setScanInstances(ImmutableList.of(scanInstance))
            .build();
    windows.add(window);

    // WHEN
    when(exposureNotificationClientWrapper.getExposureWindows())
        .thenReturn(Tasks.forResult(windows));
    ListenableFuture<List<Integer>> val = histogramMetric.getDataVector();

    // THEN
    int[] emptyDurationVector = getEmptyDurationVector();
    assertThat(val.get()).containsExactlyElementsIn(Ints.asList(emptyDurationVector)).inOrder();
  }

  // Ignore expositions of less than one minute (v2 of the metric)
  @Test
  public void getDataVector_fiftyNineSecondsExposure_ignored() throws Exception {
    // GIVEN
    List<ExposureWindow> windows = new ArrayList<>();
    ScanInstance scanInstance =
        new ScanInstance.Builder()
            .setMinAttenuationDb(60)
            .setSecondsSinceLastScan(59)
            .setTypicalAttenuationDb(70)
            .build();
    ExposureWindow window =
        new ExposureWindow.Builder()
            .setDateMillisSinceEpoch(clock.now().toEpochMilli())
            .setInfectiousness(1)
            .setReportType(ReportType.RECURSIVE)
            .setScanInstances(ImmutableList.of(scanInstance))
            .build();
    windows.add(window);

    // WHEN
    when(exposureNotificationClientWrapper.getExposureWindows())
        .thenReturn(Tasks.forResult(windows));
    ListenableFuture<List<Integer>> val = histogramMetric.getDataVector();

    // THEN
    int[] emptyDurationVector = getEmptyDurationVector();
    assertThat(val.get()).containsExactlyElementsIn(Ints.asList(emptyDurationVector)).inOrder();
  }

  @Test
  public void getDataVector_multipleDurationsOneDay() throws Exception {
    // GIVEN
    List<ExposureWindow> windows = new ArrayList<>();
    ScanInstance scanInstance =
        new ScanInstance.Builder()
            .setMinAttenuationDb(60)
            .setSecondsSinceLastScan(400)
            .setTypicalAttenuationDb(65)
            .build();
    ScanInstance scanInstance2 =
        new ScanInstance.Builder()
            .setMinAttenuationDb(60)
            .setSecondsSinceLastScan(602)
            .setTypicalAttenuationDb(65)
            .build();
    ExposureWindow window =
        new ExposureWindow.Builder()
            .setDateMillisSinceEpoch(clock.now().toEpochMilli())
            .setInfectiousness(1)
            .setReportType(ReportType.CONFIRMED_TEST)
            .setScanInstances(ImmutableList.of(scanInstance, scanInstance2))
            .build();
    windows.add(window);

    // WHEN
    when(exposureNotificationClientWrapper.getExposureWindows())
        .thenReturn(Tasks.forResult(windows));
    ListenableFuture<List<Integer>> val = histogramMetric.getDataVector();
    /*
      Attenuation is set to 65 -> falls under the 4th attenuation bin

      Infectiousness is set to 1 -> falls under the 2nd Infectiousness bin

      Duration is set to 400 + 602 = 1002 -> falls under 4th duration bin

      Clock is now -> falls under 1st day bin

      <p>(bin's are zero indexed so minus 1) We'd expect a given vector with (dayBin=0,
      infectiousness=1, attenuation=3, duration=3) set to true and duration=0 for all other combos
      of infectiousness, attenuation and dayBin set to true
     */
    // THEN
    // Start with an empty duration vector
    int[] emptyDurationVector = getEmptyDurationVector();
    // Fill in the 5th duration bin for this tuple
    int[] expectedVector = fillVectorWithDurationBin(emptyDurationVector, 1, 3, 3, 0, 1);
    assertThat(val.get()).containsExactlyElementsIn(Ints.asList(expectedVector)).inOrder();
  }

  @Test
  public void getDataVector_multipleDayBins() throws Exception {
    // GIVEN
    List<ExposureWindow> windows = new ArrayList<>();
    ScanInstance scanInstance =
        new ScanInstance.Builder()
            .setMinAttenuationDb(60)
            .setSecondsSinceLastScan(400)
            .setTypicalAttenuationDb(70)
            .build();
    ExposureWindow window =
        new ExposureWindow.Builder()
            .setDateMillisSinceEpoch(clock.now().toEpochMilli())
            .setInfectiousness(1)
            .setReportType(ReportType.CONFIRMED_TEST)
            .setScanInstances(ImmutableList.of(scanInstance))
            .build();
    ScanInstance scanInstance2 =
        new ScanInstance.Builder()
            .setMinAttenuationDb(60)
            .setSecondsSinceLastScan(400)
            .setTypicalAttenuationDb(70)
            .build();
    ExposureWindow window2 =
        new ExposureWindow.Builder()
            // subtract 1 sec to be clear about edge
            .setDateMillisSinceEpoch(
                clock.now().minus(Duration.ofDays(4).minusSeconds(1)).toEpochMilli())
            .setInfectiousness(1)
            .setReportType(ReportType.CONFIRMED_TEST)
            .setScanInstances(ImmutableList.of(scanInstance2))
            .build();
    windows.add(window);
    windows.add(window2);

    // WHEN
    when(exposureNotificationClientWrapper.getExposureWindows())
        .thenReturn(Tasks.forResult(windows));
    ListenableFuture<List<Integer>> val = histogramMetric.getDataVector();
    /*
      Attenuation is set to 70 -> falls under the 5th attenuation bin

      Infectiousness is set to 1 -> falls under the 2nd Infectiousness bin

      Duration is set to 400 -> falls under the 2nd duration bin

      Clock is now -> falls under 1st day bin,

      Clock is almost 2 days ago -> falls under 2nd day bin

      <p>(bin's are zero indexed so minus 1) We'd expect a given vector with (dayBin=0,
      infectiousness=1, attenuation=3, duration=1) and (dayBin=1, infectiousness=1, attenuation=3,
      duration=1) set to true and duration=0 for all other combos of infectiousness and attenuation
      and dayBin set to true
     */
    // THEN

    // Start with an empty duration vector
    int[] emptyDurationVector = getEmptyDurationVector();
    // Fill in the 2nd duration bin for these tuples
    int[] expectedVector = fillVectorWithDurationBin(emptyDurationVector, 1, 4, 1, 0, 1);
    expectedVector = fillVectorWithDurationBin(expectedVector, 1, 4, 1, 1, 1);
    assertThat(val.get()).containsExactlyElementsIn(Ints.asList(expectedVector)).inOrder();
  }

  @Test
  public void testHammingWeight() {
    // v2 of the metric does not have a constant Hamming weight. We should read 0.
    assertThat(histogramMetric.getMetricHammingWeight()).isEqualTo(0);
  }

  /**
   * Create a vector where each possible pairing of {dayBin, infectiousness, attenuation} is set to
   * have 0 recorded durations.
   */
  private static int[] getEmptyDurationVector() {
    int[] uploadVector =
        new int
            [NUM_INFECTIOUSNESS_BINS
            * NUM_ATTENUATION_BINS
            * NUM_DURATION_BINS
            * NUM_EXPOSURE_DAY_BINS];
    for (int i = 0; i < NUM_INFECTIOUSNESS_BINS; i++) {
      for (int j = 0; j < NUM_ATTENUATION_BINS; j++) {
        for (int k = 0; k < NUM_EXPOSURE_DAY_BINS; k++) {
          uploadVector = fillVectorWithDurationBin(uploadVector, i, j, 0, k, 0);
        }
      }
    }
    return uploadVector;
  }

  /**
   * Fill the given vector w/ (dayBin, infectiousnessBin, attenuationBin, durationBin) set to the
   * given bit (1=true, 0=false)
   */
  private static int[] fillVectorWithDurationBin(
      int[] vector,
      int infectiousnessBin,
      int attenuationBin,
      int durationBin,
      int dayBin,
      int bit) {
    vector[
        dayBin * NUM_INFECTIOUSNESS_BINS * NUM_ATTENUATION_BINS * NUM_DURATION_BINS
            + infectiousnessBin * NUM_ATTENUATION_BINS * NUM_DURATION_BINS
            + attenuationBin * NUM_DURATION_BINS
            + durationBin] =
        bit;
    return vector;
  }
}
