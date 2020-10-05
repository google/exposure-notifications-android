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

package com.google.android.apps.exposurenotification.network;

import android.net.Uri;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.Response.ErrorListener;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.HttpHeaderParser;
import com.google.android.apps.exposurenotification.common.time.Clock;

/**
 * A request for the raw bytes of a keyfile. An extension to {@link Request} to make {@link
 * #deliverResponse(byte[])} public so that a fake request queue can deliver a test's response.
 *
 * <p>This is a bit of an unfortunate workaround, but other alternatives that were considered were
 * even more awkward or complicated.
 */
public class RespondableByteArrayRequest extends Request<byte[]> {

  private final Response.Listener<byte[]> listener;

  public RespondableByteArrayRequest(
      Uri uri, Response.Listener<byte[]> listener, ErrorListener errorListener, Clock clock) {
    super(Method.GET, uri.toString(), errorListener);
    this.listener = listener;
    setRetryPolicy(new CustomRetryPolicy(clock));
  }

  @Override
  protected Response<byte[]> parseNetworkResponse(NetworkResponse response) {
    return response.statusCode < 400
        ? Response.success(response.data, HttpHeaderParser.parseCacheHeaders(response))
        : Response.error(new VolleyError(response));
  }

  @Override
  public void deliverResponse(byte[] response) {
    listener.onResponse(response);
  }
}
