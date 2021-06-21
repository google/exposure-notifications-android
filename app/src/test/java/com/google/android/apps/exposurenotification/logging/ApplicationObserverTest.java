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

package com.google.android.apps.exposurenotification.logging;

import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;

import androidx.lifecycle.Lifecycle.Event;
import androidx.lifecycle.Lifecycle.State;
import androidx.lifecycle.testing.TestLifecycleOwner;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.apps.exposurenotification.proto.UiInteraction.EventType;
import com.google.android.apps.exposurenotification.testsupport.ExposureNotificationRules;
import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import dagger.hilt.android.testing.UninstallModules;
import dagger.hilt.components.SingletonComponent;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

@RunWith(AndroidJUnit4.class)
@HiltAndroidTest
@Config(application = HiltTestApplication.class)
@UninstallModules({LoggerModule.class})
public class ApplicationObserverTest {

  @Module
  @InstallIn(SingletonComponent.class)
  static class FakeLoggerModule {
    @Provides
    @Singleton
    public  AnalyticsLogger provideAnalyticsLogger() { return mock(FirelogAnalyticsLogger.class); }
    @Provides
    public ApplicationObserver provideApplicationObserver(AnalyticsLogger analyticsLogger) {
      return new ApplicationObserver(analyticsLogger);
    }
  }

  TestLifecycleOwner testLifecycleOwner;

  @Inject
  AnalyticsLogger analyticsLogger;

  @Inject
  ApplicationObserver applicationObserver;

  @Rule
  public ExposureNotificationRules rules =
      ExposureNotificationRules.forTest(this).withMocks().build();

  @Before
  public void setUp() {
    rules.hilt().inject();
    testLifecycleOwner = new TestLifecycleOwner();
    // Default to initialized. That means the App is not yet created / onCreate was not yet called
    testLifecycleOwner.setCurrentState(State.INITIALIZED);
    // Same call as in ExposureNotificationApplication
    applicationObserver.observeLifecycle(testLifecycleOwner);
    clearInvocations(analyticsLogger);
  }

  /**
   * We only want to log APP_OPENED when the app is started, not when its created (as creation
   * might be called very rarely)
   */
  @Test
  public void applicationObserver_lifecycleChangesInitializedOnCreate_appOpenedLogNotCalled() {
    testLifecycleOwner.handleLifecycleEvent(Event.ON_CREATE);

    verifyZeroInteractions(analyticsLogger);
  }

  /**
   * If the app is started for the first time, we want to log APP_OPENED
   */
  @Test
  public void applicationObserver_lifecycleChangesInitializedOnStart_appOpenedLogCalled() {
    testLifecycleOwner.handleLifecycleEvent(Event.ON_START);

    verify(analyticsLogger, times(1)).logUiInteraction(EventType.APP_OPENED);
    verifyNoMoreInteractions(analyticsLogger);
  }

  /**
   *  If the app is stopped and re-started (killed and restarted,
   *  put into background and back into foreground), we want to log APP_OPENED
   */
  @Test
  public void applicationObserver_lifecycleChangesStopOnStart_appOpenedLogCalled() {
    testLifecycleOwner.handleLifecycleEvent(Event.ON_STOP);
    clearInvocations(analyticsLogger);

    testLifecycleOwner.handleLifecycleEvent(Event.ON_START);

    verify(analyticsLogger, times(1)).logUiInteraction(EventType.APP_OPENED);
    verifyNoMoreInteractions(analyticsLogger);
  }

  /*
   * If the app is paused then resumed (e.g. orientation change, inter-app intents), we do not want
   * to log APP_OPENED
   */
  @Test
  public void applicationObserver_lifecycleChangesPauseOnResume_appOpenedLogNotCalled() {
    testLifecycleOwner.handleLifecycleEvent(Event.ON_PAUSE);
    clearInvocations(analyticsLogger); /*Clear previous INITIALIZED -> START -> PAUSE transitions*/

    testLifecycleOwner.handleLifecycleEvent(Event.ON_RESUME);

    verifyZeroInteractions(analyticsLogger);
  }

}
