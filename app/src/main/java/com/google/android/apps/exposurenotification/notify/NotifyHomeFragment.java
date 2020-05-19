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

import android.content.Intent;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ViewSwitcher;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.home.ExposureNotificationViewModel;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.material.snackbar.Snackbar;

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
        .getIsEnabledLiveData()
        .observe(getViewLifecycleOwner(), isEnabled -> refreshUiForEnabled(isEnabled));

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
  }

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

  /** Update UI state after Exposure Notifications client state changes */
  private void refreshUi() {
    exposureNotificationViewModel.refreshIsEnabledState();
  }

  /**
   * Update UI to match Exposure Notifications client has become enabled/not-enabled.
   *
   * @param currentlyEnabled True if Exposure Notifications is enabled
   */
  private void refreshUiForEnabled(Boolean currentlyEnabled) {
    View rootView = getView();
    if (rootView == null) {
      return;
    }
    if (currentlyEnabled) {
      // if we're seeing it enabled then permission has been granted
      ExposureNotificationSharedPreferences sharedPrefs =
          new ExposureNotificationSharedPreferences(requireContext());
      sharedPrefs.setOnboardedState(true);
    }
    rootView
        .findViewById(R.id.settings_banner_section)
        .setVisibility(currentlyEnabled ? View.GONE : View.VISIBLE);
    rootView
        .findViewById(R.id.notify_share_section)
        .setVisibility(currentlyEnabled ? View.VISIBLE : View.GONE);
    rootView
        .findViewById(R.id.diagnosis_history_container)
        .setVisibility(currentlyEnabled ? View.VISIBLE : View.GONE);
  }
}
