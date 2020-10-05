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

package com.google.android.apps.exposurenotification.common;

import androidx.concurrent.futures.CallbackToFutureAdapter;
import com.google.android.gms.tasks.Task;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.threeten.bp.Duration;

/**
 * A converter similar to {@link CallbackToFutureAdapter} for GMSCore Tasks (so similar in fact,
 * that it uses {@link CallbackToFutureAdapter} internally.
 */
public final class TaskToFutureAdapter {

  private static final String TAG = "TaskToFutureAdapter";

  public static <T> ListenableFuture<T> getFutureWithTimeout(
      Task<T> task, Duration timeout, ScheduledExecutorService executor) {
    return FluentFuture.<T>from(
        CallbackToFutureAdapter.getFuture(
            completer -> {
              task.addOnCompleteListener(
                  executor,
                  completed -> {
                    try {
                      if (completed.isCanceled()) {
                        completer.setCancelled();
                      } else if (completed.getException() != null) {
                        completer.setException(completed.getException());
                      } else {
                        completer.set(completed.getResult());
                      }
                    } catch (Exception ex) {
                      completer.setException(ex);
                    }
                  });
              return "GmsCoreTask";
            }))
        .withTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS, executor);
  }

  private TaskToFutureAdapter() {}

}
