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

import android.util.Pair;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

/**
 * {@link MediatorLiveData} subclass which observes a pair of {@code LiveData} objects and reacts
 * when {@code onChanged} events are triggered for both of them.
 *
 */
public class PairLiveData<S, T> extends MediatorLiveData<Pair<S, T>> {

  private S firstSource = null;
  private T secondSource = null;

  /**
   * Creates a new {@code PairLiveData} object that observes the given pair of {@link LiveData}
   * objects and emits the value when {@code onChanged} is triggered for both of them.
   *
   * @param firstLiveData  First {@code LiveData} to observe.
   * @param secondLiveData Second {@code LiveData} to observe.
   */
  @MainThread
  public static <S, T> PairLiveData<S, T> of(
      @NonNull LiveData<S> firstLiveData, @NonNull LiveData<T> secondLiveData) {
    return new PairLiveData<>(firstLiveData, secondLiveData);
  }

  @MainThread
  public void observe(
      @NonNull LifecycleOwner owner, @NonNull PairObserver<? super S, ? super T> pairObserver) {
    super.observe(owner, pair -> pairObserver.onChanged(pair.first, pair.second));
  }

  @MainThread
  public void observeForever(@NonNull PairObserver<? super S, ? super T> pairObserver) {
    super.observeForever(pair -> pairObserver.onChanged(pair.first, pair.second));
  }

  /**
   * This constructor is only for the internal usage. To create a new PairLiveData, use
   * {@code PairLiveData.of(firstLiveData, secondLiveData)}.
   * */
  private PairLiveData(@NonNull LiveData<S> firstLiveData, @NonNull LiveData<T> secondLiveData) {
    super.addSource(firstLiveData, firstSource -> {
      this.firstSource = firstSource;
      update();
    });

    super.addSource(secondLiveData, secondSource -> {
      this.secondSource = secondSource;
      update();
    });
  }

  private void update() {
    S currentFirstSource = firstSource;
    T currentSecondSource = secondSource;

    if (currentFirstSource != null && currentSecondSource != null) {
      super.setValue(Pair.create(currentFirstSource, currentSecondSource));
    }
  }

  /**
   * A simple callback that can receive updates from a pair of {@link LiveData}s.
   *
   * @param <S> The type of the first parameter.
   * @param <T> The type of the second parameter.
   */
  public interface PairObserver<S, T> {

    /**
     * Called when both data objects have changed.
     *
     * @param s The updated value of the first data object.
     * @param t The updated value of the second data object.
     */
    void onChanged(S s, T t);
  }

}
