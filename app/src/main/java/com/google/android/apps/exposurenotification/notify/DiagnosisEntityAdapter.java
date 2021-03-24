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

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ViewSwitcher;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.notify.DiagnosisEntityAdapter.DiagnosisViewHolder;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity.Shared;
import com.google.android.apps.exposurenotification.storage.DiagnosisEntity.TestResult;
import java.util.Collections;
import java.util.List;
import org.threeten.bp.format.DateTimeFormatter;
import org.threeten.bp.format.FormatStyle;

/** View adapter for displaying a list of {@link DiagnosisEntity}. */
public class DiagnosisEntityAdapter
    extends RecyclerView.Adapter<DiagnosisViewHolder> {

  private static final String TAG = "DiagnosisEntityAdapter";

  private List<DiagnosisEntity> diagnosisEntities = Collections.emptyList();
  private final NotifyHomeViewModel notifyHomeViewModel;

  private final DateTimeFormatter dateTimeFormatter =
      DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM);
  private final DiagnosisClickListener onDiagnosisClickListener;

  public DiagnosisEntityAdapter(
      DiagnosisClickListener onDiagnosisClickListener, NotifyHomeViewModel notifyHomeViewModel) {
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
    return new DiagnosisViewHolder(
        LayoutInflater.from(viewGroup.getContext())
            .inflate(R.layout.item_diagnosis_entity, viewGroup, false));
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
    private final TextView type;
    private final TextView date;
    private final ViewSwitcher shared;
    private final View itemDivider;

    DiagnosisViewHolder(@NonNull View view) {
      super(view);
      this.rootView = view;
      type = view.findViewById(R.id.diagnosis_type);
      date = view.findViewById(R.id.diagnosis_date);
      shared = view.findViewById(R.id.diagnosis_status);
      itemDivider = view.findViewById(R.id.horizontal_divider_view);
    }

    void bind(final DiagnosisEntity entity, boolean lastElement) {
      rootView.setOnClickListener(v -> onDiagnosisClickListener.onClick(entity));

      TestResult testResult = entity.getTestResult();
      if (testResult == null) {
        Log.e(TAG, "Unknown TestResult=null");
        type.setText(R.string.test_result_type_confirmed);
      } else {
        switch (testResult) {
          case LIKELY:
            type.setText(R.string.test_result_type_likely);
            break;
          case NEGATIVE:
            type.setText(R.string.test_result_type_negative);
            break;
          case CONFIRMED:
            type.setText(R.string.test_result_type_confirmed);
            break;
          default:
            Log.e(TAG, "Unknown TestResult=" + testResult);
            type.setText(R.string.test_result_type_confirmed);
            break;
        }
      }

      if (entity.getOnsetDate() != null) {
        date.setText(dateTimeFormatter.format(entity.getOnsetDate()));
        date.setVisibility(View.VISIBLE);
      } else {
        date.setVisibility(View.GONE);
        date.setText("");
      }

      if (lastElement) {
        itemDivider.setVisibility(View.GONE);
      } else {
        itemDivider.setVisibility(View.VISIBLE);
      }

      shared.setDisplayedChild(Shared.SHARED.equals(entity.getSharedStatus()) ? 0 : 1);
    }
  }

  public interface DiagnosisClickListener {

    void onClick(DiagnosisEntity entity);
  }
}
