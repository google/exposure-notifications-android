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
import static com.google.android.apps.exposurenotification.nearby.ProvideDiagnosisKeysWorker.DEFAULT_API_TIMEOUT;

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
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.activities.utils.RequestCodes;
import com.google.android.apps.exposurenotification.common.AppExecutors;
import com.google.android.apps.exposurenotification.common.TaskToFutureAdapter;
import com.google.android.apps.exposurenotification.nearby.ExposureNotificationClientWrapper;
import com.google.android.apps.exposurenotification.network.DiagnosisKey;
import com.google.android.apps.exposurenotification.network.DiagnosisKeys;
import com.google.android.apps.exposurenotification.storage.PositiveDiagnosisEntity;
import com.google.android.apps.exposurenotification.storage.PositiveDiagnosisViewModel;
import com.google.android.apps.exposurenotification.storage.ZonedDateTimeTypeConverter;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.nearby.exposurenotification.ExposureNotificationStatusCodes;
import com.google.android.gms.nearby.exposurenotification.TemporaryExposureKey;
import com.google.android.material.snackbar.Snackbar;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;
import org.threeten.bp.ZonedDateTime;
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
public class ShareExposureReviewFragment extends Fragment {

  private static final String TAG = "ShareExposureReviewFrag";

  private static final String KEY_DATE = "KEY_DATE";
  private static final String KEY_POSITIVE_DIAGNOSIS_ID = "KEY_POSITIVE_DIAGNOSIS_ID";
  private static final long UNKNOWN_POSITIVE_DIAGNOSIS_ID = -1;

  private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter
      .ofLocalizedDate(FormatStyle.MEDIUM);
  // The timeout for calls to the Google Play Services APIs.
  private boolean hasInFlightResolution;

  /**
   * Creates an instance for adding a new entity flow
   */
  static ShareExposureReviewFragment newInstanceForAdd(ZonedDateTime zonedDateTime) {
    ShareExposureReviewFragment fragment = new ShareExposureReviewFragment();
    Bundle args = new Bundle();
    args.putString(KEY_DATE, ZonedDateTimeTypeConverter.fromOffsetDateTime(zonedDateTime));
    fragment.setArguments(args);
    return fragment;
  }

