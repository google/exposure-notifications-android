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

package com.google.android.apps.exposurenotification.debug;

import static com.google.android.apps.exposurenotification.debug.VerifiableSmsActivity.EXTRA_VERIFICATION_CODE;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.os.Parcel;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.work.WorkInfo;
import androidx.work.WorkInfo.State;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.KeyboardHelper;
import com.google.android.apps.exposurenotification.common.SnackbarUtil;
import com.google.android.apps.exposurenotification.common.logging.Logger;
import com.google.android.apps.exposurenotification.databinding.FragmentDebugHomeBinding;
import com.google.android.apps.exposurenotification.debug.VerificationCodeCreator.VerificationCode;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.libraries.privateanalytics.PrivateAnalyticsMetric;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.CalendarConstraints.DateValidator;
import com.google.android.material.datepicker.MaterialDatePicker;
import dagger.hilt.android.AndroidEntryPoint;
import java.util.List;
import java.util.Locale;
import org.threeten.bp.Instant;
import org.threeten.bp.LocalDate;
import org.threeten.bp.ZoneId;
import org.threeten.bp.ZoneOffset;
import org.threeten.bp.ZonedDateTime;
import org.threeten.bp.format.DateTimeFormatter;
import org.threeten.bp.format.FormatStyle;

@AndroidEntryPoint
public final class DebugActivity extends AppCompatActivity {

