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

package com.google.android.apps.exposurenotification.activities;

import static android.app.Activity.RESULT_OK;
import static com.google.android.apps.exposurenotification.activities.ShareExposureActivity.SHARE_EXPOSURE_FRAGMENT_TAG;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.os.Bundle;
import android.os.Parcel;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.activities.utils.RequestCodes;
import com.google.android.apps.exposurenotification.common.AppExecutors;
import com.google.android.apps.exposurenotification.common.TaskToFutureAdapter;
import com.google.android.apps.exposurenotification.nearby.ExposureNotificationClientWrapper;
import com.google.android.apps.exposurenotification.network.DiagnosisKey;
import com.google.android.apps.exposurenotification.network.DiagnosisKeys;
import com.google.android.apps.exposurenotification.storage.PositiveDiagnosisViewModel;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.nearby.exposurenotification.ExposureNotificationStatusCodes;
import com.google.android.gms.nearby.exposurenotification.TemporaryExposureKey;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.CalendarConstraints.DateValidator;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.snackbar.Snackbar;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.threeten.bp.Duration;
import org.threeten.bp.Instant;
import org.threeten.bp.ZoneId;
import org.threeten.bp.ZonedDateTime;
import org.threeten.bp.format.DateTimeFormatter;
import org.threeten.bp.format.FormatStyle;

/**
 * Page 1 of the Verify Diagnosis & Notify Others flow
 */
public class ShareExposureStartFragment extends Fragment {

