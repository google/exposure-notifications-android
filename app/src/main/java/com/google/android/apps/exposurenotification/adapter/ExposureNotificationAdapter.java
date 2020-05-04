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
import com.google.android.apps.exposurenotification.activities.ExposureFragment.ExposureClick;
import com.google.android.apps.exposurenotification.common.StringUtils;
import com.google.android.gms.nearby.exposurenotification.ExposureInformation;
import java.util.Locale;

public class ExposureNotificationAdapter extends
    ListAdapter<ExposureInformation, ExposureNotificationAdapter.ViewHolder> {

  private final ExposureClick clickListener;

  public ExposureNotificationAdapter(ExposureClick exposureClick) {
    super(new ExposureItemCallback());
    clickListener = exposureClick;
  }

  @NonNull
  @Override
  public ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
    LayoutInflater inflater = LayoutInflater.from(viewGroup.getContext());
    View view = inflater.inflate(R.layout.item_exposure, viewGroup, false);
    return new ViewHolder(view, clickListener);
  }

  @Override
  public void onBindViewHolder(@NonNull ViewHolder viewHolder, int i) {
    ExposureInformation item = getItem(i);
    viewHolder.bind(item);
  }

  static class ViewHolder extends RecyclerView.ViewHolder {

    private final TextView exposureItemTimestamp;
    private ExposureInformation currentItem;

    ViewHolder(@NonNull View itemView, ExposureClick exposureClick) {
      super(itemView);
      exposureItemTimestamp = itemView.findViewById(R.id.exposure_item_timestamp);
      itemView.setOnClickListener((v) -> {
        if (currentItem != null) {
          exposureClick.onClicked(currentItem);
        }
      });
    }

    void bind(ExposureInformation item) {
      currentItem = item;
      Locale locale = itemView.getContext().getResources().getConfiguration().locale;
      String formatted = StringUtils.timestampMsToMediumString(item.getDateMillisSinceEpoch(),
          locale);
      exposureItemTimestamp.setText(formatted);
    }
  }

  private static class ExposureItemCallback extends DiffUtil.ItemCallback<ExposureInformation> {

    @Override
    public boolean areItemsTheSame(@NonNull ExposureInformation left,
        @NonNull ExposureInformation right) {
      return left == right;
    }

    @Override
    public boolean areContentsTheSame(@NonNull ExposureInformation left,
        @NonNull ExposureInformation right) {
      return left.equals(right);
    }
  }

}
