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

import android.content.Intent;
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
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.utils.RequestCodes;
import com.google.android.material.snackbar.Snackbar;

/** Fragment for the view tab in {@link MatchingDebugActivity}. */
public class KeysMatchingFragment extends Fragment {

  private static final String TAG = "ViewKeysFragment";

  private KeysMatchingViewModel keysMatchingViewModel;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_matching_view, parent, false);
  }

  @Override
  public void onViewCreated(View view, Bundle savedInstanceState) {
    keysMatchingViewModel =
        new ViewModelProvider(KeysMatchingFragment.this, getDefaultViewModelProviderFactory())
            .get(KeysMatchingViewModel.class);

    TemporaryExposureKeyAdapter temporaryExposureKeyAdapter = new TemporaryExposureKeyAdapter();
    final LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
    RecyclerView recyclerView = view.findViewById(R.id.temporary_exposure_key_recycler_view);
    recyclerView.setLayoutManager(layoutManager);
    recyclerView.setAdapter(temporaryExposureKeyAdapter);

    keysMatchingViewModel
        .getTemporaryExposureKeysLiveData()
        .observe(
            getViewLifecycleOwner(),
            temporaryExposureKeys ->
                temporaryExposureKeyAdapter.setTemporaryExposureKeys(temporaryExposureKeys));

    keysMatchingViewModel
        .getResolutionRequiredLiveEvent()
        .observe(
            this,
            apiException -> {
              try {
                apiException
                    .getStatus()
                    .startResolutionForResult(
                        getActivity(), RequestCodes.REQUEST_CODE_GET_TEMP_EXPOSURE_KEY_HISTORY);
              } catch (SendIntentException e) {
                Log.w(TAG, "Error calling startResolutionForResult", apiException);
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

    keysMatchingViewModel.updateTemporaryExposureKeys();
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    if (requestCode == RequestCodes.REQUEST_CODE_GET_TEMP_EXPOSURE_KEY_HISTORY) {
      if (resultCode == RESULT_OK) {
        // Resolution completed. Submit data again.
        keysMatchingViewModel.startResolutionResultOk();
      } else {
        maybeShowSnackbar(getString(R.string.debug_matching_view_rejected));
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
