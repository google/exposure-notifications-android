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

import android.Manifest;
import android.Manifest.permission;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.AbstractTextWatcher;
import com.google.android.apps.exposurenotification.common.KeyboardHelper;
import com.google.android.apps.exposurenotification.common.SnackbarUtil;
import com.google.android.apps.exposurenotification.databinding.ActivityVerifiableSmsBinding;
import com.google.android.apps.exposurenotification.debug.VerificationCodeCreator.VerificationCode;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import dagger.hilt.android.AndroidEntryPoint;

/**
 * Activity for generating verifiable SMS.
 */
@AndroidEntryPoint
public final class VerifiableSmsActivity extends AppCompatActivity {

  public static final String EXTRA_VERIFICATION_CODE =
      "VerifiableSmsActivity.EXTRA_VERIFICATION_CODE";
  private static final String SAVED_INSTANCE_STATE_PHONE_STATE_PERMISSION_REQUIRED_DIALOG_SHOWN =
      "VerifiableSmsActivity.SAVED_INSTANCE_STATE_PHONE_STATE_PERMISSION_REQUIRED_DIALOG_SHOWN";

  private boolean readPhoneStatePermissionRequiredDialogShown = false;

  private ActivityVerifiableSmsBinding binding;
  VerifiableSmsViewModel verifiableSmsViewModel;

  private final ActivityResultLauncher<String> requestReadPhoneStatePermissionLauncher =
      registerForActivityResult(new RequestPermission(), isGranted -> {
        if (isGranted) {
          verifiableSmsViewModel.queryAndMaybeSetPhoneNumber();
        } else {
          maybeShowSnackbar(getString(R.string.debug_verifiable_sms_phone_state_permission_error));
        }
      });

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    binding = ActivityVerifiableSmsBinding.inflate(getLayoutInflater());
    setContentView(binding.getRoot());

    binding.home.setContentDescription(getString(R.string.navigate_up));
    binding.home.setOnClickListener((v) -> {
      KeyboardHelper.maybeHideKeyboard(getApplicationContext(), binding.home);
      onBackPressed();
    });

    verifiableSmsViewModel = new ViewModelProvider(this)
        .get(VerifiableSmsViewModel.class);

    verifiableSmsViewModel.getSnackbarSingleLiveEvent()
        .observe(
            this,
            this::maybeShowSnackbar);

    Intent intent = getIntent();
    if (intent != null && intent.hasExtra(EXTRA_VERIFICATION_CODE)) {
      VerificationCode verificationCode =
          (VerificationCode) intent.getSerializableExtra(EXTRA_VERIFICATION_CODE);
      verifiableSmsViewModel.setVerificationCode(verificationCode);
    }

    if (savedInstanceState == null) {
      requestReadPhoneStatePermissionAndQueryAndMaybeSetPhoneNumber();
    }

    binding.phoneNumber.addTextChangedListener(new AbstractTextWatcher() {
        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
          super.onTextChanged(s, start, before, count);
          verifiableSmsViewModel.setPhoneNumber(s.toString());
        }
      });
    verifiableSmsViewModel.getPhoneNumberLiveData().observe(this,
        phoneNumber -> {
          if (binding.phoneNumber.getText() != null
              && !binding.phoneNumber.getText().toString().equals(phoneNumber)) {
            binding.phoneNumber.setText(phoneNumber);
          }
        });

    binding.smsMessage.setOnClickListener(
        v -> {
          ClipboardManager clipboard =
              (ClipboardManager) v.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
          ClipData clip = ClipData.newPlainText(
              binding.smsMessage.getText(), binding.smsMessage.getText());
          clipboard.setPrimaryClip(clip);
          SnackbarUtil.maybeShowRegularSnackbar(
              v,
              getString(
                  R.string.debug_snackbar_copied_text,
                  binding.smsMessage.getText()));
        });

    verifiableSmsViewModel.getVerifiableSmsLiveData().observe(
        this,
        verifiableSms -> binding.smsMessage.setText(verifiableSms)
    );

    binding.debugGenerateSmsButton.setOnClickListener(v ->
        {
          if (binding.phoneNumber.getText() != null
              && !TextUtils.isEmpty(binding.phoneNumber.getText().toString())) {
            verifiableSmsViewModel.createVerifiableSms(binding.phoneNumber.getText().toString());
          } else {
            maybeShowSnackbar(getString(R.string.debug_verifiable_sms_invalid_phone_number_error));
          }
        });

    // Attach debug public key fragment
    getSupportFragmentManager()
        .beginTransaction()
        .add(R.id.debug_public_key_fragment, new DebugPublicKeyFragment())
        .commit();

    if (savedInstanceState != null) {
      if (savedInstanceState
          .getBoolean(SAVED_INSTANCE_STATE_PHONE_STATE_PERMISSION_REQUIRED_DIALOG_SHOWN, false)) {
        showReadPhoneStatePermissionRequiredDialog();
      }
    }
  }

  @Override
  public void onSaveInstanceState(@NonNull Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putBoolean(SAVED_INSTANCE_STATE_PHONE_STATE_PERMISSION_REQUIRED_DIALOG_SHOWN,
        readPhoneStatePermissionRequiredDialogShown);
  }

  private void maybeShowSnackbar(String message) {
    View rootView = findViewById(android.R.id.content);
    SnackbarUtil.maybeShowRegularSnackbar(rootView, message);
  }

  private void requestReadPhoneStatePermissionAndQueryAndMaybeSetPhoneNumber() {
    if (ContextCompat.checkSelfPermission(this, permission.READ_PHONE_STATE)
        == PackageManager.PERMISSION_GRANTED) {
      verifiableSmsViewModel.queryAndMaybeSetPhoneNumber();
    } else if (VERSION.SDK_INT >= VERSION_CODES.M
        && shouldShowRequestPermissionRationale(Manifest.permission.READ_PHONE_STATE)) {
      showReadPhoneStatePermissionRequiredDialog();
    } else {
      requestReadPhoneStatePermissionLauncher.launch(permission.READ_PHONE_STATE);
    }
  }

  /**
   * Display the "Read phone state permission required" dialog.
   */
  void showReadPhoneStatePermissionRequiredDialog() {
    if (readPhoneStatePermissionRequiredDialogShown) {
      return;
    }
    readPhoneStatePermissionRequiredDialogShown = true;
    new MaterialAlertDialogBuilder(this, R.style.ExposureNotificationAlertDialogTheme)
        .setTitle(R.string.debug_verifiable_sms_phone_state_permission_prompt_title)
        .setMessage(R.string.debug_verifiable_sms_phone_state_permission_prompt_message)
        .setCancelable(true)
        .setNegativeButton(R.string.btn_cancel, (dialog, i) -> {
          readPhoneStatePermissionRequiredDialogShown = false;
          dialog.cancel();
        })
        .setPositiveButton(R.string.btn_yes_continue,
            (dialog, i) -> {
              readPhoneStatePermissionRequiredDialogShown = false;
              requestReadPhoneStatePermissionLauncher.launch(permission.READ_PHONE_STATE);
            })
        .setOnCancelListener(dialog -> readPhoneStatePermissionRequiredDialogShown = false)
        .show();
  }

}