  private static final Logger logger = Logger.getLogger("DebugHomeActivity");
  private static final DateTimeFormatter CODE_EXPIRY_FORMAT =
      DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneOffset.UTC);
  private static final DateTimeFormatter SYMPTOM_ONSET_DATE_FORMATTER =
      DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM);

  private FragmentDebugHomeBinding binding;
  private DebugViewModel debugViewModel;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    binding = FragmentDebugHomeBinding.inflate(getLayoutInflater());
    setContentView(binding.getRoot());

    binding.home.setContentDescription(getString(R.string.navigate_up));
    binding.home.setOnClickListener((v) -> {
      KeyboardHelper.maybeHideKeyboard(getApplicationContext(), binding.home);
      onBackPressed();
    });

    setupViewModels();
    setupVersionInfo();
    setupTestTypeDropDown();
    setupSymptomOnSetDatePicker();
    setupMatchingControls();
    setupVerificationCodeControls();
    setupRoamingControls();
    setupPrivateAnalyticsControls();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    binding = null;
  }

  private void setupViewModels() {
    debugViewModel =
        new ViewModelProvider(this, getDefaultViewModelProviderFactory())
            .get(DebugViewModel.class);
    debugViewModel
        .getSnackbarSingleLiveEvent()
        .observe(
            this,
            this::maybeShowSnackbar);
  }

  private void setupTestTypeDropDown() {
    String[] testTypesArray =
        new String[]{
            getResources().getString(R.string.debug_test_type_confirmed),
            getResources().getString(R.string.debug_test_type_likely),
            getResources().getString(R.string.debug_test_type_negative)
        };
    ArrayAdapter<CharSequence> adapter =
        new ArrayAdapter<>(this, R.layout.item_input_mode, testTypesArray);
    binding.testTypeDropdown.setAdapter(adapter);
    binding.testTypeDropdown.setText(testTypesArray[0], false);
  }

  private void setupSymptomOnSetDatePicker() {
    debugViewModel
        .getSymptomOnSetDateLiveData()
        .observe(
            this,
            timestamp ->
                binding.symptomOnsetDate.setText(
                    timestamp != null ? SYMPTOM_ONSET_DATE_FORMATTER.format(timestamp) : ""));
    binding.symptomOnsetDate.setOnClickListener(v -> showMaterialDatePicker());
  }

  private void setupVersionInfo() {
    binding.debugAppVersion.setText(
        getString(R.string.debug_version_app,
            getVersionNameForPackage(getPackageName())));

    binding.debugGmsVersion.setText(getString(R.string.debug_version_gms,
        getVersionNameForPackage(GoogleApiAvailability.GOOGLE_PLAY_SERVICES_PACKAGE)));

    debugViewModel.getEnModuleVersionLiveData()
        .observe(this, version -> {
          if (TextUtils.isEmpty(version)) {
            binding.debugEnVersion.setVisibility(View.GONE);
          } else {
            binding.debugEnVersion.setText(getString(R.string.debug_version_en, version));
            binding.debugEnVersion.setVisibility(View.VISIBLE);
          }
        });
  }

  private void setupMatchingControls() {
    binding.debugMatchingManualButton.setOnClickListener(
        v -> startActivity(new Intent(this, MatchingDebugActivity.class)));

    debugViewModel.getProvidedDiagnosisKeyHexToLogLiveData()
        .observe(this, binding.keyToLog::setText);

    // Control button state depending on the number of manual "provide" jobs.
    // If any "DebugProvide" job is still running, keep the button disabled.
    debugViewModel.getDebugProvideStateLiveData().observe(this,
        workInfos -> {
            if (workInfos == null) {
              binding.debugProvideNow.setEnabled(true);
              return;
            }
            for (WorkInfo workInfo : workInfos) {
              if (!workInfo.getState().isFinished()) {
                binding.debugProvideNow.setEnabled(false);
                return;
              }
            }
            binding.debugProvideNow.setEnabled(true);
          });

    binding.debugProvideNow.setOnClickListener(
        v -> {
          debugViewModel.setProvidedDiagnosisKeyHexToLog(binding.keyToLog.getText().toString());
          debugViewModel.provideKeys();
          maybeShowSnackbar(getString(R.string.debug_provide_keys_enqueued));
        });

    debugViewModel
        .getProvideDiagnosisKeysWorkLiveData()
        .observe(
            this,
            workInfos -> {
              if (workInfos == null) {
                logger.e("workInfos is null");
                binding.debugMatchingJobStatus.setText(getString(R.string.debug_job_status,
                    getString(R.string.debug_job_status_error)));
                return;
              }
              String jobStatusText;
              switch (workInfos.size()) {
                case 0:
                  jobStatusText = getString(
                      R.string.debug_job_status,
                      getString(R.string.debug_job_status_not_scheduled));
                  break;
                case 1:
                  if (workInfos.get(0).getState() == State.ENQUEUED) {
                    jobStatusText = getString(R.string.debug_job_status,
                        getString(R.string.debug_job_status_scheduled));
                  } else if (workInfos.get(0).getState() == State.RUNNING) {
                    jobStatusText = getString(R.string.debug_job_status,
                        getString(R.string.debug_job_status_running));
                  } else {
                    jobStatusText = getString(
                        R.string.debug_job_status, getString(R.string.debug_job_status_error));
                  }
                  break;
                default:
                  logger.e("workInfos.size() != 1");
                  jobStatusText = getString(
                      R.string.debug_job_status, getString(R.string.debug_job_status_error));
                  break;
              }
              binding.debugMatchingJobStatus.setText(jobStatusText);
            });
  }

  private void setupPrivateAnalyticsControls() {
    if (debugViewModel.shouldDisplayPrivateAnalyticsControls()) {
      binding.debugSubmitPrivateAnalyticsButton.setOnClickListener(
          v -> {
            debugViewModel.submitPrivateAnalytics();
            maybeShowSnackbar(getString(R.string.debug_provide_keys_enqueued));
          });

      binding.debugPrivateAnalyticsClearKeyStoreButton.setOnClickListener(
          v -> {
            debugViewModel.clearKeyStore();
            maybeShowSnackbar(getString(R.string.debug_provide_keys_enqueued));
          });

      binding.debugPrivateAnalyticsContainer.setVisibility(View.VISIBLE);
      PrivateAnalyticsMetricAdapter privateAnalyticsMetricAdapter =
          new PrivateAnalyticsMetricAdapter();
      List<PrivateAnalyticsMetric> privateAnalytics = debugViewModel.getPrivateAnalyticsMetrics();
      privateAnalyticsMetricAdapter.setPrivateAnalyticsMetrics(privateAnalytics);
      binding.debugPrivateAnalyticsMetricsRecycler
          .setLayoutManager(new LinearLayoutManager(getBaseContext()));
      binding.debugPrivateAnalyticsMetricsRecycler.setAdapter(privateAnalyticsMetricAdapter);
    }
  }

  private void setupVerificationCodeControls() {
    binding.debugCreateVerificationCodeButton.setOnClickListener(x -> {
      debugViewModel.createVerificationCode(binding.testTypeDropdown.getText().toString());
    });

    binding.debugVerificationCode.setOnClickListener(
        v -> {
          ClipboardManager clipboard =
              (ClipboardManager) v.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
          ClipData clip = ClipData.newPlainText(
              binding.debugVerificationCode.getText(), binding.debugVerificationCode.getText());
          clipboard.setPrimaryClip(clip);
          SnackbarUtil.maybeShowRegularSnackbar(
              v,
              getString(
                  R.string.debug_snackbar_copied_text,
                  binding.debugVerificationCode.getText()));
        });

    binding.debugCreateVerifiableSmsButton.setEnabled(false);
    debugViewModel.getVerificationCodeLiveData().observe(
        this,
        verificationCode -> {
          if (verificationCode == null) {
            return;
          }
          if (verificationCode.equals(VerificationCode.EMPTY)) {
            binding.debugVerificationCodeContainer.setVisibility(View.GONE);
            binding.debugCreateVerifiableSmsButton.setEnabled(false);
            return;
          }
          binding.debugVerificationCodeContainer.setVisibility(View.VISIBLE);
          binding.debugVerificationCode.setText(verificationCode.code());
          binding.debugVerificationCodeExpiry.setText(getApplicationContext()
              .getString(R.string.debug_verification_code_expiry,
                  CODE_EXPIRY_FORMAT.format(verificationCode.expiry())));
          binding.debugCreateVerifiableSmsButton.setEnabled(true);
          binding.debugCreateVerifiableSmsButton.setOnClickListener(
              v -> {
                if (VerificationCode.EMPTY.equals(verificationCode)) {
                  maybeShowSnackbar(getString(R.string.debug_verifiable_sms_no_code_error));
                } else {
                  Intent intent = new Intent(this, VerifiableSmsActivity.class);
                  intent.putExtra(EXTRA_VERIFICATION_CODE, verificationCode);
                  startActivity(intent);
                }
              });
        });
  }

  private void setupRoamingControls() {
    binding.debugRoamingCountryCodeInsertButton.setOnClickListener(
        v -> debugViewModel.markCountryCodesSeen(
            binding.debugRoamingCountryCodeInput.getText().toString()));
    binding.debugRoamingCountryCodeClearButton.setOnClickListener(
        v -> debugViewModel.clearCountryCodes());
  }

  /**
   * Gets the version name for a specified package. Returns a debug string if not found.
   */
  private String getVersionNameForPackage(String packageName) {
    try {
      return getPackageManager().getPackageInfo(packageName, 0).versionName;
    } catch (NameNotFoundException e) {
      logger.e("Couldn't get the app version", e);
    }
    return getString(R.string.debug_version_not_available);
  }

  private void maybeShowSnackbar(String message) {
    View rootView = findViewById(android.R.id.content);
    SnackbarUtil.maybeShowRegularSnackbar(rootView, message);
  }

  private void showMaterialDatePicker() {
    Locale.setDefault(getResources().getConfiguration().locale);

    @Nullable
    ZonedDateTime selectedZonedDateTime =
        debugViewModel.getSymptomOnSetDateLiveData().getValue();

    @NonNull
    Instant selectedInstant =
        selectedZonedDateTime != null ? selectedZonedDateTime.toInstant() : Instant.now();

    MaterialDatePicker<Long> dialog =
        MaterialDatePicker.Builder.datePicker()
            .setCalendarConstraints(
                new CalendarConstraints.Builder()
                    .setEnd(System.currentTimeMillis())
                    .setValidator(IS_VALID_SYMPTOM_DATE)
                    .build())
            .setSelection(selectedInstant.toEpochMilli())
            .build();
    dialog.addOnPositiveButtonClickListener(
        selection -> {
          ZonedDateTime timestamp = Instant.ofEpochMilli(selection).atZone(ZoneId.of("UTC"));
          debugViewModel.onSymptomOnSetDateChanged(timestamp);
        });
    dialog.show(getSupportFragmentManager(), "date_picker");
  }

  private static boolean isNotInFuture(long date) {
    return date <= System.currentTimeMillis();
  }

  private static boolean isWithinLast14Days(long date) {
    long minAllowedDateWithinLast14Days =
        LocalDate.now(ZoneOffset.UTC)
            .atStartOfDay(ZoneOffset.UTC)
            .minusDays(14)
            .toInstant()
            .toEpochMilli();
    return date >= minAllowedDateWithinLast14Days && isNotInFuture(date);
  }

  private static final DateValidator IS_VALID_SYMPTOM_DATE =
      new DateValidator() {

        @Override
        public boolean isValid(long date) {
          return isWithinLast14Days(date);
        }

        @Override
        public int describeContents() {
          // Return no-op value. This validator has no state to describe
          return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
          // No-op. This validator has no state to parcelize
        }
      };
}
