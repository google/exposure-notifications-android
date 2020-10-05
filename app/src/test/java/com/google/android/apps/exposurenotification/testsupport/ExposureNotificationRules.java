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

import static android.os.Looper.getMainLooper;
import static com.google.common.truth.Truth.assertWithMessage;
import static org.robolectric.Shadows.shadowOf;

import androidx.annotation.Nullable;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import dagger.hilt.android.testing.HiltAndroidRule;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.mockito.MockitoAnnotations;

public class ExposureNotificationRules implements TestRule {

  private final RuleChain chain;

  // Required rules, applied to all tests.
  private final HiltAndroidRule hiltRule;


  private ExposureNotificationRules(Builder builder) {
    chain = builder.chain;
    hiltRule = builder.hiltRule;
  }

  @Override
  public Statement apply(Statement base, Description description) {
    return chain.apply(base, description);
  }

  public HiltAndroidRule hilt() {
    return hiltRule;
  }

  public static ExposureNotificationRules.Builder forTest(Object test) {
    return new Builder(test);
  }

  /**
   * Builder for {@link ExposureNotificationRules}
   */
  public static class Builder {

    private final Object testInstance;
    private final HiltAndroidRule hiltRule;
    private final InstantTaskExecutorRule instantTaskExecutorRule;
    private final LooperIdleRule looperIdleRule;

    private MockRule mocks;
    private RuleChain chain;

    public Builder(Object testInstance) {
      this.testInstance = testInstance;
      hiltRule = new HiltAndroidRule(testInstance);
      instantTaskExecutorRule = new InstantTaskExecutorRule();
      looperIdleRule = new LooperIdleRule();
    }

    public Builder withMocks() {
      mocks = new MockRule(testInstance);
      return this;
    }

    public ExposureNotificationRules build() {
      chain = RuleChain.outerRule(hiltRule).around(instantTaskExecutorRule).around(looperIdleRule);
      if (mocks != null) {
        chain = chain.around(mocks);
      }
      return new ExposureNotificationRules(this);
    }
  }

  /**
   * A testrule that inits Mockito mocks before tests.
   */
  public static class MockRule implements TestRule {

    private final Object target;

    public MockRule(Object target) {
      this.target = target;
    }

    public Statement apply(final Statement base, Description description) {
      return new Statement() {
        @Override
        public void evaluate() throws Throwable {
          MockitoAnnotations.initMocks(target);
          base.evaluate();
        }
      };
    }
  }

  /**
   * A {@link TestRule} to flush tasks on the main looper before ending the test.
   */
  public static class LooperIdleRule implements TestRule {
    public Statement apply(final Statement base, Description description) {
      return new Statement() {
        @Override
        public void evaluate() throws Throwable {
          base.evaluate();
          shadowOf(getMainLooper()).idle();
        }
      };
    }
  }
}
