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

package com.google.android.apps.exposurenotification.notify;

import static com.google.common.truth.Truth.assertThat;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.BuildUtils;
import com.google.android.apps.exposurenotification.common.BuildUtils.Type;
import com.google.android.apps.exposurenotification.notify.ShareDiagnosisFlowHelper.ShareDiagnosisFlow;
import com.google.android.apps.exposurenotification.notify.ShareDiagnosisViewModel.Step;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity.Shared;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity.TestResult;
import com.google.android.apps.exposurenotification.testsupport.FakeShadowResources;
import com.google.common.collect.ImmutableList;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

/**
 * Test for the helper class that defines the sharing flows.
 */
@RunWith(AndroidJUnit4.class)
@HiltAndroidTest
@Config(application = HiltTestApplication.class, shadows = {FakeShadowResources.class})
public class ShareDiagnosisFlowHelperTest {

  private static final DiagnosisEntity EMPTY_DIAGNOSIS = DiagnosisEntity.newBuilder().build();
  private static final DiagnosisEntity DIAGNOSIS_THAT_SKIPS_CODE_STEP = DiagnosisEntity.newBuilder()
      .setIsCodeFromLink(true)
      .setVerificationCode("verification-code")
      .setLongTermToken("long-term-token")
      .setSharedStatus(Shared.NOT_ATTEMPTED)
      .build();
  private static final DiagnosisEntity DIAGNOSIS = DiagnosisEntity.newBuilder().setId(1L).build();

  private final Context context = ApplicationProvider.getApplicationContext();

  @Test
  public void isTravelStatusSkippable_travelQuestionEnabled_returnsFalse() {
    FakeShadowResources resources = (FakeShadowResources) shadowOf(context.getResources());
    resources.addFakeResource(R.string.share_travel_detail, "non-empty");

    boolean isTravelStatusSkippable = ShareDiagnosisFlowHelper.isTravelStatusStepSkippable(context);

    assertThat(isTravelStatusSkippable).isFalse();
  }

  @Test
  public void isTravelStatusSkippable_travelQuestionDisabled_returnsTrue() {
    FakeShadowResources resources = (FakeShadowResources) shadowOf(context.getResources());
    resources.addFakeResource(R.string.share_travel_detail, "");

    boolean isTravelStatusSkippable = ShareDiagnosisFlowHelper.isTravelStatusStepSkippable(context);

    assertThat(isTravelStatusSkippable).isTrue();
  }

  @Test
  public void isCodeStepSkippable_diagnosesRequiringCodeStep_returnsFalse() {
    DiagnosisEntity notCodeFromLinkDiagnosis = DiagnosisEntity.newBuilder()
        .setIsCodeFromLink(false).build();
    DiagnosisEntity noVerificationCodeDiagnosis = DiagnosisEntity.newBuilder()
        .setVerificationCode("").build();
    DiagnosisEntity notVerifiedDiagnosis = DiagnosisEntity.newBuilder()
        .setVerificationCode("verification-code")
        .setLongTermToken("").build();
    List<DiagnosisEntity> diagnosesRequiringCodeStep = ImmutableList
        .of(notCodeFromLinkDiagnosis, noVerificationCodeDiagnosis, notVerifiedDiagnosis);

    for (DiagnosisEntity diagnosis : diagnosesRequiringCodeStep) {
      assertThat(ShareDiagnosisFlowHelper.isCodeStepSkippable(diagnosis)).isFalse();
    }
  }

  @Test
  public void isCodeStepSkippable_diagnosisThatSkipsCodeStep_returnsTrue() {
    assertThat(
        ShareDiagnosisFlowHelper.isCodeStepSkippable(DIAGNOSIS_THAT_SKIPS_CODE_STEP)).isTrue();
  }

  @Test
  public void isSelfReportEnabled_helpUrlIsEmpty_returnsFalse() {
    FakeShadowResources resources = (FakeShadowResources) shadowOf(context.getResources());
    resources.addFakeResource(R.string.self_report_website_url, "");

    boolean isSelfReportEnabled = ShareDiagnosisFlowHelper.isSelfReportEnabled(context);

    assertThat(isSelfReportEnabled).isFalse();
  }

  @Test
  public void isSelfReportEnabled_helpUrlIsNotEmpty_returnsTrue() {
    FakeShadowResources resources = (FakeShadowResources) shadowOf(context.getResources());
    resources.addFakeResource(R.string.self_report_website_url, "example-url");

    boolean isSelfReportEnabled = ShareDiagnosisFlowHelper.isSelfReportEnabled(context);

    assertThat(isSelfReportEnabled).isTrue();
  }

