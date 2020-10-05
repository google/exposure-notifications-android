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

import static com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences.BadgeStatus.DISMISSED;
import static com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences.BadgeStatus.NEW;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.work.ListenableWorker.Result;
import androidx.work.WorkerParameters;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.NotificationHelper;
import com.google.android.apps.exposurenotification.logging.AnalyticsLogger;
import com.google.android.apps.exposurenotification.nearby.DailySummaryWrapper;
import com.google.android.apps.exposurenotification.nearby.DailySummaryWrapper.ExposureSummaryDataWrapper;
import com.google.android.apps.exposurenotification.nearby.ExposureNotificationClientWrapper;
import com.google.android.apps.exposurenotification.nearby.StateUpdatedWorker;
import com.google.android.apps.exposurenotification.storage.DbModule;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationDatabase;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences.BadgeStatus;
import com.google.android.apps.exposurenotification.storage.ExposureRepository;
import com.google.android.apps.exposurenotification.testsupport.ExposureNotificationRules;
import com.google.android.apps.exposurenotification.testsupport.HAConfigObjects;
import com.google.android.apps.exposurenotification.testsupport.InMemoryDb;
import com.google.android.gms.nearby.exposurenotification.DailySummariesConfig;
import com.google.android.gms.nearby.exposurenotification.ReportType;
import com.google.android.gms.tasks.Tasks;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.testing.TestingExecutors;
import dagger.hilt.android.testing.BindValue;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import dagger.hilt.android.testing.UninstallModules;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowNotificationManager;
import org.threeten.bp.LocalDate;
import org.threeten.bp.ZoneOffset;

/**
 * This is more of a integration test than a unit test, it tests
 * risk-calculation/revocation/notification as a whole.
 *
 * Unit tests covering the components in isolation can be found in the other classes in the
 * riskcalculation package.
 *
 * Essentially this test only really mocks the behavior of exposureNotificationClientWrapper to
 * simulate different exposure over a longer period of time.
 *
 * It then calls the StateUpdateWorker and observes if the right Notifications and/or
 * SharedPreferenceChanges happen as expected.
 */
@RunWith(AndroidJUnit4.class)
@HiltAndroidTest
@Config(application = HiltTestApplication.class)
@UninstallModules({DbModule.class})
public class ExposureNotificationsIntegrationTest {

  private static final LocalDate TODAY = LocalDate.now(ZoneOffset.UTC);

  @Rule
  public ExposureNotificationRules rules = ExposureNotificationRules.forTest(this)
      .withMocks()
      .build();

  @BindValue
  ExposureNotificationDatabase db = InMemoryDb.create();

  @Inject
  ExposureRepository exposureRepository;
  @Inject
  ExposureNotificationSharedPreferences exposureNotificationSharedPreferences;
  @Inject
  NotificationHelper notificationHelper;

  @Mock
  AnalyticsLogger analyticsLogger;
  @Mock
  WorkerParameters workerParameters;
  @Mock
  ExposureNotificationClientWrapper exposureNotificationClientWrapper;

  Context context;
  StateUpdatedWorker stateUpdatedWorker;
  ExposuresHelper exposuresHelper;
  NotificationManager notificationManager;
  ShadowNotificationManager shadowNotificationManager;

