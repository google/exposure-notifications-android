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

package com.google.android.apps.exposurenotification.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.adapter.NotifyAdapter.PositiveDiagnosisViewHolder;
import com.google.android.apps.exposurenotification.storage.PositiveDiagnosisEntity;
import java.util.Collections;
import java.util.List;
import org.threeten.bp.format.DateTimeFormatter;
import org.threeten.bp.format.FormatStyle;

/**
 * View adapter for displaying a list of {@link PositiveDiagnosisEntity}.
 */
public class NotifyAdapter extends RecyclerView.Adapter<PositiveDiagnosisViewHolder> {

  private List<PositiveDiagnosisEntity> positiveDiagnosisEntities = Collections.emptyList();

  private final DateTimeFormatter dateTimeFormatter
      = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM);

  /**
   * Updates the {@link PositiveDiagnosisEntity} to display.
   */
  public void setPositiveDiagnosisEntities(
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

  /**
   * The {@link RecyclerView.ViewHolder} for the {@link PositiveDiagnosisEntity}.
   */
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
}
