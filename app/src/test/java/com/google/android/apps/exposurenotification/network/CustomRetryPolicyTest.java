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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.android.volley.Header;
import com.android.volley.NetworkResponse;
import com.android.volley.VolleyError;
import com.google.android.apps.exposurenotification.testsupport.FakeClock;
import com.google.common.collect.ImmutableList;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import java.util.List;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.threeten.bp.Duration;
import org.threeten.bp.ZoneOffset;
import org.threeten.bp.format.DateTimeFormatter;

/**
 * Tests for {@link CustomRetryPolicy}.
 *
 * <p>This test is actually rather unsatisfying. It tests that {@link CustomRetryPolicy} works the
 * way we *think* it should work, not how Volley *actually* requires it to work. Attempts to do an
 * integration-style test involving a real Volley RequestQueue and other constructs failed due to
 * difficulties testing with Loopers.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner.class)
@Config(application = HiltTestApplication.class)
public class CustomRetryPolicyTest {

  private static final DateTimeFormatter RETRY_TIME_FORMAT = DateTimeFormatter.RFC_1123_DATE_TIME;

  private final FakeClock clock = new FakeClock();

  @Test
  public void http4xx_shouldNotRetry_exceptFor429() {
    for (int httpStatus = 400; httpStatus < 500; httpStatus++) {
      if (httpStatus == 429) {
        continue;
      }
      assertNoRetryForStatus(httpStatus);
    }
  }

  @Test
  public void http5xx_shouldRetry() throws Exception {
    for (int httpStatus = 500; httpStatus < 600; httpStatus++) {
      assertRetryForStatus(httpStatus);
    }
  }

  @Test
  public void http500_shouldRetryThreeTimes_thenFail() throws Exception {
    // GIVEN
    CustomRetryPolicy policy = new CustomRetryPolicy(clock);

    // WHEN
    policy.retry(errorOf(500));
    policy.retry(errorOf(500));
    policy.retry(errorOf(500));
    ThrowingRunnable failure = () -> policy.retry(errorOf(500));

    // THEN
    assertThrows(VolleyError.class, failure);
    assertThat(policy.getCurrentRetryCount()).isEqualTo(3);
  }

  @Test
  public void http429_shouldRetryAtTimeDesignatedByResponseHeader() throws Exception {
    // GIVEN
    CustomRetryPolicy policy = new CustomRetryPolicy(clock);
    // The server's retry header format has granularity of seconds, so we must delay at least one
    // second from now(). It also needs to be different from the default delay.
    int defaultDelayMs = policy.getCurrentTimeout();
    // Needs to be rounded to seconds
    Duration newDelay = Duration.ofSeconds(Duration.ofMillis(defaultDelayMs + 3000).getSeconds());
    String retryTime = RETRY_TIME_FORMAT.format(clock.now().plus(newDelay).atZone(ZoneOffset.UTC));

    // WHEN
    policy.retry(errorOf(429, ImmutableList.of(new Header("X-Retry-After", retryTime))));

    // THEN
    assertThat(policy.getCurrentRetryCount()).isEqualTo(1);
    assertThat(policy.getCurrentTimeout()).isEqualTo(newDelay.toMillis());
  }

  @Test
  public void http429_shouldRetryOnce() throws Exception {
    // GIVEN
    CustomRetryPolicy policy = new CustomRetryPolicy(clock);
    int defaultDelayMs = policy.getCurrentTimeout();
    Duration retryDelay = Duration.ofMillis(defaultDelayMs + 3000);
    String retryTime =
        RETRY_TIME_FORMAT.format(clock.now().plus(retryDelay).atZone(ZoneOffset.UTC));

    // WHEN
    // Same test as http429_shouldRetryAtTimeDesignatedByResponseHeader(), but we attempt twice.
    policy.retry(errorOf(429, ImmutableList.of(new Header("X-Retry-After", retryTime))));
    ThrowingRunnable failure =
        () -> policy.retry(errorOf(429, ImmutableList.of(new Header("X-Retry-After", retryTime))));

    // THEN
    // The num retries should still be 1.
    assertThat(policy.getCurrentRetryCount()).isEqualTo(1);
    // And the second attempt should fail.
    assertThrows(VolleyError.class, failure);
  }

  @Test
  public void http429_headerMatchingShouldBeCaseInsensitive() throws Exception {
    // GIVEN
    CustomRetryPolicy policy = new CustomRetryPolicy(clock);
    int defaultDelayMs = policy.getCurrentTimeout();
    // Needs to be rounded to seconds
    Duration newDelay = Duration.ofSeconds(Duration.ofMillis(defaultDelayMs + 3000).getSeconds());
    String retryTime = RETRY_TIME_FORMAT.format(clock.now().plus(newDelay).atZone(ZoneOffset.UTC));

    // WHEN
    // Same test as http429_shouldRetryAtTimeDesignatedByResponseHeader, but with a case-jumbled
    // header key.
    policy.retry(errorOf(429, ImmutableList.of(new Header("x-RETRY-afteR", retryTime))));

    // THEN
    assertThat(policy.getCurrentRetryCount()).isEqualTo(1);
    assertThat(policy.getCurrentTimeout()).isEqualTo(newDelay.toMillis());
  }

  private static VolleyError errorOf(int httpStatus) throws Exception {
    return errorOf(httpStatus, ImmutableList.of());
  }

  private static VolleyError errorOf(int httpStatus, List<Header> headers) throws Exception {
    NetworkResponse networkResponse = new NetworkResponse(
        httpStatus,
        new byte[]{},
        false, // notModified
        0L, // networkTimeMs
        headers);
    return new VolleyError(networkResponse);
  }

  private void assertRetryForStatus(int httpStatus) throws Exception {
    // WHEN
    // This should not throw anything.
    CustomRetryPolicy policy = new CustomRetryPolicy(clock);
    policy.retry(errorOf(httpStatus));

    // THEN
    assertThat(policy.getCurrentRetryCount()).isEqualTo(1);
  }

  private void assertNoRetryForStatus(int httpStatus) {
    // WHEN
    CustomRetryPolicy policy = new CustomRetryPolicy(clock);
    ThrowingRunnable failure = () -> policy.retry(errorOf(httpStatus));

    // THEN
    assertThrows(VolleyError.class, failure);
    assertThat(policy.getCurrentRetryCount()).isEqualTo(0);
  }
}
