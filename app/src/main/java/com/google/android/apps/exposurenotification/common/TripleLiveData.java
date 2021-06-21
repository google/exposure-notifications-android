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
import kotlin.Triple;

/**
 * {@link MediatorLiveData} subclass which observes a triad of {@code LiveData} objects and reacts
 * when {@code onChanged} event is triggered for one of them.
 */
public class TripleLiveData<R, S, T> extends MediatorLiveData<Triple<R, S, T>> {

  private R firstSource = null;
  private S secondSource = null;
  private T thirdSource = null;

  /**
   * Creates a new {@code TripleLiveData} object that observes the given triad of {@link LiveData}
   * objects and emits the value when {@code onChanged} is triggered for one of them.
   *
   * Note that to get the first update triggered all the values emitted by observed LiveDatas must
   * be non-null.
   *
   * @param firstLiveData  First {@code LiveData} to observe.
   * @param secondLiveData Second {@code LiveData} to observe.
   * @param thirdLiveData  Third {@code LiveData} to observe.
   */
  @MainThread
  public static <R, S, T> TripleLiveData<R, S, T> of(
      @NonNull LiveData<R> firstLiveData, @NonNull LiveData<S> secondLiveData,
      @NonNull LiveData<T> thirdLiveData) {
    return new TripleLiveData<>(firstLiveData, secondLiveData, thirdLiveData);
  }

  @MainThread
  public void observe(
      @NonNull LifecycleOwner owner,
      @NonNull TripleLiveData.TripleObserver<? super R, ? super S, ? super T> tripleObserver) {
    super.observe(owner, triad ->
        tripleObserver.onChanged(triad.getFirst(), triad.getSecond(), triad.getThird()));
  }

  @MainThread
  public void observeForever(
      @NonNull TripleLiveData.TripleObserver<? super R, ? super S, ? super T> tripleObserver) {
    super.observeForever(triad ->
        tripleObserver.onChanged(triad.getFirst(), triad.getSecond(), triad.getThird()));
  }

  /**
   * This constructor is only for the internal usage. To create a new TripleLiveData, use
   * {@code TripleLiveData.of(firstLiveData, secondLiveData, thirdLiveData)}.
   * */
  private TripleLiveData(@NonNull LiveData<R> firstLiveData, @NonNull LiveData<S> secondLiveData,
      @NonNull LiveData<T> thirdLiveData) {
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
  }

  private void update() {
    R currentFirstSource = firstSource;
    S currentSecondSource = secondSource;
    T currentThirdSource = thirdSource;

    if (currentFirstSource != null && currentSecondSource != null && currentThirdSource != null) {
      super.setValue(new Triple<>(currentFirstSource, currentSecondSource, currentThirdSource));
    }
  }

  /**
   * A simple callback that can receive updates from a triad of {@link LiveData}s.
   *
   * @param <R> The type of the first parameter.
   * @param <S> The type of the second parameter.
   * @param <T> The type of the third parameter.
   */
  public interface TripleObserver<R, S, T> {

    /**
     * Called when one of the data objects have changed.
     *
     * @param r The updated value of the first data object.
     * @param s The updated value of the second data object.
     * @param t The updated value of the third data object.
     */
    void onChanged(R r, S s, T t);
  }

}