  private static final String TAG = "ShareExposureStartFrag";
  // The timeout for calls to the Google Play Services APIs. TODO: should be adjustable by flag.
  private static final Duration API_TIMEOUT = Duration.ofSeconds(10);
  private boolean hasInFlightResolution;
  private WeakReference<View> viewReference;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_share_exposure_start, parent, false);
  }

  @SuppressWarnings("UnstableApiUsage")
  @Override
  public void onViewCreated(@NotNull View view, Bundle savedInstanceState) {
    viewReference = new WeakReference<>(view);

    EditText identifierEditText = view.findViewById(R.id.share_test_identifier);
    EditText dateEditText = view.findViewById(R.id.share_test_date);
    Button nextButton = view.findViewById(R.id.share_next_button);
    Button cancelButton = view.findViewById(R.id.share_done_button);
    View upButton = view.findViewById(android.R.id.home);

    // "Next" button should be disabled until both fields are non-empty
    nextButton.setEnabled(false);
    identifierEditText.addTextChangedListener(enableNextWhenFieldsAreFilledOut);
    dateEditText.addTextChangedListener(enableNextWhenFieldsAreFilledOut);

    PositiveDiagnosisViewModel viewModel =
        new ViewModelProvider(this, getDefaultViewModelProviderFactory())
            .get(PositiveDiagnosisViewModel.class);

    nextButton.setOnClickListener(v -> submitData());

    DateTimeFormatter formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM);
    viewModel
        .getTestTimestamp()
        .observe(
            getViewLifecycleOwner(),
            timestamp ->
                dateEditText.setText(timestamp != null ? formatter.format(timestamp) : ""));
    dateEditText.setOnClickListener((v) -> showMaterialDatePicker(viewModel));

    cancelButton.setOnClickListener((v) -> navigateUp());

    upButton.setContentDescription(getString(R.string.navigate_up));
    upButton.setOnClickListener((v) -> navigateUp());
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    if (requestCode == RequestCodes.REQUEST_CODE_GET_TEMP_EXPOSURE_KEY_HISTORY) {
      hasInFlightResolution = false;
      if (resultCode == RESULT_OK) {
        // Resolution completed. Submit data again.
        submitData();
      }
    }
  }

  private void submitData() {
    View view = requireView();
    EditText identifierEditText = view.findViewById(R.id.share_test_identifier);
    EditText dateEditText = view.findViewById(R.id.share_test_date);
    // Both fields are required.
    if (TextUtils.isEmpty(identifierEditText.getText())) {
      Snackbar.make(requireView(), R.string.missing_test_identifier, Snackbar.LENGTH_LONG)
          .show();
      return;
    }
    if (TextUtils.isEmpty(dateEditText.getText())) {
      Snackbar.make(requireView(), R.string.missing_test_date, Snackbar.LENGTH_LONG).show();
      return;
    }
    ListenableFuture<Void> submitDiagnosis =
        FluentFuture.from(getRecentKeys())
            .transformAsync(this::submitKeysToService,
                AppExecutors.getBackgroundExecutor())
            .transformAsync((unused) -> insertDiagnosis(),
                AppExecutors.getBackgroundExecutor());
    // TODO: We should probably also record that the submission succeeded in the stored diagnosis.
    Futures.addCallback(
        submitDiagnosis, applyResultToUi,
        ContextCompat.getMainExecutor(requireContext()));
  }

  /**
   * Inserts a diagnosis into the local database.
   *
   * <p>TODO: Accept and store the user-supplied diagnosis date.
   */
  private ListenableFuture<Void> insertDiagnosis() {
    PositiveDiagnosisViewModel positiveDiagnosisViewModel =
        new ViewModelProvider(this, getDefaultViewModelProviderFactory())
            .get(PositiveDiagnosisViewModel.class);
    return positiveDiagnosisViewModel.insertOrUpdatePositiveDiagnosisEntity();
  }

  /** Gets recent (initially 14 days) Temporary Exposure Keys from Google Play Services. */
  private ListenableFuture<List<TemporaryExposureKey>> getRecentKeys() {
    return TaskToFutureAdapter.getFutureWithTimeout(
        new ExposureNotificationClientWrapper(requireContext()).getTemporaryExposureKeyHistory(),
        API_TIMEOUT.toMillis(),
        TimeUnit.MILLISECONDS,
        AppExecutors.getScheduledExecutor());
  }

  /**
   * Submits the given Temporary Exposure Keys to the key sharing service, designating them as
   * Diagnosis Keys.
   */
  private ListenableFuture<Void> submitKeysToService(List<TemporaryExposureKey> recentKeys) {
    List<DiagnosisKey> keys = new ArrayList<>();
    for (TemporaryExposureKey k : recentKeys) {
      keys.add(
          DiagnosisKey.newBuilder()
              .setKeyBytes(k.getKeyData())
              .setIntervalNumber(k.getRollingStartIntervalNumber())
              .build());
    }
    return new DiagnosisKeys(requireContext()).upload(keys);
  }

  private TextWatcher enableNextWhenFieldsAreFilledOut =
      new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {}

        @Override
        public void afterTextChanged(Editable s) {
          EditText identifierEditText = requireView().findViewById(R.id.share_test_identifier);
          EditText dateEditText = requireView().findViewById(R.id.share_test_date);
          Button nextButton = requireView().findViewById(R.id.share_next_button);
          nextButton.setEnabled(
              !TextUtils.isEmpty(identifierEditText.getText())
                  && !TextUtils.isEmpty(dateEditText.getText()));
        }
      };

  private FutureCallback<Void> applyResultToUi =
      new FutureCallback<Void>() {
        @Override
        public void onSuccess(Void result) {
          getParentFragmentManager()
              .beginTransaction()
              .replace(
                  R.id.share_exposure_fragment,
                  new ShareExposureCompleteFragment(),
                  SHARE_EXPOSURE_FRAGMENT_TAG)
              .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
              .commit();
        }

        @Override
        public void onFailure(Throwable exception) {
          if (!(exception instanceof ApiException) || hasInFlightResolution) {
            Log.e(TAG, "Unknown error or has hasInFlightResolution", exception);
            // Reset hasInFlightResolution so we don't block future resolution if user wants to
            // try again manually
            showError();
            return;
          }
          ApiException apiException = (ApiException) exception;
          if (apiException.getStatusCode() ==
              ExposureNotificationStatusCodes.RESOLUTION_REQUIRED) {
            try {
              hasInFlightResolution = true;
              apiException.getStatus()
                  .startResolutionForResult(requireActivity(),
                      RequestCodes.REQUEST_CODE_GET_TEMP_EXPOSURE_KEY_HISTORY);
            } catch (SendIntentException e) {
              Log.w(TAG, "Error calling startResolutionForResult, sending to settings",
                  apiException);
              showError();
            }
          } else {
            Log.w(TAG, "No RESOLUTION_REQUIRED in result, sending to settings", apiException);
            showError();
          }
        }
      };

  private void showError() {
    hasInFlightResolution = false;
    if (viewReference != null) {
      View rootview = viewReference.get();
      if (rootview != null) {
        Snackbar.make(rootview, R.string.generic_error_message, Snackbar.LENGTH_LONG).show();
      }
    }
  }

  private void navigateUp() {
    Activity activity = getActivity();
    if (activity != null) {
      ((AppCompatActivity) activity).onSupportNavigateUp();
    }
  }

  private void showMaterialDatePicker(@NonNull final PositiveDiagnosisViewModel viewModel) {
    @Nullable ZonedDateTime selectedZonedDateTime = viewModel.getTestTimestamp().getValue();

    @NonNull
    Instant selectedInstant =
        selectedZonedDateTime != null ? selectedZonedDateTime.toInstant() : Instant.now();

    MaterialDatePicker<Long> dialog =
        MaterialDatePicker.Builder.datePicker()
            .setCalendarConstraints(
                new CalendarConstraints.Builder()
                    .setEnd(System.currentTimeMillis())
                    .setValidator(NOW_OR_PAST_DATE_VALIDATOR)
                    .build())
            .setSelection(selectedInstant.toEpochMilli())
            .build();
    dialog.addOnPositiveButtonClickListener(
        selection -> {
          ZonedDateTime timestamp = Instant.ofEpochMilli(selection).atZone(ZoneId.of("UTC"));
          viewModel.onTestTimestampChanged(timestamp);
        });
    dialog.show(getChildFragmentManager(), "date_picker");
  }

  private static final DateValidator NOW_OR_PAST_DATE_VALIDATOR =
      new DateValidator() {
        @Override
        public boolean isValid(long date) {
          return date <= System.currentTimeMillis();
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
