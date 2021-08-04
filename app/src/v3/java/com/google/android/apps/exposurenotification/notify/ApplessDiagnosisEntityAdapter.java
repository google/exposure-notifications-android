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

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.StringUtils;
import com.google.android.apps.exposurenotification.databinding.ItemApplessDiagnosisEntityBinding;
import com.google.android.apps.exposurenotification.notify.ApplessDiagnosisEntityAdapter.ApplessDiagnosisViewHolder;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity.Shared;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity.TestResult;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * View adapter for displaying a list of {@link DiagnosisEntity}.
 */
public class ApplessDiagnosisEntityAdapter
    extends RecyclerView.Adapter<ApplessDiagnosisViewHolder> {

  private List<DiagnosisEntity> diagnosisEntities = Collections.emptyList();
  private final NotifyHomeViewModel notifyHomeViewModel;

  private final DiagnosisClickListener onDiagnosisClickListener;
  private @Nullable
  DiagnosisDeleteListener onDiagnosisDeleteListener = null;

  public ApplessDiagnosisEntityAdapter(DiagnosisClickListener onDiagnosisClickListener,
      NotifyHomeViewModel notifyHomeViewModel) {
    this.onDiagnosisClickListener = onDiagnosisClickListener;
    this.notifyHomeViewModel = notifyHomeViewModel;
  }

  /**
   * Sets the onDiagnosisDeleteListener
   */
  public void setOnDiagnosisDeleteListener(
      @Nullable DiagnosisDeleteListener onDiagnosisDeleteListener) {
    this.onDiagnosisDeleteListener = onDiagnosisDeleteListener;
  }

  /**
   * Updates the {@link DiagnosisEntity} to display.
   */
  public void setDiagnosisEntities(
      List<DiagnosisEntity> diagnosisEntities) {
    this.diagnosisEntities = diagnosisEntities;
    notifyDataSetChanged();
  }

  /**
   * Deletes the {@link DiagnosisEntity} at the given position.
   */
  public void deleteDiagnosisEntity(int position) {
    DiagnosisEntity diagnosis = diagnosisEntities.remove(position);
    notifyHomeViewModel.deleteEntity(diagnosis);
    notifyDataSetChanged();
  }

  /**
   * Get the date string of a {@link DiagnosisEntity} at the given position.
   */
  public String getDiagnosisEntityDate(Locale locale, int position) {
    return StringUtils.epochTimestampToMediumUTCDateString(
        diagnosisEntities.get(position).getLastUpdatedTimestampMs(), locale);
  }

  @NonNull
  @Override
  public ApplessDiagnosisViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
    return new ApplessDiagnosisViewHolder(ItemApplessDiagnosisEntityBinding
        .inflate(LayoutInflater.from(viewGroup.getContext()), viewGroup, false));
  }

  @Override
  public void onBindViewHolder(@NonNull ApplessDiagnosisViewHolder holder, int position) {
    holder.bind(diagnosisEntities.get(position), position);
  }

  @Override
  public int getItemCount() {
    return diagnosisEntities.size();
  }

  /**
   * The {@link RecyclerView.ViewHolder} for the {@link DiagnosisEntity}.
   */
  class ApplessDiagnosisViewHolder extends RecyclerView.ViewHolder {

    private final View rootView;
    private final ItemApplessDiagnosisEntityBinding binding;

    ApplessDiagnosisViewHolder(@NonNull ItemApplessDiagnosisEntityBinding binding) {
      super(binding.getRoot());
      this.rootView = binding.getRoot();
      this.binding = binding;
    }

    void bind(final DiagnosisEntity entity, int position) {
      rootView.setOnClickListener(v -> onDiagnosisClickListener.onClick(entity));
      if (onDiagnosisDeleteListener != null) {
        binding.deleteSharedDiagnosis.setOnClickListener
            (v -> onDiagnosisDeleteListener.onClick(position));
      }
      TestResult testResult = entity.getTestResult();
      binding.diagnosisTitle.setText(DiagnosisEntityHelper.getDiagnosisTypeStringResourceFromTestResult(testResult));

      String sharedStatus = Shared.SHARED.equals(entity.getSharedStatus()) ?
          rootView.getResources().getString(R.string.positive_test_result_status_shared) :
          rootView.getResources().getString(R.string.positive_test_result_status_not_shared);

      binding.diagnosisSubtitle.setText(
          rootView.getResources().getString(R.string.diagnosis_subtitle_template,
          sharedStatus,
          getDiagnosisEntityDate(rootView.getResources().getConfiguration().locale, position)));

    }
  }

  public interface DiagnosisClickListener {

    void onClick(DiagnosisEntity entity);
  }

  public interface DiagnosisDeleteListener {

    void onClick(int position);
  }
}
