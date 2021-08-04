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

package com.google.android.apps.exposurenotification.common;

import static com.google.android.apps.exposurenotification.common.IntentUtil.SELF_REPORT_PATH;
import static com.google.android.apps.exposurenotification.notify.ShareDiagnosisFragment.EXTRA_DIAGNOSIS_ID;
import static com.google.android.apps.exposurenotification.notify.ShareDiagnosisFragment.EXTRA_STEP;
import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.apps.exposurenotification.BuildConfig;
import com.google.common.base.Optional;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

@RunWith(AndroidJUnit4.class)
@HiltAndroidTest
@Config(application = HiltTestApplication.class)
public class IntentUtilTest {

  private final Context context = ApplicationProvider.getApplicationContext();
  private final PackageManager packageManager = context.getPackageManager();

  @Test
  public void actionLaunchHome_v2_intentNotResolvable() {
    if (!BuildConfig.FLAVOR_type.equals(BuildConfig.TYPE_V2)) {
      return;
    }
    Intent intent = IntentUtil.getActionLaunchHomeIntent(context);

    assertThat(intent.resolveActivity(packageManager)).isNull();
  }

  @Test
  public void actionLaunchHome_v3_intentResolvable() {
    if (!BuildConfig.FLAVOR_type.equals(BuildConfig.TYPE_V3)) {
      return;
    }
    Intent intent = IntentUtil.getActionLaunchHomeIntent(context);

    assertThat(intent.resolveActivity(packageManager)).isNotNull();
  }

  @Test
  public void getNotificationContentIntentExposure_v2_hasActionMain() {
    if (!BuildConfig.FLAVOR_type.equals(BuildConfig.TYPE_V2)) {
      return;
    }
    Intent intent = IntentUtil.getNotificationContentIntentExposure(context);

    assertThat(intent.getAction()).isEqualTo(Intent.ACTION_MAIN);
  }

  @Test
  public void getNotificationContentIntentExposure_v3_hasActionLaunchHome() {
    if (!BuildConfig.FLAVOR_type.equals(BuildConfig.TYPE_V3)) {
      return;
    }
    Intent intent = IntentUtil.getNotificationContentIntentExposure(context);

    assertThat(intent.getAction()).isEqualTo(IntentUtil.ACTION_LAUNCH_HOME);
  }

  @Test
  public void isValidBundleToOpenShareDiagnosisFlow_nullBundle_returnsFalse() {
    assertThat(IntentUtil.isValidBundleToOpenShareDiagnosisFlow(null)).isFalse();
  }

  @Test
  public void isValidBundleToOpenShareDiagnosisFlow_invalidBundle_returnsFalse() {
    // GIVEN
    Bundle invalidBundle = new Bundle();
    invalidBundle.putLong(EXTRA_DIAGNOSIS_ID, -10);
    invalidBundle.putString(EXTRA_STEP, "invalid-step-name");

    // WHEN
    boolean isValidBundle = IntentUtil.isValidBundleToOpenShareDiagnosisFlow(invalidBundle);

    // THEN
    assertThat(isValidBundle).isFalse();
  }

  @Test
  public void isValidBundleToOpenShareDiagnosisFlow_validBundle_returnsTrue() {
    // GIVEN
    Bundle validBundle = new Bundle();
    validBundle.putLong(EXTRA_DIAGNOSIS_ID, 3);
    validBundle.putString(EXTRA_STEP, "ONSET");

    // WHEN
    boolean isValidBundle = IntentUtil.isValidBundleToOpenShareDiagnosisFlow(validBundle);

    // THEN
    assertThat(isValidBundle).isTrue();
  }

  @Test
  public void maybeGetCodeFromDeepLinkUri_correctlyFormattedUri_returnsVerificationCode() {
    String verificationCode = "123";
    Uri uri = Uri.parse("ens://v?c=" + verificationCode);

    Optional<String> codeFromDeepLink = IntentUtil.maybeGetCodeFromDeepLinkUri(uri);

    assertThat(codeFromDeepLink).hasValue(verificationCode);
  }

  @Test
  public void maybeGetCodeFromDeepLinkUri_googleLink_returnsAbsent() {
    Uri uri = Uri.parse("https://www.google.com");

    Optional<String> codeFromDeepLink = IntentUtil.maybeGetCodeFromDeepLinkUri(uri);

    assertThat(codeFromDeepLink).isAbsent();
  }