  public void isSmsInterceptEnabled_enableTextMessageVerificationIsFalse_returnsFalse() {
    FakeShadowResources resources = (FakeShadowResources) shadowOf(context.getResources());
    resources.addFakeResource(R.bool.enx_enableTextMessageVerification, false);
    resources.addFakeResource(R.string.enx_testVerificationNotificationBody, "non-empty");

    boolean isTextMessageVerificationEnabled =
        ShareDiagnosisFlowHelper.isSmsInterceptEnabled(context);

    assertThat(isTextMessageVerificationEnabled).isFalse();
  }

  public void isSmsInterceptEnabled_testVerificationNotificationBodyIsEmpty_returnsFalse() {
    FakeShadowResources resources = (FakeShadowResources) shadowOf(context.getResources());
    resources.addFakeResource(R.bool.enx_enableTextMessageVerification, true);
    resources.addFakeResource(R.string.enx_testVerificationNotificationBody, "");

    boolean isTextMessageVerificationEnabled =  ShareDiagnosisFlowHelper
        .isSmsInterceptEnabled(context);

    assertThat(isTextMessageVerificationEnabled).isFalse();
  }

  public void isSmsInterceptEnabled_bothConfigsProvided_returnsTrueIfV2() {
    FakeShadowResources resources = (FakeShadowResources) shadowOf(context.getResources());
    resources.addFakeResource(R.bool.enx_enableTextMessageVerification, true);
    resources.addFakeResource(R.string.enx_testVerificationNotificationBody, "non-empty");

    boolean isTextMessageVerificationEnabled =  ShareDiagnosisFlowHelper
        .isSmsInterceptEnabled(context);

    if (BuildUtils.getType().equals(Type.V2)) {
      assertThat(isTextMessageVerificationEnabled).isTrue();
    } else {
      assertThat(isTextMessageVerificationEnabled).isFalse();
    }
  }

  @Test
  public void isPreAuthForSelfReportEnabled_preAuthorizeAfterSelfReportSetToFalse_returnsFalse() {
    FakeShadowResources resources = (FakeShadowResources) shadowOf(context.getResources());
    resources.addFakeResource(R.bool.enx_preAuthorizeAfterSelfReport, false);

    boolean isPreAuthForSelfReportEnabled =
        ShareDiagnosisFlowHelper.isPreAuthForSelfReportEnabled(context);

    assertThat(isPreAuthForSelfReportEnabled).isFalse();
  }

  @Test
  public void isPreAuthForSelfReportEnabled_preAuthorizeAfterSelfReportSetToTrue_returnsTrue() {
    FakeShadowResources resources = (FakeShadowResources) shadowOf(context.getResources());
    resources.addFakeResource(R.bool.enx_preAuthorizeAfterSelfReport, true);

    boolean isPreAuthForSelfReportEnabled =
        ShareDiagnosisFlowHelper.isPreAuthForSelfReportEnabled(context);

    assertThat(isPreAuthForSelfReportEnabled).isTrue();
  }

  /* Testing previous step API. */
  @Test
  public void isCodeNeeded_previousStep_alwaysReturnsStepBegin() {
    for (ShareDiagnosisFlow flow : ShareDiagnosisFlow.values()) {
      Step previousStep = ShareDiagnosisFlowHelper
          .getPreviousStep(Step.IS_CODE_NEEDED, EMPTY_DIAGNOSIS, flow, context);
      assertThat(previousStep).isEqualTo(Step.BEGIN);
    }
  }

  @Test
  public void getCode_previousStep_alwaysReturnsStepIsCodeNeeded() {
    for (ShareDiagnosisFlow flow : ShareDiagnosisFlow.values()) {
      Step previousStep = ShareDiagnosisFlowHelper
          .getPreviousStep(Step.GET_CODE, EMPTY_DIAGNOSIS, flow, context);
      assertThat(previousStep).isEqualTo(Step.IS_CODE_NEEDED);
    }
  }

