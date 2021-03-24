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

package com.google.android.libraries.privateanalytics;

import android.os.Process;
import android.os.StrictMode;
import android.os.StrictMode.ThreadPolicy;
import androidx.annotation.VisibleForTesting;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.concurrent.ThreadFactory;

public class Executors {

  // Number of lightweight executor threads is dynamic. See #lightweightThreadCount()
  private static final int NUM_BACKGROUND_THREADS = 4;
  public static final ThreadPolicy LIGHTWEIGHT_POLICY =
      new ThreadPolicy.Builder().detectAll().penaltyLog().build();
  public static final ThreadPolicy BACKGROUND_POLICY =
      new ThreadPolicy.Builder().permitAll().build();

  private static ListeningExecutorService backgroundListeningExecutor;
  private static ListeningExecutorService lightweightListeningExecutor;
  private static ListeningScheduledExecutorService scheduledExecutor;

  static {
    backgroundListeningExecutor = createFixed("Background", NUM_BACKGROUND_THREADS,
        Process.THREAD_PRIORITY_BACKGROUND, BACKGROUND_POLICY);
    lightweightListeningExecutor = createFixed(
        "Lightweight", lightweightThreadCount(), Process.THREAD_PRIORITY_DEFAULT,
        LIGHTWEIGHT_POLICY);
    scheduledExecutor = createScheduled("Scheduled", NUM_BACKGROUND_THREADS,
        Process.THREAD_PRIORITY_BACKGROUND, BACKGROUND_POLICY);
  }

  public static ListeningExecutorService getBackgroundListeningExecutor() {
    return backgroundListeningExecutor;
  }

  public static ListeningExecutorService getLightweightListeningExecutor() {
    return lightweightListeningExecutor;
  }

  public static ListeningScheduledExecutorService getScheduledExecutor() {
    return scheduledExecutor;
  }

  @VisibleForTesting
  public static void setBackgroundListeningExecutor(
      ListeningExecutorService backgroundListeningExecutor) {
    Executors.backgroundListeningExecutor = backgroundListeningExecutor;
  }

  @VisibleForTesting
  public static void setLightweightListeningExecutor(
      ListeningExecutorService lightweightListeningExecutor) {
    Executors.lightweightListeningExecutor = lightweightListeningExecutor;
  }

  @VisibleForTesting
  public static void setScheduledExecutor(
      ListeningScheduledExecutorService scheduledExecutor) {
    Executors.scheduledExecutor = scheduledExecutor;
  }

  private static ListeningExecutorService createFixed(
      String name, int count, int priority, StrictMode.ThreadPolicy policy) {
    return MoreExecutors.listeningDecorator(
        java.util.concurrent.Executors
            .newFixedThreadPool(count, createThreadFactory(name, priority, policy)));
  }

  private static ListeningScheduledExecutorService createScheduled(
      String name, int minCount, int priority, StrictMode.ThreadPolicy policy) {
    return MoreExecutors.listeningDecorator(
        java.util.concurrent.Executors
            .newScheduledThreadPool(minCount, createThreadFactory(name, priority, policy)));
  }

  private static ThreadFactory createThreadFactory(
      String name, int priority, StrictMode.ThreadPolicy policy) {
    return new ThreadFactoryBuilder()
        .setDaemon(true)
        .setNameFormat(name + " #%d")
        .setThreadFactory(
            runnable ->
                new Thread(
                    () -> {
                      StrictMode.setThreadPolicy(policy);
                      // Despite what the class name might imply, the Process class
                      // operates on the current thread.
                      Process.setThreadPriority(priority);
                      runnable.run();
                    }))
        .build();
  }

  public static int lightweightThreadCount() {
    // Always use at least two threads.
    return Math.max(2, Runtime.getRuntime().availableProcessors() - 2);
  }

}
