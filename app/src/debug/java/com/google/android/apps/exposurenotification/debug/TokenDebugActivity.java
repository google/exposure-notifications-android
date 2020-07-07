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

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.apps.exposurenotification.R;

/** Activity for the various debug UIs related to matching like viewing and providing keys. */
public final class TokenDebugActivity extends AppCompatActivity {

  private static final String TAG = "TokenDebugActivity";

  private TokenDebugViewModel tokenDebugViewModel;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    tokenDebugViewModel = new ViewModelProvider(this).get(TokenDebugViewModel.class);

    setContentView(R.layout.activity_token_debug);

    Toolbar toolbar = findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
    if (getSupportActionBar() != null) {
      getSupportActionBar().setDisplayShowTitleEnabled(false);
      getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    TokenEntityAdapter tokenEntityAdapter = new TokenEntityAdapter();
    LinearLayoutManager layoutManager = new LinearLayoutManager(this);
    RecyclerView recyclerView = findViewById(R.id.provide_keys_job_entity_recycler_view);
    recyclerView.setLayoutManager(layoutManager);
    recyclerView.setAdapter(tokenEntityAdapter);
    TextView emptyText = findViewById(R.id.provide_keys_job_empty);
    tokenDebugViewModel
        .getTokenEntityLiveData()
        .observe(
            this,
            provideKeysJobEntities -> {
              tokenEntityAdapter.setTokenEntities(provideKeysJobEntities);
              if (provideKeysJobEntities == null || provideKeysJobEntities.isEmpty()) {
                emptyText.setVisibility(View.VISIBLE);
              } else {
                emptyText.setVisibility(View.GONE);
              }
            });
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == android.R.id.home) {
      onBackPressed();
      return true;
    }
    return super.onOptionsItemSelected(item);
  }
}
