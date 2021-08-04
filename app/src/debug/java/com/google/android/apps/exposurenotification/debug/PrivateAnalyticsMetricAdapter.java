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

package com.google.android.apps.exposurenotification.debug;


import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.logging.Logger;
import com.google.android.apps.exposurenotification.debug.PrivateAnalyticsMetricAdapter.PrivateAnalyticsMetricViewHolder;
import com.google.android.libraries.privateanalytics.PrivateAnalyticsMetric;
import java.util.Collections;
import java.util.List;

/**
 * Adapter for displaying metrics in {@link DebugActivity}.
 */
class PrivateAnalyticsMetricAdapter extends RecyclerView.Adapter<PrivateAnalyticsMetricViewHolder> {

  private final static Logger logger = Logger.getLogger("PrivateAnalyticsMetricAdapter");

  private List<PrivateAnalyticsMetric> metrics = Collections.emptyList();

  void setPrivateAnalyticsMetrics(List<PrivateAnalyticsMetric> privateAnalytics) {
    this.metrics = privateAnalytics;
    notifyDataSetChanged();
  }

  @NonNull
  @Override
  public PrivateAnalyticsMetricViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
    return new PrivateAnalyticsMetricViewHolder(
        LayoutInflater.from(viewGroup.getContext())
            .inflate(R.layout.item_metric, viewGroup, false));
  }

  @Override
  public void onBindViewHolder(
      @NonNull PrivateAnalyticsMetricViewHolder privateAnalyticsMetricViewHolder, int i) {
    try {
      List<Integer> data = metrics.get(i).getDataVector().get();
      privateAnalyticsMetricViewHolder
          .bind(metrics.get(i).getMetricName(), data.toString());
    } catch (Exception e) {
      logger.e("Could not get data for metric: " + metrics.get(i).getMetricName(), e);
    }
  }

  @Override
  public int getItemCount() {
    return metrics.size();
  }

  static class PrivateAnalyticsMetricViewHolder extends RecyclerView.ViewHolder {

    private final TextView name;
    private final TextView value;

    PrivateAnalyticsMetricViewHolder(@NonNull View view) {
      super(view);
      name = view.findViewById(R.id.debug_private_analytics_metric_name);
      value = view.findViewById(R.id.debug_private_analytics_metric_value);
    }

    void bind(String metricName, String metricValue) {
      name.setText(metricName);
      value.setText(metricValue);
    }
  }
}
