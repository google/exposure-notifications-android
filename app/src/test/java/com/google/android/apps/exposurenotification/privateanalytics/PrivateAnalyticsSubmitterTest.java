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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.apps.exposurenotification.common.ExecutorsModule;
import com.google.android.apps.exposurenotification.common.Qualifiers.BackgroundExecutor;
import com.google.android.apps.exposurenotification.common.Qualifiers.LightweightExecutor;
import com.google.android.apps.exposurenotification.common.Qualifiers.ScheduledExecutor;
import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.android.apps.exposurenotification.common.time.RealTimeModule;
import com.google.android.apps.exposurenotification.nearby.ExposureNotificationClientWrapper;
import com.google.android.apps.exposurenotification.nearby.ExposureNotificationsClientModule;
import com.google.android.apps.exposurenotification.proto.CreatePacketsResponse;
import com.google.android.apps.exposurenotification.proto.ResponseStatus;
import com.google.android.apps.exposurenotification.proto.ResponseStatus.StatusCode;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.apps.exposurenotification.testsupport.ExposureNotificationRules;
import com.google.android.apps.exposurenotification.testsupport.FakeClock;
import com.google.android.gms.tasks.Tasks;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListeningExecutorService;
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
    PrivateAnalyticsFirebaseModule.class, PrioModule.class, RealTimeModule.class})
public class PrivateAnalyticsSubmitterTest {

  @Rule
  public ExposureNotificationRules rules = ExposureNotificationRules.forTest(this).withMocks()
      .build();
  @BindValue
  Clock clock = new FakeClock();
  @BindValue
  @Mock
  PrivateAnalyticsRemoteConfig privateAnalyticsRemoteConfig;
  @BindValue
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
  @Inject
  ExposureNotificationSharedPreferences exposureNotificationSharedPreferences;
  @Inject
  PrivateAnalyticsSubmitter privateAnalyticsSubmitter;

  @BindValue
  @BackgroundExecutor
  static final ExecutorService BACKGROUND_EXEC = MoreExecutors.newDirectExecutorService();
  @BindValue
  @LightweightExecutor
  static final ExecutorService LIGHTWEIGHT_EXEC = MoreExecutors.newDirectExecutorService();
  @BindValue
  @BackgroundExecutor
  static final ListeningExecutorService BACKGROUND_LISTENING_EXEC =
      MoreExecutors.newDirectExecutorService();
  @BindValue
  @LightweightExecutor
  static final ListeningExecutorService LIGHTWEIGHT_LISTENING_EXEC =
      MoreExecutors.newDirectExecutorService();
  @BindValue
  @ScheduledExecutor
  static final ScheduledExecutorService SCHEDULED_EXEC =
      TestingExecutors.sameThreadScheduledExecutor();

  @Captor
  ArgumentCaptor<Object> documentsCaptor;

  @Before
  public void setUp() {
    rules.hilt().inject();
    exposureNotificationSharedPreferences.setPrivateAnalyticsState(true);

    RemoteConfigs remoteConfig = RemoteConfigs.newBuilder()
        .setEnabled(true)
        .setDeviceAttestationRequired(false)
        .build();
    when(privateAnalyticsRemoteConfig.fetchUpdatedConfigs())
        .thenReturn(Futures.immediateFuture(remoteConfig));
  }

  @Test
  public void testSubmitPackets_uploadsThreeShares()
      throws ExecutionException, InterruptedException {
    CreatePacketsResponse createPacketsResponse = CreatePacketsResponse.newBuilder()
        .addShares(generateRandomByteString())
        .addShares(generateRandomByteString())
        .setResponseStatus(ResponseStatus.newBuilder().setStatusCode(StatusCode.OK))
        .build();
    when(prio.getPackets(any())).thenReturn(createPacketsResponse);

    when(firebaseFirestore.collection(any())).thenReturn(collectionReference);
    when(collectionReference.document(any())).thenReturn(documentReference);
    when(documentReference.collection(any())).thenReturn(collectionReference);

    when(exposureNotificationClientWrapper.getExposureWindows())
        .thenReturn(Tasks.forResult(new ArrayList<>()));

    privateAnalyticsSubmitter.submitPackets().get();
    verify(documentReference, times(3)).set(documentsCaptor.capture());
  }

  private ByteString generateRandomByteString() {
    SecureRandom random = new SecureRandom();
    byte[] bytes = new byte[20];
    random.nextBytes(bytes);
    return ByteString.copyFrom(bytes);
  }
}