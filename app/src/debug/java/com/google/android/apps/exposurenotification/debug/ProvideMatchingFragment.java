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

import static android.app.Activity.RESULT_CANCELED;
import static android.app.Activity.RESULT_OK;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.ViewFlipper;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.StringUtils;
import com.google.android.apps.exposurenotification.debug.TemporaryExposureKeyEncodingHelper.DecodeException;
import com.google.android.gms.nearby.exposurenotification.TemporaryExposureKey;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;
import com.google.common.collect.Lists;
import com.google.common.io.BaseEncoding;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/** Fragment for the provide tab in matching debug. */
public class ProvideMatchingFragment extends Fragment {

  private static final String TAG = "ProvideMatchingFragment";

  private static final BaseEncoding BASE16 = BaseEncoding.base16().lowerCase();

  private static final int QR_SCAN_REQUEST_CODE = 1234;
  private static final int FILE_REQUEST_CODE = 1235;

  private static final int POS_SINGLE = 0;
  private static final int POS_FILE = 1;

  private static final String TEMP_INPUT_FILENAME = "input-file.zip";

  private ProvideMatchingViewModel provideMatchingViewModel;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_matching_provide, parent, false);
  }

  @Override
  public void onViewCreated(View view, Bundle savedInstanceState) {
    provideMatchingViewModel =
        new ViewModelProvider(this, getDefaultViewModelProviderFactory())
            .get(ProvideMatchingViewModel.class);

    provideMatchingViewModel
        .getSnackbarLiveEvent()
        .observe(getViewLifecycleOwner(), message -> maybeShowSnackbar(message));

    // Submit section
    MaterialButton provideButton = view.findViewById(R.id.provide_button);
    EditText token = view.findViewById(R.id.token);
    token.addTextChangedListener(
        new TextWatcher() {
          @Override
          public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

          @Override
          public void onTextChanged(CharSequence s, int start, int before, int count) {}

          @Override
          public void afterTextChanged(Editable s) {
            if (!TextUtils.isEmpty(s.toString())) {
              provideMatchingViewModel.setToken(s.toString());
            }
          }
        });

    // Input sections
    ViewFlipper viewFlipper = view.findViewById(R.id.input_view_flipper);
    provideMatchingViewModel
        .getDisplayedChildLiveData()
        .observe(
            getViewLifecycleOwner(),
            pos -> {
              viewFlipper.setDisplayedChild(pos);
              switch (pos) {
                case POS_SINGLE:
                  provideButton.setOnClickListener(
                      (v) -> provideMatchingViewModel.provideSingleAction());
                  break;
                case POS_FILE:
                  provideButton.setOnClickListener(
                      (v) -> provideMatchingViewModel.provideFileAction());
                  break;
                default:
                  break;
              }
            });

    AutoCompleteTextView inputModeDropDown = view.findViewById(R.id.input_method);
    List<String> provideInputMethods =
        Lists.newArrayList(
            getString(R.string.debug_matching_provide_single),
            getString(R.string.debug_matching_provide_file));
    ArrayAdapter<String> adapter =
        new ArrayAdapter<>(getContext(), R.layout.item_input_mode, provideInputMethods);
    inputModeDropDown.setAdapter(adapter);
    inputModeDropDown.setText(provideInputMethods.get(0), false);
    inputModeDropDown.setOnItemClickListener(
        (parent, arg1, pos, id) -> provideMatchingViewModel.setDisplayedChild(pos));

    // 1. Single
    EditText inputSingleKey = view.findViewById(R.id.input_single_key);
    inputSingleKey.addTextChangedListener(
        new TextWatcher() {
          @Override
          public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

          @Override
          public void onTextChanged(CharSequence s, int start, int before, int count) {}

          @Override
          public void afterTextChanged(Editable s) {
            if (!TextUtils.isEmpty(s.toString())) {
              provideMatchingViewModel.setSingleInputKey(s.toString());
            }
          }
        });

    Button inputSingleScanButton = view.findViewById(R.id.scan_button);
    inputSingleScanButton.setOnClickListener(
        v ->
            startActivityForResult(
                new Intent(requireContext(), QRScannerActivity.class), QR_SCAN_REQUEST_CODE));

    EditText inputSingleIntervalNumber = view.findViewById(R.id.input_single_interval_number);
    inputSingleIntervalNumber.addTextChangedListener(
        new TextWatcher() {
          @Override
          public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

          @Override
          public void onTextChanged(CharSequence s, int start, int before, int count) {}

          @Override
          public void afterTextChanged(Editable s) {
            if (!TextUtils.isEmpty(s.toString())) {
              provideMatchingViewModel.setSingleInputIntervalNumber(tryParseInteger(s.toString()));
            }
          }
        });

    EditText inputSingleRollingPeriod = view.findViewById(R.id.input_single_rolling_period);
    inputSingleRollingPeriod.addTextChangedListener(
        new TextWatcher() {
          @Override
          public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

          @Override
          public void onTextChanged(CharSequence s, int start, int before, int count) {}

          @Override
          public void afterTextChanged(Editable s) {
            if (!TextUtils.isEmpty(s.toString())) {
              provideMatchingViewModel.setSingleInputRollingPeriod(tryParseInteger(s.toString()));
            }
          }
        });

    EditText inputSingleTransmissionRiskLevel =
        view.findViewById(R.id.input_single_transmission_risk_level);
    inputSingleTransmissionRiskLevel.addTextChangedListener(
        new TextWatcher() {
          @Override
          public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

          @Override
          public void onTextChanged(CharSequence s, int start, int before, int count) {}

          @Override
          public void afterTextChanged(Editable s) {
            if (!TextUtils.isEmpty(s.toString())) {
              provideMatchingViewModel.setSingleInputTransmissionRiskLevel(
                  tryParseInteger(s.toString()));
            }
          }
        });

    // 2. File
    EditText inputFile = view.findViewById(R.id.input_file);
    provideMatchingViewModel
        .getFileInputLiveData()
        .observe(
            getViewLifecycleOwner(),
            file -> {
              if (file == null) {
                inputFile.setText("");
              } else {
                inputFile.setText(file.toString());
              }
            });
    TextInputLayout inputFileLayout = view.findViewById(R.id.input_file_layout);
    inputFileLayout.setEndIconOnClickListener(
        v -> {
          Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
          intent.setType("*/*");
          intent.addCategory(Intent.CATEGORY_OPENABLE);
          startActivityForResult(
              Intent.createChooser(
                  intent, getString(R.string.debug_matching_input_file_chooser_title)),
              FILE_REQUEST_CODE);
        });

    provideMatchingViewModel
        .getSigningKeyInfoLiveData()
        .observe(
            getViewLifecycleOwner(),
            keyInfo -> {
              TextView signaturePublicKey = view.findViewById(R.id.keyfile_signature_public_key);
              TextView signaturePackage = view.findViewById(R.id.keyfile_signature_package_name);
              TextView signatureId = view.findViewById(R.id.keyfile_signature_id);
              TextView signatureVersion = view.findViewById(R.id.keyfile_signature_version);
              setTextAndCopyAction(signaturePublicKey, keyInfo.publicKeyBase64());
              setTextAndCopyAction(signaturePackage, keyInfo.packageName());
              setTextAndCopyAction(signatureId, keyInfo.keyId());
              setTextAndCopyAction(signatureVersion, keyInfo.keyVersion());
            });
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
                      R.string.debug_matching_signature_info_copied_text,
                      StringUtils.truncateWithEllipsis(text, 35)),
                  Snackbar.LENGTH_SHORT)
              .show();
        });
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    ProvideMatchingViewModel provideMatchingViewModel =
        new ViewModelProvider(this, getDefaultViewModelProviderFactory())
            .get(ProvideMatchingViewModel.class);
    if (requestCode == QR_SCAN_REQUEST_CODE) {
      switch (resultCode) {
        case RESULT_OK:
          Log.d(TAG, "onActivityResult with requestCode=QR_SCAN_REQUEST_CODE: OK");
          try {
            TemporaryExposureKey temporaryExposureKey =
                TemporaryExposureKeyEncodingHelper.decodeSingle(
                    data.getStringExtra(QRScannerActivity.RESULT_KEY));

            EditText key = requireView().findViewById(R.id.input_single_key);
            EditText interValNumber = requireView().findViewById(R.id.input_single_interval_number);
            EditText rollingPeriod = requireView().findViewById(R.id.input_single_rolling_period);
            EditText transmissionRiskLevel =
                requireView().findViewById(R.id.input_single_transmission_risk_level);

            key.setText(BASE16.encode(temporaryExposureKey.getKeyData()));
            interValNumber.setText(
                Integer.toString(temporaryExposureKey.getRollingStartIntervalNumber()));
            rollingPeriod.setText(Integer.toString(temporaryExposureKey.getRollingPeriod()));
            transmissionRiskLevel.setText(
                Integer.toString(temporaryExposureKey.getTransmissionRiskLevel()));
          } catch (DecodeException e) {
            Log.e(TAG, "Decode error", e);
            maybeShowSnackbar(getString(R.string.debug_matching_provide_scan_error));
          }
          break;
        case RESULT_CANCELED:
          Log.d(TAG, "onActivityResult with requestCode=QR_SCAN_REQUEST_CODE: CANCELED");
          break;
        default:
          Log.d(TAG, "onActivityResult with requestCode=QR_SCAN_REQUEST_CODE: UNKNOWN");
          break;
      }
    } else if (requestCode == FILE_REQUEST_CODE) {
      switch (resultCode) {
        case RESULT_OK:
          Log.d(TAG, "onActivityResult with requestCode=FILE_REQUEST_CODE: OK");
          Uri uri = data.getData();
          // Copy the file to a local app file for providing to the API.
          File file = new File(requireContext().getFilesDir(), TEMP_INPUT_FILENAME);
          try (InputStream in = getContext().getContentResolver().openInputStream(uri)) {
            try (OutputStream out = new FileOutputStream(file)) {
              byte[] buf = new byte[1024];
              int len;
              while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
              }
            }
          } catch (FileNotFoundException e) {
            Log.e(TAG, "FileNotFoundException creating temp file", e);
          } catch (IOException e) {
            Log.e(TAG, "IOException creating temp file", e);
          }
          provideMatchingViewModel.setFileInput(file);
          break;
        case RESULT_CANCELED:
          Log.d(TAG, "onActivityResult with requestCode=FILE_REQUEST_CODE: CANCELED");
          break;
        default:
          Log.d(TAG, "onActivityResult with requestCode=FILE_REQUEST_CODE: UNKNOWN");
          break;
      }
    } else {
      Log.d(TAG, String.format("onActivityResult unknown requestCode=%d", requestCode));
    }
    super.onActivityResult(requestCode, resultCode, data);
  }

  /** Tries to parse an integer string, if not returns 0 and shows a snackbar. */
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