  @Before
  public void setUp() {
    rules.hilt().inject();
    context = ApplicationProvider.getApplicationContext();

    // Instantiate the real notification helper and observe the system's notifications
    notificationManager = (NotificationManager) context
        .getSystemService(Context.NOTIFICATION_SERVICE);
    shadowNotificationManager = shadowOf(notificationManager);

    // Retrieve dependencies that usually use the HA-provided config but use a test config instead
    DailySummaryRiskCalculator dailySummaryRiskCalculator =
        new DailySummaryRiskCalculator(HAConfigObjects.CLASSIFICATION_THRESHOLDS_ARRAY);
    DailySummariesConfig dailySummariesConfig = HAConfigObjects.DAILY_SUMMARIES_CONFIG;
    RevocationDetector revocationDetector = new RevocationDetector(dailySummariesConfig);

    // Use testing versions for all the threading dependencies
    ExecutorService backgroundExecutor = MoreExecutors.newDirectExecutorService();
    ExecutorService lightweightExecutor = MoreExecutors.newDirectExecutorService();
    ScheduledExecutorService scheduledExecutor =  TestingExecutors.sameThreadScheduledExecutor();

    /*
     * Instantiate an ExposureHelper object that will simulate different exposures for us.
     * It transfers it's current exposure state to exposureNotificationClientWrapper every time we
     * call .commit()
     */
    exposuresHelper = new ExposuresHelper(exposureNotificationClientWrapper);

    // Instantiate the actual object under test
    stateUpdatedWorker = new StateUpdatedWorker(context, workerParameters, exposureRepository,
        exposureNotificationClientWrapper, exposureNotificationSharedPreferences,
        revocationDetector, dailySummariesConfig, dailySummaryRiskCalculator, notificationHelper,
        backgroundExecutor, lightweightExecutor, scheduledExecutor, analyticsLogger);
  }


  /*
   * Minimal success/failure cases of calls to stateUpdatedWorker.startWork()
   */
  @Test
  public void stateUpdateWorker_exceptionInExposureNotificationClientWrapper_returnsFailure()
      throws Exception {
    when(exposureNotificationClientWrapper.getDailySummaries(any()))
        .thenReturn(Tasks.forException(new Exception()));

    Result result = stateUpdatedWorker.startWork().get();

    assertThat(result).isEqualTo(Result.failure());
  }

  @Test
  public void stateUpdateWorker_functioningExposureNotificationClientWrapper_returnsSuccess()
      throws Exception {
    exposuresHelper.commit();

    Result result = stateUpdatedWorker.startWork().get();

    assertThat(result).isEqualTo(Result.success());
  }

   // Following test methods: Most basic "exposure" / "no exposure" cases

  @Test
  public void basic_stateUpdateWorker_noExposure_noNotification_noExposureUI()
      throws Exception {
    exposuresHelper.commit();

    stateUpdatedWorker.startWork().get();

    assertNoNotificationTriggered();
    assertUINoExposure();
  }

  @Test
  public void basic_stateUpdateWorker_singleExposure_notification_exposureUI()
      throws Exception {
    exposuresHelper.addExposure(TODAY, ReportType.CONFIRMED_TEST, 3000.0)
    .commit();

    stateUpdatedWorker.startWork().get();

    assertNotificationTriggered(R.string.exposure_notification_title_1);
    assertUIExposureState(1, TODAY, NEW, NEW);
  }

  /*
   * Cases that were explicitly clarified during design.
   */

  /**
   Scenario A: C3 (notification) -> C1 on next day (additional notification)
   */
  @Test
  public void scenario_stateUpdateWorker_likelyThenAddConfirmed_nextDay_additionalNotification()
      throws Exception {
    // Likely exposure yesterday
    exposuresHelper
        .addExposure(TODAY.minusDays(1), ReportType.CONFIRMED_CLINICAL_DIAGNOSIS, 3000.0)
        .commit();

    stateUpdatedWorker.startWork().get();

    assertNotificationTriggered(R.string.exposure_notification_title_3);
    assertUIExposureState(3, TODAY.minusDays(1), NEW, NEW);

    //Confirmed exposure today
    dismissNewBadges();
    dismissNotifications();
    exposuresHelper
        .addExposure(TODAY, ReportType.CONFIRMED_TEST, 3000.0)
        .commit();

    stateUpdatedWorker.startWork().get();

    assertNotificationTriggered(R.string.exposure_notification_title_1);
    assertUIExposureState(1, TODAY, NEW, NEW);
  }

