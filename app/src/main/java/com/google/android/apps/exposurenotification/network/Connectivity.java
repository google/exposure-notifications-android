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

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import dagger.hilt.android.qualifiers.ApplicationContext;
import javax.inject.Inject;

/**
 * A helper to encapsulate and simplify checking if we have an internet connection.
 */
public class Connectivity {

  private final ConnectivityManager connectivityManager;

  @Inject
  public Connectivity(@ApplicationContext Context context) {
    this.connectivityManager =
        (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
  }

  public boolean hasInternet() {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
      NetworkCapabilities nc =
          connectivityManager.getNetworkCapabilities(connectivityManager.getActiveNetwork());
      return nc != null
          && !nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)
          && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }
    NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
    return networkInfo != null && networkInfo.isConnected();
  }
}