  /**
   * Creates an instance for updating an existing entity flow
   */
  static ShareExposureReviewFragment newInstanceForUpdate(
      PositiveDiagnosisEntity positiveDiagnosisEntity) {
    ShareExposureReviewFragment fragment = new ShareExposureReviewFragment();
    Bundle args = new Bundle();
    args.putString(KEY_DATE,
        ZonedDateTimeTypeConverter.fromOffsetDateTime(positiveDiagnosisEntity.getTestTimestamp()));
    args.putLong(KEY_POSITIVE_DIAGNOSIS_ID, positiveDiagnosisEntity.getId());
    fragment.setArguments(args);
    return fragment;
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_share_exposure_review, parent, false);
  }

  @Override
  public void onViewCreated(View view, Bundle savedInstanceState) {
    Button shareButton = view.findViewById(R.id.share_share_button);
    shareButton.setOnClickListener(v -> trySubmitSaveAndProgress());

    Button cancelButton = view.findViewById(R.id.share_cancel_button);
    cancelButton.setOnClickListener((v) -> cancelAction());

    View upButton = view.findViewById(android.R.id.home);
    upButton.setContentDescription(getString(R.string.navigate_up));
    upButton.setOnClickListener((v) -> navigateUp());

    if (getTimestamp() != null) {
      TextView date = view.findViewById(R.id.share_review_date);
      date.setText(dateTimeFormatter.format(getTimestamp()));
    }
  }

  @Nullable
  private ZonedDateTime getTimestamp() {
    if (getArguments() != null) {
      return ZonedDateTimeTypeConverter.toOffsetDateTime(getArguments().getString(KEY_DATE));
    }
    return null;
  }

  private long getPositiveDiagnosisId() {
    if (getArguments() != null) {
      return getArguments().getLong(KEY_POSITIVE_DIAGNOSIS_ID, UNKNOWN_POSITIVE_DIAGNOSIS_ID);
    }
    return UNKNOWN_POSITIVE_DIAGNOSIS_ID;
  }

  private void cancelAction() {
    requireActivity().finish();
  }

  private void navigateUp() {
    getParentFragmentManager().popBackStack();
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    if (requestCode == RequestCodes.REQUEST_CODE_GET_TEMP_EXPOSURE_KEY_HISTORY) {
      hasInFlightResolution = false;
      // Resolution completed.
      if (resultCode == RESULT_OK) {
        // Okay to share, submit data.
        trySubmitSaveAndProgress();
      } else {
        // Not okay to share, just store for later.
        saveAndProgress();
      }
    }
  }

  private void trySubmitSaveAndProgress() {
    FluentFuture<Boolean> getKeysAndSubmitToService =
        FluentFuture.from(getRecentKeys())
            .transformAsync(this::submitKeysToService, AppExecutors.getBackgroundExecutor())
            .transformAsync((shared) -> {
                  if (!shared) {
                    requireActivity().runOnUiThread(
                        () -> Toast
                            .makeText(requireContext(), R.string.share_error, Toast.LENGTH_LONG)
                            .show());
                  }
                  // Insert or update the diagnosis with shared state.
                  // Then returns the original shared state for UI callback handling.
                  return Futures.transform(
                      insertOrUpdateDiagnosis(shared),
                      unused -> shared,
                      AppExecutors.getLightweightExecutor());
                },
                AppExecutors.getBackgroundExecutor());

    Futures.addCallback(
        getKeysAndSubmitToService, trySubmitSaveAndProgressCallback,
        ContextCompat.getMainExecutor(requireContext()));
  }

  private void saveAndProgress() {
    ListenableFuture<Void> submitDiagnosis = insertOrUpdateDiagnosis(false);
    Futures.addCallback(submitDiagnosis, saveAndProgressCallback,
        ContextCompat.getMainExecutor(requireContext()));
  }

  /**
   * Inserts a diagnosis into the local database.
   */
  private ListenableFuture<Void> insertOrUpdateDiagnosis(boolean testShared) {
    PositiveDiagnosisViewModel positiveDiagnosisViewModel =
        new ViewModelProvider(this, getDefaultViewModelProviderFactory())
            .get(PositiveDiagnosisViewModel.class);
    long positiveDiagnosisId = getPositiveDiagnosisId();
    if (positiveDiagnosisId == UNKNOWN_POSITIVE_DIAGNOSIS_ID) {
      // Add flow so add the entity
      return positiveDiagnosisViewModel.upsertAsync(getTimestamp(), testShared);
    } else {
      // Update flow so just update the shared status
      return positiveDiagnosisViewModel.markSharedForIdAsync(positiveDiagnosisId, testShared);
    }
  }

  /**
   * Gets recent (initially 14 days) Temporary Exposure Keys from Google Play Services.
   */
  private ListenableFuture<List<TemporaryExposureKey>> getRecentKeys() {
    return TaskToFutureAdapter.getFutureWithTimeout(
        ExposureNotificationClientWrapper.get(requireContext()).getTemporaryExposureKeyHistory(),
        DEFAULT_API_TIMEOUT.toMillis(),
        TimeUnit.MILLISECONDS,
        AppExecutors.getScheduledExecutor());
  }

  /**
   * Submits the given Temporary Exposure Keys to the key sharing service, designating them as
   * Diagnosis Keys.
   *
   * @return a {@link ListenableFuture} of type {@link Boolean} of successfully submitted state
   */
  private ListenableFuture<Boolean> submitKeysToService(List<TemporaryExposureKey> recentKeys) {
    ImmutableList.Builder<DiagnosisKey> builder = new Builder<>();
    for (TemporaryExposureKey k : recentKeys) {
      builder.add(
          DiagnosisKey.newBuilder()
              .setKeyBytes(k.getKeyData())
              .setIntervalNumber(k.getRollingStartIntervalNumber())
              .build());
    }
    return FluentFuture.from(new DiagnosisKeys(requireContext()).upload(builder.build()))
        .transform(v -> {
          // Successfully submitted
          return true;
        }, AppExecutors.getLightweightExecutor())
        .catching(
            ApiException.class,
            (e) -> {
              // Not successfully submitted,
              return false;
            },
            AppExecutors.getLightweightExecutor());
  }

  private final FutureCallback<Boolean> trySubmitSaveAndProgressCallback =
      new FutureCallback<Boolean>() {
        @Override
        public void onSuccess(Boolean shared) {
          // Remove previous fragment from the stack if it is there so we can't go back.
          getParentFragmentManager()
              .popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);

          Fragment nextFragment;
          if (shared) {
            nextFragment = new ShareExposureSharedFragment();
          } else {
            nextFragment = new ShareExposureNotSharedFragment();
          }
          getParentFragmentManager()
              .beginTransaction()
              .replace(
                  R.id.share_exposure_fragment,
                  nextFragment,
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

  private final FutureCallback<Void> saveAndProgressCallback =
      new FutureCallback<Void>() {
        @Override
        public void onSuccess(@NullableDecl Void result) {
          // Remove previous fragment from the stack if it is there so we can't go back.
          getParentFragmentManager()
              .popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);

          getParentFragmentManager()
              .beginTransaction()
              .replace(
                  R.id.share_exposure_fragment,
                  new ShareExposureNotSharedFragment(),
                  SHARE_EXPOSURE_FRAGMENT_TAG)
              .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
              .commit();
        }

        @Override
        public void onFailure(Throwable t) {
          showError();
        }
      };

  private void showError() {
    hasInFlightResolution = false;
    View rootview = getView();
    if (rootview != null) {
      Snackbar.make(rootview, R.string.generic_error_message, Snackbar.LENGTH_LONG).show();
    }
  }

}