  /**
   * Scenario B: C1 (notification) -> C3 on next day (no additional notification)
   */
  @Test
  public void scenario_stateUpdateWorker_confirmedThenAddLikely_nextDay_noAdditionalNotification()
      throws Exception {
    // Confirmed exposure yesterday
    exposuresHelper
        .addExposure(TODAY.minusDays(1), ReportType.CONFIRMED_TEST, 3000.0)
        .commit();

    stateUpdatedWorker.startWork().get();

    assertNotificationTriggered(R.string.exposure_notification_title_1);
    assertUIExposureState(1, TODAY.minusDays(1), NEW, NEW);

    //Another likely exposure today
    dismissNewBadges();
    dismissNotifications();
    exposuresHelper
        .addExposure(TODAY, ReportType.CONFIRMED_CLINICAL_DIAGNOSIS, 3000.0)
        .commit();

    stateUpdatedWorker.startWork().get();

    assertNoNotificationTriggered();
    assertUIExposureState(1, TODAY.minusDays(1), DISMISSED, DISMISSED);
  }

  /**
   Scenario C: C1 (notification) -> new C1 on next day (additional notification)
   */
  @Test
  public void scenario_stateUpdateWorker_confirmedThenAddConfirmed_nextDay_additionalNotification()
      throws Exception {
    // Confirmed exposure yesterday
    exposuresHelper
        .addExposure(TODAY.minusDays(1), ReportType.CONFIRMED_TEST, 3000.0)
        .commit();

    stateUpdatedWorker.startWork().get();

    assertNotificationTriggered(R.string.exposure_notification_title_1);
    assertUIExposureState(1, TODAY.minusDays(1), NEW, NEW);

    //Another confirmed exposure today
    dismissNewBadges();
    dismissNotifications();
    exposuresHelper
        .addExposure(TODAY, ReportType.CONFIRMED_TEST, 3000.0)
        .commit();

    stateUpdatedWorker.startWork().get();

    assertNotificationTriggered(R.string.exposure_notification_title_1);
    assertUIExposureState(1, TODAY, DISMISSED, NEW);
  }

  // Scenario D: C3 (notification) -> any type of key revision for the same day
  /**
   * a) C3 -> revoked: revoked notification
   */
  @Test
  public void scenario_stateUpdateWorker_likelyThenRevocation_sameDay_additionalNotification()
      throws Exception {
    // Test base exposure
    exposuresHelper
        .addExposure(TODAY, ReportType.CONFIRMED_CLINICAL_DIAGNOSIS, 2700.0)
        .commit();

    stateUpdatedWorker.startWork().get();

    assertNotificationTriggered(R.string.exposure_notification_title_3);
    assertUIExposureState(3, TODAY, NEW, NEW);

    //Test follow-up revocation
    dismissNewBadges();
    dismissNotifications();
    exposuresHelper.clearAllExposures().commit();

    stateUpdatedWorker.startWork().get();

    assertNotificationTriggered(R.string.exposure_notification_title_revoked);
    assertUIRevocation();
  }

  /**
   * b) C3 replaced by C1: c1 notification
   */
  @Test
  public void scenario_stateUpdateWorker_likelyThenRevisedConfirmed_sameDay_additionalNotification()
      throws Exception {
    // Likely exposure
    exposuresHelper
        .addExposure(TODAY, ReportType.CONFIRMED_CLINICAL_DIAGNOSIS, 3000.0)
        .commit();

    stateUpdatedWorker.startWork().get();

    assertNotificationTriggered(R.string.exposure_notification_title_3);
    assertUIExposureState(3, TODAY, NEW, NEW);

    //Revised to confirmed exposure
    dismissNewBadges();
    dismissNotifications();
    exposuresHelper
        .clearAllExposures()
        .addExposure(TODAY, ReportType.CONFIRMED_TEST, 3000.0)
        .commit();

    stateUpdatedWorker.startWork().get();

    assertNotificationTriggered(R.string.exposure_notification_title_1);
    assertUIExposureState(1, TODAY, NEW, DISMISSED);
  }