  @Test
  public void code_previousStepAndNoDiagnosisAndSelfReportEnabled_returnsAsExpected() {
    FakeShadowResources resources = (FakeShadowResources) shadowOf(context.getResources());
    resources.addFakeResource(R.string.self_report_website_url, "example-url");

    Step previousStepDeepLinkFlow = ShareDiagnosisFlowHelper.getPreviousStep(
        Step.CODE, EMPTY_DIAGNOSIS, ShareDiagnosisFlow.DEEP_LINK, context);
    Step previousStepSelfReportFlow = ShareDiagnosisFlowHelper.getPreviousStep(
        Step.CODE, EMPTY_DIAGNOSIS, ShareDiagnosisFlow.SELF_REPORT, context);
    Step previousStepDefaultFlow = ShareDiagnosisFlowHelper.getPreviousStep(
        Step.CODE, EMPTY_DIAGNOSIS, ShareDiagnosisFlow.DEFAULT, context);

    assertThat(previousStepDeepLinkFlow).isEqualTo(Step.BEGIN);
    assertThat(previousStepSelfReportFlow).isEqualTo(Step.GET_CODE);
    assertThat(previousStepDefaultFlow).isEqualTo(Step.IS_CODE_NEEDED);
  }

  @Test
  public void code_previousStepAndDiagnosisExistsAndSelfReportEnabled_returnsAsExpected() {
    FakeShadowResources resources = (FakeShadowResources) shadowOf(context.getResources());
    resources.addFakeResource(R.string.self_report_website_url, "example-url");

    Step previousStepDeepLinkFlow = ShareDiagnosisFlowHelper.getPreviousStep(
        Step.CODE, DIAGNOSIS, ShareDiagnosisFlow.DEEP_LINK, context);
    Step previousStepSelfReportFlow = ShareDiagnosisFlowHelper.getPreviousStep(
        Step.CODE, DIAGNOSIS, ShareDiagnosisFlow.SELF_REPORT, context);
    Step previousStepDefaultFlow = ShareDiagnosisFlowHelper.getPreviousStep(
        Step.CODE, DIAGNOSIS, ShareDiagnosisFlow.DEFAULT, context);

    assertThat(previousStepDeepLinkFlow).isEqualTo(Step.BEGIN);
    assertThat(previousStepSelfReportFlow).isEqualTo(Step.GET_CODE);
    assertThat(previousStepDefaultFlow).isEqualTo(Step.BEGIN);
  }

  @Test
  public void code_previousStepAndSelfReportDisabled_alwaysReturnsStepBegin() {
    FakeShadowResources resources = (FakeShadowResources) shadowOf(context.getResources());
    resources.addFakeResource(R.string.self_report_website_url, "");

    Step previousStepNoDiagnosisDeepLinkFlow = ShareDiagnosisFlowHelper.getPreviousStep(
        Step.CODE, EMPTY_DIAGNOSIS, ShareDiagnosisFlow.DEEP_LINK, context);
    Step previousStepNoDiagnosisDefaultFlow = ShareDiagnosisFlowHelper.getPreviousStep(
        Step.CODE, EMPTY_DIAGNOSIS, ShareDiagnosisFlow.DEFAULT, context);
    Step previousStepDiagnosisExistsDeepLinkFlow = ShareDiagnosisFlowHelper.getPreviousStep(
        Step.CODE, DIAGNOSIS, ShareDiagnosisFlow.DEEP_LINK, context);
    Step previousStepDiagnosisExistsDefaultFlow = ShareDiagnosisFlowHelper.getPreviousStep(
        Step.CODE, DIAGNOSIS, ShareDiagnosisFlow.DEFAULT, context);

    assertThat(previousStepNoDiagnosisDeepLinkFlow).isEqualTo(Step.BEGIN);
    assertThat(previousStepNoDiagnosisDefaultFlow).isEqualTo(Step.BEGIN);
    assertThat(previousStepDiagnosisExistsDeepLinkFlow).isEqualTo(Step.BEGIN);
    assertThat(previousStepDiagnosisExistsDefaultFlow).isEqualTo(Step.BEGIN);
  }

  @Test
  public void upload_previousStep_returnsAsExpected() {
    assertThat(ShareDiagnosisFlowHelper.getPreviousStep(Step.UPLOAD,
        EMPTY_DIAGNOSIS, ShareDiagnosisFlow.DEFAULT, context))
        .isEqualTo(Step.CODE);

    assertThat(ShareDiagnosisFlowHelper.getPreviousStep(Step.UPLOAD,
        DIAGNOSIS_THAT_SKIPS_CODE_STEP, ShareDiagnosisFlow.DEFAULT, context))
        .isEqualTo(Step.BEGIN);
  }

