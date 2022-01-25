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

import static com.google.android.apps.exposurenotification.nearby.ExposureInformationHelper.EXPOSURE_EXPIRATION_DEFAULT_THRESHOLD;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.work.ListenableWorker.Result;
import androidx.work.WorkerParameters;
import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.android.apps.exposurenotification.common.time.RealTimeModule;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.apps.exposurenotification.testsupport.ExposureClassificationUtils;
import com.google.android.apps.exposurenotification.testsupport.ExposureNotificationRules;
import com.google.android.apps.exposurenotification.testsupport.FakeClock;
import com.google.android.gms.nearby.exposurenotification.DailySummariesConfig;
import com.google.android.gms.nearby.exposurenotification.DailySummariesConfig.DailySummariesConfigBuilder;
import com.google.android.gms.nearby.exposurenotification.ExposureNotificationStatus;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.testing.TestingExecutors;
import dagger.hilt.android.testing.BindValue;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import dagger.hilt.android.testing.UninstallModules;
import java.util.Arrays;
import java.util.Set;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.annotation.Config;

/**
 * This unit test verifies the behavior of ExposureInfoCleanupWorker, which is fired upon the EN
 * turndown states to wipe out the exposure information after it expires.
 */
@RunWith(AndroidJUnit4.class)
@HiltAndroidTest
@Config(application = HiltTestApplication.class)
@UninstallModules({RealTimeModule.class})
public class ExposureInfoCleanupWorkerTest {

  private static final DailySummariesConfig DAILY_SUMMARIES_CONFIG =
      new DailySummariesConfigBuilder()
          .setDaysSinceExposureThreshold((int) EXPOSURE_EXPIRATION_DEFAULT_THRESHOLD.toDays())
          .setAttenuationBuckets(Arrays.asList(1, 2, 3), Arrays.asList(0.0, 0.0, 0.0, 0.0))
          .build();
  private static final Task<Set<ExposureNotificationStatus>> TASK_FOR_ACTIVATED =
      Tasks.forResult(ImmutableSet.of(ExposureNotificationStatus.ACTIVATED));
  private static final Task<Set<ExposureNotificationStatus>> TASK_FOR_EN_TURNDOWN =
      Tasks.forResult(ImmutableSet.of(ExposureNotificationStatus.EN_NOT_SUPPORT));
  private static final Task<Set<ExposureNotificationStatus>> TASK_FOR_EN_TURNDOWN_FOR_REGION =
      Tasks.forResult(ImmutableSet.of(ExposureNotificationStatus.NOT_IN_ALLOWLIST));

  @Rule
  public ExposureNotificationRules rules = ExposureNotificationRules.forTest(this)
      .withMocks()
      .build();

  @Inject
  ExposureNotificationSharedPreferences exposureNotificationSharedPreferences;

  @Mock
  WorkerParameters workerParameters;
  @Mock
  ExposureNotificationClientWrapper exposureNotificationClientWrapper;

  @BindValue
  Clock clock = new FakeClock();

  private ExposureInformationHelper exposureInformationHelper;
  private ExposureInfoCleanupWorker exposureInfoCleanupWorker;

  @Before
  public void setUp() {
    rules.hilt().inject();

    // Spy on exposureInformationHelper since we need to verify its methods are being called.
    exposureInformationHelper = spy(new ExposureInformationHelper(
        exposureNotificationSharedPreferences,
        DAILY_SUMMARIES_CONFIG,
        clock));

    // Instantiate the actual object under test
    exposureInfoCleanupWorker = new ExposureInfoCleanupWorker(
        ApplicationProvider.getApplicationContext(),
        workerParameters,
        exposureNotificationClientWrapper,
        exposureInformationHelper,
        MoreExecutors.newDirectExecutorService(),
        TestingExecutors.sameThreadScheduledExecutor());
  }

  @Test
  public void exposureInfoCleanupWorker_enNotTurnedDown_exposureInfoNotDeleted() throws Exception {
    when(exposureNotificationClientWrapper.getStatus()).thenReturn(TASK_FOR_ACTIVATED);
    exposureNotificationSharedPreferences.setExposureClassification(
        ExposureClassificationUtils.getActiveExposure());

    assertThat(exposureInformationHelper.isActiveExposurePresent()).isTrue();
    Result result = exposureInfoCleanupWorker.startWork().get();

    assertThat(result).isEqualTo(Result.success());
    verify(exposureNotificationClientWrapper).getStatus();
    verify(exposureInformationHelper, never()).deleteExposures();
    assertThat(exposureInformationHelper.isActiveExposurePresent()).isTrue();
  }

  @Test
  public void exposureInfoCleanupWorker_enTurnedDownForRegion_deletesExposureInfo()
      throws Exception {
    when(exposureNotificationClientWrapper.getStatus()).thenReturn(TASK_FOR_EN_TURNDOWN_FOR_REGION);
    exposureNotificationSharedPreferences.setExposureClassification(
        ExposureClassificationUtils.getOutdatedExposure());

    assertThat(exposureInformationHelper.isOutdatedExposurePresent()).isTrue();
    Result result = exposureInfoCleanupWorker.startWork().get();

    assertThat(result).isEqualTo(Result.success());
    verify(exposureNotificationClientWrapper).getStatus();
    verify(exposureInformationHelper).deleteExposures();
    assertThat(exposureInformationHelper.isActiveExposurePresent()).isFalse();
    assertThat(exposureInformationHelper.isOutdatedExposurePresent()).isFalse();
  }

  @Test
  public void exposureInfoCleanupWorker_enTurnedDown_deletesExposureInfo()
      throws Exception {
    when(exposureNotificationClientWrapper.getStatus()).thenReturn(TASK_FOR_EN_TURNDOWN);
    exposureNotificationSharedPreferences.setExposureClassification(
        ExposureClassificationUtils.getOutdatedExposure());

    assertThat(exposureInformationHelper.isOutdatedExposurePresent()).isTrue();
    Result result = exposureInfoCleanupWorker.startWork().get();

    assertThat(result).isEqualTo(Result.success());
    verify(exposureNotificationClientWrapper).getStatus();
    verify(exposureInformationHelper).deleteExposures();
    assertThat(exposureInformationHelper.isActiveExposurePresent()).isFalse();
    assertThat(exposureInformationHelper.isOutdatedExposurePresent()).isFalse();
  }

}
