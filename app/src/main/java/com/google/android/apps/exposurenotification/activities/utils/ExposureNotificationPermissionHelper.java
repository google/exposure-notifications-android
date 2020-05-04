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

package com.google.android.apps.exposurenotification.activities.utils;

import android.app.Activity;
import android.content.Context;
import android.content.IntentSender.SendIntentException;
import android.util.Log;
import android.view.View;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LifecycleObserver;
import com.google.android.apps.exposurenotification.R;
import com.google.android.apps.exposurenotification.common.AppExecutors;
import com.google.android.apps.exposurenotification.common.TaskToFutureAdapter;
import com.google.android.apps.exposurenotification.nearby.ExposureNotificationClientWrapper;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.nearby.exposurenotification.ExposureNotificationStatusCodes;
import com.google.android.material.snackbar.Snackbar;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.lang.ref.WeakReference;
import java.util.concurrent.TimeUnit;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;
import org.threeten.bp.Duration;

/**
 * A helper to let user opt-in to exposure notification, and start the service after opting in.
 * <p/>
 * Usage:
 * <ul>
 *   <li>Fragment implements {@link .Callback}</li>
 *   <li>Fragment calls {@link #optInAndStartExposureTracing}</li>
 *   <li>Fragment overrides {@code onActivityResult} and call {@link #onResolutionComplete}</li>
 *   <li>Fragment's hosting activity overrides {@code onActivityResult} and delegates the
 *   result handling to fragment</li>
 * </ul>
 */
public class ExposureNotificationPermissionHelper implements LifecycleObserver {

  public interface Callback {

    default void onFailure() {
    }

    default void onOptOutSuccess() {
    }

    void onOptInSuccess();
  }

  private static final String TAG = "ENPermissionHelper";
  private static final Duration API_TIMEOUT = Duration.ofSeconds(10);

  private WeakReference<View> viewReference;
  private final Fragment host;
  private final Callback callback;
  private boolean hasInFlightResolution;

  public ExposureNotificationPermissionHelper(Fragment host, Callback callback) {
    this.host = host;
    this.callback = callback;
  }

  /**
   * Starts the exposure tracing. Possibly show permission dialog if user hasn't opted in before.
   *
   * @param rootView The root view to show any error UI.
   */
  public void optInAndStartExposureTracing(View rootView) {
    viewReference = new WeakReference<>(rootView);
    Activity activity = host.getActivity();
    if (activity == null) {
      Log.w(TAG, "No activity, skipping");
      return;
    }
    FluentFuture.from(isEnabled(activity))
        .transformAsync(
            isEnabled -> {
              if (isEnabled != null && isEnabled) {
                Log.d(TAG, "Already enabled. Skipping.");
                return Futures.immediateFuture(null);
              }
              Log.d(TAG, "Not enabled. Starting.");
              return start(activity);
            }, AppExecutors.getLightweightExecutor())
        .addCallback(new FutureCallback<Void>() {
          @Override
          public void onSuccess(@NullableDecl Void result) {
            hasInFlightResolution = false;
            callback.onOptInSuccess();
          }

          @Override
          public void onFailure(Throwable exception) {
            if (!(exception instanceof ApiException) || hasInFlightResolution) {
              Log.e(TAG, "Unknown error or has hasInFlightResolution", exception);
              // Reset hasInFlightResolution so we don't block future resolution if user wants to
              // try again manually
              showError();
              return;
            }
            ApiException apiException = (ApiException) exception;
            if (apiException.getStatusCode() ==
                ExposureNotificationStatusCodes.RESOLUTION_REQUIRED) {
              try {
                hasInFlightResolution = true;
                apiException.getStatus()
                    .startResolutionForResult(activity,
                        RequestCodes.REQUEST_CODE_START_EXPOSURE_NOTIFICATION);
              } catch (SendIntentException e) {
                Log.w(TAG, "Error calling startResolutionForResult, sending to settings",
                    apiException);
                showError();
              }
            } else {
              Log.w(TAG, "No RESOLUTION_REQUIRED in result, sending to settings", apiException);
              showError();
            }
          }
        }, MoreExecutors.directExecutor());
  }

  public void optOut(View rootView) {
    viewReference = new WeakReference<>(rootView);
    Activity activity = host.getActivity();
    if (activity == null) {
      Log.w(TAG, "No activity, skipping");
      return;
    }
    FluentFuture.from(isEnabled(activity))
        .transformAsync(
            isEnabled -> {
              if (isEnabled != null && !isEnabled) {
                Log.d(TAG, "Already disabled. Skipping.");
                return Futures.immediateFuture(null);
              }
              Log.d(TAG, "Not disabled, stopping.");
              return stop(activity);
            }, AppExecutors.getLightweightExecutor())
        .addCallback(
            new FutureCallback<Void>() {
              @Override
              public void onSuccess(@NullableDecl Void result) {
                Log.d(TAG, "Service stopped and user is opted out");
                hasInFlightResolution = false;
                callback.onOptOutSuccess();
              }

              @Override
              public void onFailure(Throwable t) {
                showError();
              }
            }, MoreExecutors.directExecutor());
  }

  /**
   * Called when opt-in resolution is completed by user.
   * <p/>
   * Modeled after {@code Activity#onActivityResult} as that's how the API sends callback to apps.
   *
   * @param rootView The root view to show any error UI.
   */
  public void onResolutionComplete(int requestCode, int resultCode, View rootView) {
    if (requestCode != RequestCodes.REQUEST_CODE_START_EXPOSURE_NOTIFICATION) {
      return;
    }
    if (resultCode == Activity.RESULT_OK) {
      optInAndStartExposureTracing(rootView);
    } else {
      hasInFlightResolution = false;
      callback.onFailure();
    }
  }

  private static ListenableFuture<Boolean> isEnabled(Context context) {
    return TaskToFutureAdapter.getFutureWithTimeout(
        ExposureNotificationClientWrapper.get(context).isEnabled(),
        API_TIMEOUT.toMillis(),
        TimeUnit.MILLISECONDS,
        AppExecutors.getScheduledExecutor());
  }

  private static ListenableFuture<Void> start(Context context) {
    return TaskToFutureAdapter.getFutureWithTimeout(
        ExposureNotificationClientWrapper.get(context).start(),
        API_TIMEOUT.toMillis(),
        TimeUnit.MILLISECONDS,
        AppExecutors.getScheduledExecutor());
  }

  private static ListenableFuture<Void> stop(Context context) {
    return TaskToFutureAdapter.getFutureWithTimeout(
        ExposureNotificationClientWrapper.get(context).stop(),
        API_TIMEOUT.toMillis(),
        TimeUnit.MILLISECONDS,
        AppExecutors.getScheduledExecutor());
  }

  private void showError() {
    hasInFlightResolution = false;
    if (viewReference != null) {
      View rootview = viewReference.get();
      if (rootview != null) {
        Snackbar.make(rootview, R.string.generic_error_message, Snackbar.LENGTH_LONG).show();
      }
    }
    callback.onFailure();
  }

}