  /**
   * c) C3 fades out / C3 -> no exposure: no notification
   */
  @Test
  public void scenario_stateUpdateWorker_likelyTimingOut_14Days_noAdditionalNotification()
      throws Exception {
    /*
     * Yesterday, there was an exposure 14 days ago (so its still kept by GMScore)
     * => State update worker is called and store this as "yesterdays" state
     * => It is still a valid exposure, so we notify
     */
    exposuresHelper
        .addExposure(TODAY.minusDays(15), ReportType.CONFIRMED_CLINICAL_DIAGNOSIS, 3000.0)
        .commit();

    stateUpdatedWorker.startWork().get();

    assertNotificationTriggered(R.string.exposure_notification_title_3);
    assertUIExposureState(3, TODAY.minusDays(15), NEW, NEW);

    /*
     * Today, the exposure is 15 days old (this TODAY.minusDays(15)), so GMScore removes it
     * automatically. The exposure disappears because it timed-out, so we do NOT trigger a
     * revocation notification (and just display the "no exposures" UI)
     */
    dismissNewBadges();
    dismissNotifications();
    exposuresHelper
        .clearAllExposures()
        .commit();

    stateUpdatedWorker.startWork().get();

    assertNoNotificationTriggered();
    assertUINoExposure();
  }

  // Following test methods: Additional edge cases

  @Test
  public void edge_stateUpdateWorker_likelyThenAddLikely_sameDay_noAdditionalNotification()
      throws Exception {
    // Likely exposure
    exposuresHelper
        .addExposure(TODAY, ReportType.CONFIRMED_CLINICAL_DIAGNOSIS, 2700.0)
        .commit();

    stateUpdatedWorker.startWork().get();

    assertNotificationTriggered(R.string.exposure_notification_title_3);
    assertUIExposureState(3, TODAY, NEW, NEW);

    //Follow up likely exposure
    dismissNewBadges();
    dismissNotifications();
    exposuresHelper
        .addExposure(TODAY, ReportType.CONFIRMED_CLINICAL_DIAGNOSIS, 2700.0)
        .commit();

    stateUpdatedWorker.startWork().get();

    assertNoNotificationTriggered();
    assertUIExposureState(3, TODAY, DISMISSED, DISMISSED);
  }

  @Test
  public void
      edge_stateUpdateWorker_confirmedShortAddConfirmedShort_sameDay_additionalLongNotification()
      throws Exception {
    // Confirmed exposure that stays under the "long" threshold of 2700 and thus qualifies as short
    exposuresHelper
        .addExposure(TODAY, ReportType.CONFIRMED_TEST, 1500.0)
        .commit();

    stateUpdatedWorker.startWork().get();

    assertNotificationTriggered(R.string.exposure_notification_title_2);
    assertUIExposureState(2, TODAY, NEW, NEW);

    //Follow up additional confirmed exposure that pushed the total exposure seconds to 3000
    dismissNewBadges();
    dismissNotifications();
    exposuresHelper
        .addExposure(TODAY, ReportType.CONFIRMED_TEST, 1500.0)
        .commit();

    stateUpdatedWorker.startWork().get();

    assertNotificationTriggered(R.string.exposure_notification_title_1);
    assertUIExposureState(1, TODAY, NEW, DISMISSED);
  }

  @Test
  public void edge_stateUpdateWorker_confirmedThenAddLikely_sameDay_noAdditionalNotification()
      throws Exception {
    // Confirmed exposure
    exposuresHelper
        .addExposure(TODAY, ReportType.CONFIRMED_TEST, 3000.0)
        .commit();

    stateUpdatedWorker.startWork().get();

    assertNotificationTriggered(R.string.exposure_notification_title_1);
    assertUIExposureState(1, TODAY, NEW, NEW);

    //Follow up likely exposure should be ignored since we have a more important confirmed exposure
    dismissNewBadges();
    dismissNotifications();
    exposuresHelper
        .addExposure(TODAY, ReportType.CONFIRMED_CLINICAL_DIAGNOSIS, 3000.0)
        .commit();

    stateUpdatedWorker.startWork().get();

    assertNoNotificationTriggered();
    assertUIExposureState(1, TODAY, DISMISSED, DISMISSED);
  }

