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

package com.google.android.apps.exposurenotification.debug;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.StringUtils;
import com.google.android.apps.exposurenotification.debug.TokenEntityAdapter.TokenEntityViewHolder;
import com.google.android.apps.exposurenotification.storage.TokenEntity;
import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for displaying keys in {@link TokenDebugActivity}.
 */
class TokenEntityAdapter extends RecyclerView.Adapter<TokenEntityViewHolder> {

  private static final String TAG = "TokenEntityAdapter";

  private List<TokenEntity> tokenEntities = new ArrayList<>();

  void setTokenEntities(List<TokenEntity> tokenEntities) {
    if (tokenEntities != null) {
      this.tokenEntities = new ArrayList<>(tokenEntities);
    }
    notifyDataSetChanged();
  }

  @NonNull
  @Override
  public TokenEntityViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
    return new TokenEntityViewHolder(
        LayoutInflater.from(viewGroup.getContext())
            .inflate(R.layout.item_token_entity, viewGroup, false));
  }

  @Override
  public void onBindViewHolder(@NonNull TokenEntityViewHolder holder, int i) {
    holder.bind(tokenEntities.get(i));
  }

  @Override
  public int getItemCount() {
    return tokenEntities.size();
  }

  class TokenEntityViewHolder extends RecyclerView.ViewHolder {

    private final View view;
    private final TextView token;
    private final TextView createdDate;
    private final TextView lastUpdateDate;
    private final TextView status;

    TokenEntityViewHolder(@NonNull View view) {
      super(view);
      this.view = view;
      token = view.findViewById(R.id.token_id);
      createdDate = view.findViewById(R.id.token_created_date);
      lastUpdateDate = view.findViewById(R.id.token_last_update_date);
      status = view.findViewById(R.id.token_status);
    }

    void bind(final TokenEntity entity) {
      token.setText(token.getResources().getString(R.string.debug_token_token, entity.getToken()));
      createdDate.setText(
          createdDate.getResources()
              .getString(
                  R.string.debug_token_created,
                  StringUtils.epochTimestampToLongUTCDateTimeString(
                      entity.getCreatedTimestampMs(),
                      createdDate.getResources().getConfiguration().locale)));
      lastUpdateDate.setText(
          lastUpdateDate.getResources()
              .getString(
                  R.string.debug_token_updated,
                  StringUtils.epochTimestampToLongUTCDateTimeString(
                      entity.getLastUpdatedTimestampMs(),
                      createdDate.getResources().getConfiguration().locale)));

      if (entity.isResponded()) {
        status.setText(status.getResources().getString(R.string.debug_token_status,
            status.getResources().getString(R.string.debug_token_status_responded)));
      } else {
        status.setText(status.getResources().getString(R.string.debug_token_status,
            status.getResources().getString(R.string.debug_token_status_pending)));
      }
      view.setOnClickListener((v) -> {
      });
    }
  }
}
