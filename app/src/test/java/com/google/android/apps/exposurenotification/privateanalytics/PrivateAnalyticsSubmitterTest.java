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

package com.google.android.apps.exposurenotification.privateanalytics;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.apps.exposurenotification.common.ExecutorsModule;
import com.google.android.apps.exposurenotification.common.Qualifiers;
import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.android.apps.exposurenotification.common.time.RealTimeModule;
import com.google.android.apps.exposurenotification.nearby.ExposureNotificationClientWrapper;
import com.google.android.apps.exposurenotification.nearby.ExposureNotificationsClientModule;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.apps.exposurenotification.testsupport.ExposureNotificationRules;
import com.google.android.apps.exposurenotification.testsupport.FakeClock;
import com.google.android.gms.tasks.Tasks;
import com.google.android.libraries.privateanalytics.Executors;
import com.google.android.libraries.privateanalytics.Prio;
import com.google.android.libraries.privateanalytics.PrivateAnalyticsDeviceAttestation;
import com.google.android.libraries.privateanalytics.PrivateAnalyticsEnabledProvider;
import com.google.android.libraries.privateanalytics.PrivateAnalyticsEventListener;
import com.google.android.libraries.privateanalytics.PrivateAnalyticsFirestoreRepository;
import com.google.android.libraries.privateanalytics.PrivateAnalyticsLogger;
import com.google.android.libraries.privateanalytics.PrivateAnalyticsRemoteConfig;
import com.google.android.libraries.privateanalytics.PrivateAnalyticsSubmitter;
import com.google.android.libraries.privateanalytics.PrivateAnalyticsSubmitter.PrioDataPointsProvider;
import com.google.android.libraries.privateanalytics.RemoteConfigs;
import com.google.android.libraries.privateanalytics.proto.CreatePacketsResponse;
import com.google.android.libraries.privateanalytics.proto.ResponseStatus;
import com.google.android.libraries.privateanalytics.proto.ResponseStatus.StatusCode;
import com.google.common.base.Optional;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.testing.TestingExecutors;
import com.google.firebase.FirebaseApp;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.protobuf.ByteString;
import dagger.hilt.android.testing.BindValue;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import dagger.hilt.android.testing.UninstallModules;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.robolectric.annotation.Config;

@RunWith(AndroidJUnit4.class)
@HiltAndroidTest
@Config(application = HiltTestApplication.class)
@UninstallModules({ExecutorsModule.class, ExposureNotificationsClientModule.class,
    PrivateAnalyticsFirebaseModule.class, RealTimeModule.class, PrivateAnalyticsModule.class})
public class PrivateAnalyticsSubmitterTest {

  private static final int DAILY_METRICS = 7;
  private static final int BIWEEKLY_METRICS = 6;

  @Rule
  public ExposureNotificationRules rules = ExposureNotificationRules.forTest(this).withMocks()
      .build();
  @BindValue
  Clock clock = new FakeClock();
  @BindValue
  @Mock
  PrivateAnalyticsMetricsRemoteConfig appRemoteConfig;
  @BindValue
  @Mock
  PrivateAnalyticsRemoteConfig sdkRemoteConfig;
  @Mock
  Prio prio;
  @BindValue
  @Mock
  FirebaseFirestore firebaseFirestore;
  @BindValue
  @Mock
  FirebaseApp firebaseApp;
  @Mock
  CollectionReference collectionReference;
  @Mock
  DocumentReference documentReference;
  @Mock
  @BindValue
  ExposureNotificationClientWrapper exposureNotificationClientWrapper;
  @BindValue
  Optional<PrivateAnalyticsEventListener> listener = Optional.absent();
  @BindValue
  PrivateAnalyticsEnabledProvider privateAnalyticsEnabledProvider = new PrivateAnalyticsEnabledProvider() {
    @Override
    public boolean isSupportedByApp() {
      return true;
    }

    @Override
    public boolean isEnabledForUser() {
      return true;
    }
  };

  @BindValue
  @Mock
  PrivateAnalyticsDeviceAttestation deviceAttestation;
  @Inject
  ExposureNotificationSharedPreferences exposureNotificationSharedPreferences;
  @Inject
  PrioDataPointsProvider prioDataPointsProvider;
  @Inject
  PrivateAnalyticsFirestoreRepository firestoreRepository;
  @BindValue
  PrivateAnalyticsLogger.Factory loggerFactory = new FakePrivateAnalyticsLoggerFactory();


  PrivateAnalyticsSubmitter privateAnalyticsSubmitter;

  @BindValue
  @Qualifiers.BackgroundExecutor
  static final ExecutorService BACKGROUND_EXEC = MoreExecutors.newDirectExecutorService();
  @BindValue
  @Qualifiers.LightweightExecutor
  static final ExecutorService LIGHTWEIGHT_EXEC = MoreExecutors.newDirectExecutorService();
  @BindValue
  @Qualifiers.ScheduledExecutor
  static final ScheduledExecutorService SCHEDULED_EXEC =
      TestingExecutors.sameThreadScheduledExecutor();
  @BindValue
  @Qualifiers.BackgroundExecutor
  static final ListeningExecutorService BACKGROUND_LISTENING_EXEC =
      MoreExecutors.newDirectExecutorService();
  @BindValue
  @Qualifiers.LightweightExecutor
  static final ListeningExecutorService LIGHTWEIGHT_LISTENING_EXEC =
      MoreExecutors.newDirectExecutorService();
  @BindValue
  @Qualifiers.ScheduledExecutor
  static final ListeningScheduledExecutorService SCHEDULED_LISTENING_EXEC =
      TestingExecutors.sameThreadScheduledExecutor();

