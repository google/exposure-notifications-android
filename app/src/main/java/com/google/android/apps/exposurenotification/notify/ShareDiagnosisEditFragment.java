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

package com.google.android.apps.exposurenotification.notify;

import static com.google.android.apps.exposurenotification.notify.ShareDiagnosisActivity.SHARE_EXPOSURE_FRAGMENT_TAG;

import android.os.Bundle;
import android.os.Parcel;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.apps.exposurenotification.R;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.CalendarConstraints.DateValidator;
import com.google.android.material.datepicker.MaterialDatePicker;
import org.threeten.bp.Instant;
import org.threeten.bp.ZoneId;
import org.threeten.bp.ZonedDateTime;
import org.threeten.bp.format.DateTimeFormatter;
import org.threeten.bp.format.FormatStyle;

/** Page 2 of the adding a positive diagnosis flow */
public class ShareDiagnosisEditFragment extends Fragment {

  private static final String TAG = "ShareExposureEditFrag";

  private static final DateTimeFormatter formatter =
      DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM);

  private ShareDiagnosisViewModel shareDiagnosisViewModel;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_share_diagnosis_edit, parent, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
    shareDiagnosisViewModel =
        new ViewModelProvider(getActivity()).get(ShareDiagnosisViewModel.class);

    EditText dateEditText = view.findViewById(R.id.share_test_date);
    shareDiagnosisViewModel
        .getTestTimestampLiveData()
        .observe(
            getViewLifecycleOwner(),
            timestamp ->
                dateEditText.setText(timestamp != null ? formatter.format(timestamp) : ""));

    TextWatcher enableNextWhenFieldsAreFilledOut =
        new TextWatcher() {
          @Override
          public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

          @Override
          public void onTextChanged(CharSequence s, int start, int before, int count) {}

          @Override
          public void afterTextChanged(Editable s) {
            EditText identifierEditText = view.findViewById(R.id.share_test_identifier);
            shareDiagnosisViewModel.setTestIdentifier(identifierEditText.getText().toString());

            EditText dateEditText = view.findViewById(R.id.share_test_date);

            Button nextButton = view.findViewById(R.id.share_next_button);
            nextButton.setEnabled(
                !TextUtils.isEmpty(identifierEditText.getText())
                    && !TextUtils.isEmpty(dateEditText.getText()));
          }
        };

    dateEditText.addTextChangedListener(enableNextWhenFieldsAreFilledOut);
    dateEditText.setOnClickListener((v) -> showMaterialDatePicker());

    EditText identifierEditText = view.findViewById(R.id.share_test_identifier);
    identifierEditText.setText(shareDiagnosisViewModel.getTestIdentifierLiveData().getValue());
    identifierEditText.addTextChangedListener(enableNextWhenFieldsAreFilledOut);

    // "Next" button should be disabled until both fields are non-empty
    Button nextButton = view.findViewById(R.id.share_next_button);
    nextButton.setEnabled(false);
    nextButton.setOnClickListener(
        v ->
            getParentFragmentManager()
                .beginTransaction()
                .replace(
                    R.id.share_exposure_fragment,
                    new ShareDiagnosisReviewFragment(),
                    SHARE_EXPOSURE_FRAGMENT_TAG)
                .addToBackStack(null)
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                .commit());

    view.findViewById(R.id.share_cancel_button).setOnClickListener((v) -> cancelAction());

    view.findViewById(R.id.learn_more_button)
        .setOnClickListener(
            v ->
                getParentFragmentManager()
                    .beginTransaction()
                    .replace(
                        R.id.share_exposure_fragment,
                        new ShareDiagnosisLearnMoreFragment(),
                        SHARE_EXPOSURE_FRAGMENT_TAG)
                    .addToBackStack(null)
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                    .commit());

    View upButton = view.findViewById(android.R.id.home);
    upButton.setContentDescription(getString(R.string.navigate_up));
    upButton.setOnClickListener((v) -> navigateUp());
  }

  private void cancelAction() {
    requireActivity().finish();
  }

  private void navigateUp() {
    getParentFragmentManager().popBackStack();
  }

  private void showMaterialDatePicker() {
    @Nullable
    ZonedDateTime selectedZonedDateTime =
        shareDiagnosisViewModel.getTestTimestampLiveData().getValue();

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
          shareDiagnosisViewModel.onTestTimestampChanged(timestamp);
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
