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

import static android.app.Activity.RESULT_OK;
import static com.google.android.apps.exposurenotification.notify.ShareDiagnosisActivity.SHARE_EXPOSURE_FRAGMENT_TAG;

import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.utils.RequestCodes;
import com.google.android.material.snackbar.Snackbar;
import org.threeten.bp.format.DateTimeFormatter;
import org.threeten.bp.format.FormatStyle;

/**
 * Page for reviewing adding or updating a positive diagnosis flows
 *
 * <p><ul>
 * <li> Page 3 for the adding a positive diagnosis flow
 * <li> Page 2 for the view a positive diagnosis flow for updating the share status
 * </ul><p>
 */
public class ShareDiagnosisReviewFragment extends Fragment {

  private static final String TAG = "ShareExposureReviewFrag";

  private static final DateTimeFormatter dateTimeFormatter =
      DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM);

  private ShareDiagnosisViewModel shareDiagnosisViewModel;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_share_diagnosis_review, parent, false);
  }

  @Override
  public void onViewCreated(View view, Bundle savedInstanceState) {
    shareDiagnosisViewModel =
        new ViewModelProvider(getActivity()).get(ShareDiagnosisViewModel.class);

    TextView date = view.findViewById(R.id.share_review_date);
    shareDiagnosisViewModel
        .getTestTimestampLiveData()
        .observe(
            getViewLifecycleOwner(),
            timestamp -> {
              if (timestamp != null) {
                date.setText(dateTimeFormatter.format(timestamp));
              }
            });

    shareDiagnosisViewModel
        .getByIdLiveData(shareDiagnosisViewModel.getExistingIdLiveData().getValue())
        .observe(
            getViewLifecycleOwner(),
            entity -> {
              if (entity != null) {
                shareDiagnosisViewModel.onTestTimestampChanged(entity.getTestTimestamp());
              }
            });

    Button shareButton = view.findViewById(R.id.share_share_button);
    shareButton.setOnClickListener(v -> shareAction());
    shareDiagnosisViewModel
        .getInFlightResolutionLiveData()
        .observe(
            getViewLifecycleOwner(),
            hasInFlightResolution -> {
              if (hasInFlightResolution) {
                shareButton.setEnabled(false);
              } else {
                shareButton.setEnabled(true);
              }
            });

    shareDiagnosisViewModel
        .getSnackbarSingleLiveEvent()
        .observe(getViewLifecycleOwner(), message -> maybeShowSnackbar(message));

    shareDiagnosisViewModel
        .getResolutionRequiredLiveEvent()
        .observe(
            getViewLifecycleOwner(),
            apiException -> {
              try {
                shareDiagnosisViewModel.setInflightResolution(true);
                apiException.getStatus().startResolutionForResult(
                    requireActivity(), RequestCodes.REQUEST_CODE_GET_TEMP_EXPOSURE_KEY_HISTORY);
              } catch (SendIntentException e) {
                Log.w(TAG, "Error calling startResolutionForResult", apiException);
                maybeShowSnackbar(getString(R.string.generic_error_message));
              }
            });

    shareDiagnosisViewModel
        .getSharedLiveEvent()
        .observe(
            getViewLifecycleOwner(),
            shared -> {
              if (!shared) {
                Toast.makeText(getContext(), R.string.share_error, Toast.LENGTH_LONG).show();
              }
              shareDiagnosisViewModel.save(shared);
            });

    shareDiagnosisViewModel
        .getSavedLiveEvent()
        .observe(
            getViewLifecycleOwner(),
            shared -> {
              if (shared) {
                transitionToFragment(new ShareDiagnosisSharedFragment());
              } else {
                transitionToFragment(new ShareDiagnosisNotSharedFragment());
              }
            });

    Button cancelButton = view.findViewById(R.id.share_cancel_button);
    cancelButton.findViewById(R.id.share_cancel_button).setOnClickListener((v) -> cancelAction());

    View upButton = view.findViewById(android.R.id.home);
    upButton.setContentDescription(getString(R.string.navigate_up));
    upButton.setOnClickListener((v) -> navigateUp());
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    if (requestCode == RequestCodes.REQUEST_CODE_GET_TEMP_EXPOSURE_KEY_HISTORY) {
      shareDiagnosisViewModel.setInflightResolution(false);
      if (resultCode == RESULT_OK) {
        // Okay to share, submit data.
        shareDiagnosisViewModel.share();
      } else {
        // Not okay to share, just store for later.
        shareDiagnosisViewModel.save(false);
      }
    }
  }

  private void shareAction() {
    shareDiagnosisViewModel.share();
  }

  private void cancelAction() {
    requireActivity().finish();
  }

  private void navigateUp() {
    getParentFragmentManager().popBackStack();
  }

  private void transitionToFragment(Fragment fragment) {
    // Remove previous fragment from the stack if it is there so we can't go back.
    getParentFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);

    getParentFragmentManager()
        .beginTransaction()
        .replace(R.id.share_exposure_fragment, fragment, SHARE_EXPOSURE_FRAGMENT_TAG)
        .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
        .commit();
  }

  /**
   * Shows a snackbar with a given message if the {@link View} is visible.
   */
  private void maybeShowSnackbar(String message) {
    View rootView = getView();
    if (rootView != null) {
      Snackbar.make(rootView, message, Snackbar.LENGTH_LONG).show();
    }
  }
}
