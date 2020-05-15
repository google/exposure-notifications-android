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
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.StringUtils;
import com.google.android.apps.exposurenotification.storage.ExposureEntity;
import java.util.Locale;

/**
 * View adapter for displaying a list of {@link ExposureEntity}.
 */
public class ExposureAdapter extends
    ListAdapter<ExposureEntity, ExposureAdapter.ViewHolder> {

  private final OnExposureClickListener onExposureClickListener;

  public ExposureAdapter(OnExposureClickListener onExposureClickListener) {
    super(new ExposureItemCallback());
    this.onExposureClickListener = onExposureClickListener;
  }

  @NonNull
  @Override
  public ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
    LayoutInflater inflater = LayoutInflater.from(viewGroup.getContext());
    View view = inflater.inflate(R.layout.item_exposure, viewGroup, false);
    return new ViewHolder(view, onExposureClickListener);
  }

  @Override
  public void onBindViewHolder(@NonNull ViewHolder viewHolder, int i) {
    ExposureEntity item = getItem(i);
    viewHolder.bind(item);
  }

  static class ViewHolder extends RecyclerView.ViewHolder {

    private final TextView exposureItemTimestamp;
    private ExposureEntity currentItem;

    ViewHolder(@NonNull View itemView, OnExposureClickListener onExposureClickListener) {
      super(itemView);
      exposureItemTimestamp = itemView.findViewById(R.id.exposure_item_timestamp);
      itemView.setOnClickListener((v) -> {
        if (currentItem != null) {
          onExposureClickListener.onClick(currentItem);
        }
      });
    }

    void bind(ExposureEntity item) {
      currentItem = item;
      Locale locale = itemView.getContext().getResources().getConfiguration().locale;
      String formatted = StringUtils.epochTimestampToMediumUTCDateString(item.getDateMillisSinceEpoch(),
          locale);
      exposureItemTimestamp.setText(formatted);
    }
  }

  private static class ExposureItemCallback extends DiffUtil.ItemCallback<ExposureEntity> {

    @Override
    public boolean areItemsTheSame(@NonNull ExposureEntity left,
        @NonNull ExposureEntity right) {
      return left.getId() == right.getId();
    }

    @Override
    public boolean areContentsTheSame(@NonNull ExposureEntity left,
        @NonNull ExposureEntity right) {
      return left.getDateMillisSinceEpoch() == right.getDateMillisSinceEpoch();
    }
  }

  public interface OnExposureClickListener {
    void onClick(ExposureEntity exposureEntity);
  }

}
