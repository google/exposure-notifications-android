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

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.os.Parcel;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.work.WorkInfo.State;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.KeyboardHelper;
import com.google.android.apps.exposurenotification.debug.VerificationCodeCreator.VerificationCode;
import com.google.android.apps.exposurenotification.privateanalytics.PrivateAnalyticsSettingsUtil;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.CalendarConstraints.DateValidator;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.snackbar.Snackbar;
import dagger.hilt.android.AndroidEntryPoint;
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

  private static final String TAG = "DebugHomeActivity";
  private static final DateTimeFormatter CODE_EXPIRY_FORMAT =
      DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneOffset.UTC);
  private static final DateTimeFormatter SYMPTOM_ONSET_DATE_FORMATTER =
      DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM);

  private DebugViewModel debugViewModel;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.fragment_debug_home);

    View upButton = findViewById(android.R.id.home);
    upButton.setContentDescription(getString(R.string.navigate_up));
    upButton.setOnClickListener((v) -> {
      KeyboardHelper.maybeHideKeyboard(getApplicationContext(), upButton);
      onBackPressed();
    });

    setupViewModels();
    setupVersionInfo();
    setupTestTypeDropDown();
    setupSymptomOnSetDatePicker();
    setupMatchingControls();
    setupVerificationCodeControls();
    setupRoamingControls();
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
    AutoCompleteTextView testTypeDropDown = findViewById(R.id.test_type_dropdown);
    String[] testTypesArray =
        new String[]{
            getResources().getString(R.string.debug_test_type_confirmed),
            getResources().getString(R.string.debug_test_type_likely),
            getResources().getString(R.string.debug_test_type_negative)
        };
    ArrayAdapter<CharSequence> adapter =
        new ArrayAdapter<>(this, R.layout.item_input_mode, testTypesArray);
    testTypeDropDown.setAdapter(adapter);
    testTypeDropDown.setText(testTypesArray[0], false);
  }

  private void setupSymptomOnSetDatePicker() {
    EditText symptomOnSetDateEditText = findViewById(R.id.symptom_onset_date);
    debugViewModel
        .getSymptomOnSetDateLiveData()
        .observe(
            this,
            timestamp ->
                symptomOnSetDateEditText.setText(
                    timestamp != null ? SYMPTOM_ONSET_DATE_FORMATTER.format(timestamp) : ""));
    symptomOnSetDateEditText.setOnClickListener((v) -> showMaterialDatePicker());
  }

  private void setupVersionInfo() {
    TextView appVersion = findViewById(R.id.debug_app_version);
    appVersion.setText(
        getString(R.string.debug_version_app,
            getVersionNameForPackage(getPackageName())));

    TextView gmsVersion = findViewById(R.id.debug_gms_version);
    gmsVersion.setText(getString(R.string.debug_version_gms,
        getVersionNameForPackage(GoogleApiAvailability.GOOGLE_PLAY_SERVICES_PACKAGE)));

    TextView enVersion = findViewById(R.id.debug_en_version);
    debugViewModel.getEnModuleVersionLiveData()
        .observe(this, version -> {
          if (TextUtils.isEmpty(version)) {
            enVersion.setVisibility(View.GONE);
          } else {
            enVersion.setText(getString(R.string.debug_version_en, version));
            enVersion.setVisibility(View.VISIBLE);
          }
        });
  }

  private void setupMatchingControls() {
    Button manualMatching = findViewById(R.id.debug_matching_manual_button);
    manualMatching.setOnClickListener(
        v -> startActivity(new Intent(this, MatchingDebugActivity.class)));

    Button enqueueProvide = findViewById(R.id.debug_provide_now);
    enqueueProvide.setOnClickListener(
        v -> {
          debugViewModel.provideKeys();
          maybeShowSnackbar(getString(R.string.debug_provide_keys_enqueued));
        });

    TextView jobStatus = findViewById(R.id.debug_matching_job_status);
    debugViewModel
        .getProvideDiagnosisKeysWorkLiveData()
        .observe(
            this,
            workInfos -> {
              if (workInfos == null) {
                Log.e(TAG, "workInfos is null");
                jobStatus.setText(getString(R.string.debug_job_status,
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
                  Log.e(TAG, "workInfos.size() != 1");
                  jobStatusText = getString(
                      R.string.debug_job_status, getString(R.string.debug_job_status_error));
                  break;
              }
              jobStatus.setText(jobStatusText);
            });

    Button submitPrivateAnalytics = findViewById(R.id.debug_submit_private_analytics_button);
    submitPrivateAnalytics.setOnClickListener(
        v -> {
          debugViewModel.submitPrivateAnalytics();
          maybeShowSnackbar(getString(R.string.debug_provide_keys_enqueued));
        });

    Button clearKeyStoreButton = findViewById(R.id.debug_private_analytics_clear_key_store_button);
    clearKeyStoreButton.setOnClickListener(
        v -> {
          debugViewModel.clearKeyStore();
          maybeShowSnackbar(getString(R.string.debug_provide_keys_enqueued));
        });

    View privateAnalyticsContainer = findViewById(R.id.debug_private_analytics_container);
    privateAnalyticsContainer.setVisibility(
        PrivateAnalyticsSettingsUtil.isPrivateAnalyticsSupported() ? TextView.VISIBLE
            : TextView.GONE);
  }

  private void setupVerificationCodeControls() {
    Button createVerificationCode = findViewById(R.id.debug_create_verification_code_button);
    createVerificationCode.setOnClickListener(x -> {
      AutoCompleteTextView testTypeDropDown = findViewById(R.id.test_type_dropdown);
      debugViewModel.createVerificationCode(testTypeDropDown.getText().toString());
    });
    View codeContainer = findViewById(R.id.debug_verification_code_container);
    TextView code = findViewById(R.id.debug_verification_code);
    TextView expiry = findViewById(R.id.debug_verification_code_expiry);
    code.setOnClickListener(
        v -> {
          ClipboardManager clipboard =
              (ClipboardManager) v.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
          ClipData clip = ClipData.newPlainText(code.getText(), code.getText());
          clipboard.setPrimaryClip(clip);
          Snackbar.make(
              v,
              getString(
                  R.string.debug_snackbar_copied_text,
                  code.getText()),
              Snackbar.LENGTH_SHORT)
              .show();
        });

    debugViewModel.getVerificationCodeLiveData().observe(
        this,
        verificationCode -> {
          if (verificationCode == null) {
            return;
          }
          if (verificationCode.equals(VerificationCode.EMPTY)) {
            codeContainer.setVisibility(View.GONE);
            return;
          }
          codeContainer.setVisibility(View.VISIBLE);
          code.setText(verificationCode.code());
          expiry.setText(getApplicationContext()
              .getString(R.string.debug_verification_code_expiry,
                  CODE_EXPIRY_FORMAT.format(verificationCode.expiry())));
        });
  }

  private void setupRoamingControls() {
    EditText countryCodeEditText = findViewById(R.id.debug_roaming_country_code_input);
    Button insertButton = findViewById(R.id.debug_roaming_country_code_insert_button);
    insertButton.setOnClickListener(
        (v) -> debugViewModel.markCountryCodesSeen(countryCodeEditText.getText().toString()));
    Button clearButton = findViewById(R.id.debug_roaming_country_code_clear_button);
    clearButton.setOnClickListener((v) -> debugViewModel.clearCountryCodes());
  }

  /**
   * Gets the version name for a specified package. Returns a debug string if not found.
   */
  private String getVersionNameForPackage(String packageName) {
    try {
      return getPackageManager().getPackageInfo(packageName, 0).versionName;
    } catch (NameNotFoundException e) {
      Log.e(TAG, "Couldn't get the app version", e);
    }
    return getString(R.string.debug_version_not_available);
  }

  private void maybeShowSnackbar(String message) {
    View rootView = findViewById(android.R.id.content);
    if (rootView != null) {
      Snackbar.make(rootView, message, Snackbar.LENGTH_LONG).show();
    }
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
