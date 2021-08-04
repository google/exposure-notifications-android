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
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.StringUtils;
import com.google.android.apps.exposurenotification.databinding.ItemDiagnosisEntityBinding;
import com.google.android.apps.exposurenotification.notify.DiagnosisEntityAdapter.DiagnosisViewHolder;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity.Shared;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity.TestResult;
import java.util.Collections;
import java.util.List;

/** View adapter for displaying a list of {@link DiagnosisEntity}. */
public class DiagnosisEntityAdapter
    extends RecyclerView.Adapter<DiagnosisViewHolder> {

  private final NotifyHomeViewModel notifyHomeViewModel;
  private final DiagnosisClickListener onDiagnosisClickListener;

  private List<DiagnosisEntity> diagnosisEntities = Collections.emptyList();

  public DiagnosisEntityAdapter(DiagnosisClickListener onDiagnosisClickListener,
      NotifyHomeViewModel notifyHomeViewModel) {
    this.onDiagnosisClickListener = onDiagnosisClickListener;
    this.notifyHomeViewModel = notifyHomeViewModel;
  }

  /** Updates the {@link DiagnosisEntity} to display. */
  public void setDiagnosisEntities(
      List<DiagnosisEntity> diagnosisEntities) {
    this.diagnosisEntities = diagnosisEntities;
    notifyDataSetChanged();
  }

  /** Deletes the {@link DiagnosisEntity} at the given position. */
  public void deleteDiagnosisEntity(int position) {
    DiagnosisEntity diagnosis = diagnosisEntities.remove(position);
    notifyHomeViewModel.deleteEntity(diagnosis);
    notifyDataSetChanged();
  }

  @NonNull
  @Override
  public DiagnosisViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
    return new DiagnosisViewHolder(ItemDiagnosisEntityBinding
        .inflate(LayoutInflater.from(viewGroup.getContext()), viewGroup, false));
  }

  @Override
  public void onBindViewHolder(@NonNull DiagnosisViewHolder holder, int position) {
    holder.bind(diagnosisEntities.get(position), position == getItemCount() - 1);
  }

  @Override
  public int getItemCount() {
    return diagnosisEntities.size();
  }

  /** The {@link RecyclerView.ViewHolder} for the {@link DiagnosisEntity}. */
  class DiagnosisViewHolder extends RecyclerView.ViewHolder {

    private final View rootView;
    private final View itemDivider;
    private final ItemDiagnosisEntityBinding binding;

    DiagnosisViewHolder(@NonNull ItemDiagnosisEntityBinding binding) {
      super(binding.getRoot());
      this.rootView = binding.getRoot();
      this.binding = binding;
      itemDivider = rootView.findViewById(R.id.horizontal_divider_view);
    }

    void bind(final DiagnosisEntity entity, boolean lastElement) {
      rootView.setOnClickListener(v -> onDiagnosisClickListener.onClick(entity));

      TestResult testResult = entity.getTestResult();
      binding.diagnosisType.setText(
          DiagnosisEntityHelper.getDiagnosisTypeStringResourceFromTestResult(testResult));

      String sharedStatus = Shared.SHARED.equals(entity.getSharedStatus()) ?
          rootView.getResources().getString(R.string.positive_test_result_status_shared) :
          rootView.getResources().getString(R.string.positive_test_result_status_not_shared);
      String lastUpdatedDate = StringUtils.epochTimestampToMediumUTCDateString(
          entity.getLastUpdatedTimestampMs(), rootView.getResources().getConfiguration().locale);
      binding.diagnosisSharedDate.setText(rootView.getResources()
          .getString(R.string.diagnosis_subtitle_template, sharedStatus, lastUpdatedDate));

      itemDivider.setVisibility(lastElement ? View.GONE : View.VISIBLE);
    }
  }

  public interface DiagnosisClickListener {

    void onClick(DiagnosisEntity entity);
  }
}
