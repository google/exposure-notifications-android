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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.testing.TestingExecutors;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * Testing for the {@link TaskToFutureAdapter} helper class.
 */
@RunWith(RobolectricTestRunner.class)
public class TaskToFutureTest {

  @Test
  public void getFutureWithTimeout_forCanceled() {
    Task<String> task = Tasks.forCanceled();

    ListenableFuture<String> listenableFuture =
        TaskToFutureAdapter.getFutureWithTimeout(task, 10, TimeUnit.SECONDS,
            TestingExecutors.sameThreadScheduledExecutor());

    assertThrows(CancellationException.class, () -> listenableFuture.get(20, TimeUnit.SECONDS));
  }

  @Test
  public void getFutureWithTimeout_forException_throwsException()
      throws TimeoutException, InterruptedException {
    SomeException someException = new SomeException();
    Task<String> task = Tasks.forException(someException);

    ListenableFuture<String> listenableFuture =
        TaskToFutureAdapter.getFutureWithTimeout(task, 10, TimeUnit.SECONDS,
            TestingExecutors.sameThreadScheduledExecutor());

    try {
      listenableFuture.get(20, TimeUnit.SECONDS);
      fail("Didn't throw ExecutionException");
    } catch (ExecutionException expected) {
      assertThat(expected.getCause()).isInstanceOf(SomeException.class);
    }
  }

  @Test
  public void getFutureWithTimeout_forOverrun_isCancelled() {
    Task<String> task = Tasks.call(AppExecutors.getBackgroundExecutor(), () -> {
      Thread.sleep(5000L);
      return "";
    });

    ListenableFuture<String> listenableFuture =
        TaskToFutureAdapter.getFutureWithTimeout(task, 1, TimeUnit.SECONDS,
            TestingExecutors.sameThreadScheduledExecutor());

    assertThrows(ExecutionException.class, () -> listenableFuture.get(10, TimeUnit.SECONDS));
    assertThat(listenableFuture.isCancelled());
  }

  @Test
  public void getFutureWithTimeout_forSuccess()
      throws ExecutionException, InterruptedException, TimeoutException {
    final String success = "Success!";
    Task<String> task = Tasks.forResult(success);

    ListenableFuture<String> listenableFuture =
        TaskToFutureAdapter.getFutureWithTimeout(task, 10, TimeUnit.SECONDS,
            TestingExecutors.sameThreadScheduledExecutor());

    assertThat(listenableFuture.get(20, TimeUnit.SECONDS)).isEqualTo(success);
  }

  private static class SomeException extends Exception {

  }

}