  @Test
  public void
      edge_stateUpdateWorker_confirmedThenAddConfirmed_previousDay_noAdditionalNotification()
      throws Exception {
    // Confirmed exposure today
    exposuresHelper
        .addExposure(TODAY, ReportType.CONFIRMED_TEST, 3000.0)
        .commit();

    stateUpdatedWorker.startWork().get();

    assertNotificationTriggered(R.string.exposure_notification_title_1);
    assertUIExposureState(1, TODAY, NEW, NEW);

    //Another confirmed exposure yesterday
    dismissNewBadges();
    dismissNotifications();
    exposuresHelper
        .addExposure(TODAY.minusDays(1), ReportType.CONFIRMED_TEST, 3000.0)
        .commit();

    stateUpdatedWorker.startWork().get();

    assertNoNotificationTriggered();
    assertUIExposureState(1, TODAY, DISMISSED, DISMISSED);
  }

  @Test
  public void edge_stateUpdateWorker_confirmedFadingOutThenLikely_14Days_additionalNotification()
      throws Exception {
    // Confirmed exposure 14 days ago, likely exposure 13 days ago
    exposuresHelper
        .addExposure(TODAY.minusDays(14), ReportType.CONFIRMED_TEST, 3000.0)
        .addExposure(TODAY.minusDays(13), ReportType.CONFIRMED_CLINICAL_DIAGNOSIS, 3000.0)
        .commit();

    stateUpdatedWorker.startWork().get();

    assertNotificationTriggered(R.string.exposure_notification_title_1);
    assertUIExposureState(1, TODAY.minusDays(14), NEW, NEW);

    // Confirmed exposure "times out" (simulated by just removing it)
    dismissNewBadges();
    dismissNotifications();
    exposuresHelper
        .removeExposuresOnDate(TODAY.minusDays(14))
        .commit();

    stateUpdatedWorker.startWork().get();

    assertNotificationTriggered(R.string.exposure_notification_title_3);
    assertUIExposureState(3, TODAY.minusDays(13), NEW, NEW);
  }


  /**
   * Helper to dismiss the notifications
   */
  private void dismissNewBadges() {
    exposureNotificationSharedPreferences
        .setIsExposureClassificationNewAsync(BadgeStatus.DISMISSED);
    exposureNotificationSharedPreferences
        .setIsExposureClassificationDateNewAsync(BadgeStatus.DISMISSED);
  }

  private void dismissNotifications() {
    notificationManager.cancelAll();
  }

  /**
   * Helper to verify that the notification was fired
   */
  private void assertNotificationTriggered(int titleStringResource) {
    if (shadowNotificationManager.size() == 0) {
      fail("Expected: " + context.getString(titleStringResource) + ", but got no notification");
    }
    Notification notification = shadowNotificationManager.getNotification(0);
    assertThat(shadowOf(notification).getContentTitle())
        .isEqualTo(context.getString(titleStringResource));
  }

  private void assertNoNotificationTriggered() {
    if (shadowNotificationManager.size() != 0) {
      Notification notification = shadowNotificationManager.getNotification(0);
      fail("Expected no notification, but got " +  shadowOf(notification).getContentTitle());
    }
  }

