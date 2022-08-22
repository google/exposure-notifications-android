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

package com.google.android.apps.exposurenotification.privateanalytics.metrics;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.android.apps.exposurenotification.common.time.RealTimeModule;
import com.google.android.apps.exposurenotification.network.RealRequestQueueModule;
import com.google.android.apps.exposurenotification.network.RequestQueueWrapper;
import com.google.android.apps.exposurenotification.privateanalytics.MetricsRemoteConfigs;
import com.google.android.apps.exposurenotification.privateanalytics.MetricsRemoteConfigs.Builder;
import com.google.android.apps.exposurenotification.privateanalytics.PrivateAnalyticsMetricsRemoteConfig;
import com.google.android.apps.exposurenotification.testsupport.ExposureNotificationRules;
import com.google.android.apps.exposurenotification.testsupport.FakeClock;
import com.google.android.apps.exposurenotification.testsupport.FakeRequestQueue;
import com.google.android.apps.exposurenotification.testsupport.FakeShadowResources;
import com.google.android.libraries.privateanalytics.MetricsCollection;
import com.google.android.libraries.privateanalytics.PrioDataPoint;
import com.google.android.libraries.privateanalytics.PrivateAnalyticsSubmitter.PrioDataPointsProvider;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.Futures;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.android.testing.BindValue;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import dagger.hilt.android.testing.UninstallModules;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Tests of {@link MetricsModule}.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner.class)
@Config(application = HiltTestApplication.class, shadows = {FakeShadowResources.class})
@UninstallModules({RealTimeModule.class, RealRequestQueueModule.class})
public class MetricsModuleTest {

  private static final double DEFAULT_SAMPING_RATE = 1.0;
  private static final double DEFAULT_DAILY_METRIC_EPSILON = 8.0;
  private static final double DEFAULT_BIWEEKLY_METRIC_EPSILON = 8.0;
  private static final int DAILY_METRICS = 7;
  private static final int BIWEEKLY_METRICS = 6;

  @Rule
  public ExposureNotificationRules rules = ExposureNotificationRules.forTest(this).withMocks()
      .build();

  @Inject
  @ApplicationContext
  Context context;

  @BindValue
  Clock clock = new FakeClock();

  @BindValue
  @Mock
  PrivateAnalyticsMetricsRemoteConfig privateAnalyticsMetricsRemoteConfig;

  @BindValue
  RequestQueueWrapper queue = new FakeRequestQueue();

  @Inject
  KeysUploadedVaccineStatusMetric keysUploadedVaccineStatusMetricMetric;

  @Inject
  PrioDataPointsProvider metricsModule;

  @Before
  public void setup() {
    rules.hilt().inject();
    MetricsRemoteConfigs defaultRemoteConfig = MetricsRemoteConfigs.newBuilder().build();
    when(privateAnalyticsMetricsRemoteConfig.fetchUpdatedConfigs()).thenReturn(
        Futures.immediateFuture(defaultRemoteConfig));
  }

  @Test
  public void getMetricsList_whenVaccineQuestionProvided_vaccineMetricExpected()
      throws ExecutionException, InterruptedException {
    FakeShadowResources resources = (FakeShadowResources) shadowOf(context.getResources());
    resources.addFakeResource(R.string.share_vaccination_detail, "non-empty");

    List<PrioDataPoint> metricsList = metricsModule.get().get().getDailyMetrics();
    List<String> metricNames = metricsList.stream()
        .map(dataPoint -> dataPoint.getMetric().getMetricName()).collect(Collectors.toList());

    assertThat(metricNames).contains(keysUploadedVaccineStatusMetricMetric.getMetricName());
  }

  @Test
  public void getMetricsList_whenVaccineQuestionNotProvided_vaccineMetricNotExpected()
      throws ExecutionException, InterruptedException {
    FakeShadowResources resources = (FakeShadowResources) shadowOf(context.getResources());
    resources.addFakeResource(R.string.share_vaccination_detail, "");

    List<PrioDataPoint> metricsList = metricsModule.get().get().getDailyMetrics();
    List<String> metricNames = metricsList.stream()
        .map(dataPoint -> dataPoint.getMetric().getMetricName()).collect(Collectors.toList());

    assertThat(metricNames).doesNotContain(keysUploadedVaccineStatusMetricMetric.getMetricName());
  }

  @Test
  public void getMetricsList_hasAppropriateDefaultConfig()
      throws ExecutionException, InterruptedException {
    MetricsCollection metricsList = metricsModule.get().get();

    assertThat(metricsList.getDailyMetrics()).hasSize(DAILY_METRICS);
    for (PrioDataPoint point: metricsList.getDailyMetrics()) {
      assertThat(point.getSampleRate()).isEqualTo(DEFAULT_SAMPING_RATE);
      assertThat(point.getEpsilon()).isEqualTo(DEFAULT_DAILY_METRIC_EPSILON);
    }

    assertThat(metricsList.getBiweeklyMetrics()).hasSize(BIWEEKLY_METRICS);
    for (PrioDataPoint point: metricsList.getBiweeklyMetrics()) {
      assertThat(point.getSampleRate()).isEqualTo(DEFAULT_SAMPING_RATE);
      assertThat(point.getEpsilon()).isEqualTo(DEFAULT_BIWEEKLY_METRIC_EPSILON);
    }
  }
}
