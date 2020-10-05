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

import com.android.volley.RetryPolicy;
import com.android.volley.VolleyError;
import com.google.android.apps.exposurenotification.common.time.Clock;
import com.google.common.base.Optional;
import java.util.HashMap;
import java.util.Map;
import org.threeten.bp.Duration;
import org.threeten.bp.Instant;
import org.threeten.bp.ZoneOffset;
import org.threeten.bp.format.DateTimeFormatter;

/**
 * A customised {@link RetryPolicy} that applies rules based on the http status of the response, and
 * in some cases the response headers.
 */
public class CustomRetryPolicy implements RetryPolicy {

  private static final int SERVER_ERR = 500;
  private static final int SERVER_ERR_NUM_RETRIES = 3;
  private static final int RATE_LIMITED = 429;
  // We lowercase the header keys to match case-insensitively.
  private static final String RATE_LIMITED_RETRY_HEADER = "x-retry-after";
  private static final DateTimeFormatter RATE_LIMITED_HEADER_FORMAT =
      DateTimeFormatter.RFC_1123_DATE_TIME;

  private final Clock clock;
  private Duration delay = Duration.ofSeconds(3);
  private int currentRetryCount = 0;

  public CustomRetryPolicy(Clock clock) {
    this.clock = clock;
  }


  @Override
  public int getCurrentTimeout() {
    return (int) delay.toMillis();
  }

  @Override
  public int getCurrentRetryCount() {
    return currentRetryCount;
  }

  @Override
  public void retry(VolleyError error) throws VolleyError {
    int httpStatus = VolleyUtils.getHttpStatus(error);

    // Rate limited requests retry once.
    if (httpStatus == RATE_LIMITED && currentRetryCount < 1) {
      currentRetryCount++;
      Optional<Instant> retryTime = parseRetryTime(error);
      if (retryTime.isPresent() && retryTime.get().isAfter(clock.now())) {
        // Threre's a small bug here in that: We're deciding how long to delay based on the current
        // time, but the Volley stack will start counting that delay at some future time. It'll be
        // close enough for our purposes though.
        delay = Duration.between(clock.now(), retryTime.get());
      }
      // If we didn't get a useful retry-time from the response header, we just keep whatever delay
      // we had.
      return;
    }

    // Server errors retry SERVER_ERR_NUM_RETRIES times.
    if (httpStatus >= SERVER_ERR && currentRetryCount < SERVER_ERR_NUM_RETRIES) {
      currentRetryCount++;
      return;
    }

    throw error;
  }

  private static Optional<Instant> parseRetryTime(VolleyError err) {
    try {
      Map<String, String> headers = lowercasedKeys(err.networkResponse.headers);
      if (headers.containsKey(RATE_LIMITED_RETRY_HEADER)) {
        return Optional.of(
            Instant.from(RATE_LIMITED_HEADER_FORMAT
                .withZone(ZoneOffset.UTC)
                .parse(headers.get(RATE_LIMITED_RETRY_HEADER))));
      }
    } catch (Exception e) {
      // Swallow all failures and return absent.
    }
    return Optional.absent();
  }

  private static Map<String, String> lowercasedKeys(Map<String, String> headers) {
    Map<String, String> lowercased = new HashMap<>();
    for (Map.Entry<String, String> e : headers.entrySet()) {
      lowercased.put(e.getKey().toLowerCase(), e.getValue());
    }
    return lowercased;
  }
}