  @Captor
  ArgumentCaptor<Object> documentsCaptor;

  @Before
  public void setUp() throws Exception {
    when(appRemoteConfig.fetchUpdatedConfigs())
        .thenReturn(Futures.immediateFuture(
            MetricsRemoteConfigs.newBuilder()
                .build()));

    Executors.setBackgroundListeningExecutor(MoreExecutors.newDirectExecutorService());
    Executors.setLightweightListeningExecutor(MoreExecutors.newDirectExecutorService());
    Executors.setScheduledExecutor(TestingExecutors.sameThreadScheduledExecutor());

    // The appRemoteConfig needs to be mocked properly before we trigger Hilt injection, since one
    // of the modules provides method needs the mock's output.
    rules.hilt().inject();

    // We use a default non-existent day, so that tests always behave the same, regardless of the
    // day of week they're running.
    int weeklyMetricsUploadDay = 0;

    privateAnalyticsSubmitter = new PrivateAnalyticsSubmitter(prioDataPointsProvider,
        sdkRemoteConfig, firestoreRepository, privateAnalyticsEnabledProvider, loggerFactory,
        weeklyMetricsUploadDay);
    exposureNotificationSharedPreferences.setPrivateAnalyticsState(true);
  }

  @Test
  public void testSubmitPackets_doesNotUploadSharesIfKeysOrCertificatesMissing() {
    RemoteConfigs remoteConfig = RemoteConfigs.newBuilder()
        .setEnabled(true)
        .setDeviceAttestationRequired(false)
        .build();
    when(sdkRemoteConfig.fetchUpdatedConfigs())
        .thenReturn(Futures.immediateFuture(remoteConfig));

    verify(prio, never()).getPackets(any());
  }

  @Test
  public void testSubmitPackets_uploadsShares()
      throws ExecutionException, InterruptedException {
    setupSubmissionFixture();

    // The following function should submit the _five_ metrics available.
    privateAnalyticsSubmitter.submitPackets().get();
    verify(documentReference, times(DAILY_METRICS)).set(documentsCaptor.capture());
  }

  @Test
  public void testSubmitPackets_uploadsWeeklySharesOnCorrectDay()
      throws ExecutionException, InterruptedException {
    Calendar calendar = Calendar.getInstance();
    int dayIndex = calendar.get(Calendar.DAY_OF_WEEK) - 1;
    int weekIndex = calendar.get(Calendar.WEEK_OF_YEAR) % 2;
    int currentDay = dayIndex + 7 * weekIndex;
    privateAnalyticsSubmitter = new PrivateAnalyticsSubmitter(
        prioDataPointsProvider, sdkRemoteConfig, firestoreRepository,
        privateAnalyticsEnabledProvider, loggerFactory, currentDay);
    setupSubmissionFixture();

    // The following function should submit the _five_ metrics available.
    privateAnalyticsSubmitter.submitPackets().get();
    verify(documentReference, times(DAILY_METRICS + BIWEEKLY_METRICS))
        .set(documentsCaptor.capture());
  }

  // This creates a configuration where privateAnalyticsSubmitter will run all the way
  // (creating the datashare files in the firebaseFirestore mock).
  private void setupSubmissionFixture() {
    RemoteConfigs remoteConfig = RemoteConfigs.newBuilder()
        .setEnabled(true)
        .setDeviceAttestationRequired(false)
        .setFacilitatorCertificate("foo")
        .setPhaCertificate("foo")
        .build();
    when(sdkRemoteConfig.fetchUpdatedConfigs())
        .thenReturn(Futures.immediateFuture(remoteConfig));

    CreatePacketsResponse createPacketsResponse = CreatePacketsResponse.newBuilder()
        .addShares(generateRandomByteString())
        .addShares(generateRandomByteString())
        .setResponseStatus(ResponseStatus.newBuilder().setStatusCode(StatusCode.OK))
        .build();
    privateAnalyticsSubmitter.setPrio(prio);
    when(prio.getPackets(any())).thenReturn(createPacketsResponse);

    when(firebaseFirestore.collection(any())).thenReturn(collectionReference);
    when(collectionReference.document(any())).thenReturn(documentReference);
    when(documentReference.collection(any())).thenReturn(collectionReference);

    when(exposureNotificationClientWrapper.getExposureWindows())
        .thenReturn(Tasks.forResult(new ArrayList<>()));
  }

  private ByteString generateRandomByteString() {
    SecureRandom random = new SecureRandom();
    byte[] bytes = new byte[20];
    random.nextBytes(bytes);
    return ByteString.copyFrom(bytes);
  }
}