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

import static android.app.Activity.RESULT_OK;
import static com.google.android.gms.nearby.exposurenotification.ExposureNotificationClient.ACTION_PRE_AUTHORIZE_RELEASE_PHONE_UNLOCKED;
import static com.google.android.gms.nearby.exposurenotification.ExposureNotificationClient.EXTRA_TEMPORARY_EXPOSURE_KEY_LIST;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender.SendIntentException;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.databinding.FragmentMatchingViewBinding;
import com.google.android.apps.exposurenotification.debug.KeysMatchingViewModel.ResolutionRequiredEvent;
import com.google.android.apps.exposurenotification.debug.KeysMatchingViewModel.ResolutionType;
import com.google.android.apps.exposurenotification.utils.RequestCodes;
import com.google.android.gms.nearby.exposurenotification.TemporaryExposureKey;
import com.google.android.material.snackbar.Snackbar;
import dagger.hilt.android.AndroidEntryPoint;
import java.util.List;

/**
 * Fragment for the view tab in {@link MatchingDebugActivity}.
 */
@AndroidEntryPoint
public class KeysMatchingFragment extends Fragment {

  private static final String TAG = "ViewKeysFragment";

  private FragmentMatchingViewBinding binding;
  private KeysMatchingViewModel keysMatchingViewModel;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
    binding = FragmentMatchingViewBinding.inflate(inflater, parent, false);
    return binding.getRoot();
  }

  @Override
  public void onViewCreated(View view, Bundle savedInstanceState) {
    keysMatchingViewModel =
        new ViewModelProvider(KeysMatchingFragment.this, getDefaultViewModelProviderFactory())
            .get(KeysMatchingViewModel.class);

    TemporaryExposureKeyAdapter temporaryExposureKeyAdapter = new TemporaryExposureKeyAdapter();
    final LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
    binding.temporaryExposureKeyRecyclerView.setLayoutManager(layoutManager);
    binding.temporaryExposureKeyRecyclerView.setAdapter(temporaryExposureKeyAdapter);

    keysMatchingViewModel
        .getTemporaryExposureKeysLiveData()
        .observe(
            getViewLifecycleOwner(),
            temporaryExposureKeys ->
                displayTemporaryExposureKeys(temporaryExposureKeys, temporaryExposureKeyAdapter));

    keysMatchingViewModel
        .getResolutionRequiredLiveEvent()
        .observe(
            this,
            event -> {
              try {
                event
                    .getException()
                    .getStatus()
                    .startResolutionForResult(
                        getActivity(), getRequestCodeForResolutionRequiredEvent(event));
              } catch (SendIntentException e) {
                Log.w(TAG, "Error calling startResolutionForResult", event.getException());
              }
            });

    keysMatchingViewModel
        .getApiErrorLiveEvent()
        .observe(
            getViewLifecycleOwner(),
            unused -> maybeShowSnackbar(getString(R.string.generic_error_message)));

    keysMatchingViewModel
        .getApiDisabledLiveEvent()
        .observe(
            getViewLifecycleOwner(),
            unused -> maybeShowSnackbar(getString(R.string.debug_matching_view_api_not_enabled)));

    keysMatchingViewModel
        .getInFlightResolutionLiveData()
        .observe(
            getViewLifecycleOwner(),
            inFlightResolution -> {
              if (inFlightResolution.hasInFlightResolution()) {
                if (inFlightResolution.getResolutionType()
                    == ResolutionType.GET_TEMPORARY_EXPOSURE_KEY_HISTORY) {
                  binding.debugMatchingViewRequestKeysButton.setEnabled(false);
                  binding.debugMatchingViewRequestKeysButton.setText("");
                  binding.debugMatchingViewRequestKeyProgressBar.setVisibility(View.VISIBLE);
                } else if (inFlightResolution.getResolutionType()
                    == ResolutionType.PREAUTHORIZE_TEMPORARY_EXPOSURE_KEY_RELEASE) {
                  binding.debugMatchingViewRequestKeysPreauthorizationButton.setEnabled(false);
                  binding.debugMatchingViewRequestKeysPreauthorizationButton.setText("");
                  binding.debugMatchingViewRequestKeysPreauthorizationProgressBar
                      .setVisibility(View.VISIBLE);
                } else if (inFlightResolution.getResolutionType()
                    == ResolutionType.GET_PREAUTHORIZED_TEMPORARY_EXPOSURE_KEY_HISTORY) {
                  binding.debugMatchingViewRequestKeysPreauthorizationGetButton.setEnabled(false);
                  binding.debugMatchingViewRequestKeysPreauthorizationGetButton.setText("");
                  binding.debugMatchingViewRequestKeysPreauthorizationGetProgressBar
                      .setVisibility(View.VISIBLE);
                }
              } else {
                binding.debugMatchingViewRequestKeysButton.setEnabled(true);
                binding.debugMatchingViewRequestKeysButton.setText(
                    R.string.debug_matching_view_get_keys_button_text);
                binding.debugMatchingViewRequestKeyProgressBar.setVisibility(View.INVISIBLE);

                binding.debugMatchingViewRequestKeysPreauthorizationButton.setEnabled(true);
                binding.debugMatchingViewRequestKeysPreauthorizationButton.setText(
                    R.string.debug_matching_view_get_keys_preauthorize_button_text);
                binding.debugMatchingViewRequestKeysPreauthorizationProgressBar
                    .setVisibility(View.INVISIBLE);

                binding.debugMatchingViewRequestKeysPreauthorizationGetButton.setEnabled(true);
                binding.debugMatchingViewRequestKeysPreauthorizationGetButton.setText(
                    R.string.debug_matching_view_get_keys_preauthorize_get_button_text);
                binding.debugMatchingViewRequestKeysPreauthorizationGetProgressBar
                    .setVisibility(View.INVISIBLE);
              }
            });
    keysMatchingViewModel
        .getWaitForKeyBroadcastsEvent()
        .observe(
            getViewLifecycleOwner(),
            unused -> {
              if (getContext() != null) {
                getContext()
                    .registerReceiver(
                        new BroadcastReceiver() {
                          @Override
                          public void onReceive(Context context, Intent intent) {
                            if (intent.hasExtra(EXTRA_TEMPORARY_EXPOSURE_KEY_LIST)) {
                              keysMatchingViewModel.handleTemporaryExposureKeys(
                                  intent.getParcelableArrayListExtra(
                                      EXTRA_TEMPORARY_EXPOSURE_KEY_LIST));
                            }
                            if (getContext() != null) {
                              getContext().unregisterReceiver(this);
                            }
                          }
                        }, new IntentFilter(ACTION_PRE_AUTHORIZE_RELEASE_PHONE_UNLOCKED));
              }
            });
    binding.debugMatchingViewRequestKeysButton.setOnClickListener(
        v -> keysMatchingViewModel.updateTemporaryExposureKeys());
    binding.debugMatchingViewRequestKeysPreauthorizationButton.setOnClickListener(
        v -> keysMatchingViewModel.requestPreAuthorizationOfTemporaryExposureKeyHistory());
    binding.debugMatchingViewRequestKeysPreauthorizationGetButton.setOnClickListener(
        v -> keysMatchingViewModel.requestPreAuthorizedReleaseOfTemporaryExposureKeys());
  }

  private void displayTemporaryExposureKeys(
      List<TemporaryExposureKey> keys, TemporaryExposureKeyAdapter adapter) {
    adapter.setTemporaryExposureKeys(keys);
    if (keys.isEmpty()) {
      binding.debugMatchingViewKeysSwitcher.setDisplayedChild(0);
    } else {
      binding.debugMatchingViewKeysSwitcher.setDisplayedChild(1);
    }
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    binding = null;
  }

  private int getRequestCodeForResolutionRequiredEvent(ResolutionRequiredEvent event) {
    if (event.getResolutionType() == ResolutionType.GET_TEMPORARY_EXPOSURE_KEY_HISTORY) {
      return RequestCodes.REQUEST_CODE_GET_TEMP_EXPOSURE_KEY_HISTORY;
    } else if (event.getResolutionType()
        == ResolutionType.PREAUTHORIZE_TEMPORARY_EXPOSURE_KEY_RELEASE) {
      return RequestCodes.REQUEST_CODE_PREAUTHORIZE_TEMP_EXPOSURE_KEY_RELEASE;
    }
    return RequestCodes.REQUEST_CODE_UNKNOWN;
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    if (requestCode == RequestCodes.REQUEST_CODE_GET_TEMP_EXPOSURE_KEY_HISTORY) {
      if (resultCode == RESULT_OK) {
        // Resolution completed. Submit data again.
        keysMatchingViewModel.startResolutionResultGetHistoryOk();
      } else {
        keysMatchingViewModel.startResolutionResultNotOk();
        maybeShowSnackbar(getString(R.string.debug_matching_view_rejected));
      }
    } else if (requestCode == RequestCodes.REQUEST_CODE_PREAUTHORIZE_TEMP_EXPOSURE_KEY_RELEASE) {
      if (resultCode == RESULT_OK) {
        keysMatchingViewModel.startResolutionResultPreauthorizationOk();
      } else {
        keysMatchingViewModel.startResolutionResultNotOk();
        maybeShowSnackbar(getString(R.string.debug_matching_view_preauthorization_rejected));
      }
    }
  }

  private void maybeShowSnackbar(String message) {
    View view = getView();
    if (view != null) {
      Snackbar.make(requireView(), message, Snackbar.LENGTH_SHORT).show();
    }
  }
}
