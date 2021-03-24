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
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.AbstractTextWatcher;
import com.google.android.apps.exposurenotification.common.KeyboardHelper;
import com.google.android.apps.exposurenotification.common.StringUtils;
import com.google.android.apps.exposurenotification.databinding.FragmentMatchingProvideBinding;
import com.google.android.apps.exposurenotification.debug.TemporaryExposureKeyEncodingHelper.DecodeException;
import com.google.android.gms.nearby.exposurenotification.TemporaryExposureKey;
import com.google.android.material.snackbar.Snackbar;
import com.google.common.io.BaseEncoding;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import dagger.hilt.android.AndroidEntryPoint;
import java.util.ArrayList;
import org.threeten.bp.Duration;

/**
 * Fragment for the provide tab in matching debug.
 */
@AndroidEntryPoint
public class ProvideMatchingFragment extends Fragment {

  private static final String TAG = "ProvideMatchingFragment";

  private static final BaseEncoding BASE16 = BaseEncoding.base16().lowerCase();

  private static final Duration INTERVAL_DURATION = Duration.ofMinutes(10);

  private FragmentMatchingProvideBinding binding;
  private ProvideMatchingViewModel provideMatchingViewModel;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
    binding = FragmentMatchingProvideBinding.inflate(inflater, parent, false);
    return binding.getRoot();
  }

  @Override
  public void onViewCreated(View view, Bundle savedInstanceState) {
    provideMatchingViewModel =
        new ViewModelProvider(this, getDefaultViewModelProviderFactory())
            .get(ProvideMatchingViewModel.class);

    provideMatchingViewModel
        .getSnackbarLiveEvent()
        .observe(getViewLifecycleOwner(), this::maybeShowSnackbar);

    // Submit section
    binding.provideButton.setOnClickListener(
        (v) -> {
          KeyboardHelper.maybeHideKeyboard(getContext(), view);
          provideMatchingViewModel.provideSingleAction();
        });

    // Single
    binding.inputSingleKey.addTextChangedListener(
        new AbstractTextWatcher() {
          @Override
          public void afterTextChanged(Editable s) {
            if (!TextUtils.isEmpty(s.toString())) {
              provideMatchingViewModel.setSingleInputKey(s.toString());
            }
          }
        });

    binding.scanButton.setOnClickListener(
        v -> IntentIntegrator.forSupportFragment(this)
            .setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
            .setOrientationLocked(false)
            .setBarcodeImageEnabled(false).initiateScan());

    binding.inputSingleIntervalNumber.addTextChangedListener(
        new AbstractTextWatcher() {
          @Override
          public void afterTextChanged(Editable s) {
            if (!TextUtils.isEmpty(s.toString())) {
              provideMatchingViewModel.setSingleInputIntervalNumber(tryParseInteger(s.toString()));
            } else {
              provideMatchingViewModel.setSingleInputIntervalNumber(0);
            }
          }
        });

    provideMatchingViewModel.getSingleInputIntervalNumberLiveData().observe(getViewLifecycleOwner(),
        intervalNumber -> {
          if (intervalNumber == 0) {
            binding.inputSingleIntervalNumberLayout.setSuffixText("");
          } else {
            binding.inputSingleIntervalNumberLayout.setSuffixText(StringUtils
                .epochTimestampToLongUTCDateTimeString(
                    intervalNumber * INTERVAL_DURATION.toMillis(),
                    requireContext().getResources().getConfiguration().locale));
          }
        });

    binding.inputSingleRollingPeriod.addTextChangedListener(
        new AbstractTextWatcher() {
          @Override
          public void afterTextChanged(Editable s) {
            if (!TextUtils.isEmpty(s.toString())) {
              provideMatchingViewModel.setSingleInputRollingPeriod(tryParseInteger(s.toString()));
            }
          }
        });

    binding.inputSingleTransmissionRiskLevel.addTextChangedListener(
        new AbstractTextWatcher() {
          @Override
          public void afterTextChanged(Editable s) {
            if (!TextUtils.isEmpty(s.toString())) {
              provideMatchingViewModel.setSingleInputTransmissionRiskLevel(
                  tryParseInteger(s.toString()));
            }
          }
        });

    binding.inputSingleOnsetDayDropdown.addTextChangedListener(
        new AbstractTextWatcher() {
          @Override
          public void afterTextChanged(Editable s) {
            if (!TextUtils.isEmpty(s.toString())) {
              provideMatchingViewModel.setSingleInputDaysSinceOnsetOfSymptomsLiveData(
                  tryParseInteger(s.toString()));
            }
          }
        });

    binding.inputSingleReportTypeDropdown.addTextChangedListener(
        new AbstractTextWatcher() {
          @Override
          public void afterTextChanged(Editable s) {
            if (!TextUtils.isEmpty(s.toString())) {
              int reportType = 1;
              if (getResources().getString(R.string.debug_test_type_confirmed)
                  .equals(s.toString())) {
                reportType = 1;
              } else if (getResources().getString(R.string.debug_test_type_likely)
                  .equals(s.toString())) {
                reportType = 2;
              } else if (getResources().getString(R.string.debug_test_type_negative)
                  .equals(s.toString())) {
                reportType = 5;
              }
              provideMatchingViewModel.setSingleInputReportTypeLiveData(reportType);
            }
          }
        });

    provideMatchingViewModel
        .getSigningKeyInfoLiveData()
        .observe(
            getViewLifecycleOwner(),
            keyInfo -> {
              setTextAndCopyAction(binding.keyfileSignaturePublicKey, keyInfo.publicKeyBase64());
              setTextAndCopyAction(binding.keyfileSignaturePackageName, keyInfo.packageName());
              setTextAndCopyAction(binding.keyfileSignatureId, keyInfo.keyId());
              setTextAndCopyAction(binding.keyfileSignatureVersion, keyInfo.keyVersion());
            });
    setupReportTypeDropDown();
    setupDaysSinceOnsetOfSymptoms();
  }

  private void setupReportTypeDropDown() {
    String[] testTypesArray =
        new String[]{
            getResources().getString(R.string.debug_test_type_confirmed),
            getResources().getString(R.string.debug_test_type_likely),
            getResources().getString(R.string.debug_test_type_negative)
        };
    ArrayAdapter<CharSequence> adapter =
        new ArrayAdapter<>(this.getActivity(), R.layout.item_input_mode, testTypesArray);
    binding.inputSingleReportTypeDropdown.setAdapter(adapter);
    binding.inputSingleReportTypeDropdown.setText(testTypesArray[0], false);
  }

  private void setupDaysSinceOnsetOfSymptoms() {
    ArrayList<CharSequence> days = new ArrayList<>();
    for (int i = -14; i <= 14; i++) {
      days.add(Integer.toString(i));
    }
    ArrayAdapter<CharSequence> adapter =
        new ArrayAdapter<>(this.getActivity(), R.layout.item_input_mode, days);
    binding.inputSingleOnsetDayDropdown.setAdapter(adapter);
    binding.inputSingleOnsetDayDropdown.setText("0", false);
  }

  private void setTextAndCopyAction(TextView view, String text) {
    view.setText(text);
    view.setOnClickListener(
        v -> {
          ClipboardManager clipboard =
              (ClipboardManager) v.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
          ClipData clip = ClipData.newPlainText(text, text);
          clipboard.setPrimaryClip(clip);
          Snackbar.make(
              v,
              getString(
                  R.string.debug_snackbar_copied_text,
                  StringUtils.truncateWithEllipsis(text, 35)),
              Snackbar.LENGTH_SHORT)
              .show();
        });
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode == IntentIntegrator.REQUEST_CODE) {
      Log.d(TAG, "onActivityResult with requestCode=IntentIntegrator.REQUEST_CODE");
      IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
      if (result != null && result.getContents() != null) {
        try {
          TemporaryExposureKey temporaryExposureKey =
              TemporaryExposureKeyEncodingHelper.decodeSingle(result.getContents());

          binding.inputSingleKey.setText(BASE16.encode(temporaryExposureKey.getKeyData()));
          binding.inputSingleIntervalNumber.setText(
              Integer.toString(temporaryExposureKey.getRollingStartIntervalNumber()));
          binding.inputSingleRollingPeriod.setText(
              Integer.toString(temporaryExposureKey.getRollingPeriod()));
          binding.inputSingleTransmissionRiskLevel.setText(
              Integer.toString(temporaryExposureKey.getTransmissionRiskLevel()));
        } catch (DecodeException e) {
          Log.e(TAG, "Decode error", e);
          maybeShowSnackbar(getString(R.string.debug_matching_provide_scan_error));
        }
      }
    } else {
      Log.d(TAG, String.format("onActivityResult unknown requestCode=%d", requestCode));
    }
    super.onActivityResult(requestCode, resultCode, data);
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    binding = null;
  }

  /**
   * Tries to parse an integer string, if not returns 0 and shows a snackbar.
   */
  private int tryParseInteger(String integer) {
    try {
      return Integer.parseInt(integer);
    } catch (NumberFormatException e) {
      Log.e(TAG, "Couldn't parse number", e);
      maybeShowSnackbar(getString(R.string.debug_matching_single_parse_error));
    }
    return 0;
  }

  private void maybeShowSnackbar(String message) {
    View view = getView();
    if (view != null) {
      Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG).show();
    }
  }
}