  @Test
  public void maybeGetCodeFromDeepLinkUri_nullUri_returnsAbsent() {
    Optional<String> codeFromDeepLink = IntentUtil.maybeGetCodeFromDeepLinkUri(null);

    assertThat(codeFromDeepLink).isAbsent();
  }

  @Test
  public void maybeGetCodeFromDeepLinkUri_emptyVerificationCode_returnsAbsent() {
    String verificationCode = "";
    Uri uri = Uri.parse("ens://v?c=" + verificationCode);

    Optional<String> codeFromDeepLink = IntentUtil.maybeGetCodeFromDeepLinkUri(uri);

    assertThat(codeFromDeepLink).isAbsent();
  }

  @Test
  public void maybeGetCodeFromDeepLinkUri_invalidChars_returnsAbsent() {
    String verificationCode = "aA1!";
    Uri uri = Uri.parse("ens://v?c=" + verificationCode);

    Optional<String> codeFromDeepLink = IntentUtil.maybeGetCodeFromDeepLinkUri(uri);

    assertThat(codeFromDeepLink).isAbsent();
  }

  @Test
  public void maybeGetCodeFromDeepLinkUri_exactMaxLength_returnsVerificationCode() {
    String verificationCode = "";
    for (int i = 0; i < IntentUtil.VERIFICATION_CODE_MAX_CHARS; i++) {
      verificationCode += "a";
    }
    Uri uri = Uri.parse("ens://v?c=" + verificationCode);

    Optional<String> codeFromDeepLink = IntentUtil.maybeGetCodeFromDeepLinkUri(uri);

    assertThat(codeFromDeepLink).hasValue(verificationCode);
  }

  @Test
  public void maybeGetCodeFromDeepLinkUri_tooLong_returnsAbsent() {
    String verificationCode = "";
    for (int i = 0; i < IntentUtil.VERIFICATION_CODE_MAX_CHARS + 1; i++) {
      verificationCode += "a";
    }
    Uri uri = Uri.parse("ens://v?c=" + verificationCode);

    Optional<String> codeFromDeepLink = IntentUtil.maybeGetCodeFromDeepLinkUri(uri);

    assertThat(codeFromDeepLink).isAbsent();
  }

  @Test
  public void maybeGetCodeFromDeepLinkUri_validChars_returnsVerificationCode() {
    String verificationCode = "aA1";
    Uri uri = Uri.parse("ens://v?c=" + verificationCode);

    Optional<String> codeFromDeepLink = IntentUtil.maybeGetCodeFromDeepLinkUri(uri);

    assertThat(codeFromDeepLink).hasValue(verificationCode);
  }

  @Test
  public void isSelfReportUri_nullUri_returnsFalse() {
    boolean isSelfReportUri = IntentUtil.isSelfReportUri(null);

    assertThat(isSelfReportUri).isFalse();
  }

  @Test
  public void isSelfReportUri_noReportPath_returnsFalse() {
    Uri ensUri = Uri.parse("ens://");
    Uri httpsUri = Uri.parse("https://" + BuildConfig.APP_LINK_HOST);

    assertThat(IntentUtil.isSelfReportUri(ensUri)).isFalse();
    assertThat(IntentUtil.isSelfReportUri(httpsUri)).isFalse();
  }


  @Test
  public void isSelfReportUri_uriWithCode_returnsFalse() {
    String verificationCode = "aA1";
    Uri ensUri = Uri.parse("ens://v?c=" + verificationCode);
    Uri httpsUri = Uri.parse("https://" + BuildConfig.APP_LINK_HOST + "/v?c=" + verificationCode);

    assertThat(IntentUtil.isSelfReportUri(ensUri)).isFalse();
    assertThat(IntentUtil.isSelfReportUri(httpsUri)).isFalse();
  }

  @Test
  public void isSelfReportUri_reportPathPresent_returnsTrue() {
    Uri ensUri = Uri.parse("ens://" + SELF_REPORT_PATH);
    Uri httpsUri = Uri.parse("https://" + BuildConfig.APP_LINK_HOST + "/" + SELF_REPORT_PATH);

    assertThat(IntentUtil.isSelfReportUri(ensUri)).isTrue();
    assertThat(IntentUtil.isSelfReportUri(httpsUri)).isTrue();
  }
}