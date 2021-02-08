/*
 * Copyright 2021 Google LLC
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

package com.google.android.apps.exposurenotification.exposure;

import static android.text.format.DateUtils.DAY_IN_MILLIS;

import android.content.Context;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.exposure.ExposureCheckEntityAdapter.ExposureCheckViewHolder;
import com.google.android.apps.exposurenotification.storage.ExposureCheckEntity;
import java.util.Collections;
import java.util.List;

/**
 * View adapter for displaying a list of {@link ExposureCheckEntity} objects.
 */
public class ExposureCheckEntityAdapter extends RecyclerView.Adapter<ExposureCheckViewHolder> {

  private static final String TAG = "ExposureCheckEntityAdapter";

  private final Context context;
  private List<ExposureCheckEntity> exposureChecks = Collections.emptyList();

  public ExposureCheckEntityAdapter(Context context) {
    this.context = context;
  }

  /**
   * Updates the {@link ExposureCheckEntity} to display.
   */
  public void setExposureChecks(List<ExposureCheckEntity> exposureChecks) {
    this.exposureChecks = exposureChecks;
    notifyDataSetChanged();
  }

  @NonNull
  @Override
  public ExposureCheckViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
    return new ExposureCheckViewHolder(
        LayoutInflater.from(viewGroup.getContext())
            .inflate(R.layout.item_exposure_check, viewGroup, false));
  }

  @Override
  public void onBindViewHolder(@NonNull ExposureCheckViewHolder holder, int position) {
    holder.bind(exposureChecks.get(position));
  }

  @Override
  public int getItemCount() {
    return exposureChecks.size();
  }

  /**
   * The {@link RecyclerView.ViewHolder} for the {@link ExposureCheckEntity}.
   */
  class ExposureCheckViewHolder extends RecyclerView.ViewHolder {

    private final TextView exposureCheckTimestamp;

    ExposureCheckViewHolder(@NonNull View view) {
      super(view);
      exposureCheckTimestamp = view.findViewById(R.id.exposure_check_timestamp);
    }

    void bind(final ExposureCheckEntity entity) {
      CharSequence relativeTimestamp = DateUtils
          .getRelativeDateTimeString(context, entity.getCheckTime().toEpochMilli(),
              DAY_IN_MILLIS, 2 * DAY_IN_MILLIS, 0);
      exposureCheckTimestamp.setText(relativeTimestamp);
    }
  }
}
