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
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.work.WorkInfo.State;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.debug.VerificationCodeCreator.VerificationCode;
import com.google.android.apps.exposurenotification.home.ExposureNotificationViewModel;
import com.google.android.apps.exposurenotification.home.ExposureNotificationViewModel.ExposureNotificationState;
import com.google.android.apps.exposurenotification.network.Uris;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences.NetworkMode;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.switchmaterial.SwitchMaterial;
import org.threeten.bp.ZoneOffset;
import org.threeten.bp.format.DateTimeFormatter;

/**
 * Fragment for Debug tab on home screen
 */
public class DebugHomeFragment extends Fragment {

  private static final String TAG = "DebugHomeFragment";
  private static final DateTimeFormatter CODE_EXPIRY_FORMAT =
      DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneOffset.UTC);

  private ExposureNotificationViewModel exposureNotificationViewModel;
  private DebugHomeViewModel debugHomeViewModel;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_debug_home, parent, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
    setupViewModels();
    setupVersionInfo(view);
    setupEnMasterSwitch(view);
    setupMatchingControls(view);
    setupKeyServerControls(view);
    setupVerificationServerControls(view);
  }

  private void setupViewModels() {
    exposureNotificationViewModel =
        new ViewModelProvider(requireActivity()).get(ExposureNotificationViewModel.class);
    exposureNotificationViewModel
        .getStateLiveData()
        .observe(getViewLifecycleOwner(), this::refreshUiForState);
    exposureNotificationViewModel
        .getApiErrorLiveEvent()
        .observe(
            getViewLifecycleOwner(),
            unused -> maybeShowSnackbar(getString(R.string.generic_error_message)));

    debugHomeViewModel =
        new ViewModelProvider(this, getDefaultViewModelProviderFactory())
            .get(DebugHomeViewModel.class);
    debugHomeViewModel
        .getSnackbarSingleLiveEvent()
        .observe(
            this,
            message -> {
              View rootView = getView();
              if (rootView == null) {
                return;
              }
              Snackbar.make(rootView, message, Snackbar.LENGTH_LONG).show();
            });
  }

  private void setupEnMasterSwitch(View view) {
    SwitchMaterial masterSwitch = view.findViewById(R.id.master_switch);
    masterSwitch.setOnCheckedChangeListener(masterSwitchChangeListener);
    exposureNotificationViewModel
        .getInFlightLiveData()
        .observe(getViewLifecycleOwner(), isInFlight -> masterSwitch.setEnabled(!isInFlight));
  }

  private void setupVersionInfo(View view) {
    TextView appVersion = view.findViewById(R.id.debug_app_version);
    appVersion.setText(
        getString(
            R.string.debug_version_app, getVersionNameForPackage(getContext().getPackageName())));

    TextView gmsVersion = view.findViewById(R.id.debug_gms_version);
    gmsVersion.setText(
        getString(
            R.string.debug_version_gms,
            getVersionNameForPackage(GoogleApiAvailability.GOOGLE_PLAY_SERVICES_PACKAGE)));
  }

  private void setupMatchingControls(View view) {
    Button manualMatching = view.findViewById(R.id.debug_matching_manual_button);
    manualMatching.setOnClickListener(
        v -> startActivity(new Intent(requireContext(), MatchingDebugActivity.class)));

    Button enqueueProvide = view.findViewById(R.id.debug_provide_now);
    enqueueProvide.setOnClickListener(
        v -> {
          debugHomeViewModel.provideKeys();
          maybeShowSnackbar(getString(R.string.debug_provide_keys_enqueued));
        });

    TextView jobStatus = view.findViewById(R.id.debug_matching_job_status);
    debugHomeViewModel
        .getProvideDiagnosisKeysWorkLiveData()
        .observe(
            getViewLifecycleOwner(),
            workInfos -> {
              if (workInfos == null) {
                Log.e(TAG, "workInfos is null");
                jobStatus.setText(getString(R.string.debug_job_status,
                    getString(R.string.debug_job_status_error)));
                return;
              }
              String jobStatusText = "";
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
  }

  private void setupKeyServerControls(View view) {
    ExposureNotificationSharedPreferences prefs =
        new ExposureNotificationSharedPreferences(getContext());

    Button enqueueProvide = view.findViewById(R.id.debug_provide_now);

    SwitchMaterial keyServerNetworkModeSwitch = view.findViewById(R.id.keyserver_network_mode);
    keyServerNetworkModeSwitch.setOnCheckedChangeListener(keyServerNetworkModeChangeListener);
    keyServerNetworkModeSwitch.setChecked(
        debugHomeViewModel.getKeySharingNetworkMode(NetworkMode.DISABLED)
            .equals(NetworkMode.LIVE));

    debugHomeViewModel
        .getKeySharingNetworkModeLiveData()
        .observe(
            getViewLifecycleOwner(),
            networkMode -> {
              enqueueProvide.setEnabled(networkMode.equals(NetworkMode.LIVE));
              keyServerNetworkModeSwitch.setChecked(networkMode.equals(NetworkMode.LIVE));
            });

    EditText downloadServer = view.findViewById(R.id.debug_download_server_address);
    downloadServer.setText(
        prefs.getDownloadServerAddress(
            getString(R.string.key_server_download_base_uri)));
    downloadServer.addTextChangedListener(
        new TextWatcher() {
          @Override
          public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            // NOOP
          }

          @Override
          public void onTextChanged(CharSequence s, int start, int before, int count) {
            // NOOP
          }

          @Override
          public void afterTextChanged(Editable s) {
            if (!s.toString().equals(getString(R.string.key_server_download_base_uri))) {
              prefs.setDownloadServerAddress(s.toString());
            }
          }
        });

    EditText uploadServer = view.findViewById(R.id.debug_upload_server_address);
    uploadServer.setText(prefs.getUploadServerAddress(
        getString(R.string.key_server_upload_uri)));
    uploadServer.addTextChangedListener(
        new TextWatcher() {
          @Override
          public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            // NOOP
          }

          @Override
          public void onTextChanged(CharSequence s, int start, int before, int count) {
            // NOOP
          }

          @Override
          public void afterTextChanged(Editable s) {
            if (!s.toString().equals(getString(R.string.key_server_upload_uri))) {
              prefs.setUploadServerAddress(s.toString());
            }
          }
        });

    Button serverReset = view.findViewById(R.id.debug_server_reset_button);
    serverReset.setOnClickListener(
        v -> {
          prefs.clearDownloadServerAddress();
          downloadServer.setText(R.string.key_server_download_base_uri);
          prefs.clearUploadServerAddress();
          uploadServer.setText(R.string.key_server_upload_uri);
        });
  }

  private void setupVerificationServerControls(View view) {
    ExposureNotificationSharedPreferences prefs =
        new ExposureNotificationSharedPreferences(getContext());

    SwitchMaterial verificationServerSwitch = view.findViewById(R.id.verification_network_mode);
    verificationServerSwitch.setOnCheckedChangeListener(verificationNetworkModeChangeListener);
    verificationServerSwitch.setChecked(
        debugHomeViewModel.getVerificationNetworkMode(NetworkMode.DISABLED).
            equals(NetworkMode.LIVE));

    debugHomeViewModel
        .getVerificationNetworkModeLiveData()
        .observe(
            getViewLifecycleOwner(),
            networkMode -> {
              verificationServerSwitch.setChecked(networkMode.equals(NetworkMode.LIVE));
            });

    EditText verificationServerStep1 = view.findViewById(R.id.debug_verification_server_address_1);
    EditText verificationServerStep2 = view.findViewById(R.id.debug_verification_server_address_2);

    verificationServerStep1.setText(
        prefs.getVerificationServerAddress1(
            getString(R.string.verification_server_uri_step_1)));
    verificationServerStep2.setText(
        prefs.getVerificationServerAddress2(
            getString(R.string.verification_server_uri_step_2)));

    verificationServerStep1.addTextChangedListener(
        new TextWatcher() {
          @Override
          public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            // NOOP
          }

          @Override
          public void onTextChanged(CharSequence s, int start, int before, int count) {
            // NOOP
          }

          @Override
          public void afterTextChanged(Editable s) {
            if (!s.toString().equals(getString(R.string.verification_server_uri_step_1))) {
              prefs.setVerificationServerAddress1(s.toString());
            }
          }
        });
    verificationServerStep2.addTextChangedListener(
        new TextWatcher() {
          @Override
          public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            // NOOP
          }

          @Override
          public void onTextChanged(CharSequence s, int start, int before, int count) {
            // NOOP
          }

          @Override
          public void afterTextChanged(Editable s) {
            if (!s.toString().equals(getString(R.string.verification_server_uri_step_2))) {
              prefs.setVerificationServerAddress2(s.toString());
            }
          }
        });

    Button verificationServerReset = view.findViewById(R.id.debug_verification_server_reset_button);
    verificationServerReset.setOnClickListener(
        v -> {
          prefs.clearVerificationServerAddress1();
          verificationServerStep1.setText(R.string.verification_server_uri_step_1);
          verificationServerStep2.setText(R.string.verification_server_uri_step_2);
        });

    Button createVerificationCode = view.findViewById(R.id.debug_create_verification_code_button);
    createVerificationCode.setOnClickListener(x -> {
      debugHomeViewModel.createVerificationCode();
    });
    View codeContainer = view.findViewById(R.id.debug_verification_code_container);
    TextView code = view.findViewById(R.id.debug_verification_code);
    TextView expiry = view.findViewById(R.id.debug_verification_code_expiry);
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

    debugHomeViewModel.getVerificationCodeLiveData().observe(
        getViewLifecycleOwner(),
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
          expiry.setText(getContext()
              .getString(R.string.debug_verification_code_expiry,
                  CODE_EXPIRY_FORMAT.format(verificationCode.expiry())));
        });

    debugHomeViewModel
        .getVerificationNetworkModeLiveData()
        .observe(
            getViewLifecycleOwner(),
            networkMode -> {
              if (networkMode == null) {
                return;
              }
              createVerificationCode.setEnabled(networkMode.equals(NetworkMode.LIVE));
              codeContainer
                  .setVisibility(networkMode.equals(NetworkMode.LIVE) ? View.VISIBLE : View.GONE);
            });

  }

  @Override
  public void onResume() {
    super.onResume();
    refreshUi();
  }

  private final OnCheckedChangeListener masterSwitchChangeListener =
      new OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
          buttonView.setOnCheckedChangeListener(null);
          // Set the toggle back. It will only toggle to correct state if operation succeeds.
          buttonView.setChecked(!isChecked);
          if (isChecked) {
            exposureNotificationViewModel.startExposureNotifications();
          } else {
            exposureNotificationViewModel.stopExposureNotifications();
          }
        }
      };

  private final OnCheckedChangeListener keyServerNetworkModeChangeListener =
      (buttonView, isChecked) -> {
        Uris uris = new Uris(requireContext());
        if (uris.hasDefaultUris()) {
          debugHomeViewModel.setKeySharingNetworkMode(NetworkMode.DISABLED);
          maybeShowSnackbar(getString(R.string.debug_network_default_uri));
          ((SwitchMaterial) requireView().findViewById(R.id.keyserver_network_mode))
              .setChecked(false);
          return;
        }
        if (isChecked) {
          debugHomeViewModel.setKeySharingNetworkMode(NetworkMode.LIVE);
        } else {
          debugHomeViewModel.setKeySharingNetworkMode(NetworkMode.DISABLED);
        }
      };

  private final OnCheckedChangeListener verificationNetworkModeChangeListener =
      (buttonView, isChecked) -> {
        Uris uris = new Uris(requireContext());
        if (uris.hasDefaultUris()) {
          debugHomeViewModel.setVerificationNetworkMode(NetworkMode.DISABLED);
          maybeShowSnackbar(getString(R.string.debug_network_default_uri));
          ((SwitchMaterial) requireView().findViewById(R.id.verification_network_mode))
              .setChecked(false);
          return;
        }
        if (isChecked) {
          debugHomeViewModel.setVerificationNetworkMode(NetworkMode.LIVE);
        } else {
          debugHomeViewModel.setVerificationNetworkMode(NetworkMode.DISABLED);
        }
      };

  /**
   * Update UI state after Exposure Notifications client state changes
   */
  private void refreshUi() {
    exposureNotificationViewModel.refreshState();
  }

  /**
   * Update UI to match Exposure Notifications state.
   *
   * @param state the {@link ExposureNotificationState} of the API
   */
  private void refreshUiForState(ExposureNotificationState state) {
    View rootView = getView();
    if (rootView == null) {
      return;
    }

    SwitchMaterial masterSwitch = rootView.findViewById(R.id.master_switch);
    masterSwitch.setOnCheckedChangeListener(null);
    switch (state) {
      case ENABLED:
      case PAUSED_BLE_OR_LOCATION_OFF:
        masterSwitch.setChecked(true);
        break;
      case DISABLED:
      default:
        masterSwitch.setChecked(false);
        break;
    }
    masterSwitch.setOnCheckedChangeListener(masterSwitchChangeListener);
  }

  /**
   * Gets the version name for a specified package. Returns a debug string if not found.
   */
  private String getVersionNameForPackage(String packageName) {
    try {
      return getContext().getPackageManager().getPackageInfo(packageName, 0).versionName;
    } catch (NameNotFoundException e) {
      Log.e(TAG, "Couldn't get the app version", e);
    }
    return getString(R.string.debug_version_not_available);
  }

  private void maybeShowSnackbar(String message) {
    View rootView = getView();
    if (rootView != null) {
      Snackbar.make(rootView, message, Snackbar.LENGTH_LONG).show();
    }
  }
}