  /* Testing next step API. */
  @Test
  public void begin_nextStep_codeStepNotSkippableAndNoDiagnosisAndSelfReportEnabled_returnsAsExpected() {
    FakeShadowResources resources = (FakeShadowResources) shadowOf(context.getResources());
    resources.addFakeResource(R.string.self_report_website_url, "self-report-url");

    Step nextStep = ShareDiagnosisFlowHelper.getNextStep(Step.BEGIN, EMPTY_DIAGNOSIS,
        ShareDiagnosisFlow.DEFAULT, /* showVaccinationQuestion= */false, context);
    Step nextStepDeepLinkFlow = ShareDiagnosisFlowHelper.getNextStep(Step.BEGIN, EMPTY_DIAGNOSIS,
        ShareDiagnosisFlow.DEEP_LINK, /* showVaccinationQuestion= */false, context);

    assertThat(nextStep).isEqualTo(Step.IS_CODE_NEEDED);
    assertThat(nextStepDeepLinkFlow).isEqualTo(Step.CODE);
  }

  @Test
  public void begin_nextStep_codeStepNotSkippableAndDiagnosisExistsAndSelfReportEnabled_alwaysReturnsStepCode() {
    FakeShadowResources resources = (FakeShadowResources) shadowOf(context.getResources());
    resources.addFakeResource(R.string.self_report_website_url, "self-report-url");

    Step nextStep = ShareDiagnosisFlowHelper.getNextStep(Step.BEGIN, DIAGNOSIS,
        ShareDiagnosisFlow.DEFAULT, /* showVaccinationQuestion= */false, context);
    Step nextStepDeepLinkFlow = ShareDiagnosisFlowHelper.getNextStep(Step.BEGIN, DIAGNOSIS,
        ShareDiagnosisFlow.DEEP_LINK, /* showVaccinationQuestion= */false, context);

    assertThat(nextStep).isEqualTo(Step.CODE);
    assertThat(nextStepDeepLinkFlow).isEqualTo(Step.CODE);
  }

  @Test
  public void begin_nextStep_codeStepNotSkippableAndSelfReportDisabled_alwaysReturnsStepCode() {
    FakeShadowResources resources = (FakeShadowResources) shadowOf(context.getResources());
    resources.addFakeResource(R.string.self_report_website_url, "");

    Step nextStepNoDiagnosis = ShareDiagnosisFlowHelper.getNextStep(Step.BEGIN, EMPTY_DIAGNOSIS,
        ShareDiagnosisFlow.DEFAULT, /* showVaccinationQuestion= */false, context);
    Step nextStepNoDiagnosisDeepLinkFlow = ShareDiagnosisFlowHelper.getNextStep(Step.BEGIN,
        EMPTY_DIAGNOSIS, ShareDiagnosisFlow.DEEP_LINK,
        /* showVaccinationQuestion= */false, context);
    Step nextStepDiagnosisExists = ShareDiagnosisFlowHelper.getNextStep(Step.BEGIN,
        DIAGNOSIS, ShareDiagnosisFlow.DEFAULT, /* showVaccinationQuestion= */false, context);
    Step nextStepDiagnosisExistsDeepLinkFlow = ShareDiagnosisFlowHelper.getNextStep(Step.BEGIN,
        DIAGNOSIS, ShareDiagnosisFlow.DEEP_LINK, /* showVaccinationQuestion= */false, context);

    assertThat(nextStepNoDiagnosis).isEqualTo(Step.CODE);
    assertThat(nextStepNoDiagnosisDeepLinkFlow).isEqualTo(Step.CODE);
    assertThat(nextStepDiagnosisExists).isEqualTo(Step.CODE);
    assertThat(nextStepDiagnosisExistsDeepLinkFlow).isEqualTo(Step.CODE);
  }

  @Test
  public void getCode_nextStep_alwaysReturnsStepCode() {
    for (ShareDiagnosisFlow flow : ShareDiagnosisFlow.values()) {
      Step nextStep = ShareDiagnosisFlowHelper.getNextStep(Step.GET_CODE, EMPTY_DIAGNOSIS,
          flow, /* showVaccinationQuestion= */false, context);
      assertThat(nextStep).isEqualTo(Step.CODE);
    }
  }

  @Test
  public void uploadNextStep_isSharedParamAbsent_alwaysReturnsNull() {
    for (ShareDiagnosisFlow flow : ShareDiagnosisFlow.values()) {
      assertThat(ShareDiagnosisFlowHelper.getNextStep(Step.UPLOAD, DIAGNOSIS, flow,
          /* showVaccinationQuestion= */ false, context))
          .isNull();
      assertThat(ShareDiagnosisFlowHelper.getNextStep(Step.UPLOAD, DIAGNOSIS, flow,
          /* showVaccinationQuestion= */ true, context))
          .isNull();
    }
  }

