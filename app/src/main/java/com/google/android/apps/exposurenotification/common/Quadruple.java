/*
 * Copyright 2022 Google LLC
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

/**
 *  Represents a quadruple of values
 */
public class Quadruple<A, B, C, D> {
  private final A first;
  private final B second;
  private final C third;
  private final D fourth;

  public Quadruple(A first, B second, C third, D fourth) {
    this.first = first;
    this.second = second;
    this.third = third;
    this.fourth = fourth;
  }

  public A getFirst() { return first; }
  public B getSecond() { return second; }
  public C getThird() { return third; }
  public D getFourth() { return fourth; }

  @Override
  public String toString() {
    return "(" + first +
        ", " + second +
        ", " + third +
        ", " + fourth + ")";
  }
}