  /**
   * Helper to check the exposure-related UI-state
   */
  private void assertUIExposureState(int classificationIndex, LocalDate classificationDate,
      BadgeStatus classificationBadge, BadgeStatus dateBadge) {

    assertThat(
        exposureNotificationSharedPreferences.getExposureClassification().getClassificationIndex())
        .isEqualTo(classificationIndex);
    assertThat(
        exposureNotificationSharedPreferences.getExposureClassification().getClassificationDate())
        .isEqualTo(classificationDate.toEpochDay());

    assertThat(exposureNotificationSharedPreferences.getIsExposureClassificationRevoked())
        .isEqualTo(false);

    assertThat(exposureNotificationSharedPreferences.getIsExposureClassificationNew())
        .isEqualTo(classificationBadge);
    assertThat(exposureNotificationSharedPreferences.getIsExposureClassificationDateNew())
        .isEqualTo(dateBadge);
  }

  private void assertUINoExposure() {
    assertThat(
        exposureNotificationSharedPreferences.getExposureClassification().getClassificationIndex())
        .isEqualTo(0);
    assertThat(
        exposureNotificationSharedPreferences.getExposureClassification().getClassificationDate())
        .isEqualTo(0);
    assertThat(exposureNotificationSharedPreferences.getIsExposureClassificationRevoked())
        .isFalse();
  }

  private void assertUIRevocation() {
    assertThat(
        exposureNotificationSharedPreferences.getExposureClassification().getClassificationIndex())
        .isEqualTo(0);
    assertThat(
        exposureNotificationSharedPreferences.getExposureClassification().getClassificationDate())
        .isEqualTo(0);
    assertThat(exposureNotificationSharedPreferences.getIsExposureClassificationRevoked())
        .isTrue();
  }

  /**
   * TODO verify that this realistically simulates nearby
   * This is a class that makes it easier for us to simulate different exposure scenarios.
   * It provides a list of dailySummaries that we can clear, selectively remove days or add
   * different types of exposures to.
   * It uses the Builder pattern, but additionally enables us to provide the resulting
   * dailySummaries to ExposureNotificationsWrapper.
   */
  private static class ExposuresHelper {
    ExposureNotificationClientWrapper exposureNotificationClientWrapper;
    Map<Long, DailySummaryWrapper> dailySummaryMap = new HashMap<>();

    private static final double DAILY_SUMMARY_MAXIMUM_SCORE = 2700.0;
    private static final Set<Integer> ALL_REPORT_TYPES = Sets.newHashSet(
        ReportType.CONFIRMED_TEST, ReportType.CONFIRMED_CLINICAL_DIAGNOSIS,
        ReportType.SELF_REPORT, ReportType.RECURSIVE, ReportType.REVOKED, ReportType.UNKNOWN);

    ExposuresHelper(ExposureNotificationClientWrapper exposureNotificationClientWrapper) {
      this.exposureNotificationClientWrapper = exposureNotificationClientWrapper;
    }