  @Test
  public void uploadNextStep_notShared_alwaysReturnsNotShared() {
    for (ShareDiagnosisFlow flow : ShareDiagnosisFlow.values()) {
      assertThat(ShareDiagnosisFlowHelper.getNextStep(Step.UPLOAD, DIAGNOSIS, flow,
          /* showVaccinationQuestion= */false, /* isShared= */false, context))
          .isEqualTo(Step.NOT_SHARED);
      assertThat(ShareDiagnosisFlowHelper.getNextStep(Step.UPLOAD, DIAGNOSIS, flow,
          /* showVaccinationQuestion= */true, /* isShared= */false, context))
          .isEqualTo(Step.NOT_SHARED);
    }
  }

  @Test
  public void uploadNextStep_sharedNotSelfReportedNoVacc_returnsShared() {
    ImmutableList<DiagnosisEntity> notSelfReportedDiagnoses = ImmutableList.of(
        DiagnosisEntity.newBuilder()
            .setTestResult(TestResult.CONFIRMED)
            .build(),
        DiagnosisEntity.newBuilder()
            .setTestResult(TestResult.LIKELY)
            .build(),
        DiagnosisEntity.newBuilder()
            .setTestResult(TestResult.NEGATIVE)
            .build()
    );

    for (DiagnosisEntity diagnosis : notSelfReportedDiagnoses) {
      assertThat(ShareDiagnosisFlowHelper.getNextStep(Step.UPLOAD, diagnosis,
          ShareDiagnosisFlow.DEFAULT, /* showVaccinationQuestion= */false, /* isShared= */true,
          context)).isEqualTo(Step.SHARED);
    }
  }

  @Test
  public void uploadNextStep_sharedNotSelfReportedButVacc_returnsVacc() {
    ImmutableList<DiagnosisEntity> notSelfReportedDiagnoses = ImmutableList.of(
        DiagnosisEntity.newBuilder()
            .setTestResult(TestResult.CONFIRMED)
            .build(),
        DiagnosisEntity.newBuilder()
            .setTestResult(TestResult.LIKELY)
            .build(),
        DiagnosisEntity.newBuilder()
            .setTestResult(TestResult.NEGATIVE)
            .build()
    );

    for (DiagnosisEntity diagnosis : notSelfReportedDiagnoses) {
      assertThat(ShareDiagnosisFlowHelper.getNextStep(Step.UPLOAD, diagnosis,
          ShareDiagnosisFlow.DEFAULT, /* showVaccinationQuestion= */true, /* isShared= */true,
          context)).isEqualTo(Step.VACCINATION);
    }
  }

  @Test
  public void uploadNextStep_sharedSelfReportedDiagnosisAndPreAuthIsOffAndVaccIsOn_returnsVacc() {
    // Set up resources needed...
    FakeShadowResources resources = (FakeShadowResources) shadowOf(context.getResources());
    resources.addFakeResource(R.string.self_report_website_url, "not-empty");
    resources.addFakeResource(R.bool.enx_preAuthorizeAfterSelfReport, false);
    // ...and shared self-reported diagnosis.
    DiagnosisEntity selfReportedDiagnosis = DiagnosisEntity.newBuilder()
        .setTestResult(TestResult.USER_REPORT)
        .build();

    assertThat(ShareDiagnosisFlowHelper.getNextStep(Step.UPLOAD, selfReportedDiagnosis,
        ShareDiagnosisFlow.SELF_REPORT, /* showVaccinationQuestion= */true, /* isShared= */true,
        context)).isEqualTo(Step.VACCINATION);
  }

  @Test
  public void uploadNextStep_sharedSelfReportedDiagnosisAndPreAuthIsOn_alwaysReturnsPreAuth() {
    // Set up resources needed...
    FakeShadowResources resources = (FakeShadowResources) shadowOf(context.getResources());
    resources.addFakeResource(R.string.self_report_website_url, "not-empty");
    resources.addFakeResource(R.bool.enx_preAuthorizeAfterSelfReport, true);
    // ...and shared self-reported diagnosis.
    DiagnosisEntity selfReportedDiagnosis = DiagnosisEntity.newBuilder()
        .setTestResult(TestResult.USER_REPORT)
        .build();

    assertThat(ShareDiagnosisFlowHelper.getNextStep(Step.UPLOAD, selfReportedDiagnosis,
        ShareDiagnosisFlow.SELF_REPORT, /* showVaccinationQuestion= */false, /* isShared= */true,
        context)).isEqualTo(Step.PRE_AUTH);
    assertThat(ShareDiagnosisFlowHelper.getNextStep(Step.UPLOAD, selfReportedDiagnosis,
        ShareDiagnosisFlow.SELF_REPORT, /* showVaccinationQuestion= */true, /* isShared= */true,
        context)).isEqualTo(Step.PRE_AUTH);
  }

