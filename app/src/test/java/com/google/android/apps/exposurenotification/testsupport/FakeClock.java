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

package com.google.android.apps.exposurenotification.testsupport;

import com.google.android.apps.exposurenotification.common.time.Clock;
import org.threeten.bp.Duration;
import org.threeten.bp.Instant;

/**
 * A very simple fake {@link Clock} providing values in place of
 * {@link System#currentTimeMillis()} or {@link Instant#now()}.
 */
public class FakeClock implements Clock {
  private Instant now;

  public FakeClock() {
    this(Instant.ofEpochMilli(100_000_000_000L));
  }

  public FakeClock(Instant now) {
    this.now = now;
  }

  @Override
  public long currentTimeMillis() {
    return now.toEpochMilli();
  }

  @Override
  public Instant now() {
    return now;
  }

  public void advance() {
    advanceBy(Duration.ofMillis(1));
  }

  public void advanceBy(Duration increment) {
    now = now.plus(increment);
  }
    
  public void set(Instant time) {
    now = time;
  }
}