    public ExposuresHelper addExposure(LocalDate date, int reportType,
        double weightedExposureSeconds) {

      DailySummaryWrapper previousSummary = dailySummaryMap.get(date.toEpochDay());

      // Add an Exposure on a day that has not seen Exposures before
      if (previousSummary == null) {
        ExposureSummaryDataWrapper exposureSummaryData = ExposureSummaryDataWrapper.newBuilder()
            .setMaximumScore(Math.min(DAILY_SUMMARY_MAXIMUM_SCORE, weightedExposureSeconds))
            .setScoreSum(weightedExposureSeconds)
            .setWeightedDurationSum(weightedExposureSeconds)
            .build();

        // As this is this day's first exposure, ReportSummary and SummaryData are the same
        DailySummaryWrapper dailySummary = DailySummaryWrapper.newBuilder()
            .setDaysSinceEpoch((int)date.toEpochDay())
            .setReportSummary(reportType, exposureSummaryData)
            .setSummaryData(exposureSummaryData)
            .build();

        dailySummaryMap.put(date.toEpochDay(), dailySummary);
      }

      // Merge the new Exposure to the information already available on this day
     else {
        // Refresh the reportType-specific sum first:
        ExposureSummaryDataWrapper prev = previousSummary.getSummaryDataForReportType(reportType);
        ExposureSummaryDataWrapper refreshedReportTypeSummary =
            ExposureSummaryDataWrapper.newBuilder()
            .setMaximumScore(
                Math.min(DAILY_SUMMARY_MAXIMUM_SCORE,
                    Math.max(prev.getMaximumScore(), weightedExposureSeconds)))
            .setScoreSum(prev.getScoreSum() + weightedExposureSeconds)
            .setWeightedDurationSum(prev.getMaximumScore() + weightedExposureSeconds)
            .build();

        //Create a new DailySummaryObject with the new report info
        DailySummaryWrapper.Builder dailySummaryBuilder = DailySummaryWrapper.newBuilder()
            .setDaysSinceEpoch((int)date.toEpochDay())
            .setReportSummary(reportType, refreshedReportTypeSummary)
            /*
             * This will be overwritten by a correct overall summary by refreshOverallSummaryOnDate
             */
            .setSummaryData(refreshedReportTypeSummary);
        for (int r : ALL_REPORT_TYPES) {
          // Skip the report type that was modified
          if (r != reportType) {
            dailySummaryBuilder.setReportSummary(r, previousSummary.getSummaryDataForReportType(r));
          }
        }


        dailySummaryMap.put(date.toEpochDay(), dailySummaryBuilder.build());

        // Recreate the summery with the summary information
        refreshOverallSummaryOnDate(date);
      }

      return this;
    }

    public ExposuresHelper removeExposuresOnDate(LocalDate date) {
      dailySummaryMap.remove(date.toEpochDay());
      refreshOverallSummaryOnDate(date);
      return this;
    }

    /**
     * DailySummary.getSummaryData() is computed from the other ReportType data. Do that here.
     * This recreates the DailySummary object.
     */
    private void refreshOverallSummaryOnDate(LocalDate date) {
      DailySummaryWrapper unrefreshedDailySummary = dailySummaryMap.get(date.toEpochDay());

      if (unrefreshedDailySummary == null) {
        return;
      }

      // Adding the refreshed summary (and removing it from the ReportTypes still to be summarized)
      double maximumScore = 0.0;
      double scoreSum = 0.0;
      double weightedDurationSum = 0.0;

      // Summarize all the report types
      for (int r : ALL_REPORT_TYPES) {
        ExposureSummaryDataWrapper prevReportExposureSummaryData =
            unrefreshedDailySummary.getSummaryDataForReportType(r);
        maximumScore = Math.max(maximumScore, prevReportExposureSummaryData.getMaximumScore());
        scoreSum += prevReportExposureSummaryData.getScoreSum();
        weightedDurationSum += prevReportExposureSummaryData.getWeightedDurationSum();
      }

      // Create overall ExposureSummary
      ExposureSummaryDataWrapper refreshedOverallSummary = ExposureSummaryDataWrapper.newBuilder()
          .setMaximumScore(maximumScore)
          .setScoreSum(scoreSum)
          .setWeightedDurationSum(weightedDurationSum)
          .build();

      //Finally create the new DailySummaryObject
      DailySummaryWrapper.Builder dailySummaryBuilder = DailySummaryWrapper.newBuilder()
          .setDaysSinceEpoch((int)date.toEpochDay())
          .setSummaryData(refreshedOverallSummary);
      for (int r : ALL_REPORT_TYPES) {
        dailySummaryBuilder
            .setReportSummary(r, unrefreshedDailySummary.getSummaryDataForReportType(r));
      }

      //Write new DailySummary object back
      dailySummaryMap.put(date.toEpochDay(), dailySummaryBuilder.build());
    }

    public ExposuresHelper clearAllExposures() {
      dailySummaryMap.clear();
      return this;
    }

    public List<DailySummaryWrapper> get() {
      return new ArrayList<>(dailySummaryMap.values());
    }

    public void commit() {
      when(exposureNotificationClientWrapper.getDailySummaries(any()))
          .thenReturn(Tasks.forResult(get()));
    }

  }

}