  @Test
  public void preAuth_nextStep_vaccIsOff_returnsNull() {
    DiagnosisEntity selfReportedSharedDiagnosis = DiagnosisEntity.newBuilder()
        .setSharedStatus(Shared.SHARED)
        .setTestResult(TestResult.USER_REPORT)
        .build();

    assertThat(ShareDiagnosisFlowHelper.getNextStep(Step.PRE_AUTH, selfReportedSharedDiagnosis,
        ShareDiagnosisFlow.DEFAULT, /* showVaccinationQuestion= */false, context))
        .isEqualTo(null);
  }

  @Test
  public void preAuth_nextStep_vaccIsOn_returnsVacc() {
    DiagnosisEntity selfReportedSharedDiagnosis = DiagnosisEntity.newBuilder()
        .setSharedStatus(Shared.SHARED)
        .setTestResult(TestResult.USER_REPORT)
        .build();

    assertThat(ShareDiagnosisFlowHelper.getNextStep(Step.PRE_AUTH, selfReportedSharedDiagnosis,
        ShareDiagnosisFlow.DEFAULT, /* showVaccinationQuestion= */true, context))
        .isEqualTo(Step.VACCINATION);
  }

  /*  Testing Add Step X of Y */
  @Test
  public void getTotalNumberOfStepsInDiagnosisFlow_returnsAsExpected() {
    assertThat(ShareDiagnosisFlowHelper.getTotalNumberOfStepsInDiagnosisFlow(
        ShareDiagnosisFlow.SELF_REPORT)).isEqualTo(3);
    assertThat(ShareDiagnosisFlowHelper.getTotalNumberOfStepsInDiagnosisFlow(
        ShareDiagnosisFlow.DEEP_LINK)).isEqualTo(2);
    assertThat(ShareDiagnosisFlowHelper.getTotalNumberOfStepsInDiagnosisFlow(
        ShareDiagnosisFlow.DEFAULT)).isEqualTo(2);
  }

  @Test
  public void getNumberForCurrentStepInDiagnosisFlow_defaultFlow_returnsAsExpected() {
    assertThat(ShareDiagnosisFlowHelper.getNumberForCurrentStepInDiagnosisFlow(
        ShareDiagnosisFlow.DEFAULT, Step.CODE)).isEqualTo(1);
    assertThat(ShareDiagnosisFlowHelper.getNumberForCurrentStepInDiagnosisFlow(
        ShareDiagnosisFlow.DEFAULT, Step.UPLOAD)).isEqualTo(2);
  }

  @Test
  public void getNumberForCurrentStepInDiagnosisFlow_deepLinkFlow_returnsAsExpected() {
    assertThat(ShareDiagnosisFlowHelper.getNumberForCurrentStepInDiagnosisFlow(
        ShareDiagnosisFlow.DEEP_LINK, Step.CODE)).isEqualTo(1);
    assertThat(ShareDiagnosisFlowHelper.getNumberForCurrentStepInDiagnosisFlow(
        ShareDiagnosisFlow.DEEP_LINK, Step.UPLOAD)).isEqualTo(2);
  }

  @Test
  public void getNumberForCurrentStepInDiagnosisFlow_selfReportFlow_returnsAsExpected() {
    assertThat(ShareDiagnosisFlowHelper.getNumberForCurrentStepInDiagnosisFlow(
        ShareDiagnosisFlow.SELF_REPORT, Step.GET_CODE)).isEqualTo(1);
    assertThat(ShareDiagnosisFlowHelper.getNumberForCurrentStepInDiagnosisFlow(
        ShareDiagnosisFlow.SELF_REPORT, Step.CODE)).isEqualTo(2);
    assertThat(ShareDiagnosisFlowHelper.getNumberForCurrentStepInDiagnosisFlow(
        ShareDiagnosisFlow.SELF_REPORT, Step.UPLOAD)).isEqualTo(3);
  }
}
