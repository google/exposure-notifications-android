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

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ViewSwitcher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.activities.utils.ExposureNotificationPermissionHelper;
import com.google.android.apps.exposurenotification.common.StringUtils;
import com.google.android.apps.exposurenotification.nearby.ExposureNotificationClientWrapper;
import com.google.android.apps.exposurenotification.storage.ExposureNotificationSharedPreferences;
import com.google.android.apps.exposurenotification.storage.PositiveDiagnosisEntity;
import com.google.android.apps.exposurenotification.storage.PositiveDiagnosisViewModel;
import java.util.Collections;
import java.util.List;
import org.threeten.bp.format.DateTimeFormatter;
import org.threeten.bp.format.FormatStyle;

/**
 * Fragment for Notify tab on home screen
 */
public class NotifyFragment extends Fragment {

  private static final String TAG = "NotifyFragment";

  private final ExposureNotificationPermissionHelper exposureNotificationPermissionHelper;

  public NotifyFragment() {
    exposureNotificationPermissionHelper =
        new ExposureNotificationPermissionHelper(this, this::refreshUi);
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_notify, parent, false);
  }

  @Override
  public void onViewCreated(View view, Bundle savedInstanceState) {
    TextView descriptionView = view.findViewById(R.id.fragment_notify_description);
    StringUtils.appendLearnMoreLink(descriptionView,
        requireContext().getString(R.string.share_test_result_learn_more_href));
    Button shareButton = view.findViewById(R.id.fragment_notify_share_button);
    shareButton.setOnClickListener(v -> {
          Intent shareExposureIntent = new Intent(getContext(), ShareExposureActivity.class);
          startActivity(shareExposureIntent);
        }
    );

    NotifyViewAdapter notifyViewAdapter = new NotifyViewAdapter();
    final LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
    RecyclerView recyclerView = view.findViewById(R.id.notify_recycler_view);
    recyclerView.setLayoutManager(layoutManager);
    recyclerView.setAdapter(notifyViewAdapter);

    final ViewSwitcher switcher = requireView()
        .findViewById(R.id.fragment_notify_diagnosis_switcher);

    PositiveDiagnosisViewModel positiveDiagnosisViewModel =
        new ViewModelProvider(this, getDefaultViewModelProviderFactory())
            .get(PositiveDiagnosisViewModel.class);
    positiveDiagnosisViewModel
        .getAllPositiveDiagnosisEntityLiveData()
        .observe(getViewLifecycleOwner(), l -> {
              switcher.setDisplayedChild(l.isEmpty() ? 0 : 1);
              notifyViewAdapter.setPositiveDiagnosisEntities(l);
            }
        );

    Button goToSettingsButton = view.findViewById(R.id.go_to_settings_button);
    goToSettingsButton.setOnClickListener(
        v -> exposureNotificationPermissionHelper.optInAndStartExposureTracing(view));
  }

  @Override
  public void onResume() {
    super.onResume();
    refreshUi();
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    exposureNotificationPermissionHelper
        .onResolutionComplete(requestCode, resultCode, requireView());
  }

  /**
   * Update UI state after Exposure Notifications client state changes
   */
  private void refreshUi() {
    ExposureNotificationClientWrapper exposureNotificationClientWrapper =
        ExposureNotificationClientWrapper.get(requireContext());

    exposureNotificationClientWrapper.isEnabled()
        .addOnSuccessListener(this::refreshUiForEnabled)
        .addOnFailureListener((cause) -> refreshUiForEnabled(false));
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
      ExposureNotificationSharedPreferences sharedPrefs = new ExposureNotificationSharedPreferences(
          requireContext());
      sharedPrefs.setOnboardedState(true);
    }
    rootView.findViewById(R.id.settings_banner_section)
        .setVisibility(currentlyEnabled ? View.GONE : View.VISIBLE);
    rootView.findViewById(R.id.notify_share_section)
        .setVisibility(currentlyEnabled ? View.VISIBLE : View.GONE);
    rootView.findViewById(R.id.diagnosis_history_container)
        .setVisibility(currentlyEnabled ? View.VISIBLE : View.GONE);
  }

  static class PositiveDiagnosisViewHolder extends RecyclerView.ViewHolder {

    private final TextView date;
    private final View itemDivider;
    private final DateTimeFormatter dateTimeFormatter;

    PositiveDiagnosisViewHolder(@NonNull View view, @NonNull DateTimeFormatter dateTimeFormatter) {
      super(view);
      date = view.findViewById(R.id.positive_diagnosis_date);
      itemDivider = view.findViewById(R.id.horizontal_divider_view);
      this.dateTimeFormatter = dateTimeFormatter;
    }

    void bind(final PositiveDiagnosisEntity entity, boolean lastElement) {
      date.setText(dateTimeFormatter.format(entity.getTestTimestamp()));
      if (lastElement) {
        itemDivider.setVisibility(View.GONE);
      } else {
        itemDivider.setVisibility(View.VISIBLE);
      }
    }
  }

  static class NotifyViewAdapter extends RecyclerView.Adapter<PositiveDiagnosisViewHolder> {

    private List<PositiveDiagnosisEntity> positiveDiagnosisEntities = Collections.emptyList();

    private final DateTimeFormatter dateTimeFormatter
        = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM);

    void setPositiveDiagnosisEntities(
        List<PositiveDiagnosisEntity> positiveDiagnosisEntities) {
      this.positiveDiagnosisEntities = positiveDiagnosisEntities;
      notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PositiveDiagnosisViewHolder onCreateViewHolder(
        @NonNull ViewGroup viewGroup, int i) {
      return new PositiveDiagnosisViewHolder(
          LayoutInflater.from(viewGroup.getContext())
              .inflate(R.layout.item_positive_diagnosis, viewGroup, false),
          dateTimeFormatter
      );
    }

    @Override
    public void onBindViewHolder(@NonNull PositiveDiagnosisViewHolder holder, int position) {
      holder.bind(positiveDiagnosisEntities.get(position), position == getItemCount() - 1);
    }

    @Override
    public int getItemCount() {
      return positiveDiagnosisEntities.size();
    }
  }

}
