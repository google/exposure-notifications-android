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

package com.google.android.libraries.privateanalytics.utils;

import com.android.volley.NetworkResponse;
import com.android.volley.Response;
import com.android.volley.Response.Listener;
import com.android.volley.toolbox.JsonObjectRequest;
import org.json.JSONObject;
import org.threeten.bp.Instant;

/**
 * A thin extension to {@link JsonObjectRequest} to make {@link #deliverResponse(JSONObject)} public
 * so that a fake request queue can deliver a test's response.
 *
 * <p>This is a bit of an unfortunate workaround, but other alternatives that were considered were
 * even more awkward or complicated.
 */
public class RespondableJsonObjectRequest extends JsonObjectRequest {

  public RespondableJsonObjectRequest(
      int method, String url, JSONObject jsonRequest, Listener<JSONObject> listener,
      Response.ErrorListener errorListener) {
    super(method, url, jsonRequest, listener, errorListener);
  }

  @Override
  public void deliverResponse(JSONObject response) {
    super.deliverResponse(response);
  }

  @Override
  public Response<JSONObject> parseNetworkResponse(NetworkResponse response) {
    return super.parseNetworkResponse(response);
  }
}
