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

import android.os.Process;
import android.os.StrictMode;
import android.os.StrictMode.ThreadPolicy;
import com.google.android.apps.exposurenotification.common.Qualifiers.BackgroundExecutor;
import com.google.android.apps.exposurenotification.common.Qualifiers.LightweightExecutor;
import com.google.android.apps.exposurenotification.common.Qualifiers.ScheduledExecutor;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.components.ApplicationComponent;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import javax.inject.Singleton;

/**
 * Module providing singleton executors in several flavors.
 *
 * <p>This module should be excluded from any tests that touch any of our async code. Instead, such
 * tests should use direct executors such as MoreExecutors.newDirectExecutorService() or
 * TestingExecutors.sameThreadScheduledExecutor().
 */
@Module
@InstallIn(ApplicationComponent.class)
public class ExecutorsModule {

  // Number of lightweight executor threads is dynamic. See #lightweightThreadCount()
  private static final int NUM_BACKGROUND_THREADS = 4;
  private static final ThreadPolicy LIGHTWEIGHT_POLICY =
      new ThreadPolicy.Builder().detectAll().penaltyLog().build();
  private static final ThreadPolicy BACKGROUND_POLICY =
      new ThreadPolicy.Builder().permitAll().build();

  @Provides
  @BackgroundExecutor
  @Singleton
  public ListeningExecutorService provideBackgroundListeningExecutor() {
    return createFixed(
        "Background",
        backgroundThreadCount(),
        Process.THREAD_PRIORITY_BACKGROUND,
        BACKGROUND_POLICY);
  }

  @Provides
  @LightweightExecutor
  @Singleton
  public ListeningExecutorService provideLightweightListeningExecutor() {
    return createFixed(
        "Lightweight",
        lightweightThreadCount(),
        Process.THREAD_PRIORITY_DEFAULT,
        LIGHTWEIGHT_POLICY);
  }

  @Provides
  @BackgroundExecutor
  @Singleton
  public ExecutorService provideBackgroundExecutor(
      @BackgroundExecutor ListeningExecutorService executorService) {
    return executorService;
  }

  @Provides
  @LightweightExecutor
  @Singleton
  public ExecutorService provideLightweightExecutor(
      @LightweightExecutor ListeningExecutorService executorService) {
    return executorService;
  }

  @Provides
  @ScheduledExecutor
  @Singleton
  public ScheduledExecutorService provideScheduledExecutor() {
    return createScheduled(
        "Scheduled",
        backgroundThreadCount(),
        Process.THREAD_PRIORITY_BACKGROUND,
        BACKGROUND_POLICY);
  }

  private static ListeningExecutorService createFixed(
      String name, int count, int priority, StrictMode.ThreadPolicy policy) {
    return MoreExecutors.listeningDecorator(
        Executors.newFixedThreadPool(count, createThreadFactory(name, priority, policy)));
  }

  private static ListeningScheduledExecutorService createScheduled(
      String name, int minCount, int priority, StrictMode.ThreadPolicy policy) {
    return MoreExecutors.listeningDecorator(
        Executors.newScheduledThreadPool(minCount, createThreadFactory(name, priority, policy)));
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

  private static int lightweightThreadCount() {
    // Always use at least two threads.
    return Math.max(2, Runtime.getRuntime().availableProcessors() - 2);
  }

  private static int backgroundThreadCount() {
    return NUM_BACKGROUND_THREADS;
  }
}
