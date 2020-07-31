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

import static com.google.android.gms.nearby.exposurenotification.ExposureNotificationClient.ACTION_EXPOSURE_NOTIFICATION_SETTINGS;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.LocationManager;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.ViewFlipper;
import android.widget.ViewSwitcher;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.StorageManagementHelper;
import com.google.android.apps.exposurenotification.home.ExposureNotificationViewModel;
import com.google.android.apps.exposurenotification.home.ExposureNotificationViewModel.ExposureNotificationState;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.material.snackbar.Snackbar;
import com.mikepenz.aboutlibraries.LibsBuilder;

/** Fragment for Notify tab on home screen */
public class NotifyHomeFragment extends Fragment {

  private static final String TAG = "NotifyHomeFragment";

  private ExposureNotificationViewModel exposureNotificationViewModel;
  private NotifyHomeViewModel notifyHomeViewModel;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_notify_home, parent, false);
  }

  @Override
  public void onViewCreated(View view, Bundle savedInstanceState) {
    exposureNotificationViewModel =
        new ViewModelProvider(requireActivity()).get(ExposureNotificationViewModel.class);
    notifyHomeViewModel =
        new ViewModelProvider(this, getDefaultViewModelProviderFactory())
            .get(NotifyHomeViewModel.class);

    exposureNotificationViewModel
        .getStateLiveData()
        .observe(getViewLifecycleOwner(), this::refreshUiForState);

    view.findViewById(R.id.exposure_menu).setOnClickListener(v -> showPopup(v));
    
    Button startApiButton = view.findViewById(R.id.start_api_button);
    startApiButton.setOnClickListener(
        v -> exposureNotificationViewModel.startExposureNotifications());
    exposureNotificationViewModel
        .getInFlightLiveData()
        .observe(getViewLifecycleOwner(), isInFlight -> startApiButton.setEnabled(!isInFlight));

    exposureNotificationViewModel
        .getApiErrorLiveEvent()
        .observe(getViewLifecycleOwner(), unused -> {
          View rootView = getView();
          if (rootView != null) {
            Snackbar.make(rootView, R.string.generic_error_message, Snackbar.LENGTH_LONG).show();
          }
        });

    Button shareButton = view.findViewById(R.id.fragment_notify_share_button);
    shareButton.setOnClickListener(
        v -> startActivity(ShareDiagnosisActivity.newIntentForAddFlow(requireContext())));

    PositiveDiagnosisEntityAdapter notifyViewAdapter =
        new PositiveDiagnosisEntityAdapter(
            positiveDiagnosisEntity ->
                startActivity(
                    ShareDiagnosisActivity.newIntentForViewFlow(
                        requireContext(), positiveDiagnosisEntity)));
    final LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
    RecyclerView recyclerView = view.findViewById(R.id.notify_recycler_view);
    recyclerView.setLayoutManager(layoutManager);
    recyclerView.setAdapter(notifyViewAdapter);

    final ViewSwitcher switcher =
        requireView().findViewById(R.id.fragment_notify_diagnosis_switcher);

    view.findViewById(R.id.api_settings_button).setOnClickListener(v -> launchEnSettings());
    view.findViewById(R.id.manage_storage_button)
        .setOnClickListener(v -> StorageManagementHelper.launchStorageManagement(getContext()));

    notifyHomeViewModel
        .getAllPositiveDiagnosisEntityLiveData()
        .observe(
            getViewLifecycleOwner(),
            l -> {
              switcher.setDisplayedChild(l.isEmpty() ? 0 : 1);
              notifyViewAdapter.setPositiveDiagnosisEntities(l);
            });

    TextView notifyDescription = view.findViewById(R.id.fragment_notify_description);
    appendLearnMoreLink(
        notifyDescription, new Intent(requireContext(), NotifyLearnMoreActivity.class));

    IntentFilter intentFilter = new IntentFilter();
    intentFilter.addAction(LocationManager.PROVIDERS_CHANGED_ACTION);
    intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
    requireContext().registerReceiver(refreshBroadcastReceiver, intentFilter);
  }

  private final BroadcastReceiver refreshBroadcastReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      refreshUi();
    }
  };

  /** Appends a clickable learn more link to the end of the text view specified. */
  public static void appendLearnMoreLink(TextView textView, Intent intent) {
    ClickableSpan clickableSpan =
        new ClickableSpan() {
          @Override
          public void onClick(View widget) {
            textView.getContext().startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
          }
        };
    String learnMoreText = textView.getContext().getString(R.string.learn_more);
    SpannableString learnMoreSpannable = new SpannableString(learnMoreText);
    learnMoreSpannable.setSpan(
        clickableSpan, 0, learnMoreText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    textView.setText(TextUtils.concat(textView.getText(), " ", learnMoreSpannable));
    textView.setMovementMethod(LinkMovementMethod.getInstance());
  }

  @Override
  public void onResume() {
    super.onResume();
    refreshUi();
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    requireContext().unregisterReceiver(refreshBroadcastReceiver);
  }

  /** Update UI state after Exposure Notifications client state changes */
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

    ViewFlipper viewFlipper = rootView.findViewById(R.id.notify_header_flipper);
    View diagnosisHistoryContainer = rootView.findViewById(R.id.diagnosis_history_container);
    Button manageStorageButton = rootView.findViewById(R.id.manage_storage_button);

    ExposureNotificationSharedPreferences sharedPrefs =
        new ExposureNotificationSharedPreferences(requireContext());
    switch (state) {
      case ENABLED:
        sharedPrefs.setOnboardedState(true);
        viewFlipper.setDisplayedChild(1);
        diagnosisHistoryContainer.setVisibility(View.VISIBLE);
        break;
      case PAUSED_BLE_OR_LOCATION_OFF:
        sharedPrefs.setOnboardedState(true);
        viewFlipper.setDisplayedChild(2);
        diagnosisHistoryContainer.setVisibility(View.VISIBLE);
        break;
      case STORAGE_LOW:
        sharedPrefs.setOnboardedState(true);
        viewFlipper.setDisplayedChild(3);
        diagnosisHistoryContainer.setVisibility(View.VISIBLE);
        manageStorageButton.setVisibility(
            StorageManagementHelper.isStorageManagementAvailable(getContext())
                ? Button.VISIBLE : Button.GONE);
        break;
      case DISABLED:
      default:
        viewFlipper.setDisplayedChild(0);
        diagnosisHistoryContainer.setVisibility(View.GONE);
        break;
    }
  }

  /**
   * Open the Exposure Notifications Settings screen.
   */
  private void launchEnSettings() {
    Intent intent = new Intent(ACTION_EXPOSURE_NOTIFICATION_SETTINGS);
    startActivity(intent);
  }

  public void showPopup(View v) {
    PopupMenu popup = new PopupMenu(getContext(), v);
    popup.setOnMenuItemClickListener(menuItem -> {
      showOsLicenses();
      return true;
    });
    MenuInflater inflater = popup.getMenuInflater();
    inflater.inflate(R.menu.popup_menu, popup.getMenu());
    popup.show();
  }

  private void showOsLicenses(){
    new LibsBuilder().withLicenseShown(true).start(getActivity());
  }

}
