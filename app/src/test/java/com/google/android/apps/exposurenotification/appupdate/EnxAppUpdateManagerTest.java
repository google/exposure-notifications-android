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

package com.google.android.apps.exposurenotification.appupdate;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.content.Context;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.apps.exposurenotification.testsupport.ExposureNotificationRules;
import com.google.android.apps.exposurenotification.testsupport.FakeShadowResources;
import com.google.android.play.core.appupdate.AppUpdateInfo;
import com.google.android.play.core.appupdate.AppUpdateManager;
import com.google.android.play.core.appupdate.testing.FakeAppUpdateManager;
import com.google.android.play.core.common.IntentSenderForResultStarter;
import com.google.android.play.core.install.model.AppUpdateType;
import com.google.android.play.core.install.model.UpdateAvailability;
import dagger.hilt.android.testing.BindValue;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import dagger.hilt.android.testing.UninstallModules;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.annotation.Config;

@RunWith(AndroidJUnit4.class)
@HiltAndroidTest
@Config(application = HiltTestApplication.class, shadows = {FakeShadowResources.class})
@UninstallModules({AppUpdateManagerModule.class})
public class EnxAppUpdateManagerTest {

  private static final int AVAILABLE_UPDATE_VERSION_CODE = 10;

  @Mock
  ActivityResultLauncher<IntentSenderRequest> launcher;

  @Rule
  public ExposureNotificationRules rules = ExposureNotificationRules.forTest(this).withMocks()
      .build();

  private final Context context = ApplicationProvider.getApplicationContext();

  @BindValue
  AppUpdateManager appUpdateManager = spy(new FakeAppUpdateManager(context));

  @Inject
  EnxAppUpdateManager enxAppUpdateManager; // The SUT.

  @Before
  public void setUp() {
    rules.hilt().inject();
  }

  @Test
  public void getAppUpdateInfo_appUpdateNotAvailable_returnsAppUpdateInfoWithExpectedValues() {
    ((FakeAppUpdateManager) appUpdateManager).setUpdateNotAvailable();

    AppUpdateInfo appUpdateInfo = enxAppUpdateManager.getAppUpdateInfo().getResult();

    assertThat(appUpdateInfo.updateAvailability())
        .isEqualTo(UpdateAvailability.UPDATE_NOT_AVAILABLE);
  }

  @Test
  public void getAppUpdateInfo_appUpdateAvailable_returnsAppUpdateInfoWithExpectedValues() {
    ((FakeAppUpdateManager) appUpdateManager).setUpdateAvailable(AVAILABLE_UPDATE_VERSION_CODE,
        AppUpdateType.IMMEDIATE);

    AppUpdateInfo appUpdateInfo = enxAppUpdateManager.getAppUpdateInfo().getResult();

    assertThat(appUpdateInfo.availableVersionCode()).isEqualTo(AVAILABLE_UPDATE_VERSION_CODE);
    assertThat(appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)).isTrue();
  }

  @Test
  public void getAppUpdateInfoFuture_appUpdateNotAvailable_returnsAppUpdateInfoWithExpectedValues()
      throws Exception {
    ((FakeAppUpdateManager) appUpdateManager).setUpdateNotAvailable();

    AppUpdateInfo appUpdateInfo = enxAppUpdateManager.getAppUpdateInfoFuture().get();

    assertThat(appUpdateInfo.updateAvailability())
        .isEqualTo(UpdateAvailability.UPDATE_NOT_AVAILABLE);
  }

  @Test
  public void getAppUpdateInfoFuture_appUpdateAvailable_returnsAppUpdateInfoWithExpectedValues()
      throws Exception {
    ((FakeAppUpdateManager) appUpdateManager).setUpdateAvailable(AVAILABLE_UPDATE_VERSION_CODE,
        AppUpdateType.IMMEDIATE);

    AppUpdateInfo appUpdateInfo = enxAppUpdateManager.getAppUpdateInfoFuture().get();

    assertThat(appUpdateInfo.availableVersionCode()).isEqualTo(AVAILABLE_UPDATE_VERSION_CODE);
    assertThat(appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)).isTrue();
  }

  @Test
  public void isImmediateAppUpdateAvailable_appUpdateNotAvailable_returnsFalse() {
    ((FakeAppUpdateManager) appUpdateManager).setUpdateNotAvailable();

    AppUpdateInfo appUpdateInfo = appUpdateManager.getAppUpdateInfo().getResult();

    assertThat(enxAppUpdateManager.isImmediateAppUpdateAvailable(appUpdateInfo)).isFalse();
  }

  @Test
  public void isImmediateAppUpdateAvailable_flexibleAppUpdateAvailableOnly_returnsFalse() {
    ((FakeAppUpdateManager) appUpdateManager).setUpdateAvailable(AVAILABLE_UPDATE_VERSION_CODE,
        AppUpdateType.FLEXIBLE);

    AppUpdateInfo appUpdateInfo = appUpdateManager.getAppUpdateInfo().getResult();

    assertThat(enxAppUpdateManager.isImmediateAppUpdateAvailable(appUpdateInfo)).isFalse();
  }

  @Test
  public void isImmediateAppUpdateAvailable_immediateAppUpdateAvailable_returnsTrue() {
    ((FakeAppUpdateManager) appUpdateManager).setUpdateAvailable(AVAILABLE_UPDATE_VERSION_CODE,
        AppUpdateType.IMMEDIATE);

    AppUpdateInfo appUpdateInfo = appUpdateManager.getAppUpdateInfo().getResult();

    assertThat(enxAppUpdateManager.isImmediateAppUpdateAvailable(appUpdateInfo)).isTrue();
  }

  @Test
  public void triggerImmediateAppUpdateFlow_flowNotLaunched_returnsFalse() throws Exception {
    doReturn(false).when(appUpdateManager).startUpdateFlowForResult(any(), anyInt(),
        any(IntentSenderForResultStarter.class), anyInt());

    boolean result = enxAppUpdateManager.triggerImmediateAppUpdateFlow(launcher).get();

    verify(appUpdateManager).startUpdateFlowForResult(any(), anyInt(),
        any(IntentSenderForResultStarter.class), anyInt());
    assertThat(result).isFalse();
  }

  @Test
  public void triggerImmediateAppUpdateFlow_flowLaunched_returnsTrue() throws Exception {
    doReturn(true).when(appUpdateManager).startUpdateFlowForResult(any(), anyInt(),
        any(IntentSenderForResultStarter.class), anyInt());

    boolean result = enxAppUpdateManager.triggerImmediateAppUpdateFlow(launcher).get();

    verify(appUpdateManager).startUpdateFlowForResult(any(), anyInt(),
        any(IntentSenderForResultStarter.class), anyInt());
    assertThat(result).isTrue();
  }

}
