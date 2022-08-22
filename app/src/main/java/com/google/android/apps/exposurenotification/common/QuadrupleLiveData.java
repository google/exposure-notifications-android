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

package com.google.android.apps.exposurenotification.common;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

/**
 * {@link MediatorLiveData} subclass which observes a quadruple of {@code LiveData} objects and
 * reacts when {@code onChanged} event is triggered for one of them.
 */
public class QuadrupleLiveData<R, S, T, U> extends MediatorLiveData<Quadruple<R, S, T, U>> {

  private R firstSource = null;
  private S secondSource = null;
  private T thirdSource = null;
  private U fourthSource = null;

  /**
   * This constructor is only for the internal usage. To create a new QuadrupleLiveData, use {@code
   * QuadrupleLiveData.of(firstLiveData, secondLiveData, thirdLiveData, fourthLiveData)}.
   */
  private QuadrupleLiveData(@NonNull LiveData<R> firstLiveData, @NonNull LiveData<S> secondLiveData,
      @NonNull LiveData<T> thirdLiveData, @NonNull LiveData<U> fourthLiveData) {
    super.addSource(firstLiveData, firstSource -> {
      this.firstSource = firstSource;
      update();
    });

    super.addSource(secondLiveData, secondSource -> {
      this.secondSource = secondSource;
      update();
    });

    super.addSource(thirdLiveData, thirdSource -> {
      this.thirdSource = thirdSource;
      update();
    });

    super.addSource(fourthLiveData, fourthSource -> {
      this.fourthSource = fourthSource;
      update();
    });
  }

  /**
   * Creates a new {@code QuadrupleLiveData} object that observes the given quadruple of
   * {@link LiveData} objects and emits the value when {@code onChanged} is triggered for one
   * of them.
   * <p>
   * Note that to get the first update triggered all the values emitted by observed LiveDatas must
   * be non-null.
   *
   * @param firstLiveData  First {@code LiveData} to observe.
   * @param secondLiveData Second {@code LiveData} to observe.
   * @param thirdLiveData  Third {@code LiveData} to observe.
   * @param fourthLiveData Fourth {@code LiveData} to observe.
   */
  @MainThread
  public static <R, S, T, U> QuadrupleLiveData<R, S, T, U> of(
      @NonNull LiveData<R> firstLiveData, @NonNull LiveData<S> secondLiveData,
      @NonNull LiveData<T> thirdLiveData, @NonNull LiveData<U> fourthLiveData) {
    return new QuadrupleLiveData<>(firstLiveData, secondLiveData, thirdLiveData, fourthLiveData);
  }

  @MainThread
  public void observe(
      @NonNull LifecycleOwner owner,
      @NonNull QuadrupleObserver<? super R, ? super S, ? super T, ? super U> quadrupleObserver) {
    super.observe(owner, quadruple ->
        quadrupleObserver.onChanged(quadruple.getFirst(), quadruple.getSecond(),
            quadruple.getThird(), quadruple.getFourth()));
  }

  @MainThread
  public void observeForever(
      @NonNull QuadrupleObserver<? super R, ? super S, ? super T, ? super U> quadrupleObserver) {
    super.observeForever(quadruple ->
        quadrupleObserver.onChanged(quadruple.getFirst(), quadruple.getSecond(),
            quadruple.getThird(), quadruple.getFourth()));
  }

  private void update() {
    R currentFirstSource = firstSource;
    S currentSecondSource = secondSource;
    T currentThirdSource = thirdSource;
    U currentFourthSource = fourthSource;

    if (currentFirstSource != null && currentSecondSource != null && currentThirdSource != null
        && currentFourthSource != null) {
      super.setValue(new Quadruple<>(currentFirstSource, currentSecondSource, currentThirdSource,
          currentFourthSource));
    }
  }

  /**
   * A simple callback that can receive updates from four {@link LiveData}s.
   *
   * @param <R> The type of the first parameter.
   * @param <S> The type of the second parameter.
   * @param <T> The type of the third parameter.
   * @param <U> The type of the fourth parameter.
   */
  public interface QuadrupleObserver<R, S, T, U> {

    /**
     * Called when one of the data objects have changed.
     *
     * @param r The updated value of the first data object.
     * @param s The updated value of the second data object.
     * @param t The updated value of the third data object.
     * @param u The updated value of the fourth data object.
     */
    void onChanged(R r, S s, T t, U u);
  }

}
